package cn.kmbeast.crm.workflow;

import cn.kmbeast.core.agent.orchestrator.AgentOrchestrator;
import cn.kmbeast.core.agent.Audience;
import cn.kmbeast.core.agent.ChatRouting;
import cn.kmbeast.core.harness.HarnessEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * {@link SeaChatWorkflow#route(Integer, String, String)} 的降级规则测试与端透传测试。
 *
 * <p><b>为什么这组规则必须锁死</b>：2026-10-06 复核时发现，用户端同步聊天把
 * {@code user_account}（登录名 like/lily）当手机号传给工作流，工作流再按
 * {@code WHERE phone = 'lily'} 反查——而 {@code user.phone} 在库里大量为 NULL，
 * 反查必然返回 null。若此时仍生成追问话术，就会出现最坏组合：
 * <b>患者档案里明明有身高体重，AI 却每轮都在追问</b>，而抽到的值又存不进去。
 * 所以「认不出身份就不做数据引导」不是可选优化，是正确性要求。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("对话路由的降级规则")
class SeaChatWorkflowRoutingTest {

    /**
     * 阶段 1 起路由决策者是 AgentOrchestrator。
     *
     * <p>此前这里 mock 的是 {@code AgentCoordinator} —— 改造后
     * {@code SeaChatWorkflow} 不再持有它，{@code @InjectMocks} 注入不进去，
     * 于是 {@code agentOrchestrator} 为 null、route() 内部抛 NPE 被 catch 吞掉，
     * 全部落回 {@code general_assistant}。本组用例真正要锁的是
     * <b>「路由出问题时怎么降级」</b>（槽位抽取炸了/追问话术炸了/路由本身炸了），
     * 而不是路由算法本身 —— 算法由 {@code AgentOrchestratorRoutingTest} 单独覆盖，
     * 所以这里把编排器整体 mock 掉、只保留它「返回/抛出什么」这一个自由度，是对的。</p>
     */
    @Mock
    private AgentOrchestrator agentOrchestrator;

    @Mock
    private HarnessEngine harnessEngine;

    @InjectMocks
    private SeaChatWorkflow workflow;

    @Test
    @DisplayName("userId 为 null 时不落库、不追问（避免反复盘问却存不下来）")
    void nullUserIdMeansNoGuidance() {
        when(agentOrchestrator.route(any(), any(), any())).thenReturn("doctor");
        when(harnessEngine.extractSlots(any())).thenReturn(Map.of("age", "34"));
        // userId=null 时 loadUserSlots 会返回「全部缺失」，追问话术非空——
        // 这正是必须拦掉的场景
        when(harnessEngine.buildGuidancePromptForUser(null)).thenReturn("## 本次需要主动追问的信息\n身高体重年龄");

        ChatRouting routing = workflow.route(null, "我最近血压有点高", Audience.USER);

        assertNull(routing.getGuidance(),
                "认不出用户身份时仍生成追问，会导致每轮都问身高体重，而答案无处可存");
        verify(harnessEngine, never()).persistSlots(any(), any());
    }

    @Test
    @DisplayName("userId 有效时正常落库并生成追问")
    void validUserIdPersistsAndGuides() {
        when(agentOrchestrator.route(any(), any(), any())).thenReturn("doctor");
        when(harnessEngine.extractSlots(any())).thenReturn(Map.of("age", "34"));
        when(harnessEngine.buildGuidancePromptForUser(4)).thenReturn("## 请补充身高体重");

        ChatRouting routing = workflow.route(4, "我34岁", Audience.USER);

        assertEquals("doctor", routing.getAgentType());
        assertEquals("## 请补充身高体重", routing.getGuidance());
        assertTrue(routing.hasNewSlots());
        assertTrue(routing.getExtractedSlots().contains("age"));
        verify(harnessEngine).persistSlots(eq(4), any());
    }

    @Test
    @DisplayName("意图识别抛异常时退回默认角色，不让整轮对话失败")
    void identifyFailureFallsBackToDefault() {
        when(agentOrchestrator.route(any(), any(), any())).thenThrow(new RuntimeException("词表配置炸了"));
        when(harnessEngine.extractSlots(any())).thenReturn(Map.of());

        ChatRouting routing = workflow.route(4, "随便问问", Audience.USER);

        assertEquals("general_assistant", routing.getAgentType(),
                "角色识别是增强能力，失败时应降级而不是把异常抛给用户");
    }

    @Test
    @DisplayName("槽位抽取抛异常时仍能继续对话（只丢数据引导）")
    void extractFailureDegradesGracefully() {
        when(agentOrchestrator.route(any(), any(), any())).thenReturn("nutritionist");
        when(harnessEngine.extractSlots(any())).thenThrow(new RuntimeException("正则炸了"));

        ChatRouting routing = workflow.route(4, "我想减肥", Audience.USER);

        assertEquals("nutritionist", routing.getAgentType());
        assertFalse(routing.hasNewSlots());
    }

    @Test
    @DisplayName("追问话术生成失败不影响路由结果")
    void guidanceFailureDegradesGracefully() {
        when(agentOrchestrator.route(any(), any(), any())).thenReturn("psychologist");
        when(harnessEngine.extractSlots(any())).thenReturn(Map.of());
        when(harnessEngine.buildGuidancePromptForUser(4)).thenThrow(new RuntimeException("档案表炸了"));

        ChatRouting routing = workflow.route(4, "最近压力很大", Audience.USER);

        assertEquals("psychologist", routing.getAgentType());
        assertNull(routing.getGuidance());
    }

    @Test
    @DisplayName("识别返回 null/空白时归一为默认角色，不把脏值传给 agent")
    void blankAgentTypeNormalized() {
        when(agentOrchestrator.route(any(), any(), any())).thenReturn(null);
        when(harnessEngine.extractSlots(any())).thenReturn(Map.of());

        assertEquals("general_assistant", workflow.route(4, "你好", Audience.USER).getAgentType());
    }

    // ==================== 阶段 3：端透传 ====================

    /**
     * 用 ArgumentCaptor 而不是 {@code verify(..., eq("user"))}：要断言的是
     * 「工作流把调用方给的端原样交给了编排器」，包括不被别处改写。
     * 若将来有人在这里插一层「端归一化」，captor 会把实际值打出来。
     */
    @Test
    @DisplayName("调用方声明的端被原样透传给编排器（医生端的 Agent 才会被排除）")
    void audienceIsForwardedToOrchestrator() {
        when(agentOrchestrator.route(any(), any(), any())).thenReturn("doctor");
        when(harnessEngine.extractSlots(any())).thenReturn(Map.of());

        workflow.route(4, "我最近血压有点高", Audience.USER);

        ArgumentCaptor<String> audience = ArgumentCaptor.forClass(String.class);
        verify(agentOrchestrator).route(any(), eq("我最近血压有点高"), audience.capture());
        assertEquals(Audience.USER, audience.getValue(),
                "写死 null 正是改造前的缺陷：null 对编排器而言是「不过滤」，"
                        + "于是只在医生端开放的 Agent 会从用户端被路由到");
    }

    @Test
    @DisplayName("端传 null 仍表示「不过滤」，保留改造前的兼容语义")
    void nullAudienceStaysUnfiltered() {
        when(agentOrchestrator.route(any(), any(), any())).thenReturn("doctor");
        when(harnessEngine.extractSlots(any())).thenReturn(Map.of());

        workflow.route(4, "你好", null);

        verify(agentOrchestrator).route(any(), any(), isNull());
    }
}
