package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.CrmException;
import cn.kmbeast.crm.agent.model.ReActResponse;
import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.crm.agent.tool.Tool;
import cn.kmbeast.crm.agent.tool.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段 3 四层 fail-safe 关键判定的单元测试（不发起 LLM 请求）。
 *
 * <p>直接调用 {@link BaseReActAgent} 的受保护方法 {@code tryOfflineFallback} 与
 * {@code shouldRetryForRouting}，覆盖：
 * <ul>
 *   <li>Layer 2 判定：首轮 + 富余轮次 + 高置信度数据意图 才重路由</li>
 *   <li>Layer 4 离线兜底：有身份 + 高置信度 + 工具存在 → 返回离线真实数据</li>
 *   <li>Layer 4 安全降级：无身份 / 无工具落点 / 工具缺失 → 返回 null（退回「服务不可用」）</li>
 * </ul>
 */
@DisplayName("四层 fail-safe 离线兜底与重路由判定")
class ReActFailSafeTest {

    /** 桩工具：query_health_records 返回固定数据 */
    private static class StubHealthTool implements Tool {
        @Override
        public String getName() {
            return "query_health_records";
        }

        @Override
        public String getDescription() {
            return "stub";
        }

        @Override
        public Map<String, Object> getParametersSchema() {
            return Map.of();
        }

        @Override
        public ToolResult execute(Map<String, Object> arguments) {
            return ToolResult.ok("STUB_BP_DATA");
        }
    }

    /** 手动构造一个只含桩工具的 ToolRegistry（避免 Spring 装配） */
    private ToolRegistry stubRegistry() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        Field tf = ToolRegistry.class.getDeclaredField("toolList");
        tf.setAccessible(true);
        tf.set(registry, List.of(new StubHealthTool()));
        registry.init();
        return registry;
    }

    private ReActAgent agentWithRegistry() throws Exception {
        ReActAgent agent = new ReActAgent();
        agent.toolRegistry = stubRegistry();
        return agent;
    }

    @AfterEach
    void clearThreadLocal() {
        ToolContext.clear();
        // 清掉 agent 可能 set 的 intentHint（ThreadLocal）
        new ReActAgent().clearConversationContext();
    }

    @Test
    @DisplayName("Layer4：高置信度指标意图 + 已登录身份 → 离线返回真实数据")
    void offlineFallbackSuccess() throws Exception {
        ReActAgent agent = agentWithRegistry();
        ToolContext.setPhoneAndUserId("13800000000", 1);
        ToolContext.setIdentity(2, null);  // 用户端
        IntentRouter.IntentHint hint = IntentRouter.IntentHint.healthRecords("血压", "血压");
        ReActResponse r = agent.tryOfflineFallback(hint);
        assertNotNull(r, "离线兜底不应为 null");
        assertTrue(r.getContent().contains("[离线模式]"), "应带离线模式标记");
        assertTrue(r.getContent().contains("STUB_BP_DATA"), "应返回工具真实数据");
        assertTrue(r.getToolsUsed().contains("query_health_records"));
    }

    @Test
    @DisplayName("Layer4：无身份（userId=null）→ 返回 null（不越权查数据）")
    void offlineFallbackNoIdentity() throws Exception {
        ReActAgent agent = agentWithRegistry();
        ToolContext.clear(); // 无 userId
        IntentRouter.IntentHint hint = IntentRouter.IntentHint.healthRecords("血压", "血压");
        assertNull(agent.tryOfflineFallback(hint), "无身份时不得离线查数据");
    }

    @Test
    @DisplayName("Layer4：非数据意图（CHAT）→ 无离线落点返回 null")
    void offlineFallbackNoToolForChat() throws Exception {
        ReActAgent agent = agentWithRegistry();
        ToolContext.setPhoneAndUserId("13800000000", 1);
        IntentRouter.IntentHint hint = IntentRouter.IntentHint.chat();
        assertNull(agent.tryOfflineFallback(hint), "CHAT 意图不应走离线工具");
    }

    @Test
    @DisplayName("Layer4：注册表里没有该工具 → 返回 null（安全降级）")
    void offlineFallbackToolMissing() throws Exception {
        ReActAgent agent = new ReActAgent();
        // 不注入 registry（toolRegistry 为 null）
        ToolContext.setPhoneAndUserId("13800000000", 1);
        IntentRouter.IntentHint hint = IntentRouter.IntentHint.healthRecords("血压", "血压");
        assertNull(agent.tryOfflineFallback(hint));
    }

    @Test
    @DisplayName("Layer2：首轮 + 有富余轮次 + 高置信度数据意图 → 应重路由")
    void retryDecisionShoulRetry() throws Exception {
        ReActAgent agent = agentWithRegistry();
        agent.setIntentHint(IntentRouter.IntentHint.healthRecords("血压", "血压"));
        assertTrue(agent.shouldRetryForRouting(1, 3), "首轮应重路由");
        assertFalse(agent.shouldRetryForRouting(2, 3), "非首轮不应重路由");
        assertFalse(agent.shouldRetryForRouting(1, 1), "无富余轮次不应重路由");
    }

    @Test
    @DisplayName("Layer2：DRUG 意图（无离线落点）→ 不应触发数据重路由")
    void retryDecisionNotForDrug() throws Exception {
        ReActAgent agent = agentWithRegistry();
        agent.setIntentHint(IntentRouter.IntentHint.drug());
        assertFalse(agent.shouldRetryForRouting(1, 3), "DRUG 意图不是高置信度数据意图");
    }

    @Test
    @DisplayName("Layer4 与 Layer2 共用：意图 hint 经 setIntentHint 注入后可读")
    void intentHintInjected() throws Exception {
        ReActAgent agent = agentWithRegistry();
        IntentRouter.IntentHint hint = IntentRouter.IntentHint.graph("糖尿病");
        agent.setIntentHint(hint);
        assertEquals(hint, agent.peekIntentHint());
        agent.clearConversationContext();
        assertNull(agent.peekIntentHint(), "clear 后应清除 intentHint，避免串号");
    }
}
