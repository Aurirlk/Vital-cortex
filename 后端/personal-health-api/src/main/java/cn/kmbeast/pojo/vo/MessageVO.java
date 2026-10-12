package cn.kmbeast.pojo.vo;

import cn.kmbeast.pojo.entity.Message;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息出参VO类
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class MessageVO extends Message {

    /**
     * 发送者名称
     */
    private String senderName;
    /**
     * 接收者头像
     */
    private String receiverAvatar;
    /**
     * 接收者名称
     */
    private String receiverName;
    /**
     * 评论内容
     */
    private String evaluationsContent;

    /**
     * 通知标题 —— 2026-10-10 新增。
     *
     * <p>{@code message} 表本身没有 title 列，但合并后的 {@code notification} 表有。
     * 原来 {@code toMessageVOs} 只搬运 content，标题被丢掉了 ——
     * 于是「预约成功 / 订单已发货」这类通知在消息中心只剩一句正文，
     * 看不出发生了什么。现在补出来供前端做加粗标题。
     */
    private String title;

    /**
     * 点击跳转路径 —— 2026-10-10 新增。
     *
     * <p>与 title 同因：{@code notification.link_url} 此前没被搬运到出参，
     * 前端拿不到就点不动。业务通知（预约/订单/AI）的价值有一半在「点了能到现场」，
     * 没有它，正文里那句「点击前往查看」就是空话。
     */
    private String linkUrl;

}
