package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.*;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.AppointmentQueryDto;
import cn.kmbeast.pojo.entity.*;
import cn.kmbeast.pojo.vo.AppointmentVO;
import cn.kmbeast.pojo.vo.DoctorVO;
import cn.kmbeast.pojo.vo.MallOrderVO;
import cn.kmbeast.pojo.vo.QuizQuestionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 归属校验（IDOR 修复）回归测试 —— 2026-10-10 新增。
 *
 * <p>背景：本轮审计在挂号、商城、答题三条链路上修复了一批「只按 id 操作、
 * 不校验归属」的越权漏洞，以及「采信前端传入的 patientId」这类冒名问题。
 * 这些修复的共同点是：<b>外部表现没有变化</b>（正常用户照常能用），
 * 所以一旦有人回退代码，接口测试与页面都发现不了 —— 只有把这些契约
 * 写成断言，才守得住。
 *
 * <p>每个用例都同时断言两件事：
 * <ol>
 *   <li>越权请求被拒绝（返回错误）；</li>
 *   <li>越权请求<b>没有产生任何写操作</b>（verify never）——
 *       只判返回值不写库才是真正的拒绝。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("归属校验（IDOR 修复）")
class IdorHardeningTest {

    /** 管理员 */
    private static final int ADMIN_ID = 1;
    /** 受害者（资源归属人） */
    private static final int VICTIM_ID = 8;
    /** 攻击者（当前登录用户） */
    private static final int ATTACKER_ID = 7;

    // ==================== 挂号 ====================

    @Mock private DepartmentMapper departmentMapper;
    @Mock private HospitalDoctorMapper doctorMapper;
    @Mock private DoctorScheduleMapper scheduleMapper;
    @Mock private AppointmentMapper appointmentMapper;
    @Mock private VisitRecordMapper visitRecordMapper;
    /** 2026-10-10：挂号/取消/下单/支付现在会发业务通知，需要这个协作者 */
    @Mock private cn.kmbeast.core.notify.UserNotifier userNotifier;
    @InjectMocks private AppointmentServiceImpl appointmentService;

    // ==================== 商城 ====================

    @Mock private ProductCategoryMapper categoryMapper;
    @Mock private MallProductMapper productMapper;
    @Mock private ShoppingCartMapper cartMapper;
    @Mock private MallOrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private ShippingAddressMapper addressMapper;
    @InjectMocks private MallServiceImpl mallService;

    // ==================== 答题 ====================

    @Mock private QuizQuestionMapper questionMapper;
    @Mock private QuizExamMapper examMapper;
    @Mock private QuizExamQuestionMapper examQuestionMapper;
    @Mock private QuizRecordMapper recordMapper;
    @Mock private QuizAnswerMapper answerMapper;
    @InjectMocks private QuizServiceImpl quizService;

    @AfterEach
    void tearDown() {
        LocalThreadHolder.clear();
    }

    private static Integer okCode() {
        return ApiResult.success().getCode();
    }

    private static Integer errCode() {
        return ApiResult.error("x").getCode();
    }

    // ================================================================
    // 挂号：patientId 必须来自服务端
    // ================================================================

    @Test
    @DisplayName("挂号：patientId/departmentId 一律由服务端派生，不采信请求体")
    void bookAppointmentShouldDerivePatientAndDepartmentOnServer() {
        LocalThreadHolder.setUserId(ATTACKER_ID, 2);

        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setId(5);
        schedule.setDoctorId(3001);
        schedule.setScheduleDate(LocalDate.of(2026, 10, 12));
        schedule.setTimeSlot("morning");
        schedule.setMaxPatients(10);
        schedule.setBookedCount(0);
        schedule.setStatus(1);
        when(scheduleMapper.getById(5)).thenReturn(schedule);

        DoctorVO doctor = new DoctorVO();
        doctor.setId(3001);
        doctor.setDepartmentId(10);
        when(doctorMapper.getById(3001)).thenReturn(doctor);

        when(scheduleMapper.reserveSlot(5)).thenReturn(1);
        when(appointmentMapper.countByScheduleId(5)).thenReturn(2);

        // 攻击者试图以别人（VICTIM_ID）的名义挂号，并伪造科室
        Appointment req = new Appointment();
        req.setScheduleId(5);
        req.setPatientId(VICTIM_ID);
        req.setDepartmentId(999);
        req.setSymptomDescription("头疼");

        Result<Void> result = appointmentService.bookAppointment(req);

        assertEquals(okCode(), result.getCode(), "正常挂号不应被拒绝");

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentMapper).save(captor.capture());
        Appointment saved = captor.getValue();

        assertEquals(ATTACKER_ID, saved.getPatientId(),
                "patientId 必须取自 token，否则可冒充他人挂号（反之前端不传时为 null，NOT NULL 约束直接插入失败）");
        assertEquals(10, saved.getDepartmentId(), "departmentId 必须由医生反查，不能被请求体覆盖");
        assertEquals(3001, saved.getDoctorId());
        assertEquals(LocalDate.of(2026, 10, 12), saved.getAppointmentDate());
        assertEquals(3, saved.getSerialNumber(), "号序应在占号成功之后计算");
        assertEquals(0, saved.getStatus());
    }

    @Test
    @DisplayName("挂号：占号失败（号源已满/已停诊）必须中止，且不能落库")
    void bookAppointmentShouldAbortWhenSlotNotReserved() {
        LocalThreadHolder.setUserId(ATTACKER_ID, 2);

        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setId(5);
        schedule.setDoctorId(3001);
        schedule.setScheduleDate(LocalDate.of(2026, 10, 12));
        schedule.setTimeSlot("morning");
        schedule.setMaxPatients(1);
        schedule.setBookedCount(1);
        schedule.setStatus(1);
        when(scheduleMapper.getById(5)).thenReturn(schedule);

        DoctorVO doctor = new DoctorVO();
        doctor.setId(3001);
        doctor.setDepartmentId(10);
        when(doctorMapper.getById(3001)).thenReturn(doctor);

        // 原子占号返回 0 行 = 数据库层判定「已满」
        when(scheduleMapper.reserveSlot(5)).thenReturn(0);

        Appointment req = new Appointment();
        req.setScheduleId(5);

        Result<Void> result = appointmentService.bookAppointment(req);

        assertEquals(errCode(), result.getCode());
        verify(appointmentMapper, never()).save(any(Appointment.class));
        verify(appointmentMapper, never()).countByScheduleId(anyInt());
    }

    // ================================================================
    // 挂号：查询与取消必须限定归属
    // ================================================================

    @Test
    @DisplayName("预约查询：普通用户强制只看自己，忽略请求体里的 patientId")
    void queryAppointmentsShouldScopeToSelf() {
        LocalThreadHolder.setUserId(ATTACKER_ID, 2);
        when(appointmentMapper.query(any(AppointmentQueryDto.class))).thenReturn(List.of());
        when(appointmentMapper.queryCount(any(AppointmentQueryDto.class))).thenReturn(0);

        AppointmentQueryDto dto = new AppointmentQueryDto();
        dto.setPatientId(VICTIM_ID);   // 攻击者指定看别人的
        appointmentService.queryAppointments(dto);

        ArgumentCaptor<AppointmentQueryDto> captor =
                ArgumentCaptor.forClass(AppointmentQueryDto.class);
        verify(appointmentMapper).query(captor.capture());
        assertEquals(ATTACKER_ID, captor.getValue().getPatientId(),
                "查询条件必须被强制改写为自己，否则空条件查询会返回全库患者的姓名/手机号/病情描述");
    }

    @Test
    @DisplayName("预约查询：管理员不受限制")
    void queryAppointmentsShouldNotScopeAdmin() {
        LocalThreadHolder.setUserId(ADMIN_ID, 1);
        when(appointmentMapper.query(any(AppointmentQueryDto.class))).thenReturn(List.of());
        when(appointmentMapper.queryCount(any(AppointmentQueryDto.class))).thenReturn(0);

        AppointmentQueryDto dto = new AppointmentQueryDto();
        dto.setPatientId(VICTIM_ID);
        appointmentService.queryAppointments(dto);

        ArgumentCaptor<AppointmentQueryDto> captor =
                ArgumentCaptor.forClass(AppointmentQueryDto.class);
        verify(appointmentMapper).query(captor.capture());
        assertEquals(VICTIM_ID, captor.getValue().getPatientId(), "管理端需要按患者筛选");
    }

    @Test
    @DisplayName("取消预约：他人的预约不可取消，且不得触发任何写操作")
    void cancelAppointmentShouldRejectOthers() {
        LocalThreadHolder.setUserId(ATTACKER_ID, 2);

        AppointmentVO others = new AppointmentVO();
        others.setId(66);
        others.setPatientId(VICTIM_ID);
        others.setStatus(0);
        others.setScheduleId(5);
        when(appointmentMapper.getById(66)).thenReturn(others);

        Result<Void> result = appointmentService.cancelAppointment(66, "不想去了");

        assertEquals(errCode(), result.getCode());
        verify(appointmentMapper, never()).update(any(Appointment.class));
        verify(scheduleMapper, never()).decrementBookedCount(anyInt());
    }

    @Test
    @DisplayName("预约详情：看别人的预约必须被拒绝")
    void getAppointmentByIdShouldRejectOthers() {
        LocalThreadHolder.setUserId(ATTACKER_ID, 2);

        AppointmentVO others = new AppointmentVO();
        others.setId(66);
        others.setPatientId(VICTIM_ID);
        when(appointmentMapper.getById(66)).thenReturn(others);

        Result<AppointmentVO> result = appointmentService.getAppointmentById(66);

        assertEquals(errCode(), result.getCode());
        assertNull(result.getData(), "拒绝时不得把数据带出去");
    }

    // ================================================================
    // 商城：支付不能替别人付
    // ================================================================

    @Test
    @DisplayName("支付订单：不得把他人订单标记为已支付（零元购）")
    void payOrderShouldRejectOthersOrder() {
        LocalThreadHolder.setUserId(ATTACKER_ID, 2);

        MallOrderVO others = new MallOrderVO();
        others.setId(88);
        others.setUserId(VICTIM_ID);
        others.setStatus(0);
        when(orderMapper.getById(88)).thenReturn(others);

        Result<Void> result = mallService.payOrder(88, "在线支付");

        assertEquals(errCode(), result.getCode());
        verify(orderMapper, never()).update(any(MallOrder.class));
        verify(productMapper, never()).incrementSalesCount(anyInt(), anyInt());
    }

    @Test
    @DisplayName("订单详情：不得查看他人订单")
    void getOrderByIdShouldRejectOthersOrder() {
        LocalThreadHolder.setUserId(ATTACKER_ID, 2);

        MallOrderVO others = new MallOrderVO();
        others.setId(88);
        others.setUserId(VICTIM_ID);
        when(orderMapper.getById(88)).thenReturn(others);

        Result<MallOrderVO> result = mallService.getOrderById(88);

        assertEquals(errCode(), result.getCode());
        assertNull(result.getData());
        verify(orderItemMapper, never()).queryByOrderId(anyInt());
    }

    @Test
    @DisplayName("购物车改数量：不得篡改他人购物车")
    void updateCartShouldRejectOthers() {
        LocalThreadHolder.setUserId(ATTACKER_ID, 2);

        ShoppingCart others = new ShoppingCart();
        others.setId(12);
        others.setUserId(VICTIM_ID);
        when(cartMapper.getById(12)).thenReturn(others);

        Result<Void> result = mallService.updateCart(12, 99);

        assertEquals(errCode(), result.getCode());
        verify(cartMapper, never()).update(any(ShoppingCart.class));
    }

    @Test
    @DisplayName("购物车数量必须为正数：负数量会把结算总价算成负数")
    void addToCartShouldRejectNonPositiveQuantity() {
        LocalThreadHolder.setUserId(ATTACKER_ID, 2);

        Result<Void> result = mallService.addToCart(ATTACKER_ID, 3, -5);

        assertEquals(errCode(), result.getCode());
        verify(cartMapper, never()).save(any(ShoppingCart.class));
        verify(cartMapper, never()).update(any(ShoppingCart.class));
    }

    // ================================================================
    // 答题：题目不下发答案
    // ================================================================

    @Test
    @DisplayName("取本卷题目：answer / analysis 必须置空（评分在服务端）")
    void getExamQuestionsShouldStripAnswers() {
        QuizExam exam = new QuizExam();
        exam.setId(3);
        when(examMapper.getById(3)).thenReturn(exam);

        QuizQuestionVO q = new QuizQuestionVO();
        q.setId(100);
        q.setTitle("高血压患者每日食盐应控制在多少克以下？");
        q.setAnswer("6");
        q.setAnalysis("指南建议每日不超过 6 克。");
        when(questionMapper.queryByExamId(3)).thenReturn(new ArrayList<>(List.of(q)));

        Result<List<QuizQuestionVO>> result = quizService.getExamQuestions(3);

        assertEquals(okCode(), result.getCode());
        List<QuizQuestionVO> questions = result.getData();
        assertNotNull(questions);
        assertEquals(1, questions.size());
        assertEquals("高血压患者每日食盐应控制在多少克以下？", questions.get(0).getTitle(),
                "题干必须保留");
        assertNull(questions.get(0).getAnswer(), "答案下发到客户端等于把答案给了考生");
        assertNull(questions.get(0).getAnalysis(), "解析同样会泄漏答案");
    }

    @Test
    @DisplayName("交卷：不得把答案写进他人答题记录")
    void submitExamShouldRejectOthersRecord() {
        LocalThreadHolder.setUserId(ATTACKER_ID, 2);

        QuizRecord others = new QuizRecord();
        others.setId(55);
        others.setUserId(VICTIM_ID);
        when(recordMapper.getById(55)).thenReturn(others);

        Result<Void> result = quizService.submitExam(55, List.of());

        assertEquals(errCode(), result.getCode());
        verify(answerMapper, never()).batchSave(anyList());
        verify(recordMapper, never()).update(any(QuizRecord.class));
    }

    @Test
    @DisplayName("答题记录明细：不得查看他人答卷")
    void getRecordAnswersShouldRejectOthers() {
        LocalThreadHolder.setUserId(ATTACKER_ID, 2);

        QuizRecord others = new QuizRecord();
        others.setId(55);
        others.setUserId(VICTIM_ID);
        when(recordMapper.getById(55)).thenReturn(others);

        Result<List<QuizAnswer>> result = quizService.getRecordAnswers(55);

        assertEquals(errCode(), result.getCode());
        verify(answerMapper, never()).queryByRecordId(anyInt());
    }

    /** 保证测试本身没有依赖真实时间/数据库 */
    @Test
    @DisplayName("未登录（无身份）时一律拒绝，不得因为两边都是 null 而放行")
    void anonymousShouldBeRejected() {
        LocalThreadHolder.clear();

        assertFalse(LocalThreadHolder.isSelfOrAdmin(null));
        assertFalse(LocalThreadHolder.isSelfOrAdmin(VICTIM_ID));
    }
}
