package cn.kmbeast.crm.agent;

import cn.kmbeast.core.graph.GraphNode;
import cn.kmbeast.core.graph.GraphSchema;
import cn.kmbeast.core.graph.MedicalVocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IntentRouter} 五类意图识别与离线兜底参数的单元测试。
 *
 * <p>用最小桩词表（只认「高血压/糖尿病/布洛芬」）替代真实 {@link MedicalVocabulary}，
 * 既避免依赖 classpath 资源，又能稳定覆盖 GRAPH 高置信度（需实体）分支。
 */
@DisplayName("IntentRouter 意图软路由分类")
class IntentRouterTest {

    /** 最小桩词表：matchIn 命中 高血压/糖尿病，drugNames 含 布洛芬 */
    private static class StubVocabulary extends MedicalVocabulary {
        @Override
        public List<GraphNode> matchIn(String text) {
            if (text == null) {
                return List.of();
            }
            if (text.contains("高血压")) {
                return List.of(new GraphNode("高血压", GraphSchema.Label.DISEASE, null, List.of(), null, null));
            }
            if (text.contains("糖尿病")) {
                return List.of(new GraphNode("糖尿病", GraphSchema.Label.DISEASE, null, List.of(), null, null));
            }
            return List.of();
        }

        @Override
        public List<String> matchAliases(String text) {
            return List.of();
        }

        @Override
        public Set<String> drugNames() {
            return Set.of("布洛芬");
        }
    }

    private IntentRouter routerWithVocab() {
        IntentRouter router = new IntentRouter();
        try {
            Field f = IntentRouter.class.getDeclaredField("vocabulary");
            f.setAccessible(true);
            f.set(router, new StubVocabulary());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return router;
    }

    @Test
    @DisplayName("指标口语（我的血压）→ HEALTH_RECORDS / HIGH / 带离线参数")
    void metricIntent() {
        IntentRouter.IntentHint hint = routerWithVocab().route("我最近血压有点高");
        assertEquals(IntentRouter.Intent.HEALTH_RECORDS, hint.getIntent());
        assertEquals(IntentRouter.Confidence.HIGH, hint.getConfidence());
        assertEquals("query_health_records", hint.getTargetTool());
        assertNotNull(hint.getSuggestedArgs());
        assertEquals("血压", hint.getSuggestedArgs().get("metric"));
        assertTrue(hint.isHighConfidenceDataIntent());
    }

    @Test
    @DisplayName("关系型+实体（糖尿病有什么症状）→ GRAPH / HIGH / 带 entity 离线参数")
    void graphIntentHigh() {
        IntentRouter.IntentHint hint = routerWithVocab().route("糖尿病有什么症状");
        assertEquals(IntentRouter.Intent.GRAPH, hint.getIntent());
        assertEquals(IntentRouter.Confidence.HIGH, hint.getConfidence());
        assertEquals("query_knowledge_graph", hint.getTargetTool());
        assertEquals("糖尿病", hint.getSuggestedArgs().get("entity"));
        assertTrue(hint.isHighConfidenceDataIntent());
    }

    @Test
    @DisplayName("药品关键词（布洛芬胶囊）→ DRUG / HIGH / 无离线落点")
    void drugIntent() {
        IntentRouter.IntentHint hint = routerWithVocab().route("布洛芬胶囊怎么吃");
        assertEquals(IntentRouter.Intent.DRUG, hint.getIntent());
        assertEquals(IntentRouter.Confidence.HIGH, hint.getConfidence());
        // 药品意图不提供离线工具落点
        assertEquals(null, hint.getTargetTool());
        assertFalse(hint.isHighConfidenceDataIntent());
    }

    @Test
    @DisplayName("科普关键词（养生）→ KNOWLEDGE / MEDIUM")
    void knowledgeIntent() {
        IntentRouter.IntentHint hint = routerWithVocab().route("有什么健康养生方法");
        assertEquals(IntentRouter.Intent.KNOWLEDGE, hint.getIntent());
        assertEquals(IntentRouter.Confidence.MEDIUM, hint.getConfidence());
    }

    @Test
    @DisplayName("纯闲聊（你好）→ CHAT / LOW")
    void chatIntent() {
        IntentRouter.IntentHint hint = routerWithVocab().route("你好");
        assertEquals(IntentRouter.Intent.CHAT, hint.getIntent());
        assertEquals(IntentRouter.Confidence.LOW, hint.getConfidence());
    }

    @Test
    @DisplayName("空输入安全降级为 CHAT")
    void emptyInputSafe() {
        IntentRouter.IntentHint hint = routerWithVocab().route("   ");
        assertEquals(IntentRouter.Intent.CHAT, hint.getIntent());
        assertEquals(IntentRouter.Confidence.LOW, hint.getConfidence());
    }
}
