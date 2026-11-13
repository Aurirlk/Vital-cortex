package cn.kmbeast.core.agent.orchestrator;

import cn.kmbeast.core.agent.AgentCoordinator;
import cn.kmbeast.core.agent.Resolution;
import cn.kmbeast.core.agent.registry.AgentDefinition;
import cn.kmbeast.core.agent.registry.AgentRegistry;
import cn.kmbeast.crm.agent.IntentRouter;
import cn.kmbeast.crm.config.CrmConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AgentOrchestrator} 路由决策的单测。
 *
 * <p><b>最重要的一条是 T1「与改造前逐条等价」</b>。这次重构把路由的角色定义源
 * 从硬编码 Map 换成了 DB 驱动的注册表，算法本身一行没改 —— 但「没改」是意图，
 * 不是保证。等价性是<b>可以测出来</b>的：把注册表按内置 6 角色的原样填满，
 * 同一批输入喂给新旧两条路径，结果必须逐一相同。少了这条，
 * 「重构后某些问句被路由到了别的角色」这种回归只能靠用户投诉发现。</p>
 *
 * <p>其余用例锁的是改造<b>新增</b>的语义：管理员停用某个 Agent 后，
 * 指向它的意图不能返回一个不存在的 key；管理员的「试跑」必须能说出
 * 这轮凭什么路由到它（只回一个 key 的话，调关键词就是盲调）。</p>
 */
@DisplayName("AgentOrchestrator 路由决策")
class AgentOrchestratorRoutingTest {

    private AgentOrchestrator orchestrator;
    private AgentRegistry registry;
    private AgentCoordinator coordinator;

    /** 把内置 6 角色灌进一个 mock 注册表，模拟「表未建」时的兜底快照。 */
    @BeforeEach
    void setUp() {
        coordinator = new AgentCoordinator();
        ReflectionTestUtils.setField(coordinator, "crmConfig", new CrmConfig());
        coordinator.init();

        registry = mock(AgentRegistry.class);
        Map<String, AgentDefinition> defs = new LinkedHashMap<>();
        for (Map.Entry<String, AgentCoordinator.AgentRole> e : coordinator.getAllAgentRoles().entrySet()) {
            AgentCoordinator.AgentRole role = e.getValue();
            defs.put(e.getKey(), AgentDefinition.builder()
                    .agentKey(e.getKey())
                    .name(role.getName())
                    .description(role.getDescription())
                    .routingKeywords(role.getKeywords())
                    .audience("all")
                    .maxIterations(5)
                    .enabled(true)
                    .isDefault(AgentRegistry.FALLBACK_KEY.equals(e.getKey()))
                    .handoffTargets(java.util.Set.of())
                    .build());
        }
        when(registry.listAll()).thenReturn(new ArrayList<>(defs.values()));
        when(registry.get(any())).thenAnswer(inv -> defs.get(inv.getArgument(0)));
        when(registry.getDefault(any())).thenReturn(defs.get(AgentRegistry.FALLBACK_KEY));

        orchestrator = new AgentOrchestrator();
        ReflectionTestUtils.setField(orchestrator, "agentRegistry", registry);
        ReflectionTestUtils.setField(orchestrator, "agentCoordinator", coordinator);
    }

    // ==================== T1：等价性 ====================

    @Test
    @DisplayName("T1 与改造前的关键词路由逐条等价（6 角色原样灌入注册表）")
    void routingIsEquivalentToLegacyScoring() {
        List<String> queries = List.of(
                "我最近血压有点高",                 // analyst: 血压
                "体检报告怎么看",                   // analyst: 报告 + 体检 = 2
                "吃什么能减肥",                     // nutritionist: 减肥
                "最近压力很大总失眠",               // psychologist: 压力 + 失眠 = 2
                "有什么养生运动推荐",               // consultant: 养生 + 运动 = 2
                "感冒了吃什么药",                   // doctor: 感冒（「药」不在任何词表里）
                "我头痛还发烧咳嗽",                 // doctor: 头痛 + 发烧 + 咳嗽 = 3
                "你好",                             // 无人命中 → 兜底
                "12345",                            // 无人命中 → 兜底（含数字，不应崩）
                "心理焦虑，同时血糖也异常");        // psychologist 与 analyst 各 2 分 → 平票

        for (String q : queries) {
            String legacy = coordinator.identifyAgent(q);
            String now = orchestrator.route(null, q, null);
            assertEquals(legacy, now,
                    "重构后路由结果变了（输入: " + q + "）—— 角色定义换源不该改变任何一句的归属");
        }
    }

    @Test
    @DisplayName("T2 空/空白输入走兜底，不退化成异常")
    void blankQueryGoesToDefault() {
        assertEquals("general_assistant", orchestrator.route(null, null, null));
        assertEquals("general_assistant", orchestrator.route(null, "   ", null));
    }

    // ==================== T3：意图优先 ====================

    @Test
    @DisplayName("T3 意图明确时不被关键词带偏（问药品但含「饮食」二字仍走全科医生）")
    void intentBeatsKeywordScoring() {
        // “饮食”是 nutritionist 的关键词，但意图识别判定为 DRUG → 应以意图为准
        Resolution drug = Resolution.fromRule(IntentRouter.IntentHint.forIntent(IntentRouter.Intent.DRUG));
        String q = "这个药有什么饮食禁忌";

        assertEquals("doctor", orchestrator.route(drug, q, null),
                "意图优先是这次改造的核心收益之一：关键词打分只该在闲聊时兜底");
        // 同一句话不带意图时，关键词确实会判给 nutritionist —— 说明上一条断言不是恒真
        assertEquals("nutritionist", orchestrator.route(null, q, null));
    }

    @Test
    @DisplayName("T4 意图指向的 Agent 被停用/删除时退回关键词，绝不返回不存在的 key")
    void intentTargetingUnavailableAgentFallsBack() {
        // 把 analyst 从注册表摘掉，模拟管理员删了它
        Map<String, AgentDefinition> only = new LinkedHashMap<>();
        only.put("nutritionist", AgentDefinition.builder()
                .agentKey("nutritionist").name("营养师")
                .routingKeywords(List.of("饮食")).audience("all")
                .maxIterations(5).enabled(true).handoffTargets(java.util.Set.of()).build());
        when(registry.listAll()).thenReturn(new ArrayList<>(only.values()));
        when(registry.get(any())).thenAnswer(inv -> only.get(inv.getArgument(0)));
        when(registry.getDefault(any())).thenReturn(AgentDefinition.builder()
                .agentKey("general_assistant").name("全能助手").audience("all")
                .maxIterations(5).enabled(true).handoffTargets(java.util.Set.of()).build());

        // 意图说 DATA_QUERY → analyst，但 analyst 已不存在
        Resolution dataQuery =
                Resolution.fromRule(IntentRouter.IntentHint.forIntent(IntentRouter.Intent.DATA_QUERY));
        String routed = orchestrator.route(dataQuery, "最近饮食怎么样", null);

        assertEquals("nutritionist", routed,
                "意图指向的 Agent 不存在时必须退回关键词路由，否则调用方拿到一个查不到的 key");
        assertNotNull(orchestrator.agentName(routed));
    }

    // ==================== T5：试跑可解释 ====================

    @Test
    @DisplayName("T5 decide 回传依据与命中数（管理员调关键词的唯一反馈）")
    void decideExposesReason() {
        AgentOrchestrator.RouteDecision intent = orchestrator.decide(
                Resolution.fromRule(IntentRouter.IntentHint.forIntent(IntentRouter.Intent.GRAPH)),
                "阿司匹林有什么禁忌", null);
        assertEquals("consultant", intent.getAgentKey());
        assertTrue(intent.getReason().startsWith("intent:"), "意图路由的 reason 应标明来源: " + intent.getReason());

        AgentOrchestrator.RouteDecision keyword = orchestrator.decide(null, "我头痛发烧", null);
        assertEquals("doctor", keyword.getAgentKey());
        assertEquals("keyword", keyword.getReason());
        assertEquals(2, keyword.getScore(), "「头痛」「发烧」两个关键词命中，分数应如实反映");

        AgentOrchestrator.RouteDecision fallback = orchestrator.decide(null, "你好", null);
        assertEquals("default", fallback.getReason());
        assertEquals(0, fallback.getScore());
    }

    // ==================== T6：按端可见性 ====================

    @Test
    @DisplayName("T6 带端过滤时看不到别的端的 Agent；不传端则不过滤（与改造前一致）")
    void audienceFiltering() {
        AgentDefinition userOnly = AgentDefinition.builder()
                .agentKey("user_helper").name("用户助手")
                .routingKeywords(List.of("我的档案")).audience("user")
                .maxIterations(5).enabled(true).handoffTargets(java.util.Set.of()).build();
        AgentDefinition adminOnly = AgentDefinition.builder()
                .agentKey("ops_bot").name("运维助手")
                .routingKeywords(List.of("我的档案")).audience("admin")
                .maxIterations(5).enabled(true).handoffTargets(java.util.Set.of()).build();
        when(registry.listAll()).thenReturn(List.of(userOnly, adminOnly));

        assertEquals("user_helper", orchestrator.route(null, "我的档案在哪", "user"));
        assertEquals("ops_bot", orchestrator.route(null, "我的档案在哪", "admin"));

        // audience=null 不过滤：这是刻意保留的向后兼容，改造前没有「端」的概念
        String unfiltered = orchestrator.route(null, "我的档案在哪", null);
        assertEquals("user_helper", unfiltered, "不传端时按 listAll 的顺序取首个命中者");
    }

    // ==================== T7：agentName 的诚实性 ====================

    @Test
    @DisplayName("T7 agentName：注册表优先；都不认识时原样返回 key，绝不张冠李戴")
    void agentNameIsHonest() {
        // 内置角色：注册表有 → 用注册表的名字
        assertEquals("全科医生", orchestrator.agentName("doctor"));

        // 管理员新建的 Agent：注册表认识、内置角色表不认识 → 必须用注册表的名字。
        // 这正是继续用 AgentCoordinator.getAgentRole(key).getName() 会出错的地方：
        // 它查不到时返回「全能助手」，于是「睡眠顾问」会显示成「全能助手」。
        AgentDefinition sleepCoach = AgentDefinition.builder()
                .agentKey("sleep_coach").name("睡眠顾问").audience("all")
                .maxIterations(5).enabled(true).handoffTargets(java.util.Set.of()).build();
        when(registry.get("sleep_coach")).thenReturn(sleepCoach);
        assertEquals("睡眠顾问", orchestrator.agentName("sleep_coach"));

        // 两边都不认识：返回 key 本身，而不是随便借一个中文名
        when(registry.get("ghost")).thenReturn(null);
        assertEquals("ghost", orchestrator.agentName("ghost"),
                "陌生的 key 至少指向真实的那个 Agent，比一个张冠李戴的中文名有用");
        assertNull(orchestrator.agentName(null));
    }

    @Test
    @DisplayName("T8 只注册表认识时也能拿到名字（不给注册表灌内置角色的极端情况）")
    void agentNameWorksWithRegistryOnly() {
        AgentDefinition def = AgentDefinition.builder()
                .agentKey("doctor").name("值班医生").audience("all")
                .maxIterations(5).enabled(true).handoffTargets(java.util.Set.of()).build();
        when(registry.get("doctor")).thenReturn(def);
        assertEquals("值班医生", orchestrator.agentName("doctor"),
                "注册表的定义优先于内置角色表（管理员改的名字必须生效）");
    }
}
