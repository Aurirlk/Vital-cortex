package cn.kmbeast.core.notify;

import cn.kmbeast.mapper.NotificationMapper;
import cn.kmbeast.pojo.em.NotificationSource;
import cn.kmbeast.pojo.entity.Notification;
import cn.kmbeast.service.NotificationPreferenceService;
import cn.kmbeast.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 业务通知发送口单元测试 —— 2026-10-10 新增。
 *
 * <p>这个类承担两个契约，都在此固化成断言：
 * <ol>
 *   <li><b>开关必须被尊重</b>：用户关掉某一类通知后，对应通知
 *       <b>一条都不能落库</b>（不是「少发」而是「不发」）；</li>
 *   <li><b>通知失败绝不能带崩业务</b>：发送过程中任何异常都必须在
 *       {@code UserNotifier} 内部消化掉、返回 false。
 *       挂号、下单这些主流程不因为「消息没发出去」而失败或回滚。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("业务通知发送口")
class UserNotifierTest {

    private static final int RECEIVER = 1001;
    private static final long BIZ_ID = 77L;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationPreferenceService preferenceService;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private UserNotifier notifier;

    private void allowAll() {
        when(preferenceService.allows(anyInt(), any())).thenReturn(true);
    }

    // ==================== 开关判定 ====================

    @Test
    @DisplayName("用户关掉了该开关：一条都不落库")
    void disabledSwitchShouldNotPersistAnything() {
        when(preferenceService.allows(RECEIVER, NotificationSource.ORDER)).thenReturn(false);

        assertFalse(notifier.send(RECEIVER, NotificationSource.ORDER,
                "订单待付款", "内容", BIZ_ID, NotifyTemplates.LINK_MALL));

        verify(notificationService, never()).save(any());
    }

    @Test
    @DisplayName("无收件人：直接返回 false，连开关都不查")
    void nullReceiverShouldShortCircuit() {
        assertFalse(notifier.send(null, NotificationSource.ORDER, "t", "c", null, null));
        verifyNoInteractions(preferenceService);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("无来源分类：不投递（否则不知道受哪个开关管）")
    void nullSourceShouldNotSend() {
        assertFalse(notifier.send(RECEIVER, null, "t", "c", null, null));
        verify(notificationService, never()).save(any());
    }

    // ==================== 落库字段 ====================

    @Test
    @DisplayName("正常发送：字段按来源枚举填全，供消息中心分类与跳转")
    void sendShouldPopulateAllClassificationFields() {
        allowAll();

        assertTrue(notifier.send(RECEIVER, NotificationSource.APPOINTMENT,
                NotifyTemplates.T_APPOINTMENT_REMINDER, "您明天有就诊安排",
                BIZ_ID, NotifyTemplates.LINK_APPOINTMENT));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).save(captor.capture());
        Notification n = captor.getValue();

        assertEquals(RECEIVER, n.getUserId());
        assertEquals(NotifyTemplates.T_APPOINTMENT_REMINDER, n.getTitle());
        assertEquals("您明天有就诊安排", n.getContent());
        // type=1 预约（实体注释口径）；message_type=5 预约提醒（消息中心 Tab）
        assertEquals(1, n.getType());
        assertEquals(5, n.getMessageType());
        assertEquals("SYSTEM", n.getSource());
        assertEquals("APPOINTMENT", n.getBizType());
        assertEquals("77", n.getBizId(), "biz_id 是字符串形式存进数字列");
        assertEquals("77", n.getRelatedId(), "related_id 与 biz_id 同源，供老代码读取");
        assertEquals("/user/appointment", n.getLinkUrl());
        // is_read / create_time 由 NotificationService#save 负责，这里不该抢着设
        assertNull(n.getIsRead(), "isRead 应由 NotificationService 统一设置");
        assertNull(n.getCreateTime(), "createTime 应由 NotificationService 统一设置");
    }

    @Test
    @DisplayName("不传业务ID：bizId/relatedId 保持 null，不写空字符串")
    void nullBizIdShouldStayNull() {
        allowAll();

        notifier.send(RECEIVER, NotificationSource.ORDER, "订单待付款", "内容", null, null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).save(captor.capture());
        assertNull(captor.getValue().getBizId(), "空字符串会污染 biz_id 的数值语义");
        assertNull(captor.getValue().getRelatedId());
        assertEquals("MALL", captor.getValue().getBizType());
        assertEquals("ORDER", captor.getValue().getSource());
    }

    // ==================== 失败必须被吞掉 ====================

    @Test
    @DisplayName("落库抛异常：不向外抛，主业务不受影响")
    void persistenceFailureMustNotPropagate() {
        allowAll();
        doThrow(new RuntimeException("notification 表写不进去"))
                .when(notificationService).save(any());

        // 不抛异常 = 调用方（挂号/下单）的事务不会被带崩
        assertFalse(notifier.send(RECEIVER, NotificationSource.ORDER, "t", "c", BIZ_ID, null));
    }

    @Test
    @DisplayName("开关查询抛异常：不向外抛")
    void preferenceFailureMustNotPropagate() {
        when(preferenceService.allows(anyInt(), any()))
                .thenThrow(new RuntimeException("user 表读不到"));

        assertFalse(notifier.send(RECEIVER, NotificationSource.ORDER, "t", "c", BIZ_ID, null));
        verify(notificationService, never()).save(any());
    }

    // ==================== 幂等发送 ====================

    @Test
    @DisplayName("幂等发送：已发过的不再发")
    void sendOnceShouldSkipWhenAlreadySent() {
        allowAll();
        when(notificationMapper.countBizNotification(eq(RECEIVER), eq("APPOINTMENT"),
                eq("77"), eq(5), eq(NotifyTemplates.T_APPOINTMENT_REMINDER)))
                .thenReturn(1);

        assertFalse(notifier.sendOnce(RECEIVER, NotificationSource.APPOINTMENT,
                NotifyTemplates.T_APPOINTMENT_REMINDER, "内容", BIZ_ID,
                NotifyTemplates.LINK_APPOINTMENT));
        verify(notificationService, never()).save(any());
    }

    @Test
    @DisplayName("幂等发送：没发过则正常发送")
    void sendOnceShouldSendWhenNotSent() {
        allowAll();
        when(notificationMapper.countBizNotification(anyInt(), anyString(), anyString(),
                anyInt(), anyString())).thenReturn(0);

        assertTrue(notifier.sendOnce(RECEIVER, NotificationSource.APPOINTMENT,
                NotifyTemplates.T_APPOINTMENT_REMINDER, "内容", BIZ_ID,
                NotifyTemplates.LINK_APPOINTMENT));
        verify(notificationService).save(any());
    }

    @Test
    @DisplayName("幂等发送：去重查询报错时照常发送（宁可重复，不能漏发）")
    void sendOnceShouldFailOpenWhenDedupQueryBreaks() {
        allowAll();
        when(notificationMapper.countBizNotification(anyInt(), anyString(), anyString(),
                anyInt(), anyString())).thenThrow(new RuntimeException("索引/列不存在"));

        assertTrue(notifier.sendOnce(RECEIVER, NotificationSource.APPOINTMENT,
                NotifyTemplates.T_APPOINTMENT_REMINDER, "内容", BIZ_ID, null),
                "就诊提醒漏发的代价远大于偶发重复");
        verify(notificationService).save(any());
    }

    @Test
    @DisplayName("幂等发送：无业务ID时退化为普通发送，不去查重")
    void sendOnceWithoutBizIdShouldDelegateToSend() {
        allowAll();

        assertTrue(notifier.sendOnce(RECEIVER, NotificationSource.AI_CHAT,
                NotifyTemplates.T_AI_CHAT_DONE, "内容", null, NotifyTemplates.LINK_AI));
        verify(notificationMapper, never())
                .countBizNotification(anyInt(), anyString(), anyString(), anyInt(), anyString());
        verify(notificationService).save(any());
    }

    // ==================== 文案 ====================

    @Test
    @DisplayName("文案：字段缺失时兜底，不出现 null 字样")
    void templatesShouldNeverRenderNull() {
        String reminder = NotifyTemplates.appointmentReminder(null, null, null, null, null);
        assertFalse(reminder.contains("null"), "实际文案: " + reminder);

        String booked = NotifyTemplates.appointmentBooked(null, null, null, null, null);
        assertFalse(booked.contains("null"), "实际文案: " + booked);

        String cancelled = NotifyTemplates.appointmentCancelled(null, null, null);
        assertFalse(cancelled.contains("null"), "实际文案: " + cancelled);

        String ai = NotifyTemplates.aiChatDone(null);
        assertFalse(ai.contains("null"), "实际文案: " + ai);
    }

    @Test
    @DisplayName("文案：时段码转中文，认不出来的原样返回")
    void timeSlotNameShouldMapKnownCodes() {
        assertEquals("上午", NotifyTemplates.timeSlotName("morning"));
        assertEquals("下午", NotifyTemplates.timeSlotName("afternoon"));
        assertEquals("晚间", NotifyTemplates.timeSlotName("evening"));
        assertEquals("", NotifyTemplates.timeSlotName(null));
        assertEquals("noon", NotifyTemplates.timeSlotName("noon"), "未知取值原样返回，便于发现新增时段");
    }

    @Test
    @DisplayName("订单状态标题：只对需要通知的流转给出标题")
    void orderStatusTitleShouldOnlyCoverNotifiableStatuses() {
        assertNull(NotifyTemplates.orderStatusTitle(0), "刚下单由 createOrder 通知，不在此重复");
        assertNull(NotifyTemplates.orderStatusTitle(1), "已支付由 payOrder 通知，不在此重复");
        assertEquals("订单已发货", NotifyTemplates.orderStatusTitle(2));
        assertEquals("订单已完成", NotifyTemplates.orderStatusTitle(3));
        assertEquals("订单已取消", NotifyTemplates.orderStatusTitle(4));
        assertEquals("订单退款中", NotifyTemplates.orderStatusTitle(5));
        assertNull(NotifyTemplates.orderStatusTitle(null));
        assertNull(NotifyTemplates.orderStatusTitle(99));
    }

    @Test
    @DisplayName("AI 通知：超长提问被截断，避免正文里塞一整段病历")
    void aiChatDoneShouldTruncateLongQuestion() {
        String longQuestion = "头晕".repeat(200);
        String content = NotifyTemplates.aiChatDone(longQuestion);

        assertTrue(content.length() < longQuestion.length(), "实际长度: " + content.length());
        assertTrue(content.contains("…"));
    }

    @Test
    @DisplayName("AI 对话通知：业务分类 5 与消息中心分类 7 都按枚举填对")
    void aiChatNotificationShouldUseItsOwnClassification() {
        allowAll();
        notifier.send(RECEIVER, NotificationSource.AI_CHAT, "t", "c", BIZ_ID, null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).save(captor.capture());
        // 系统生成的通知没有「发送人」，source 字段已经表达了来源
        assertNull(captor.getValue().getSenderId());
        assertEquals("AI助手", captor.getValue().getSource());
        assertEquals("AI_CHAT", captor.getValue().getBizType());
        assertEquals(5, captor.getValue().getType(), "AI 对话属新增业务分类 5");
        assertEquals(7, captor.getValue().getMessageType(), "消息中心 Tab 7 = AI 对话");
    }
}
