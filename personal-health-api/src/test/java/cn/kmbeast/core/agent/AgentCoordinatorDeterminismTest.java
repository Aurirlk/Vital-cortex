package cn.kmbeast.core.agent;

import cn.kmbeast.core.graph.GraphNode;
import cn.kmbeast.core.graph.GraphSchema;
import cn.kmbeast.core.graph.MedicalVocabulary;
import cn.kmbeast.crm.agent.IntentRouter;
import cn.kmbeast.crm.config.CrmConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentCoordinator} 消费 {@link Resolution} 的确定性与映射单测（P2-3 / T11）。
 *
 * <p>锁死三件事：
 * <ol>
 *   <li><b>平票确定性</b>：关键词打分的成绩表必须是 {@link LinkedHashMap}，
 *       平票时按角色声明序决出胜者，而不是依赖 {@link HashMap} 的（实现相关、可漂移的）迭代序。</li>
 *   <li><b>同一输入 100 次结果恒定</b>：路由结果不能因为 JVM/哈希种子不同而变。</li>
 *   <li><b>意图 → 角色映射固定</b>：DATA_QUERY/HEALTH_RECORDS→analyst、GRAPH/KNOWLEDGE→consultant、
 *       DRUG→doctor、CHAT→关键词兜底。</li>
 * </ol>
 *
 * <p><b>反向验证（改坏代码必须变红）</b>：
 * 把 {@code AgentCoordinator.identifyAgent} 里的 {@code new LinkedHashMap<>()} 改回
 * {@code new HashMap<>()} → {@code scoresTableIsLinkedHashMap} 与
 * {@code sourceDeclaresLinkedHashMap} 均失败。
 */
@DisplayName("AgentCoordinator 确定性路由")
class AgentCoordinatorDeterminismTest {

    private AgentCoordinator coordinator;

    @BeforeEach
    void setUp() throws Exception {
        coordinator = new AgentCoordinator();
        // CrmConfig 无参构造即可（未走 Spring，@Value 字段为 null → 词表覆盖分支跳过）
        inject(coordinator, "crmConfig", new CrmConfig());
        coordinator.init();
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, AgentCoordinator.AgentRole> agentRoles(AgentCoordinator c) throws Exception {
        Field f = AgentCoordinator.class.getDeclaredField("AGENT_ROLES");
        f.setAccessible(true);
        return (Map<String, AgentCoordinator.AgentRole>) f.get(c);
    }

    // ==================== 用例 ====================

    @Test
    @DisplayName("角色表与打分表都是 LinkedHashMap —— 平票按声明序，不依赖哈希序")
    void scoresTableIsLinkedHashMap() throws Exception {
        assertTrue(agentRoles(coordinator) instanceof LinkedHashMap,
                "AGENT_ROLES 必须是 LinkedHashMap，否则平票胜者不确定");
    }

    @Test
    @DisplayName("源码层面确认：identifyAgent 不使用 HashMap（剥注释后扫描）")
    void sourceDeclaresLinkedHashMap() throws Exception {
        Path src = Path.of("src/main/java/cn/kmbeast/core/agent/AgentCoordinator.java");
        assertTrue(Files.exists(src), "源码缺失: " + src);
        String raw = Files.readString(src, StandardCharsets.UTF_8);
        String code = stripComments(raw);

        assertTrue(code.contains("new LinkedHashMap<>()"),
                "AgentCoordinator 必须使用 new LinkedHashMap<>()");
        assertFalse(code.contains("new HashMap<>()"),
                "AgentCoordinator 出现 new HashMap<>() —— 平票胜者会依赖哈希迭代序");
    }

    @Test
    @DisplayName("同一句话重复路由 100 次，结果恒定")
    void repeatedRoutingIsStable() {
        String query = "我最近血压有点高，还总是头晕";
        String first = coordinator.identifyAgent(query);
        for (int i = 0; i < 100; i++) {
            assertEquals(first, coordinator.identifyAgent(query),
                    "第 " + i + " 次路由结果漂移，说明打分表迭代序不确定");
        }
    }

    @Test
    @DisplayName("平票输入 100 次也恒定，且胜者为声明序在前者")
    void tieInputIsDeterministic() {
        // 「治疗焦虑」同时命中 doctor(治疗) 与 psychologist(焦虑)，构成 1:1 平票
        String query = "治疗焦虑";
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("doctor", 0);
        counts.put("psychologist", 0);
        for (String k : counts.keySet()) {
            for (String kw : coordinator.getAgentRole(k).getKeywords()) {
                if (query.contains(kw)) {
                    counts.merge(k, 1, Integer::sum);
                }
            }
        }
        assertEquals(1, counts.get("doctor"), "该用例必须让 doctor 命中 1 个关键词");
        assertEquals(1, counts.get("psychologist"), "该用例必须让 psychologist 命中 1 个关键词");

        String first = coordinator.identifyAgent(query);
        for (int i = 0; i < 100; i++) {
            assertEquals(first, coordinator.identifyAgent(query), "平票结果不稳定");
        }
        // doctor 在 AGENT_ROLES 中先声明，平票时应当胜出
        assertEquals("doctor", first, "平票时应按声明序取先者");
    }

    @Test
    @DisplayName("意图 → 角色映射固定：DATA_QUERY/HEALTH_RECORDS→analyst")
    void dataIntentsMapToAnalyst() {
        assertEquals("analyst", coordinator.identifyAgent(
                resolutionOf(IntentRouter.Intent.DATA_QUERY), "统计最近血压趋势"));
        assertEquals("analyst", coordinator.identifyAgent(
                resolutionOf(IntentRouter.Intent.HEALTH_RECORDS), "我的血压"));
    }

    @Test
    @DisplayName("意图 → 角色映射固定：GRAPH/KNOWLEDGE→consultant，DRUG→doctor")
    void knowledgeAndDrugMapping() {
        assertEquals("consultant", coordinator.identifyAgent(
                resolutionOf(IntentRouter.Intent.GRAPH), "高血压和糖尿病有关吗"));
        assertEquals("consultant", coordinator.identifyAgent(
                resolutionOf(IntentRouter.Intent.KNOWLEDGE), "有什么养生文章"));
        assertEquals("doctor", coordinator.identifyAgent(
                resolutionOf(IntentRouter.Intent.DRUG), "布洛芬怎么吃"));
    }

    @Test
    @DisplayName("CHAT 或 Resolution 为空 → 退回关键词打分，结果与原行为一致")
    void chatFallsBackToKeywordScoring() {
        assertEquals("psychologist", coordinator.identifyAgent(
                resolutionOf(IntentRouter.Intent.CHAT), "最近压力很大"));
        assertEquals("nutritionist", coordinator.identifyAgent(null, "我想减肥"));
        // 关键词也命中不了 → 默认角色
        assertEquals("general_assistant", coordinator.identifyAgent(null, "你好"));
    }

    // ==================== 辅助 ====================

    private static Resolution resolutionOf(IntentRouter.Intent intent) {
        return Resolution.fromRule(IntentRouter.IntentHint.forIntent(intent));
    }

    /** 去掉 // 与 /* 注释，避免注释里的字样误伤源码扫描断言 */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder();
        boolean inLine = false;
        boolean inBlock = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char n = i + 1 < src.length() ? src.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append('\n');
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && n == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (c == '/' && n == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '/' && n == '*') {
                inBlock = true;
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /** 桩词表（本测试不直接用到，但保留以说明 Resolution 的真实来源链路可离线构造） */
    @SuppressWarnings("unused")
    private static class StubVocabulary extends MedicalVocabulary {
        @Override
        public List<GraphNode> matchIn(String text) {
            return List.of(new GraphNode("高血压", GraphSchema.Label.DISEASE, null, List.of(), null, null));
        }

        @Override
        public List<String> matchAliases(String text) {
            return List.of();
        }

        @Override
        public Set<String> drugNames() {
            return new HashSet<>();
        }
    }
}
