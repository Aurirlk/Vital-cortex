package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知实体（2026-10-04 合并 message 后扩充）
 *
 * <p><b>合并背景</b>：原 {@code message} 表与本表是同一业务概念的两套实现
 * （消息系统双轨，见《数据模型重构与项目扩展方案》缺陷 4）。现统一到本表。
 *
 * <p><b>两套字段的语义对照</b>（务必不要混淆）：
 * <table border="1">
 *   <tr><th>字段</th><th>含义</th></tr>
 *   <tr><td>{@code type}</td><td>通知业务分类：0公告 1预约 2随访 3订单 4私信</td></tr>
 *   <tr><td>{@code messageType}</td><td>用户视角分类：1评论 2点赞 3指标提醒 4系统通知
 *       （合并自 {@code message.other} / 实体 {@code messageType}）</td></tr>
 * </table>
 * 两个字段语义不同，<b>不可互相替代</b>。
 */
@Data
public class Notification {
    private Integer id;

    /** 接收人 ID（合并自 message.receiver_id） */
    private Integer userId;

    private String title;

    private String content;

    /** 业务分类：0公告 1预约 2随访 3订单 4私信 */
    private Integer type;

    /** 用户视角分类：1评论 2点赞 3指标提醒 4系统通知（合并自 message.other） */
    private Integer messageType;

    private Integer isRead;

    /** 发送人（合并自 message.sender_id） */
    private Integer senderId;

    /** 关联业务 ID（合并自 message.content_id） */
    private String relatedId;

    private String source;

    private String linkUrl;

    private String bizType;

    private String bizId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
