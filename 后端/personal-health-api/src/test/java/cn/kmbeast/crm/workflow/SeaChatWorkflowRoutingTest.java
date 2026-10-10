package cn.kmbeast.crm.workflow;

import cn.kmbeast.core.agent.AgentCoordinator;
import cn.kmbeast.core.agent.ChatRouting;
import cn.kmbeast.core.harness.HarnessEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * {@link SeaChatWorkflow#route(Integer, String)} 的降级规则测试。
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

    @Mock
    private AgentCoordinator agentCoordinator;

    @Mock
    private HarnessEngine harnessEngine;

    @InjectMocks
    private SeaChatWorkflow workflow;

    @Test
    @DisplayName("userId 为 null 时不落库、不追问（避免反复盘问却存不下来）")
    void nullUserIdMeansNoGuidance() {
        when(agentCoordinator.identifyAgent(any(), any())).thenReturn("doctor");
        when(harnessEngine.extractSlots(any())).thenReturn(Map.of("age", "34"));
        // userId=null 时 loadUserSlots 会返回「全部缺失」，追问话术非空——
        // 这正是必须拦掉的场景
        when(harnessEngine.buildGuidancePromptForUser(null)).thenReturn("## 本次需要主动追问的信息\n身高体重年龄");

        ChatRouting routing = workflow.route(null, "我最近血压有点高");

        assertNull(routing.getGuidance(),
                "认不出用户身份时仍生成追问，会导致每轮都问身高体重，而答案无处可存");
        verify(harnessEngine, never()).persistSlots(any(), any());
    }

    @Test
    @DisplayName("userId 有效时正常落库并生成追问")
    void validUserIdPersistsAndGuides() {
        when(agentCoordinator.identifyAgent(any(), any())).thenReturn("doctor");
        when(harnessEngine.extractSlots(any())).thenReturn(Map.of("age", "34"));
        when(harnessEngine.buildGuidancePromptForUser(4)).thenReturn("## 请补充身高体重");

        ChatRouting routing = workflow.route(4, "我34岁");

        assertEquals("doctor", routing.getAgentType());
        assertEquals("## 请补充身高体重", routing.getGuidance());
        assertTrue(routing.hasNewSlots());
        assertTrue(routing.getExtractedSlots().contains("age"));
        verify(harnessEngine).persistSlots(eq(4), any());
    }

    @Test
    @DisplayName("意图识别抛异常时退回默认角色，不让整轮对话失败")
    void identifyFailureFallsBackToDefault() {
        when(agentCoordinator.identifyAgent(any(), any())).thenThrow(new RuntimeException("词表配置炸了"));
        when(harnessEngine.extractSlots(any())).thenReturn(Map.of());

        ChatRouting routing = workflow.route(4, "随便问问");

        assertEquals("general_assistant", routing.getAgentType(),
                "角色识别是增强能力，失败时应降级而不是把异常抛给用户");
    }

    @Test
    @DisplayName("槽位抽取抛异常时仍能继续对话（只丢数据引导）")
    void extractFailureDegradesGracefully() {
        when(agentCoordinator.identifyAgent(any(), any())).thenReturn("nutritionist");
        when(harnessEngine.extractSlots(any())).thenThrow(new RuntimeException("正则炸了"));

        ChatRouting routing = workflow.route(4, "我想减肥");

        assertEquals("nutritionist", routing.getAgentType());
        assertFalse(routing.hasNewSlots());
    }

    @Test
    @DisplayName("追问话术生成失败不影响路由结果")
    void guidanceFailureDegradesGracefully() {
        when(agentCoordinator.identifyAgent(any(), any())).thenReturn("psychologist");
        when(harnessEngine.extractSlots(any())).thenReturn(Map.of());
        when(harnessEngine.buildGuidancePromptForUser(4)).thenThrow(new RuntimeException("档案表炸了"));

        ChatRouting routing = workflow.route(4, "最近压力很大");

        assertEquals("psychologist", routing.getAgentType());
        assertNull(routing.getGuidance());
    }

    @Test
    @DisplayName("识别返回 null/空白时归一为默认角色，不把脏值传给 agent")
    void blankAgentTypeNormalized() {
        when(agentCoordinator.identifyAgent(any(), any())).thenReturn(null);
        when(harnessEngine.extractSlots(any())).thenReturn(Map.of());

        assertEquals("general_assistant", workflow.route(4, "你好").getAgentType());
    }
}
