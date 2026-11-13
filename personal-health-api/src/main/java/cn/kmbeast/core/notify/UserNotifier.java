package cn.kmbeast.core.notify;

import cn.kmbeast.mapper.NotificationMapper;
import cn.kmbeast.pojo.em.NotificationSource;
import cn.kmbeast.pojo.entity.Notification;
import cn.kmbeast.service.NotificationPreferenceService;
import cn.kmbeast.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 业务通知统一发送口 —— 2026-10-10 新增。
 *
 * <p><b>为什么需要这一层</b>：设置页的「预约提醒 / 订单通知 / AI 对话通知」三个开关，
 * 在此之前没有任何代码读过；各业务各自往 {@code notification} 表里写行，开关纯装饰。
 * 现在规定：<b>所有面向用户的功能性通知，一律从这里出去</b>。发送前统一查一次
 * 收件人偏好（{@link NotificationPreferenceService#allows}），开关才真正生效。
 *
 * <p><b>失败不影响主流程</b>：{@link #send} 内部吞掉一切 {@code Exception}，只记 warn
 * 日志、返回 false。通知是业务的<b>副作用</b> —— 一条「预约成功」的提醒没发出去，
 * 不该让挂号本身失败，更不该让事务回滚把已经占上的号退掉。
 *
 * <p><b>什么样的通知不该走这里</b>：
 * <ul>
 *   <li>安全类提醒（健康指标超标告警）—— 理由见
 *       {@link NotificationSource#FOLLOWUP}，用户不该能一键关掉自己的异常告警；</li>
 *   <li>管理员群发的系统公告 —— 走 {@code MessageService#systemInfoUsersSave}，
 *       那条链路是「广播给一批人」，与这里「单收件人 + 偏好判定」形状不同。</li>
 * </ul>
 *
 * @see NotificationSource 来源分类及其与开关的对应关系
 */
@Slf4j
@Component
public class UserNotifier {

    @Resource
    private NotificationService notificationService;

    @Resource
    private NotificationPreferenceService preferenceService;

    @Resource
    private NotificationMapper notificationMapper;

    /**
     * 发送一条业务通知（受用户开关控制）。
     *
     * @param receiverId 收件人用户ID；为 null 直接返回 false（没有收件人）
     * @param source     通知来源分类，决定受哪个开关管、以及 message_type/biz_type/source 三个落库字段
     * @param title      标题（消息中心列表行）
     * @param content    正文
     * @param bizId      关联业务ID（预约ID / 订单ID），可为 null；会同时写入 biz_id 与 related_id
     * @param linkUrl    点击跳转路径（如 /user/appointment），可为 null
     * @return true = 已投递；false = 未投递（无收件人 / 用户关了该开关 / 落库失败）。仅供测试与日志使用
     */
    public boolean send(Integer receiverId, NotificationSource source,
                        String title, String content, Long bizId, String linkUrl) {
        if (receiverId == null || source == null) {
            return false;
        }
        try {
            if (!preferenceService.allows(receiverId, source)) {
                log.debug("用户已关闭该通知开关，跳过发送: receiverId={}, source={}", receiverId, source);
                return false;
            }

            Notification notification = new Notification();
            notification.setUserId(receiverId);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setType(source.getType());
            notification.setMessageType(source.messageTypeValue());
            notification.setSource(source.getSource());
            notification.setBizType(source.getBizType());
            if (bizId != null) {
                // biz_id 在表里是 bigint unsigned，实体里是 String（合并 message 时统一成 String 的）
                notification.setBizId(String.valueOf(bizId));
                notification.setRelatedId(String.valueOf(bizId));
            }
            notification.setLinkUrl(linkUrl);

            // is_read / create_time 的赋值与落库、以及 WebSocket 实时推送，都在这里完成
            notificationService.save(notification);
            return true;
        } catch (Exception e) {
            log.warn("业务通知发送失败，不影响主流程: receiverId={}, source={}, bizId={}, title={}",
                    receiverId, source, bizId, title, e);
            return false;
        }
    }

    /**
     * 幂等发送：同一条业务通知对同一个用户只发一次。
     *
     * <p>给「没有天然一次性保证」的调用方用 —— 目前是
     * {@code AppointmentReminderJob}：定时任务碰上服务重启、多实例部署、
     * 手动触发，都会重复执行。这里先按
     * {@code (userId, bizType, bizId, messageType, title)} 查一次，
     * 已存在就跳过（详见 {@link NotificationMapper#countBizNotification}，
     * 其中说明了为什么去重键必须带上 title）。
     *
     * <p>去重查询本身失败时<b>不</b>当作「已发过」：宁可偶发重复，
     * 也不要因为一次查询报错，让用户的就诊提醒永远发不出去。
     *
     * @param bizId 关联业务ID；为 null 时无法去重，退化为 {@link #send}
     */
    public boolean sendOnce(Integer receiverId, NotificationSource source,
                            String title, String content, Long bizId, String linkUrl) {
        if (bizId == null) {
            return send(receiverId, source, title, content, null, linkUrl);
        }
        try {
            int existed = notificationMapper.countBizNotification(
                    receiverId, source.getBizType(), String.valueOf(bizId),
                    source.messageTypeValue(), title);
            if (existed > 0) {
                log.debug("该通知已发送过，跳过: receiverId={}, source={}, bizId={}, title={}",
                        receiverId, source, bizId, title);
                return false;
            }
        } catch (Exception e) {
            log.warn("通知去重查询失败，按未发送处理: receiverId={}, source={}, bizId={}",
                    receiverId, source, bizId, e);
        }
        return send(receiverId, source, title, content, bizId, linkUrl);
    }
}
