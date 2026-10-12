package cn.kmbeast.pojo.vo;

import lombok.Data;

/**
 * 用户通知开关（只读投影）—— 2026-10-10 新增。
 *
 * <p>为什么单独建一个 VO 而不是复用 {@code User}：发送通知是高频路径
 * （一条提醒一次判断），把整行用户（含密码哈希）读出来既没必要也不安全。
 * 这里只取需要的五列。
 *
 * <p>字段类型是包装类 {@code Boolean}：列允许为 NULL（历史行未回填时），
 * NULL 一律按「开」处理，与迁移脚本的 {@code DEFAULT 1} 口径一致 ——
 * 见 {@code NotificationPreferenceServiceImpl#allows}。
 */
@Data
public class NotifyPreferenceVO {

    /** user.notify_system */
    private Boolean systemNotify;

    /** user.notify_health_remind */
    private Boolean healthRemindNotify;

    /** user.notify_ai_chat */
    private Boolean aiChatNotify;

    /** user.notify_appointment */
    private Boolean appointmentNotify;

    /** user.notify_order */
    private Boolean orderNotify;
}
