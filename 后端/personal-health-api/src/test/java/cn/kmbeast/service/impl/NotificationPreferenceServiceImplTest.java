package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.em.NotificationSource;
import cn.kmbeast.pojo.vo.NotifyPreferenceVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * 通知开关判定单元测试 —— 2026-10-10 新增。
 *
 * <p>这是「设置页那几个通知开关到底有没有生效」的<b>唯一判据</b>。
 * 之前开关是纯装饰：没有任何代码读过 {@code user.notify_*} 列。
 * 现在所有业务通知在落库前都要过这里，所以这里的边界行为直接决定
 * 用户会不会被自己关掉的通知打扰、或者反过来收不到本该收到的通知。
 *
 * <p>重点覆盖三处「容易写反」的地方：
 * <ol>
 *   <li><b>NULL 视为开启</b>：列是 {@code DEFAULT 1} 但可空。若把 NULL 当关闭，
 *       一次漏跑迁移就会静默吃掉全站通知且毫无报错；</li>
 *   <li><b>查不到用户不投递</b>：已注销/脏数据不该产生孤儿通知；</li>
 *   <li><b>查询异常时放行</b>：读开关失败不能连带把下单/挂号打挂 ——
 *       宁可多发一条，也不要因为「通知配置查不到」让主业务失败。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("通知开关判定")
class NotificationPreferenceServiceImplTest {

    private static final int UID = 42;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private NotificationPreferenceServiceImpl service;

    /** 五个开关全开 */
    private static NotifyPreferenceVO allOn() {
        NotifyPreferenceVO vo = new NotifyPreferenceVO();
        vo.setSystemNotify(true);
        vo.setHealthRemindNotify(true);
        vo.setAiChatNotify(true);
        vo.setAppointmentNotify(true);
        vo.setOrderNotify(true);
        return vo;
    }

    /** 五个开关全空（模拟漏跑迁移 / 历史行没有回填） */
    private static NotifyPreferenceVO allNull() {
        return new NotifyPreferenceVO();
    }

    // ==================== 入参边界 ====================

    @Test
    @DisplayName("无收件人：不投递，且不查库")
    void nullUserIdShouldNotAllow() {
        assertFalse(service.allows(null, NotificationSource.ORDER));
    }

    @Test
    @DisplayName("无来源分类：不投递")
    void nullSourceShouldNotAllow() {
        assertFalse(service.allows(UID, null));
    }

    @Test
    @DisplayName("用户查不到：不投递（避免孤儿通知）")
    void missingUserShouldNotAllow() {
        when(userMapper.getNotifyPreference(anyInt())).thenReturn(null);
        assertFalse(service.allows(UID, NotificationSource.ORDER));
    }

    @Test
    @DisplayName("读开关报错：按开启处理，不能把主业务打挂")
    void queryFailureShouldFailOpen() {
        when(userMapper.getNotifyPreference(anyInt()))
                .thenThrow(new RuntimeException("Unknown column 'notify_order'"));

        // 漏跑 08 迁移脚本时就是这种情况：列不存在。
        // 此时若按「关闭」处理，全站通知会静默消失，且没有任何报错。
        assertTrue(service.allows(UID, NotificationSource.ORDER),
                "开关读不出来时应放行，而不是把用户的通知全掐掉");
    }

    // ==================== 每个开关各自生效 ====================

    @Test
    @DisplayName("开关全开：五个来源都放行")
    void allOnShouldAllowEverything() {
        when(userMapper.getNotifyPreference(anyInt())).thenReturn(allOn());
        for (NotificationSource source : NotificationSource.values()) {
            assertTrue(service.allows(UID, source), source + " 应放行");
        }
    }

    @Test
    @DisplayName("开关为 NULL：视为开启（与列 DEFAULT 1 口径一致）")
    void nullColumnsShouldBeTreatedAsOn() {
        when(userMapper.getNotifyPreference(anyInt())).thenReturn(allNull());
        for (NotificationSource source : NotificationSource.values()) {
            assertTrue(service.allows(UID, source), source + " 的 NULL 应视为开启");
        }
    }

    @Test
    @DisplayName("关掉「预约提醒」只影响预约，不影响订单与 AI")
    void disablingAppointmentShouldOnlyAffectAppointment() {
        NotifyPreferenceVO vo = allOn();
        vo.setAppointmentNotify(false);
        when(userMapper.getNotifyPreference(anyInt())).thenReturn(vo);

        assertFalse(service.allows(UID, NotificationSource.APPOINTMENT));
        assertTrue(service.allows(UID, NotificationSource.ORDER));
        assertTrue(service.allows(UID, NotificationSource.AI_CHAT));
    }

    @Test
    @DisplayName("关掉「订单通知」只影响订单")
    void disablingOrderShouldOnlyAffectOrder() {
        NotifyPreferenceVO vo = allOn();
        vo.setOrderNotify(false);
        when(userMapper.getNotifyPreference(anyInt())).thenReturn(vo);

        assertFalse(service.allows(UID, NotificationSource.ORDER));
        assertTrue(service.allows(UID, NotificationSource.APPOINTMENT));
        assertTrue(service.allows(UID, NotificationSource.AI_CHAT));
    }

    @Test
    @DisplayName("关掉「AI 对话通知」只影响 AI")
    void disablingAiChatShouldOnlyAffectAiChat() {
        NotifyPreferenceVO vo = allOn();
        vo.setAiChatNotify(false);
        when(userMapper.getNotifyPreference(anyInt())).thenReturn(vo);

        assertFalse(service.allows(UID, NotificationSource.AI_CHAT));
        assertTrue(service.allows(UID, NotificationSource.APPOINTMENT));
        assertTrue(service.allows(UID, NotificationSource.ORDER));
    }

    @Test
    @DisplayName("关掉「系统消息通知」只影响系统公告")
    void disablingSystemShouldOnlyAffectSystem() {
        NotifyPreferenceVO vo = allOn();
        vo.setSystemNotify(false);
        when(userMapper.getNotifyPreference(anyInt())).thenReturn(vo);

        assertFalse(service.allows(UID, NotificationSource.SYSTEM));
        assertTrue(service.allows(UID, NotificationSource.ORDER));
    }

    @Test
    @DisplayName("关掉「健康提醒通知」只影响健康提醒")
    void disablingHealthRemindShouldOnlyAffectHealthRemind() {
        NotifyPreferenceVO vo = allOn();
        vo.setHealthRemindNotify(false);
        when(userMapper.getNotifyPreference(anyInt())).thenReturn(vo);

        assertFalse(service.allows(UID, NotificationSource.FOLLOWUP));
        assertTrue(service.allows(UID, NotificationSource.APPOINTMENT));
    }

    // ==================== 枚举本身不许被改坏 ====================

    @Test
    @DisplayName("来源枚举与消息中心分类一一对应，且 message_type 不与他类重号")
    void sourcesMustMapToDistinctMessageTypes() {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (NotificationSource source : NotificationSource.values()) {
            assertNotNull(source.getBizType(), source + " 缺 bizType");
            assertNotNull(source.getSource(), source + " 缺 source");
            assertNotNull(source.getType(), source + " 缺 type");
            assertNotNull(source.getPreferenceColumn(), source + " 缺开关列名");
            assertTrue(seen.add(source.messageTypeValue()),
                    source + " 的 message_type 与他类重号：" + source.messageTypeValue());
        }
        assertEquals(NotificationSource.values().length, seen.size());
    }
}
