package cn.kmbeast.pojo.em;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MessageType {

    // 互动消息（评论被被人回复、评论被别人点赞）、指标消息、系统通知类
    EVALUATIONS_BY_REPLY(1,"评论"),
    EVALUATIONS_BY_UPVOTE(2,"点赞"),
    DATA_MESSAGE(3,"指标提醒"),
    SYSTEM_INFO(4,"系统通知"),

    /*
     * 以下三类是 2026-10-10 新增的业务通知，对应设置页「通知设置」里的三个开关
     * （notify_appointment / notify_order / notify_ai_chat）。
     *
     * ⚠️ 追加枚举值会自动出现在消息中心的分类 Tab 里 ——
     * /message/types 直接返回 MessageType.values()，前端 Message.vue 用
     * (type, detail) 渲染 Tab、用 type 过滤列表，未知 type 走默认图标。
     * 所以这里新增即生效，前端零改动；反过来，**删改已有 number 会让历史消息筛不出来**，
     * 追加只能往后排。
     */
    APPOINTMENT(5,"预约提醒"),
    ORDER(6,"订单通知"),
    AI_CHAT(7,"AI对话");

    /**
     * 消息类型
     */
    private final Integer type;

    /**
     * 消息类型的备注
     */
    private final String detail;

}
