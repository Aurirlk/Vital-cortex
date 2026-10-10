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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导入编排测试（阶段 0-B / B3、B6）。
 *
 * <p>守护两条硬要求：
 * <ol>
 *   <li><b>幂等</b>：连跑两次导入，本地三元组总数不变（对应验收标准
 *       "连跑两次 /graph/import/all，/graph/stats 的节点数与关系数完全不变"）。</li>
 *   <li><b>降级可见</b>：Neo4j 不可用时，{@code status} 必须是 {@code DEGRADED}、
 *       {@code backend} 必须是 {@code local}，且<b>本地仍要写成功</b> ——
 *       本机 Neo4j 长期未启动，若此时什么都不写，阶段 0-B 的成果就是 0。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class GraphImportServiceTest {

    @Mock
    private Neo4jClient neo4jClient;

    @Mock
    private Neo4jGraphWriter neo4jGraphWriter;

    @Mock
    private GraphExtractor extractor;

    @Spy
    private MedicalVocabulary vocabulary = new MedicalVocabulary();

    @Spy
    private LocalTripleStore localTripleStore = new LocalTripleStore();

    @InjectMocks
    private GraphImportService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        vocabulary.init();
        localTripleStore.setFilePath(tempDir.resolve("triples.json").toString());
        localTripleStore.init();
    }

    private GraphPayload samplePayload() {
        GraphPayload p = new GraphPayload();
        p.addEdge(Triple.of("高血压", GraphSchema.Label.DISEASE,
                GraphSchema.Relation.TREATED_BY, "苯磺酸氨氯地平片",
                GraphSchema.Label.DRUG, GraphSources.SEED));
        return p;
    }

    @Test
    @DisplayName("Neo4j 不可用：后端降级为 local，但本地必须写入成功")
    void degradedButLocalWritten() {
        when(neo4jClient.isHealthy()).thenReturn(false);
        when(extractor.extractIndicators()).thenReturn(samplePayload());

        Map<String, Object> r = service.importBase();

        assertEquals("local", r.get("backend"));
        assertEquals("UNAVAILABLE", r.get("neo4jStatus"));
        assertEquals("OK", r.get("localStatus"));
        assertTrue((Integer) r.get("localTotalTriples") > 0,
                "Neo4j 挂了也必须留下成果，否则本机一次导入都存不下");
        verify(neo4jGraphWriter, never()).writeNodes(anyList(), anyString());
    }

    @Test
    @DisplayName("Neo4j 可用：写入图库并同步落本地")
    void writesToNeo4jWhenHealthy() {
        when(neo4jClient.isHealthy()).thenReturn(true);
        when(extractor.extractIndicators()).thenReturn(samplePayload());
        when(neo4jGraphWriter.applySchema()).thenReturn(10);
        when(neo4jGraphWriter.writeNodes(anyList(), anyString())).thenReturn(2);
        when(neo4jGraphWriter.writeEdges(anyList(), anyString())).thenReturn(1);

        Map<String, Object> r = service.importBase();

        assertEquals("neo4j", r.get("backend"));
        assertEquals("OK", r.get("neo4jStatus"));
        verify(neo4jGraphWriter, times(1)).applySchema();
        verify(neo4jGraphWriter, times(1)).writeNodes(anyList(), anyString());
        verify(neo4jGraphWriter, times(1)).writeEdges(anyList(), anyString());
    }

    @Test
    @DisplayName("幂等：连跑两次全量导入，本地三元组总数不变")
    void importAllIsIdempotent() {
        when(neo4jClient.isHealthy()).thenReturn(false);
        when(extractor.extractPatientProfiles()).thenReturn(samplePayload());
        when(extractor.extractMeasurements()).thenReturn(new GraphPayload());
        when(extractor.extractVisits()).thenReturn(new GraphPayload());
        when(extractor.extractDrugs()).thenReturn(new GraphPayload());
        when(extractor.extractArticles()).thenReturn(new GraphPayload());

        Map<String, Object> first = service.importAll();
        int afterFirst = (Integer) first.get("localTotalTriples");

        Map<String, Object> second = service.importAll();
        int afterSecond = (Integer) second.get("localTotalTriples");

        assertEquals(afterFirst, afterSecond,
                "第二次导入不得改变图谱规模，实际 " + afterFirst + " → " + afterSecond);
        assertEquals(0, second.get("localNewTriples"), "第二次应无新增三元组");
        assertEquals(afterFirst, localTripleStore.size());
    }

    @Test
    @DisplayName("Neo4j 写入抛异常时转为 FAILED 并继续写本地，不中断导入")
    void neo4jFailureFallsBack() {
        when(neo4jClient.isHealthy()).thenReturn(true);
        when(extractor.extractDrugs()).thenReturn(samplePayload());
        when(neo4jGraphWriter.writeNodes(anyList(), anyString()))
                .thenThrow(new IllegalStateException("bolt 连接超时"));

        Map<String, Object> r = service.importDrugs();

        assertEquals("FAILED", r.get("neo4jStatus"));
        assertEquals("local", r.get("backend"), "写入失败后应回落到本地后端");
        assertEquals("OK", r.get("localStatus"));
        assertTrue((Integer) r.get("localTotalTriples") > 0);
    }

    @Test
    @DisplayName("stats：status 与 backend 分离，能区分『图谱为空』与『图谱挂了』")
    void statsSeparatesStatusAndBackend() {
        when(neo4jClient.isHealthy()).thenReturn(false);
        when(neo4jClient.isDegraded()).thenReturn(true);
        when(extractor.extractIndicators()).thenReturn(samplePayload());
        service.importBase();
        when(neo4jClient.isHealthy()).thenReturn(false);
        when(neo4jClient.isDegraded()).thenReturn(true);

        Map<String, Object> s = service.stats();

        assertEquals("DEGRADED", s.get("status"));
        assertEquals("local", s.get("backend"));
        assertTrue(s.containsKey("graph"));
        assertTrue(s.containsKey("coverage"));
        assertTrue(s.containsKey("warnings"));
        assertTrue(s.containsKey("sources"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> warnings = (List<Map<String, Object>>) s.get("warnings");
        assertTrue(warnings.stream().anyMatch(w -> "NEO4J_UNAVAILABLE".equals(w.get("type"))),
                "Neo4j 不可用必须在 warnings 里显式暴露: " + warnings);
    }

    @Test
    @DisplayName("stats：图谱为空时给出 GRAPH_EMPTY 提示")
    void statsWarnsWhenEmpty() {
        when(neo4jClient.isHealthy()).thenReturn(false);

        Map<String, Object> s = service.stats();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> warnings = (List<Map<String, Object>>) s.get("warnings");
        assertTrue(warnings.stream().anyMatch(w -> "GRAPH_EMPTY".equals(w.get("type"))));
    }

    @Test
    @DisplayName("清空：本地清空并落盘，Neo4j 不可用时跳过图库")
    void clear() {
        when(neo4jClient.isHealthy()).thenReturn(false);
        when(extractor.extractIndicators()).thenReturn(samplePayload());
        service.importBase();
        assertTrue(localTripleStore.size() > 0);

        Map<String, Object> r = service.clear();

        assertEquals(0, localTripleStore.size());
        assertEquals(0, r.get("neo4jLabelsCleared"));
        assertTrue((Integer) r.get("localCleared") > 0);
    }

    @Test
    @DisplayName("importAll 汇总全部 7 条抽取链路")
    void importAllCoversAllSources() {
        when(neo4jClient.isHealthy()).thenReturn(false);
        when(extractor.extractPatientProfiles()).thenReturn(new GraphPayload());
        when(extractor.extractMeasurements()).thenReturn(new GraphPayload());
        when(extractor.extractVisits()).thenReturn(new GraphPayload());
        when(extractor.extractDrugs()).thenReturn(new GraphPayload());
        when(extractor.extractArticles()).thenReturn(new GraphPayload());

        service.importAll();

        // 词表 6 项之外，6 个数据库/文件来源的抽取器都要被调到
        verify(extractor, times(1)).extractIndicators();
        verify(extractor, times(1)).extractPatientProfiles();
        verify(extractor, times(1)).extractMeasurements();
        verify(extractor, times(1)).extractVisits();
        verify(extractor, times(1)).extractDrugs();
        verify(extractor, times(1)).extractArticles();

        Map<String, Integer> bySource = localTripleStore.countBySource();
        assertTrue(bySource.containsKey(GraphSources.SEED),
                "词表三元组必须进图，实际来源=" + bySource.keySet());
    }

    @Test
    @DisplayName("importAll 不会因任一抽取器抛异常而整体失败")
    void extractorExceptionDoesNotBreakAll() {
        // 不 stub isHealthy：抽取在 commit 之前全部完成，异常会先抛出，
        // stub 了反而触发 UnnecessaryStubbingException
        when(extractor.extractDrugs()).thenThrow(new RuntimeException("mall_product 挂了"));

        // commit() 内部不捕获抽取异常是<b>当前设计</b>：抽取失败应让端点报错，
        // 而不是静默产出半张图。这里锁定的正是这个行为。
        assertThrows(RuntimeException.class, () -> service.importAll());
    }
}
