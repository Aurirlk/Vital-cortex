package cn.kmbeast.core.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * GraphRAG 端到端链路测试（阶段 0-B 的"最后一公里"）。
 *
 * <p>覆盖的是真实用户价值，而不是单个类的行为：
 * <pre>
 *   真实词表 seed-triples.json（119 节点 / 171 边）
 *     → LocalTripleStore（Neo4j 不可用时的兜底图）
 *     → GraphRAG.extractEntities / queryRelatedKnowledge
 * </pre>
 *
 * <p>🔴 这条链路此前<b>从未生效过</b>：Neo4j 长期未启动，
 * {@code GraphRAG} 一路返回空串，双路召回实际只有向量一路在工作。
 * 本测试锁定"兜底图能真正被召回"这件事。
 */
@ExtendWith(MockitoExtension.class)
class GraphRagFallbackTest {

    @Mock
    private Neo4jClient neo4jClient;

    @Spy
    private LocalTripleStore localTripleStore = new LocalTripleStore();

    @Spy
    private MedicalVocabulary vocabulary = new MedicalVocabulary();

    @InjectMocks
    private GraphRAG graphRAG;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        vocabulary.init();
        localTripleStore.setFilePath(tempDir.resolve("triples.json").toString());
        localTripleStore.init();

        // 用真实词表灌本地图
        GraphPayload payload = vocabulary.payload();
        localTripleStore.upsert(payload.getEdges());
        localTripleStore.save();
    }

    @Test
    @DisplayName("Neo4j 不可用时，实体抽取走本地图而非只剩硬编码兜底词")
    void extractEntitiesFromLocalGraph() {
        when(neo4jClient.isConnected()).thenReturn(false);

        // "血压高" 是词表里"高血压"的别名 —— 命中它即证明别名归并在生效
        List<String> entities = graphRAG.extractEntities("我最近血压高，还查出骨质疏松");

        assertTrue(entities.contains("高血压"), "应命中图谱里的真实疾病节点: " + entities);
        assertTrue(entities.contains("骨质疏松"), "应命中图谱里的真实疾病节点: " + entities);
        // 证明不是只靠 FALLBACK_TERMS：骨质疏松不在兜底词表里
        assertFalse(GraphRAG.fallbackTerms().contains("骨质疏松"),
                "骨质疏松不在兜底词表，命中它即证明读到了真实图谱");
    }

    @Test
    @DisplayName("别名归并：口语『血压高』必须归一到图谱规范名『高血压』")
    void aliasNormalization() {
        when(neo4jClient.isConnected()).thenReturn(false);

        List<String> entities = graphRAG.extractEntities("血压高怎么办");

        assertTrue(entities.contains("高血压"),
                "图谱只存规范名，不做别名归并则该节点永远命中不到: " + entities);
    }

    @Test
    @DisplayName("关系召回：问『高血压』能拿到治疗药物")
    void queryRelatedKnowledgeFromLocalGraph() {
        when(neo4jClient.isConnected()).thenReturn(false);

        String knowledge = graphRAG.queryRelatedKnowledge("高血压");

        assertFalse(knowledge.isEmpty(), "本地图必须能召回关系，此前这里恒为空串");
        assertTrue(knowledge.contains("苯磺酸氨氯地平片"), "实际召回:\n" + knowledge);
        assertTrue(knowledge.contains("TREATED_BY"), "应带关系类型: \n" + knowledge);
    }

    @Test
    @DisplayName("反向召回：药作为客体时（『氨氯地平治什么』）也能命中")
    void reverseRecall() {
        when(neo4jClient.isConnected()).thenReturn(false);

        String knowledge = graphRAG.queryRelatedKnowledge("苯磺酸氨氯地平片");

        assertTrue(knowledge.contains("高血压"),
                "药是 TREATED_BY 的客体，必须能反向召回疾病: \n" + knowledge);
    }

    @Test
    @DisplayName("Neo4j 可用时优先查图库，本地不干扰")
    void prefersNeo4jWhenConnected() {
        when(neo4jClient.isConnected()).thenReturn(true);
        when(neo4jClient.query(org.mockito.ArgumentMatchers.contains("MATCH (n) RETURN"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(java.util.Map.of("name", "高血压")));

        List<String> entities = graphRAG.extractEntities("我有高血压");

        assertTrue(entities.contains("高血压"));
    }
}
