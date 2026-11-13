package cn.kmbeast.pojo.em;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务通知的来源分类 —— 2026-10-10 新增。
 *
 * <p>存在的理由：设置页有五个通知开关，但「一条通知该受哪个开关管」这件事
 * 原先没有任何地方表达过。发送方各自往 {@code notification} 表里塞行，
 * 开关自然是摆设。把来源做成枚举后，{@code UserNotifier} 就能在发送前
 * 统一查一次用户偏好，开关才真正生效。
 *
 * <p>每个枚举项绑定三个落库字段：
 * <ul>
 *   <li>{@code bizType} → {@code notification.biz_type}，用于按业务筛选/跳转，
 *       值域与建表注释一致（APPOINTMENT / FOLLOWUP / MALL / SYSTEM）；</li>
 *   <li>{@code source} → {@code notification.source}，标识这条通知由谁产生
 *       （SYSTEM 自动任务 / ORDER 商城 / AI助手），与建表注释一致；</li>
 *   <li>{@code messageType} → {@code notification.message_type}，决定前端消息中心
 *       归到哪个分类 Tab（见 {@link MessageType}）。</li>
 * </ul>
 *
 * <p>⚠️ 每个业务来源必须能对应到<b>恰好一个</b>用户开关，{@link #preferenceColumn()}
 * 说明它由哪一列控制；{@code SYSTEM} / {@code FOLLOWUP} 两项目前<b>尚未接线</b>，
 * 原因见各自注释。
 */
@AllArgsConstructor
@Getter
public enum NotificationSource {

    /** 预约成功、取消、就诊完成、就诊前一天提醒 */
    APPOINTMENT("APPOINTMENT", "SYSTEM", MessageType.APPOINTMENT, 1, "预约提醒",
            "notify_appointment"),

    /** 商城订单：创建、支付、发货、完成、取消、退款 */
    ORDER("MALL", "ORDER", MessageType.ORDER, 3, "订单通知",
            "notify_order"),

    /**
     * AI 助手对话：离线续跑完成后回告用户。
     *
     * <p>{@code type=5} 是本枚举新增的业务分类 —— 实体注释里原本只有
     * 0公告/1预约/2随访/3订单/4私信，没有 AI 一档（见
     * {@code Notification#type} 的注释，已同步补上）。
     */
    AI_CHAT("AI_CHAT", "AI助手", MessageType.AI_CHAT, 5, "AI 对话通知",
            "notify_ai_chat"),

    /**
     * 系统公告（管理员手动广播）。
     *
     * <p>其发送入口是 {@code MessageService#systemInfoUsersSave}，
     * 走的是另一条老链路（不经过 {@code UserNotifier}），**尚未接开关**。
     * 保留枚举项是为了让 {@code notify_system} 有明确归属，接线时不必再改枚举。
     */
    SYSTEM("SYSTEM", "SYSTEM", MessageType.SYSTEM_INFO, 0, "系统消息通知",
            "notify_system"),

    /**
     * 健康提醒（指标超标等）。
     *
     * <p>⚠️ 有意<b>不接开关</b>：现有的唯一生产方是「健康指标超标告警」
     * （{@code UserHealthServiceImpl#dealMessage}），那属于安全类提醒 ——
     * 把它做成用户能一键关掉，等于让人关掉自己的异常指标告警，
     * 属于产品决策，不该由代码单方面决定。接线前请先确认口径。
     */
    FOLLOWUP("FOLLOWUP", "SYSTEM", MessageType.DATA_MESSAGE, 2, "健康提醒通知",
            "notify_health_remind");

    /** 落 {@code notification.biz_type} */
    private final String bizType;

    /** 落 {@code notification.source} */
    private final String source;

    /** 落 {@code notification.message_type}，决定消息中心分类 */
    private final MessageType messageType;

    /**
     * 落 {@code notification.type} —— 通知的业务分类
     * （0公告 1预约 2随访 3订单 4私信 5AI对话），与实体注释同一口径。
     */
    private final Integer type;

    /** 设置页上的开关名称，用于日志与文档 */
    private final String label;

    /** 控制本类通知的用户偏好列名（仅文档/日志用，不参与 SQL 拼接） */
    private final String preferenceColumn;

    /** 落库用的 message_type 数值 */
    public Integer messageTypeValue() {
        return messageType.getType();
    }
}
