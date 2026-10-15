package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.core.agent.registry.AgentDefinition;
import cn.kmbeast.core.agent.registry.AgentRegistry;
import cn.kmbeast.crm.agent.ReActAgent;
import cn.kmbeast.crm.agent.model.ReActResponse;
import cn.kmbeast.crm.agent.model.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link CallAgentTool} 三道闸门的单测。
 *
 * <p>这个工具是整个「多 Agent」架构里<b>唯一一处让模型去指挥另一个模型</b>的地方，
 * 因此它的失败模式与其他工具完全不同：其他工具被越权调用最多是读错数据，
 * 这里被越权调用是<b>越过授权边界去驱动一个本不该被驱动的 Agent</b>，
 * 而那条路径上的一切（工具权限、审计身份）都会以被委派者的名义执行。</p>
 *
 * <p>三道闸门分别对应三种「模型不可信」的假设：</p>
 * <ol>
 *   <li>模型可能<b>声称</b>自己有权委派 → 授权只能查注册表，不能信模型；</li>
 *   <li>模型可能<b>递归</b>委派（A 问 B，B 又问 A）→ 深度上限兜住；</li>
 *   <li>模型可能<b>编造</b>一个目标 key → 目标必须存在且启用。</li>
 * </ol>
 *
 * <p>另一个容易漏掉的点是<b>发起方身份的来源</b>：本工具跑在工具线程池上，
 * {@code BaseReActAgent} 的 ThreadLocal 在池线程上是空的，只能从
 * {@link ToolContext} 取。取不到时必须<b>拒绝</b>而不是「当作根 Agent 放行」——
 * 后者会让身份丢失悄悄退化成一次越权。</p>
 */
@DisplayName("CallAgentTool 委派闸门")
class CallAgentToolGuardTest {

    private static final String CALLER = "general_assistant";
    private static final String TARGET = "doctor";

    private CallAgentTool tool;
    private AgentRegistry registry;
    private ReActAgent executor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = mock(AgentRegistry.class);
        executor = mock(ReActAgent.class);

        ObjectProvider<ReActAgent> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(executor);

        tool = new CallAgentTool();
        ReflectionTestUtils.setField(tool, "agentRegistry", registry);
        ReflectionTestUtils.setField(tool, "reActAgentProvider", provider);

        when(registry.get(TARGET)).thenReturn(enabled(TARGET));
        when(registry.isHandoffAllowed(CALLER, TARGET)).thenReturn(true);
        ToolContext.set(ToolContext.KEY_AGENT_KEY, CALLER);
    }

    @AfterEach
    void tearDown() {
        ToolContext.clear();
    }

    private static AgentDefinition enabled(String key) {
        return AgentDefinition.builder()
                .agentKey(key).name("全科医生").audience("all")
                .maxIterations(5).enabled(true).handoffTargets(Set.of()).build();
    }

    private static Map<String, Object> args(String agentKey, String question) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        if (agentKey != null) {
            map.put(CallAgentTool.ARG_AGENT_KEY, agentKey);
        }
        if (question != null) {
            map.put(CallAgentTool.ARG_QUESTION, question);
        }
        return map;
    }

    private static void assertRejected(ToolResult result) {
        assertFalse(result.isSuccess(), "这次调用必须被拒绝，而不是让模型自己看着办");
        assertNotNull(result.getContent());
        assertFalse(result.getContent().isBlank(), "拒绝也要给出原因，否则模型会反复重试同一个动作");
    }

    // ==================== 装配与参数 ====================

    @Test
    @DisplayName("T1 注册表未装配 → 拒绝（不是 NPE）")
    void unassembledRegistryRejects() {
        ReflectionTestUtils.setField(tool, "agentRegistry", null);
        assertRejected(tool.execute(args(TARGET, "我头痛")));
    }

    @Test
    @DisplayName("T2 缺少 agent_key / question / 全空 → 各自给出可操作的错误")
    void missingArgumentsRejected() {
        ToolResult noKey = tool.execute(args(null, "我头痛"));
        assertRejected(noKey);
        assertTrue(noKey.getContent().contains(CallAgentTool.ARG_AGENT_KEY));

        ToolResult noQuestion = tool.execute(args(TARGET, null));
        assertRejected(noQuestion);
        assertTrue(noQuestion.getContent().contains(CallAgentTool.ARG_QUESTION));

        assertRejected(tool.execute(Map.of()));
        assertRejected(tool.execute(null));
    }

    @Test
    @DisplayName("T3 空白字符串等同于缺失（模型常传 \"\" 而不是省略字段）")
    void blankArgumentsTreatedAsMissing() {
        assertRejected(tool.execute(args("   ", "我头痛")));
        assertRejected(tool.execute(args(TARGET, "  ")));
    }

    // ==================== 闸门 ⓪：身份 ====================

    @Test
    @DisplayName("T4 拿不到发起方身份 → 拒绝，绝不「当作根 Agent 放行」")
    void missingCallerIdentityRejects() {
        ToolContext.clear();
        ToolResult result = tool.execute(args(TARGET, "我头痛"));
        assertRejected(result);
        verify(registry, never()).isHandoffAllowed(any(), any());
    }

    // ==================== 闸门 ①：关系白名单 ====================

    @Test
    @DisplayName("T5 未配置 A→B 的关系 → 拒绝，且不触达执行器")
    void unauthorizedHandoffRejected() {
        when(registry.isHandoffAllowed(CALLER, TARGET)).thenReturn(false);
        assertRejected(tool.execute(args(TARGET, "我头痛")));
        verify(executor, never()).runWithContext(anyList(), any(), any());
    }

    @Test
    @DisplayName("T6 授权是有方向的：反向未配置同样拒绝")
    void authorizationIsDirectional() {
        when(registry.isHandoffAllowed(TARGET, CALLER)).thenReturn(true);
        when(registry.isHandoffAllowed(CALLER, TARGET)).thenReturn(false);
        assertRejected(tool.execute(args(TARGET, "我头痛")));
    }

    // ==================== 闸门 ③：目标可用性 ====================

    @Test
    @DisplayName("T7 目标不存在 → 拒绝（模型编造 key 的兜底）")
    void unknownTargetRejected() {
        when(registry.get("ghost_agent")).thenReturn(null);
        assertRejected(tool.execute(args("ghost_agent", "我头痛")));
        verify(executor, never()).runWithContext(anyList(), any(), any());
    }

    @Test
    @DisplayName("T8 目标已停用 → 拒绝（授权还在，但管理员已经关掉了它）")
    void disabledTargetRejected() {
        AgentDefinition disabled = AgentDefinition.builder()
                .agentKey(TARGET).name("全科医生").audience("all")
                .maxIterations(5).enabled(false).handoffTargets(Set.of()).build();
        when(registry.get(TARGET)).thenReturn(disabled);
        assertRejected(tool.execute(args(TARGET, "我头痛")));
        verify(executor, never()).runWithContext(anyList(), any(), any());
    }

    // ==================== 闸门 ②：深度 ====================

    @Test
    @DisplayName("T9 根 Agent（depth=0）可委派；已委派过一次（depth=1）必须拒绝")
    void depthGateAllowsExactlyOneHop() {
        ToolContext.set(ToolContext.KEY_HANDOFF_DEPTH, 1);
        assertRejected(tool.execute(args(TARGET, "我头痛")));
        verify(executor, never()).runWithContext(anyList(), any(), any());
    }

    @Test
    @DisplayName("T10 子 Agent 未给出有效回答 → 拒绝，且不把空串当答案收回")
    void emptySubAnswerRejected() throws Exception {
        when(executor.runWithContext(anyList(), eq(TARGET), any()))
                .thenReturn(ReActResponse.builder().content("   ").build());
        assertRejected(tool.execute(args(TARGET, "我头痛")));
    }

    @Test
    @DisplayName("T11 子 Agent 抛异常 → 收敛成拒绝，不冒泡成 500")
    void subAgentFailureIsContained() throws Exception {
        when(executor.runWithContext(anyList(), eq(TARGET), any()))
                .thenThrow(new RuntimeException("模型超时"));
        assertRejected(tool.execute(args(TARGET, "我头痛")));
    }

    @Test
    @DisplayName("T12 执行器不可用 → 拒绝")
    @SuppressWarnings("unchecked")
    void missingExecutorRejected() {
        ObjectProvider<ReActAgent> empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable()).thenReturn(null);
        ReflectionTestUtils.setField(tool, "reActAgentProvider", empty);
        assertRejected(tool.execute(args(TARGET, "我头痛")));
    }

    // ==================== 放行路径 ====================

    @Test
    @DisplayName("T13 放行：结论原样收回，子 Agent 看到 depth=1，调用方结束后深度归还为 0")
    void happyPathReturnsSubAnswerAndRestoresDepth() throws Exception {
        java.util.concurrent.atomic.AtomicInteger childDepth = new java.util.concurrent.atomic.AtomicInteger(-1);
        when(executor.runWithContext(anyList(), eq(TARGET), any())).thenAnswer(inv -> {
            // 站在子 Agent 的视角读一次 —— 它跑在池线程上，只能靠这份透传值知道自己在第几层
            childDepth.set(ToolContext.getHandoffDepth());
            return ReActResponse.builder().content("建议多休息、监测体温。").build();
        });

        ToolResult result = tool.execute(args(TARGET, "我发烧三天了，怎么办"));

        assertTrue(result.isSuccess());
        assertEquals("建议多休息、监测体温。", result.getContent());
        assertEquals(1, childDepth.get(), "子 Agent 必须看到 depth=1，否则它的下一层委派闸门形同虚设");
        assertEquals(0, ToolContext.getHandoffDepth(),
                "委派结束后深度必须归还给父 Agent，不能永久留在 ThreadLocal 里");
    }

    @Test
    @DisplayName("T19 一次失败的委派不消耗深度预算：失败后仍可再次委派")
    void failedHandoffDoesNotBurnTheDepthBudget() throws Exception {
        when(executor.runWithContext(anyList(), eq(TARGET), any()))
                .thenThrow(new RuntimeException("第一次超时"))
                .thenReturn(ReActResponse.builder().content("第二次成功").build());

        assertRejected(tool.execute(args(TARGET, "我头痛")));

        ToolResult second = tool.execute(args(TARGET, "我头痛"));
        assertTrue(second.isSuccess(),
                "重试同一个委派不该被告知「层级已达上限」——它一次都没成功委派过，"
                        + "模型收到这个假理由后不会重试，这一轮就白丢了一块能力");
        assertEquals("第二次成功", second.getContent());
    }

    @Test
    @DisplayName("T14 委派只带子问题，不夹带父对话历史")
    void onlySubQuestionIsForwarded() throws Exception {
        when(executor.runWithContext(anyList(), eq(TARGET), any()))
                .thenReturn(ReActResponse.builder().content("好的").build());

        tool.execute(args(TARGET, "我发烧三天了"));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(executor).runWithContext(captor.capture(), eq(TARGET), any());

        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = captor.getValue();
        assertEquals(1, messages.size(), "父对话可能很长且会带偏子 Agent，只传子问题");
        assertEquals("user", messages.get(0).get("role"));
        assertEquals("我发烧三天了", messages.get(0).get("content"));
    }

    @Test
    @DisplayName("T15 超长子问题被截断，不会把单次调用的预算撑爆")
    void oversizedQuestionIsTruncated() throws Exception {
        when(executor.runWithContext(anyList(), eq(TARGET), any()))
                .thenReturn(ReActResponse.builder().content("好的").build());

        tool.execute(args(TARGET, "很".repeat(5000)));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(executor).runWithContext(captor.capture(), eq(TARGET), any());
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = captor.getValue();
        assertEquals(2000, messages.get(0).get("content").length());
    }

    @Test
    @DisplayName("T16 无论成功或异常，父 Agent 的对话上下文都被还原")
    void parentContextAlwaysRestored() throws Exception {
        when(executor.runWithContext(anyList(), eq(TARGET), any()))
                .thenThrow(new RuntimeException("炸了"));
        tool.execute(args(TARGET, "我头痛"));
        verify(executor).restoreContext(any());

        reset(executor);
        when(executor.runWithContext(anyList(), eq(TARGET), any()))
                .thenReturn(ReActResponse.builder().content("好的").build());
        tool.execute(args(TARGET, "我头痛"));
        verify(executor).restoreContext(any());
    }

    // ==================== schema ====================

    @Test
    @DisplayName("T17 目标枚举来自注册表；没有配过任何委派时不写 enum（让模型别猜）")
    void schemaEnumComesFromRegistry() {
        when(registry.allHandoffTargets()).thenReturn(Set.of(TARGET));
        Map<String, Object> schema = tool.getParametersSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> keyProp = (Map<String, Object>) props.get(CallAgentTool.ARG_AGENT_KEY);
        assertEquals(List.of(TARGET), keyProp.get("enum"));

        when(registry.allHandoffTargets()).thenReturn(Set.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> props2 = (Map<String, Object>)
                tool.getParametersSchema().get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> keyProp2 = (Map<String, Object>) props2.get(CallAgentTool.ARG_AGENT_KEY);
        assertFalse(keyProp2.containsKey("enum"),
                "没有配过任何委派关系时，列出 enum 只会诱导模型去调一个必然被拒的目标");
    }

    @Test
    @DisplayName("T18 两个参数都是必填，且注册表未装配时不 NPE")
    void schemaRequiredAndNullSafe() {
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) tool.getParametersSchema().get("required");
        assertEquals(List.of(CallAgentTool.ARG_AGENT_KEY, CallAgentTool.ARG_QUESTION), required);

        ReflectionTestUtils.setField(tool, "agentRegistry", null);
        assertNotNull(tool.getParametersSchema(), "装配缺失时 schema 仍要能生成，否则启动期收集工具就会崩");
    }
}
