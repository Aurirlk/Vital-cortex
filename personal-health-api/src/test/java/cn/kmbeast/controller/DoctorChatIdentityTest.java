package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 医生端 ReAct Controller 的身份与鉴权契约（阶段 6）。
 *
 * <p><b>为什么用源码断言</b>：{@code ToolContext.setIdentity(3, doctorId)} 是
 * 「医生轨」唯一的开关，写错成 {@code (2, null)} 不会让任何现有单测变红
 * （工具会默默按用户轨查库），但会让接诊授权重查彻底失效。
 * 因此这里用反射 + 源码文本双重锁死，并配一条反向验证：
 * 临时把 {@code setIdentity(3, doctorId)} 改成 {@code (2, null)}，
 * {@link #doctorControllerDeclaresDoctorIdentity} 必须恰好真红。</p>
 */
@DisplayName("医生端 ReAct Controller 身份契约")
class DoctorChatIdentityTest {

    @Test
    @DisplayName("类映射在 /doctor/chat（由 DoctorIsolation 自动隔离）")
    void classMappedToDoctorChat() {
        RequestMapping mapping = DoctorChatController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping, "必须有 @RequestMapping");
        assertArrayEquals(new String[]{"/doctor/chat"}, mapping.value());
    }

    @Test
    @DisplayName("同步端点带 @Protector(role=医生)")
    void chatEndpointRequiresDoctorRole() throws Exception {
        Method m = DoctorChatController.class.getMethod(
                "chat", DoctorChatController.DoctorChatRequest.class);
        Protector protector = m.getAnnotation(Protector.class);
        assertNotNull(protector, "同步端点必须受 @Protector 保护");
        assertEquals("医生", protector.role());
    }

    @Test
    @DisplayName("流式端点带 @Protector(role=医生)")
    void streamEndpointRequiresDoctorRole() throws Exception {
        Method m = DoctorChatController.class.getMethod(
                "chatStream", DoctorChatController.DoctorChatRequest.class,
                jakarta.servlet.http.HttpServletResponse.class);
        Protector protector = m.getAnnotation(Protector.class);
        assertNotNull(protector, "流式端点必须受 @Protector 保护");
        assertEquals("医生", protector.role());
    }

    @Test
    @DisplayName("源码里必须显式写 setIdentity(3, doctorId)（反向验证探针的唯一目标）")
    void doctorControllerDeclaresDoctorIdentity() throws Exception {
        String src = readMainSource("controller/DoctorChatController.java");
        assertTrue(src.contains("ToolContext.setIdentity(3, doctorId)"),
                "医生端必须把工具身份标成 roleId=3 且 doctorId 来自 ThreadLocal；"
                        + "写成 (2, null) 会让 DataQueryTool 的医生分支永远进不去");
        assertTrue(src.contains("LocalThreadHolder.getUserId()"),
                "doctorId 只能来自 JWT 写入的 ThreadLocal");
        assertFalse(src.contains("ToolContext.setIdentity(2, null)"),
                "医生端 Controller 绝不能把身份写成用户端 (2, null) —— 反向验证探针目标");
        // 同步与流式两条链路都必须各自声明医生身份。
        // 只统计带分号的真实语句：类 Javadoc 里的 {@code ...} 不带分号，不能算作调用。
        assertEquals(2, countOccurrences(src, "ToolContext.setIdentity(3, doctorId);"),
                "同步 / 流式两个入口都要写 setIdentity(3, doctorId)");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    @Test
    @DisplayName("SeaChatWorkflow 同步链路按 audience 写身份，不再无条件覆盖成 2")
    void workflowDoesNotOverrideDoctorIdentity() throws Exception {
        String src = readMainSource("crm/workflow/SeaChatWorkflow.java");
        assertTrue(src.contains("ToolContext.setIdentity(3, userId)"),
                "processChat 必须对 Audience.DOCTOR 写医生身份");
        assertTrue(src.contains("Audience.DOCTOR.equals(audience)"),
                "身份判定必须依据 audience，而不是写死");
    }

    @Test
    @DisplayName("请求 DTO 不接受任何身份参数（身份只从 ThreadLocal 取）")
    void requestDtoHasNoIdentityFields() {
        Set<String> fields = Arrays.stream(DoctorChatController.DoctorChatRequest.class.getDeclaredFields())
                .map(Field::getName)
                .filter(n -> !n.startsWith("$") && !n.startsWith("this"))
                .collect(Collectors.toSet());
        assertEquals(Set.of("message", "sessionId"), fields,
                "doctorId / patientId / userId 一律不得出现在医生端请求体里");
    }

    @Test
    @DisplayName("两个端点都声明了 Sentinel 资源（ai:doctorChat*）")
    void endpointsDeclareSentinelResources() throws Exception {
        Method chat = DoctorChatController.class.getMethod(
                "chat", DoctorChatController.DoctorChatRequest.class);
        Method stream = DoctorChatController.class.getMethod(
                "chatStream", DoctorChatController.DoctorChatRequest.class,
                jakarta.servlet.http.HttpServletResponse.class);
        assertEquals("ai:doctorChat",
                chat.getAnnotation(com.alibaba.csp.sentinel.annotation.SentinelResource.class).value());
        assertEquals("ai:doctorChatStream",
                stream.getAnnotation(com.alibaba.csp.sentinel.annotation.SentinelResource.class).value());
    }

    /** surefire 工作目录是模块根（与 MapperTableConsistencyTest 的 Paths.get("src",...) 同口径）。 */
    private static String readMainSource(String relativeUnderCnKmbeast) throws Exception {
        Path p = Paths.get("src", "main", "java", "cn", "kmbeast", relativeUnderCnKmbeast);
        assertTrue(Files.isRegularFile(p), "找不到源码: " + p.toAbsolutePath());
        return Files.readString(p, StandardCharsets.UTF_8);
    }
}
