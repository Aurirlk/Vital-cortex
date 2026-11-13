package cn.kmbeast.core.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图谱 Schema 与种子词表的一致性测试（阶段 0-B / B1、B4）。
 *
 * <p>核心守护点：
 * <ol>
 *   <li>约束 DDL 必须全部 {@code IF NOT EXISTS} —— 否则第二次导入直接失败，
 *       "幂等"这条验收标准不成立。</li>
 *   <li>种子词表必须覆盖 {@link GraphRAG#fallbackTerms()} 全部 10 个词 ——
 *       否则图谱建好了，实体抽取却仍回退到硬编码词表，等于白建。</li>
 *   <li>种子文件的边不能指向未声明的节点 —— 否则 Neo4j 会静默造出裸节点。</li>
 * </ol>
 */
class GraphSchemaTest {

    @Test
    @DisplayName("约束 DDL 全部幂等（IF NOT EXISTS）")
    void constraintsAreIdempotent() {
        List<String> stmts = GraphSchema.constraintStatements();
        assertFalse(stmts.isEmpty());
        for (String s : stmts) {
            assertTrue(s.contains("IF NOT EXISTS"),
                    "约束必须幂等，否则第二次导入会失败: " + s);
            assertTrue(s.startsWith("CREATE CONSTRAINT"), s);
        }
    }

    @Test
    @DisplayName("约束名唯一且可回退（DROP 用）")
    void constraintNamesUnique() {
        List<String> names = new java.util.ArrayList<>();
        for (GraphSchema.Label l : GraphSchema.Label.values()) {
            names.add(GraphSchema.constraintName(l));
        }
        assertEquals(names.size(), new java.util.HashSet<>(names).size());
    }

    @Test
    @DisplayName("标签与关系类型的 code 非空且互不重复")
    void codesAreUnique() {
        List<String> labels = GraphSchema.allLabelCodes();
        List<String> rels = GraphSchema.allRelationCodes();
        assertEquals(labels.size(), new java.util.HashSet<>(labels).size());
        assertEquals(rels.size(), new java.util.HashSet<>(rels).size());
        labels.forEach(c -> assertFalse(c.trim().isEmpty()));
        rels.forEach(c -> assertFalse(c.trim().isEmpty()));
    }

    @Test
    @DisplayName("词表覆盖 GraphRAG 兜底的全部 10 个医学词")
    void vocabularyCoversFallbackTerms() {
        MedicalVocabulary vocab = new MedicalVocabulary();
        vocab.init();
        assertTrue(vocab.size() > 0, "词表未加载到任何条目");
        assertTrue(vocab.coversFallbackTerms(GraphRAG.fallbackTerms()),
                "词表必须覆盖兜底词，否则 GraphRAG 永远走不到真实图谱实体");
    }

    @Test
    @DisplayName("词表长词优先匹配，不被短词抢先（2型糖尿病 而非 糖尿病）")
    void longTermFirst() {
        MedicalVocabulary vocab = new MedicalVocabulary();
        vocab.init();
        List<GraphNode> hits = vocab.matchIn("我被确诊为2型糖尿病");
        assertTrue(hits.stream().anyMatch(n -> "2型糖尿病".equals(n.getName())),
                "命中项应包含细分类型: " + hits);
        assertFalse(hits.stream().anyMatch(n -> "糖尿病".equals(n.getName())),
                "已被 2型糖尿病 覆盖的区间不应再计入泛化的 糖尿病: " + hits);
    }

    @Test
    @DisplayName("别名归一：血压高 → 高血压")
    void canonicalAlias() {
        MedicalVocabulary vocab = new MedicalVocabulary();
        vocab.init();
        assertEquals("高血压", vocab.canonical("血压高"));
        assertEquals("高血压", vocab.canonical("高血压"));
        assertEquals("感冒", vocab.canonical("上呼吸道感染"));
        // 未收录的词原样返回，绝不猜测
        assertEquals("某个未收录的病", vocab.canonical("某个未收录的病"));
    }

    @Test
    @DisplayName("种子词表：边两端节点均已声明（无悬空端点）")
    void seedHasNoDanglingEndpoints() {
        MedicalVocabulary vocab = new MedicalVocabulary();
        vocab.init();
        GraphPayload p = vocab.payload();
        List<String> nodeKeys = new java.util.ArrayList<>();
        for (GraphNode n : p.getNodes()) {
            nodeKeys.add(n.key());
        }
        for (Triple t : p.getEdges()) {
            assertTrue(nodeKeys.contains(t.getSubjectLabel().code() + "|" + t.getSubject()),
                    "边的主节点未声明: " + t);
            assertTrue(nodeKeys.contains(t.getObjectLabel().code() + "|" + t.getObject()),
                    "边的客节点未声明: " + t);
        }
        assertFalse(p.getNodes().isEmpty());
        assertFalse(p.getEdges().isEmpty());
    }

    @Test
    @DisplayName("种子词表规模达到可用下限（节点≥100，边≥150）")
    void seedScale() {
        MedicalVocabulary vocab = new MedicalVocabulary();
        vocab.init();
        GraphPayload p = vocab.payload();
        assertTrue(p.nodeCount() >= 100, "词表节点过少: " + p.nodeCount());
        assertTrue(p.edgeCount() >= 150, "词表关系过少: " + p.edgeCount());
    }
}
