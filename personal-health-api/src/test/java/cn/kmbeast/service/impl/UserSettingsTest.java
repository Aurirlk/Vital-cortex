package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 用户设置读写测试 —— 2026-10-10 新增。
 *
 * <p>这个页面此前的问题不是「不好用」，而是<b>根本没有存储</b>：读的是硬编码默认值，
 * 写的是「暂未实现」的错误。所以用例的重点是钉住「真的读到了库里的值 / 真的写了库」，
 * 而不是接口返回 200。
 *
 * <p>另一半重点在校验：设置项是枚举与布尔，且值来自前端的下拉框，
 * 不做白名单校验的话，任何字符串都能写进这些列，
 * 以后通知发送方按这些值判断时就会遇到意料之外的值。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("用户设置（读写与校验）")
class UserSettingsTest {

    private static final Integer USER_ID = 1001;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl service;

    private static Integer okCode() {
        return ApiResult.success().getCode();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> group(Map<String, Object> settings, String key) {
        return (Map<String, Object>) settings.get(key);
    }

    private User userWithSettings() {
        User user = new User();
        user.setId(USER_ID);
        user.setLanguage("en-US");
        user.setTimezone("Europe/London");
        user.setNotifySystem(false);
        user.setNotifyHealthRemind(true);
        user.setNotifyAiChat(false);
        user.setNotifyAppointment(true);
        user.setNotifyOrder(false);
        user.setPrivacyHealthDataShare(false);
        user.setPrivacyProfileVisibility("public");
        user.setAiDefaultAgent("doctor");
        user.setAiChatStyle("concise");
        user.setAiEnableWebSearch(false);
        user.setAiEnableKnowledgeBase(true);
        user.setAiContextRounds(5);
        return user;
    }

    // ==================== 读 ====================

    @Test
    @DisplayName("读取：返回库里的真实设置（而不是固定默认值）")
    void shouldReturnStoredSettings() {
        when(userMapper.getUserById(USER_ID)).thenReturn(userWithSettings());

        Map<String, Object> settings = service.getUserSettings(USER_ID);

        assertEquals("en-US", settings.get("language"));
        assertEquals("Europe/London", settings.get("timezone"));
        assertEquals(Boolean.FALSE, group(settings, "notification").get("systemNotify"));
        assertEquals(Boolean.FALSE, group(settings, "notification").get("aiChatNotify"));
        assertEquals(Boolean.FALSE, group(settings, "notification").get("orderNotify"));
        assertEquals(Boolean.FALSE, group(settings, "privacy").get("healthDataShare"));
        assertEquals("public", group(settings, "privacy").get("profileVisibility"));
        assertEquals("doctor", group(settings, "ai").get("defaultAgent"));
        assertEquals("concise", group(settings, "ai").get("chatStyle"));
        assertEquals(Boolean.FALSE, group(settings, "ai").get("enableWebSearch"));
        assertEquals(5, group(settings, "ai").get("contextRounds"));
    }

    @Test
    @DisplayName("读取：列为 NULL 的历史行回退默认值，前端不必自己做空判断")
    void shouldFallBackToDefaultsForNullColumns() {
        User blank = new User();
        blank.setId(USER_ID);
        when(userMapper.getUserById(USER_ID)).thenReturn(blank);

        Map<String, Object> settings = service.getUserSettings(USER_ID);

        assertEquals("zh-CN", settings.get("language"));
        assertEquals("Asia/Shanghai", settings.get("timezone"));
        assertEquals(Boolean.TRUE, group(settings, "notification").get("systemNotify"));
        assertEquals("private", group(settings, "privacy").get("profileVisibility"));
        assertEquals("general_assistant", group(settings, "ai").get("defaultAgent"));
        assertEquals(10, group(settings, "ai").get("contextRounds"));
    }

    @Test
    @DisplayName("读取：空串同样按未设置处理，越界的上下文轮数也回退默认值")
    void shouldTreatBlankAndOutOfRangeAsUnset() {
        User u = new User();
        u.setId(USER_ID);
        u.setLanguage("   ");
        u.setAiContextRounds(999);   // 超出 1~20
        when(userMapper.getUserById(USER_ID)).thenReturn(u);

        Map<String, Object> settings = service.getUserSettings(USER_ID);

        assertEquals("zh-CN", settings.get("language"));
        assertEquals(10, group(settings, "ai").get("contextRounds"));
    }

    // ==================== 写 ====================

    @Test
    @DisplayName("保存通知设置：只写通知那几列，其余设置列保持不动")
    void shouldSaveOnlySubmittedGroup() {
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> notification = new HashMap<>();
        notification.put("appointmentRemind", false);
        body.put("notification", notification);

        Result<String> r = service.updateUserSettings(USER_ID, body);

        assertEquals(okCode(), r.getCode());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateSettings(captor.capture());
        User patch = captor.getValue();
        assertEquals(USER_ID, patch.getId());
        assertEquals(Boolean.FALSE, patch.getNotifyAppointment());
        // 未提交的项必须为 null，否则动态 SQL 会把它们一并覆盖成 null
        assertNull(patch.getNotifySystem());
        assertNull(patch.getLanguage());
        assertNull(patch.getAiContextRounds());
    }

    @Test
    @DisplayName("保存基本设置：语言与时区分别落到各自的列")
    void shouldSaveLanguageAndTimezone() {
        Map<String, Object> body = new HashMap<>();
        body.put("language", "en-US");
        body.put("timezone", "Europe/London");

        assertEquals(okCode(), service.updateUserSettings(USER_ID, body).getCode());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateSettings(captor.capture());
        assertEquals("en-US", captor.getValue().getLanguage());
        assertEquals("Europe/London", captor.getValue().getTimezone());
    }

    @Test
    @DisplayName("保存 AI 设置：布尔、枚举、数值三类都正确落到列上")
    void shouldSaveAiSettings() {
        Map<String, Object> ai = new HashMap<>();
        ai.put("defaultAgent", "nutritionist");
        ai.put("chatStyle", "professional");
        ai.put("enableWebSearch", false);
        ai.put("enableKnowledgeBase", true);
        ai.put("contextRounds", 20);
        Map<String, Object> body = new HashMap<>();
        body.put("ai", ai);

        assertEquals(okCode(), service.updateUserSettings(USER_ID, body).getCode());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateSettings(captor.capture());
        User patch = captor.getValue();
        assertEquals("nutritionist", patch.getAiDefaultAgent());
        assertEquals("professional", patch.getAiChatStyle());
        assertEquals(Boolean.FALSE, patch.getAiEnableWebSearch());
        assertEquals(Boolean.TRUE, patch.getAiEnableKnowledgeBase());
        assertEquals(20, patch.getAiContextRounds());
    }

    @Test
    @DisplayName("布尔项兼容 0/1：前端表单与 switch 两种提交形式都要能存")
    void shouldAcceptNumericBooleans() {
        Map<String, Object> notification = new HashMap<>();
        notification.put("systemNotify", 0);
        notification.put("orderNotify", 1);
        Map<String, Object> body = new HashMap<>();
        body.put("notification", notification);

        assertEquals(okCode(), service.updateUserSettings(USER_ID, body).getCode());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateSettings(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getNotifySystem());
        assertEquals(Boolean.TRUE, captor.getValue().getNotifyOrder());
    }

    // ==================== 校验：一律明确报错，不静默丢弃 ====================

    @Test
    @DisplayName("未知的顶层设置项：报错，而不是忽略后假装保存成功")
    void shouldRejectUnknownTopLevelKey() {
        Map<String, Object> body = new HashMap<>();
        body.put("theme", "dark");

        Result<String> r = service.updateUserSettings(USER_ID, body);

        assertNotEquals(okCode(), r.getCode());
        assertTrue(r.getMsg().contains("theme"), "错误信息要指出是哪个键，否则前端无从排查");
        verify(userMapper, never()).updateSettings(any());
    }

    @Test
    @DisplayName("组内未知字段：报错（多半是前端加了字段而后端没跟上）")
    void shouldRejectUnknownKeyInsideGroup() {
        Map<String, Object> notification = new HashMap<>();
        notification.put("emailNotify", true);
        Map<String, Object> body = new HashMap<>();
        body.put("notification", notification);

        Result<String> r = service.updateUserSettings(USER_ID, body);

        assertNotEquals(okCode(), r.getCode());
        assertTrue(r.getMsg().contains("emailNotify"));
        verify(userMapper, never()).updateSettings(any());
    }

    @Test
    @DisplayName("枚举取值越界：报错，不让任意字符串写进枚举列")
    void shouldRejectInvalidEnumValues() {
        Map<String, Object> bad1 = new HashMap<>();
        bad1.put("language", "ja-JP");
        assertNotEquals(okCode(), service.updateUserSettings(USER_ID, bad1).getCode());

        Map<String, Object> privacy = new HashMap<>();
        privacy.put("profileVisibility", "everyone");
        Map<String, Object> bad2 = new HashMap<>();
        bad2.put("privacy", privacy);
        assertNotEquals(okCode(), service.updateUserSettings(USER_ID, bad2).getCode());

        verify(userMapper, never()).updateSettings(any());
    }

    @Test
    @DisplayName("上下文轮数越界或非整数：报错")
    void shouldRejectInvalidContextRounds() {
        Map<String, Object> ai1 = new HashMap<>();
        ai1.put("contextRounds", 0);
        Map<String, Object> bad1 = new HashMap<>();
        bad1.put("ai", ai1);
        assertNotEquals(okCode(), service.updateUserSettings(USER_ID, bad1).getCode());

        Map<String, Object> ai2 = new HashMap<>();
        ai2.put("contextRounds", 21);
        Map<String, Object> bad2 = new HashMap<>();
        bad2.put("ai", ai2);
        assertNotEquals(okCode(), service.updateUserSettings(USER_ID, bad2).getCode());

        Map<String, Object> ai3 = new HashMap<>();
        ai3.put("contextRounds", 3.5);
        Map<String, Object> bad3 = new HashMap<>();
        bad3.put("ai", ai3);
        assertNotEquals(okCode(), service.updateUserSettings(USER_ID, bad3).getCode());

        verify(userMapper, never()).updateSettings(any());
    }

    @Test
    @DisplayName("布尔项传了字符串 / 对象：报错")
    void shouldRejectNonBooleanValue() {
        Map<String, Object> notification = new HashMap<>();
        notification.put("systemNotify", "yes");
        Map<String, Object> body = new HashMap<>();
        body.put("notification", notification);

        assertNotEquals(okCode(), service.updateUserSettings(USER_ID, body).getCode());
        verify(userMapper, never()).updateSettings(any());
    }

    @Test
    @DisplayName("空请求体 / 未登录：报错，且不写库")
    void shouldRejectEmptyOrAnonymous() {
        assertNotEquals(okCode(), service.updateUserSettings(USER_ID, new HashMap<>()).getCode());
        assertNotEquals(okCode(), service.updateUserSettings(USER_ID, null).getCode());
        assertNotEquals(okCode(), service.updateUserSettings(null, Map.of("language", "zh-CN")).getCode());
        verify(userMapper, never()).updateSettings(any());
    }
}
