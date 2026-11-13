package cn.kmbeast.crm.workflow;

import cn.kmbeast.core.agent.Audience;
import cn.kmbeast.core.agent.ChatRouting;
import cn.kmbeast.core.agent.orchestrator.AgentOrchestrator;
import cn.kmbeast.core.agent.registry.AgentDefinition;
import cn.kmbeast.core.agent.registry.AgentRegistry;
import cn.kmbeast.core.harness.HarnessEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 多端解耦的端到端用例：端过滤是否<b>真的作用在整条路由链路上</b>。
 *
 * <p><b>为什么不能只测编排器</b>：{@code AgentOrchestratorRoutingTest.T6} 已经证明
 * 「传了端就会过滤」—— 但改造前 {@code SeaChatWorkflow} 恒传 null，
 * 那个能力等于没接线。一个只在单测里成立的能力不是能力。</p>
 *
 * <p>所以这里刻意把 {@code AgentOrchestratorRoutingTest} 里的 mock 编排器换成
 * <b>真实编排器 + mock 注册表</b>，从 {@link SeaChatWorkflow#route} 的入口进，
 * 让「调用方声明端 → 编排器过滤 → 最终 agentKey」整条链路一起被断言。
 * 两个 Agent 用<b>同一个关键词</b>，唯一的区别是 audience ——
 * 这样任何一次断言失败都只可能来自端过滤，而不是关键词表变了。</p>
 *
 * <p>其中 {@code nullAudienceStillExposesDoctorOnlyAgent} 是一条<b>反向用例</b>：
 * 它锁的是「端传 null 时确实不过滤」。若有人把端过滤写成了「默认按用户端兜底」，
 * 这条会失败 —— 而那正是需要被看见的行为变更（它同时是改造前缺陷的成因说明）。</p>
 */
@DisplayName("多端解耦：端过滤贯穿 SeaChatWorkflow → AgentOrchestrator")
class SeaChatWorkflowAudienceTest {

    /** 只在医生端开放的 Agent。刻意排在 listAll 首位，让「不加过滤就会被选中」成立。 */
    private static final String DOCTOR_ONLY = "doctor_only";
    /** 只在用户端开放的 Agent。 */
    private static final String USER_ONLY = "user_helper";

    /** 两个 Agent 共用的关键词：命中它的只有端过滤这一条差别。 */
    private static final String SHARED_KEYWORD = "我的档案";

    private SeaChatWorkflow workflow;

    @BeforeEach
    void setUp() {
        AgentDefinition doctorOnly = def(DOCTOR_ONLY, "医生端专用", Audience.DOCTOR);
        AgentDefinition userOnly = def(USER_ONLY, "用户端专用", Audience.USER);
        AgentDefinition common = def("general_assistant", "全能助手", Audience.ALL);

        AgentRegistry registry = mock(AgentRegistry.class);
        List<AgentDefinition> all = new ArrayList<>(List.of(doctorOnly, userOnly, common));
        when(registry.listAll()).thenReturn(all);
        when(registry.get(any())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            for (AgentDefinition d : all) {
                if (d.getAgentKey().equals(key)) {
                    return d;
                }
            }
            return null;
        });
        when(registry.getDefault(any())).thenReturn(common);

        AgentOrchestrator orchestrator = new AgentOrchestrator();
        ReflectionTestUtils.setField(orchestrator, "agentRegistry", registry);

        HarnessEngine harness = mock(HarnessEngine.class);
        when(harness.extractSlots(any())).thenReturn(Map.of());

        workflow = new SeaChatWorkflow();
        ReflectionTestUtils.setField(workflow, "agentOrchestrator", orchestrator);
        ReflectionTestUtils.setField(workflow, "harnessEngine", harness);
    }

    @Test
    @DisplayName("用户端问不出医生端专属的 Agent（关键词完全一样也不行）")
    void userChannelCannotReachDoctorOnlyAgent() {
        ChatRouting routing = workflow.route(4, SHARED_KEYWORD + "在哪", Audience.USER);

        assertEquals(USER_ONLY, routing.getAgentType(),
                "两个 Agent 命中同一关键词，唯一差别是 audience；"
                        + "结果不是 user_helper 就说明端过滤没有在这条链路上生效");
    }

    @Test
    @DisplayName("医生端问出的正是医生端专属的那个 Agent")
    void doctorChannelReachesDoctorOnlyAgent() {
        ChatRouting routing = workflow.route(4, SHARED_KEYWORD + "在哪", Audience.DOCTOR);

        assertEquals(DOCTOR_ONLY, routing.getAgentType());
    }

    /**
     * 反向用例：锁住「null = 不过滤」这条兼容语义，而不是把它当成一个 bug 顺手「修掉」。
     *
     * <p>它同时是缺陷的现场还原 —— 改造前每一个用户端调用点都在传 null，
     * 于是这句话会被路由到医生端专属 Agent。看到这条断言等于看到旧行为，
     * 两条生产链路上的 {@code Audience.USER} 就是把它挡在外面的那道门。</p>
     */
    @Test
    @DisplayName("端为 null 时确实不过滤（还原改造前的行为，作为对照）")
    void nullAudienceStillExposesDoctorOnlyAgent() {
        ChatRouting routing = workflow.route(4, SHARED_KEYWORD + "在哪", null);

        assertEquals(DOCTOR_ONLY, routing.getAgentType(),
                "null 若也过滤，说明兼容语义被悄悄改了：改造前没有「端」的概念，"
                        + "传 null 的调用方期望的是全量可见");
    }

    @Test
    @DisplayName("无人命中关键词时按该端的兜底 Agent 落点，不返回空 key")
    void fallsBackToDefaultOfTheAudience() {
        assertEquals("general_assistant", workflow.route(4, "你好", Audience.USER).getAgentType());
        assertEquals("general_assistant", workflow.route(4, "你好", Audience.DOCTOR).getAgentType());
    }

    private static AgentDefinition def(String key, String name, String audience) {
        return AgentDefinition.builder()
                .agentKey(key)
                .name(name)
                .routingKeywords(List.of(SHARED_KEYWORD))
                .audience(audience)
                .maxIterations(5)
                .enabled(true)
                .handoffTargets(Set.of())
                .build();
    }
}
