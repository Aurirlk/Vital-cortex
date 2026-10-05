package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.AppointmentMapper;
import cn.kmbeast.mapper.DoctorScheduleMapper;
import cn.kmbeast.mapper.HospitalDoctorMapper;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.DoctorSchedule;
import cn.kmbeast.pojo.vo.AppointmentVO;
import cn.kmbeast.pojo.vo.DoctorVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 医生端工作台服务单元测试（2026-10-04）
 *
 * <p>本测试类锁定一条<b>不可让步</b>的安全约束：
 * <b>所有查询都必须用 ThreadLocal 里的医生 ID，绝不能采用任何外部传入的 doctorId</b>。
 * 一旦有人为了「方便」给方法加上 doctorId 入参，医生即可篡改参数查看他人接诊记录。
 *
 * <p>因此用 {@link ArgumentCaptor} 反查实际传给 Mapper 的值。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("医生端工作台（越权防护）")
class DoctorWorkServiceIsolationTest {

    private static final Integer MY_ID = 3001;
    private static final Integer OTHER_ID = 3002;

    @Mock
    private AppointmentMapper appointmentMapper;

    @Mock
    private DoctorScheduleMapper scheduleMapper;

    @Mock
    private HospitalDoctorMapper doctorMapper;

    @InjectMocks
    private DoctorWorkServiceImpl service;

    @BeforeEach
    void setUp() {
        // 模拟 JwtInterceptor 解析 token 后写入的身份
        LocalThreadHolder.setUserId(MY_ID, 3);
        when(scheduleMapper.queryByDoctorAndRange(any(), any(), any())).thenReturn(List.of());
        when(appointmentMapper.queryByDoctorAndDateRange(any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        LocalThreadHolder.clear();
    }

    // ---------------------------------------------------------------- 查询：医生 ID 来源

    @Test
    @DisplayName("我的接诊：传给 Mapper 的 doctorId 必须来自 ThreadLocal")
    void myAppointmentsShouldUseThreadLocalDoctorId() {
        service.myAppointments(null, null, null);

        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(appointmentMapper).queryByDoctorAndDateRange(
                captor.capture(), any(), any(), any());
        assertEquals(MY_ID, captor.getValue(),
                "必须使用当前登录医生的 ID，否则医生可查看他人接诊记录");
    }

    @Test
    @DisplayName("我的排班：传给 Mapper 的 doctorId 必须来自 ThreadLocal")
    void mySchedulesShouldUseThreadLocalDoctorId() {
        service.mySchedules(null, null);

        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(scheduleMapper).queryByDoctorAndRange(captor.capture(), any(), any());
        assertEquals(MY_ID, captor.getValue());
    }

    @Test
    @DisplayName("概览统计：传给 Mapper 的 doctorId 必须来自 ThreadLocal")
    void overviewShouldUseThreadLocalDoctorId() {
        when(appointmentMapper.summaryByDoctor(anyInt())).thenReturn(new HashMap<>());

        service.overview();

        verify(appointmentMapper).summaryByDoctor(MY_ID);
    }

    // ---------------------------------------------------------------- 就诊确认：越权

    @Test
    @DisplayName("完成接诊：SQL 需带上自己的 doctorId —— 越权操作影响 0 行")
    void completeShouldPassOwnDoctorId() {
        when(appointmentMapper.completeByDoctor(10, MY_ID)).thenReturn(1);

        Result<Void> r = service.completeAppointment(10);

        assertEquals(200, r.getCode());
        verify(appointmentMapper).completeByDoctor(10, MY_ID);
    }

    @Test
    @DisplayName("完成他人预约：影响 0 行时返回失败，不泄露预约是否存在")
    void completeOthersAppointmentShouldFail() {
        // SQL 里 doctor_id 条件不匹配 → 影响 0 行
        when(appointmentMapper.completeByDoctor(99, MY_ID)).thenReturn(0);

        Result<Void> r = service.completeAppointment(99);

        assertNotEquals(200, r.getCode());
        assertTrue(r.getMsg().contains("不存在") || r.getMsg().contains("状态"),
                "对外提示应含糊，不能让医生通过提示差异判断该预约是否属于他人，实际: " + r.getMsg());
        assertFalse(r.getMsg().contains(String.valueOf(OTHER_ID)));
    }

    @Test
    @DisplayName("重复提交已完成预约：第二次影响 0 行，返回失败（幂等保护）")
    void completeTwiceShouldBeRejected() {
        when(appointmentMapper.completeByDoctor(10, MY_ID)).thenReturn(1, 0);

        assertEquals(200, service.completeAppointment(10).getCode());
        assertNotEquals(200, service.completeAppointment(10).getCode());
    }

    @Test
    @DisplayName("缺少预约 ID：直接拒绝，不打库")
    void completeWithoutIdShouldReject() {
        Result<Void> r = service.completeAppointment(null);

        assertNotEquals(200, r.getCode());
        verify(appointmentMapper, never()).completeByDoctor(anyInt(), anyInt());
    }

    // ---------------------------------------------------------------- 停诊：归属校验

    @Test
    @DisplayName("停用自己的排班：归属校验通过后才执行更新")
    void disableOwnScheduleShouldSucceed() {
        when(scheduleMapper.countByIdAndDoctor(4001, MY_ID)).thenReturn(1);

        Result<Void> r = service.disableSchedule(4001);

        assertEquals(200, r.getCode());
        verify(scheduleMapper).update(any(DoctorSchedule.class));

        ArgumentCaptor<DoctorSchedule> captor = ArgumentCaptor.forClass(DoctorSchedule.class);
        verify(scheduleMapper).update(captor.capture());
        assertEquals(4001, captor.getValue().getId());
        assertEquals(0, captor.getValue().getStatus(), "停诊应置 status=0");
    }

    @Test
    @DisplayName("停他人排班：归属校验为 0 时拒绝，且不执行 update")
    void disableOthersScheduleShouldReject() {
        when(scheduleMapper.countByIdAndDoctor(4002, MY_ID)).thenReturn(0);

        Result<Void> r = service.disableSchedule(4002);

        assertNotEquals(200, r.getCode());
        verify(scheduleMapper, never()).update(any(DoctorSchedule.class));
    }

    @Test
    @DisplayName("归属校验返回 null：按拒绝处理，不做乐观放行")
    void disableWithNullOwnershipShouldReject() {
        when(scheduleMapper.countByIdAndDoctor(anyInt(), anyInt())).thenReturn(null);

        Result<Void> r = service.disableSchedule(4003);

        assertNotEquals(200, r.getCode());
        verify(scheduleMapper, never()).update(any(DoctorSchedule.class));
    }

    @Test
    @DisplayName("缺少排班 ID：直接拒绝")
    void disableWithoutIdShouldReject() {
        Result<Void> r = service.disableSchedule(null);

        assertNotEquals(200, r.getCode());
        verify(scheduleMapper, never()).countByIdAndDoctor(anyInt(), anyInt());
    }

    // ---------------------------------------------------------------- 日期区间

    @Test
    @DisplayName("未传日期：默认最近 30 天")
    void nullRangeShouldDefaultTo30Days() {
        service.myAppointments(null, null, null);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(appointmentMapper).queryByDoctorAndDateRange(eq(MY_ID), from.capture(), to.capture(), any());

        assertEquals(29, java.time.temporal.ChronoUnit.DAYS.between(from.getValue(), to.getValue()));
        assertEquals(LocalDate.now(), to.getValue());
    }

    @Test
    @DisplayName("区间倒置：自动交换，不返回空列表")
    void reversedRangeShouldBeSwapped() {
        LocalDate late = LocalDate.now().plusDays(10);
        LocalDate early = LocalDate.now();

        service.myAppointments(late, early, null);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(appointmentMapper).queryByDoctorAndDateRange(eq(MY_ID), from.capture(), to.capture(), any());

        assertTrue(from.getValue().isBefore(to.getValue()), "起止日期应被纠正为升序");
    }

    @Test
    @DisplayName("超长区间被截断到 366 天，防拖库")
    void overlyLongRangeShouldBeTruncated() {
        LocalDate from = LocalDate.now().minusYears(5);

        service.myAppointments(from, LocalDate.now(), null);

        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(appointmentMapper).queryByDoctorAndDateRange(eq(MY_ID), any(), to.capture(), any());

        long span = java.time.temporal.ChronoUnit.DAYS.between(from, to.getValue());
        assertTrue(span <= 366, "区间跨度应被限制，实际: " + span);
    }

    @Test
    @DisplayName("仅传起始日期：自动补 366 天上限")
    void onlyFromDateShouldCapTo() {
        LocalDate from = LocalDate.now();

        service.myAppointments(from, null, null);

        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(appointmentMapper).queryByDoctorAndDateRange(eq(MY_ID), eq(from), to.capture(), any());

        assertEquals(365, java.time.temporal.ChronoUnit.DAYS.between(from, to.getValue()));
    }

    // ---------------------------------------------------------------- 统计健壮性

    @Test
    @DisplayName("无预约时 SUM 返回 null：应转为 0 而不是抛 NPE")
    void nullSummaryShouldBecomeZero() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("totalCount", 0);
        raw.put("todayCount", null);
        raw.put("weekCount", null);
        raw.put("pendingCount", null);
        raw.put("completedCount", null);
        when(appointmentMapper.summaryByDoctor(MY_ID)).thenReturn(raw);
        when(doctorMapper.getById(MY_ID)).thenReturn(null);

        Result<Map<String, Object>> r = service.overview();
        Map<String, Object> data = r.getData();

        assertEquals(200, r.getCode());
        assertEquals(0, data.get("todayCount"));
        assertEquals(0, data.get("weekCount"));
        assertEquals(0, data.get("totalCount"));
    }

    @Test
    @DisplayName("统计返回 BigDecimal：应安全转 int")
    void bigDecimalShouldConvertSafely() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("totalCount", new BigDecimal("42"));
        raw.put("todayCount", "7");     // 脏数据：字符串
        when(appointmentMapper.summaryByDoctor(MY_ID)).thenReturn(raw);
        when(doctorMapper.getById(MY_ID)).thenReturn(null);

        Map<String, Object> data = service.overview().getData();

        assertEquals(42, data.get("totalCount"));
        assertEquals(7, data.get("todayCount"));
    }

    @Test
    @DisplayName("统计查询抛异常：概览仍应返回，不因附属数据失败而整页报错")
    void summaryFailureShouldNotBreakOverview() {
        when(appointmentMapper.summaryByDoctor(MY_ID))
                .thenThrow(new RuntimeException("数据库连接异常"));
        when(doctorMapper.getById(MY_ID)).thenReturn(null);

        Result<Map<String, Object>> r = service.overview();

        assertEquals(200, r.getCode(), "统计失败不应让医生进不了首页");
        assertEquals(0, r.getData().get("todayCount"));
    }

    @Test
    @DisplayName("医生档案查询失败：概览降级为「未知医生」而非报错")
    void profileFailureShouldDegradeGracefully() {
        when(appointmentMapper.summaryByDoctor(MY_ID)).thenReturn(new HashMap<>());
        when(doctorMapper.getById(MY_ID)).thenThrow(new RuntimeException("档案表不存在"));

        Result<Map<String, Object>> r = service.overview();

        assertEquals(200, r.getCode());
        assertEquals("未知医生", r.getData().get("name"));
    }

    @Test
    @DisplayName("概览携带医生身份信息：姓名/职称/科室")
    void overviewShouldIncludeDoctorProfile() {
        when(appointmentMapper.summaryByDoctor(MY_ID)).thenReturn(new HashMap<>());
        DoctorVO vo = new DoctorVO();
        vo.setId(MY_ID);
        vo.setName("张医生");
        vo.setTitle("主任医师");
        vo.setTitleLevel("TITLE");
        vo.setDepartmentName("内科");
        when(doctorMapper.getById(MY_ID)).thenReturn(vo);

        Map<String, Object> data = service.overview().getData();

        assertEquals("张医生", data.get("name"));
        assertEquals("主任医师", data.get("title"));
        assertEquals("内科", data.get("departmentName"));
        assertEquals(MY_ID, data.get("doctorId"));
    }

    // ---------------------------------------------------------------- 空结果

    @Test
    @DisplayName("Mapper 返回 null：应转成空列表而非把 null 交给前端")
    void nullListShouldBecomeEmpty() {
        when(appointmentMapper.queryByDoctorAndDateRange(any(), any(), any(), any()))
                .thenReturn(null);
        when(scheduleMapper.queryByDoctorAndRange(any(), any(), any()))
                .thenReturn(null);

        Result<List<AppointmentVO>> a = service.myAppointments(null, null, null);
        Result<List<DoctorSchedule>> s = service.mySchedules(null, null);

        assertNotNull(a.getData());
        assertTrue(a.getData().isEmpty());
        assertNotNull(s.getData());
        assertTrue(s.getData().isEmpty());
    }

    @Test
    @DisplayName("状态过滤参数原样透传")
    void statusFilterShouldPassThrough() {
        service.myAppointments(null, null, 0);

        verify(appointmentMapper).queryByDoctorAndDateRange(eq(MY_ID), any(), any(), eq(0));
    }
}
