package cn.kmbeast.core.notify;

import cn.kmbeast.mapper.AppointmentMapper;
import cn.kmbeast.pojo.em.NotificationSource;
import cn.kmbeast.pojo.vo.AppointmentVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 就诊前一天提醒定时任务单元测试 —— 2026-10-10 新增。
 *
 * <p>这是「预约提醒」开关的第一个真实发送方。要守住的性质：
 * <ol>
 *   <li><b>只提醒该提醒的人</b>：收件人是患者本人，不是医生、不是操作者；</li>
 *   <li><b>逐条幂等</b>：走 {@code sendOnce}，重复执行不会堆出重复消息；</li>
 *   <li><b>一条坏数据不能拖垮整轮</b>：患者ID为空的脏行跳过即可；
 *       整轮查询报错也不能让异常从定时线程里逃出去。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("就诊前一天提醒")
class AppointmentReminderJobTest {

    private static final LocalDate TOMORROW = LocalDate.of(2026, 10, 11);

    @Mock
    private AppointmentMapper appointmentMapper;

    @Mock
    private UserNotifier userNotifier;

    @InjectMocks
    private AppointmentReminderJob job;

    private AppointmentVO appointment(int id, Integer patientId, int serial) {
        AppointmentVO vo = new AppointmentVO();
        vo.setId(id);
        vo.setPatientId(patientId);
        vo.setAppointmentDate(TOMORROW);
        vo.setTimeSlot("morning");
        vo.setTimeSlotName("上午");
        vo.setSerialNumber(serial);
        vo.setDoctorName("李医生");
        vo.setDepartmentName("内科");
        return vo;
    }

    @Test
    @DisplayName("正常一轮：按通知接口约定的字段逐条发送")
    void shouldSendOneNotificationPerTarget() {
        when(appointmentMapper.queryReminderTargets(TOMORROW))
                .thenReturn(List.of(appointment(11, 1001, 3), appointment(12, 1002, 5)));
        when(userNotifier.sendOnce(anyInt(), any(), anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(true);

        AppointmentReminderJob.ReminderResult result = job.remindFor(TOMORROW);

        assertEquals(2, result.targets());
        assertEquals(2, result.sent());

        ArgumentCaptor<Integer> receiver = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<NotificationSource> source = ArgumentCaptor.forClass(NotificationSource.class);
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> bizId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(userNotifier, times(2)).sendOnce(receiver.capture(), source.capture(),
                title.capture(), content.capture(), bizId.capture(), link.capture());

        assertEquals(List.of(1001, 1002), receiver.getAllValues(), "收件人必须是患者本人");
        assertEquals(NotificationSource.APPOINTMENT, source.getAllValues().get(0));
        assertEquals(NotifyTemplates.T_APPOINTMENT_REMINDER, title.getAllValues().get(0));
        assertEquals(List.of(11L, 12L), bizId.getAllValues(), "biz_id 用预约ID，才能按条去重");
        assertEquals("/user/appointment", link.getAllValues().get(0));
        assertTrue(content.getAllValues().get(0).contains("上午"), "正文要带时段");
        assertTrue(content.getAllValues().get(0).contains("李医生"), "正文要带医生");
        assertFalse(content.getAllValues().get(0).contains("null"));
    }

    @Test
    @DisplayName("查询必须按传入日期，且只查那一天")
    void shouldQueryTheGivenDate() {
        when(appointmentMapper.queryReminderTargets(any())).thenReturn(List.of());

        job.remindFor(TOMORROW);

        ArgumentCaptor<LocalDate> date = ArgumentCaptor.forClass(LocalDate.class);
        verify(appointmentMapper).queryReminderTargets(date.capture());
        assertEquals(TOMORROW, date.getValue());
    }

    @Test
    @DisplayName("患者ID为空的脏行：跳过，不影响其它行")
    void shouldSkipRowsWithoutPatient() {
        when(appointmentMapper.queryReminderTargets(TOMORROW))
                .thenReturn(List.of(appointment(13, null, 1), appointment(14, 1004, 2)));
        when(userNotifier.sendOnce(anyInt(), any(), anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(true);

        AppointmentReminderJob.ReminderResult result = job.remindFor(TOMORROW);

        assertEquals(2, result.targets(), "目标数照实统计，便于发现脏数据");
        assertEquals(1, result.sent());
        verify(userNotifier, times(1)).sendOnce(eq(1004), any(), anyString(), anyString(),
                anyLong(), anyString());
    }

    @Test
    @DisplayName("已被去重拦下的不计入已发送")
    void shouldNotCountSkippedAsSent() {
        when(appointmentMapper.queryReminderTargets(TOMORROW))
                .thenReturn(List.of(appointment(11, 1001, 1), appointment(12, 1002, 2)));
        when(userNotifier.sendOnce(eq(1001), any(), anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(false);
        when(userNotifier.sendOnce(eq(1002), any(), any(), anyString(), anyLong(), anyString()))
                .thenReturn(true);

        AppointmentReminderJob.ReminderResult result = job.remindFor(TOMORROW);

        assertEquals(2, result.targets());
        assertEquals(1, result.sent());
    }

    @Test
    @DisplayName("整轮查询报错：吞掉异常，不逃出定时线程")
    void shouldSwallowQueryFailure() {
        when(appointmentMapper.queryReminderTargets(any()))
                .thenThrow(new RuntimeException("notify_appointment 列不存在"));

        // 不抛异常 = 定时线程存活，下一次调度照常；日志里能查到原因
        AppointmentReminderJob.ReminderResult result = job.remindFor(TOMORROW);

        assertEquals(0, result.targets());
        assertEquals(0, result.sent());
        verifyNoInteractions(userNotifier);
    }

    @Test
    @DisplayName("日期为 null：直接返回，不查库")
    void nullDateShouldShortCircuit() {
        AppointmentReminderJob.ReminderResult result = job.remindFor(null);

        assertEquals(0, result.targets());
        verifyNoInteractions(appointmentMapper);
        verifyNoInteractions(userNotifier);
    }

    @Test
    @DisplayName("定时入口用的是「明天」，不是今天")
    void scheduledEntryShouldTargetTomorrow() {
        when(appointmentMapper.queryReminderTargets(any())).thenReturn(List.of());

        job.remindTomorrowAppointments();

        ArgumentCaptor<LocalDate> date = ArgumentCaptor.forClass(LocalDate.class);
        verify(appointmentMapper).queryReminderTargets(date.capture());
        assertEquals(LocalDate.now().plusDays(1), date.getValue(),
                "提前一天提醒，扫的必须是明天");
    }
}
