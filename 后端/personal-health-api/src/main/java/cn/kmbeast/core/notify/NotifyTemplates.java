package cn.kmbeast.core.notify;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务通知的文案与跳转链接常量 —— 2026-10-10 新增。
 *
 * <p><b>为什么文案要集中放一处</b>：{@link UserNotifier#sendOnce} 的去重键里含
 * title（理由见 {@code NotificationMapper#countBizNotification}）。文案散落在各个
 * 业务类里时，同一件事在两个地方写了两遍标题，去重就会失效，而且是静默失效。
 * 集中成常量后，「同一件事」在代码层只有一个写法。
 *
 * <p>正文里的名称可能为 null（{@code appointment} 关联的医生/科室是 LEFT JOIN，
 * 排班被删就会是空），统一走 {@link #nz} 兜底，避免消息里出现 "null 医生"。
 */
public final class NotifyTemplates {

    private NotifyTemplates() {
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ==================== 跳转链接（与前端路由保持一致） ====================
    /** 我的预约 */
    public static final String LINK_APPOINTMENT = "/user/appointment";
    /** 我的订单 */
    public static final String LINK_MALL = "/user/mall";
    /** AI 健康分析 */
    public static final String LINK_AI = "/user/ai-analysis";

    // ==================== 预约提醒 ====================
    /** 挂号成功 */
    public static final String T_APPOINTMENT_BOOKED = "预约成功";
    /** 就诊前一天提醒（定时任务） */
    public static final String T_APPOINTMENT_REMINDER = "就诊提醒";
    /** 预约被取消 */
    public static final String T_APPOINTMENT_CANCELLED = "预约已取消";
    /** 医生确认就诊完成 */
    public static final String T_APPOINTMENT_COMPLETED = "就诊完成";

    // ==================== 订单通知 ====================
    public static final String T_ORDER_CREATED = "订单待付款";
    public static final String T_ORDER_PAID = "支付成功";
    /** 订单状态变更（已发货/已完成/已取消/退款中），标题由状态名拼出 */
    public static final String T_ORDER_STATUS_FORMAT = "订单%s";

    // ==================== AI 对话通知 ====================
    /** 用户在生成过程中离开页面，回复生成完成后回告 */
    public static final String T_AI_CHAT_DONE = "AI 回复已生成";

    // ==================== 正文构造 ====================

    /** 挂号成功：告知医生、科室、日期、时段、序号 */
    public static String appointmentBooked(String doctorName, String departmentName,
                                           LocalDate date, String timeSlotName,
                                           Integer serialNumber) {
        return String.format("您已成功预约 %s%s医生 %s %s 的号，序号 %s，请按时就诊。",
                nz(departmentName, ""), nz(doctorName, ""), fmt(date), nz(timeSlotName, ""),
                nz(serialNumber, "-"));
    }

    /** 就诊前一天提醒：定时任务使用 */
    public static String appointmentReminder(String doctorName, String departmentName,
                                            LocalDate date, String timeSlotName,
                                            Integer serialNumber) {
        return String.format("您明天（%s %s）在 %s%s医生 处有预约，序号 %s，请提前到院候诊。",
                fmt(date), nz(timeSlotName, ""), nz(departmentName, ""), nz(doctorName, ""),
                nz(serialNumber, "-"));
    }

    /** 预约取消（区分「自己取消」与「被他人取消」，后者才是需要提醒的） */
    public static String appointmentCancelled(LocalDate date, String timeSlotName,
                                             String doctorName) {
        return String.format("您 %s %s 与 %s医生 的预约已取消。",
                fmt(date), nz(timeSlotName, ""), nz(doctorName, ""));
    }

    /** 就诊完成 */
    public static String appointmentCompleted(LocalDate date, String doctorName) {
        return String.format("您 %s 与 %s医生 的就诊已完成，可前往「我的预约」查看就诊记录。",
                fmt(date), nz(doctorName, ""));
    }

    /** 下单成功（待付款） */
    public static String orderCreated(String orderNo, Object amount) {
        return String.format("订单 %s 已创建，应付金额 %s 元，请尽快完成支付。", nz(orderNo, "-"), nz(amount, "0"));
    }

    /** 支付成功 */
    public static String orderPaid(String orderNo, Object amount) {
        return String.format("订单 %s 支付成功，金额 %s 元，我们将尽快为您发货。", nz(orderNo, "-"), nz(amount, "0"));
    }

    /** 订单状态变更（发货/完成/取消/退款） */
    public static String orderStatusChanged(String orderNo, String statusLabel) {
        return String.format("您的订单 %s 状态已更新为「%s」。", nz(orderNo, "-"), nz(statusLabel, "未知"));
    }

    /** AI 回复完成回告 */
    public static String aiChatDone(String questionBrief) {
        if (questionBrief == null || questionBrief.isBlank()) {
            return "您刚才的提问已完成回答，点击前往查看。";
        }
        return String.format("您刚才的提问「%s」已完成回答，点击前往查看。", truncate(questionBrief, 40));
    }

    // ==================== 公共工具 ====================

    /**
     * 订单状态 → 通知标题；返回 {@code null} 表示该状态<b>不需要</b>通知。
     *
     * <p>状态值与 {@code MallManage.vue} 的
     * {@code ["待付款","待发货","待收货","已完成","已取消","退款中"]}（0..5）同一口径。
     * 只有管理员侧的流转（2/3/4/5）才通知 —— 0（刚下单）与 1（已支付）是
     * 用户自己的动作，已经在 {@code createOrder} / {@code payOrder} 里发过对应的
     * 「订单待付款」「支付成功」，若在这里再发一遍就是同一件事两条消息。
     *
     * @param status 订单状态
     * @return 通知标题，或 null
     */
    public static String orderStatusTitle(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 2 -> String.format(T_ORDER_STATUS_FORMAT, "已发货");
            case 3 -> String.format(T_ORDER_STATUS_FORMAT, "已完成");
            case 4 -> String.format(T_ORDER_STATUS_FORMAT, "已取消");
            case 5 -> String.format(T_ORDER_STATUS_FORMAT, "退款中");
            default -> null;
        };
    }

    /**
     * 订单状态 → 正文里用的中文名。
     *
     * <p>注意与列表页的列名不完全一致：列表页 2 显示「待收货」（站在用户视角的
     * 下一步），而通知里说「已发货」更贴合「刚才发生了什么」。
     *
     * @param status 订单状态
     * @return 中文状态名；认不出来时返回「未知」
     */
    public static String orderStatusLabel(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待付款";
            case 1 -> "待发货";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "已取消";
            case 5 -> "退款中";
            default -> "未知";
        };
    }

    /**
     * 时段英文码 → 中文名。
     *
     * <p>与 {@code AppointmentMapper.xml} 里几处
     * {@code CASE a.time_slot WHEN 'morning' THEN '上午' ...} <b>同一口径</b>，
     * 只是那边是为了列表展示、这边是为了拼通知正文。
     * 以后新增时段取值（如 {@code noon}）时两处都要改 —— 因此这里做了
     * {@code default} 兜底：认不出来就原样返回，至少不会发出「null」。
     */
    public static String timeSlotName(String timeSlot) {
        if (timeSlot == null) {
            return "";
        }
        return switch (timeSlot) {
            case "morning" -> "上午";
            case "afternoon" -> "下午";
            case "evening" -> "晚间";
            default -> timeSlot;
        };
    }

    // ==================== 私有工具 ====================

    private static String fmt(LocalDate date) {
        return date == null ? "（日期待定）" : date.format(DATE_FMT);
    }

    private static String nz(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value);
        return s.isBlank() ? fallback : s;
    }

    /** 截断过长文本，避免通知正文里塞进一整段提问 */
    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
