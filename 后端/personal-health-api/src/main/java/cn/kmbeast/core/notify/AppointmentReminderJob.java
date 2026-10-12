package cn.kmbeast.core.notify;

import cn.kmbeast.mapper.AppointmentMapper;
import cn.kmbeast.pojo.em.NotificationSource;
import cn.kmbeast.pojo.vo.AppointmentVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 就诊前一天提醒 —— 2026-10-10 新增。
 *
 * <p>设置页的「预约提醒」开关（{@code user.notify_appointment}）此前没有任何后端
 * 代码读过，属于纯装饰。这个任务是它的两个真实发送方之一（另一个是挂号/取消/
 * 完成时的即时通知，见 {@code AppointmentServiceImpl} 与 {@code DoctorWorkServiceImpl}）。
 *
 * <p><b>为什么单独做定时任务</b>：预约表里只有 {@code appointment_date}（日期粒度，
 * 排班的 time_slot 只有 morning/afternoon/evening），<b>没有具体钟点</b>。
 * 所以「提前 1 小时提醒」在数据上根本做不到 —— 只能做「就诊前一天晚上提醒」。
 *
 * <p><b>幂等</b>：定时任务没有天然的一次性保证（重启、多实例、手动触发都会重跑）。
 * 因此逐条走 {@link UserNotifier#sendOnce}，按
 * {@code (userId, APPOINTMENT, appointmentId, messageType, title)} 查重，
 * 已发过的不再发；多实例同时跑也只会落一条。
 *
 * <p><b>偏好过滤在 SQL 里做</b>：见
 * {@code AppointmentMapper#queryReminderTargets} —— 已经关掉开关的用户
 * 在数据库层就被滤掉，批量路径不做 N 次开关查询。
 *
 * <p>执行时间由 {@code notify.appointment-reminder.cron} 配置，默认每天 19:00；
 * 把该配置置为 {@code -} 可关闭本任务（{@code @Scheduled} 支持这种禁用写法）。
 */
@Slf4j
@Component
public class AppointmentReminderJob {

    @Resource
    private AppointmentMapper appointmentMapper;

    @Resource
    private UserNotifier userNotifier;

    /** 本轮执行结果，仅用于日志与测试断言 */
    public record ReminderResult(int targets, int sent) {
    }

    /**
     * 定时入口：扫描「明天」的预约并发提醒。
     */
    @Scheduled(cron = "${notify.appointment-reminder.cron:0 0 19 * * ?}")
    public void remindTomorrowAppointments() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime startedAt = LocalDateTime.now();
        ReminderResult result = remindFor(tomorrow);
        // 无目标时不打 info，避免没有预约的夜里天天刷日志
        if (result.targets() > 0) {
            log.info("就诊前一天提醒完成: date={}, 目标={}, 已发送={}, 跳过(已发过/已关开关)={}, 耗时={}ms",
                    tomorrow, result.targets(), result.sent(), result.targets() - result.sent(),
                    Duration.between(startedAt, LocalDateTime.now()).toMillis());
        }
    }

    /**
     * 对指定就诊日执行一轮提醒（定时入口与测试共用）。
     *
     * <p>抽成 public 是为了让 {@code AppointmentReminderJobTest} 能直接验证
     * 「哪个日期、发给了谁、发了几条」，而不必等 cron 到点。
     *
     * <p>整体包 try/catch：定时线程里抛出的异常虽不会让进程退出，但会丢掉本轮剩余
     * 处理并留下裸堆栈。这里兜住并记明「跑失败了」，下一次调度照常。
     *
     * @param appointmentDate 要处理的就诊日；null 直接返回空结果
     * @return 本轮扫到的目标数与实际投递数
     */
    public ReminderResult remindFor(LocalDate appointmentDate) {
        if (appointmentDate == null) {
            return new ReminderResult(0, 0);
        }
        try {
            List<AppointmentVO> targets = appointmentMapper.queryReminderTargets(appointmentDate);
            int sent = 0;
            for (AppointmentVO appointment : targets) {
                if (appointment == null || appointment.getPatientId() == null) {
                    continue;
                }
                boolean ok = userNotifier.sendOnce(
                        appointment.getPatientId(),
                        NotificationSource.APPOINTMENT,
                        NotifyTemplates.T_APPOINTMENT_REMINDER,
                        NotifyTemplates.appointmentReminder(
                                appointment.getDoctorName(),
                                appointment.getDepartmentName(),
                                appointment.getAppointmentDate(),
                                appointment.getTimeSlotName(),
                                appointment.getSerialNumber()),
                        appointment.getId() == null ? null : appointment.getId().longValue(),
                        NotifyTemplates.LINK_APPOINTMENT);
                if (ok) {
                    sent++;
                }
            }
            return new ReminderResult(targets.size(), sent);
        } catch (Exception e) {
            log.warn("就诊前一天提醒任务执行失败: date={}", appointmentDate, e);
            return new ReminderResult(0, 0);
        }
    }
}
