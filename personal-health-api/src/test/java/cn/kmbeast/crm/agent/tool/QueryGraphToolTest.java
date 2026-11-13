package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.core.graph.LocalTripleStore;
import cn.kmbeast.core.graph.MedicalVocabulary;
import cn.kmbeast.core.graph.Neo4jClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 阶段 1：图谱检索工具的单元测试（参数钳制 + 实体解析）。
 */
@DisplayName("QueryGraphTool 图谱检索")
class QueryGraphToolTest {

    private QueryGraphTool tool;
    private Neo4jClient neo4jClient;
    private LocalTripleStore localTripleStore;

    @BeforeEach
    void setUp() throws Exception {
        tool = new QueryGraphTool();
        neo4jClient = mock(Neo4jClient.class);
        localTripleStore = mock(LocalTripleStore.class);
        setField(tool, "neo4jClient", neo4jClient);
        setField(tool, "localTripleStore", localTripleStore);
        // 默认 Neo4j 不可用，走本地图
        when(neo4jClient.isHealthy()).thenReturn(false);
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    @Test
    @DisplayName("limit 硬钳 50")
    void clampLimit() {
        assertEquals(50, QueryGraphTool.clampLimit(9999));
        assertEquals(50, QueryGraphTool.clampLimit(100));
        assertEquals(20, QueryGraphTool.clampLimit(null));
        assertEquals(20, QueryGraphTool.clampLimit(0));
        assertEquals(25, QueryGraphTool.clampLimit(25));
    }

    @Test
    @DisplayName("depth 钳制到 [1,2]")
    void clampDepth() {
        assertEquals(2, QueryGraphTool.clampDepth(3));
        assertEquals(1, QueryGraphTool.clampDepth(0));
        assertEquals(1, QueryGraphTool.clampDepth(1));
        assertEquals(2, QueryGraphTool.clampDepth(2));
    }

    @Test
    @DisplayName("关系类型归一化：未知返回 null")
    void normalizeRelation() {
        assertEquals("HAS_SYMPTOM", QueryGraphTool.normalizeRelation("HAS_SYMPTOM"));
        assertEquals("HAS_SYMPTOM", QueryGraphTool.normalizeRelation("has_symptom"));
        assertNull(QueryGraphTool.normalizeRelation("NOT_A_RELATION"));
        assertNull(QueryGraphTool.normalizeRelation(null));
    }

    @Test
    @DisplayName("实体解析：精确命中")
    void resolveExact() {
        java.util.List<String> names = new java.util.ArrayList<>();
        names.add("高血压");
        names.add("糖尿病");
        when(localTripleStore.allNames()).thenReturn(names);
        assertEquals("高血压", tool.resolveEntity("高血压"));
    }

    @Test
    @DisplayName("实体解析：别名归并（血压高 → 高血压）")
    void resolveAlias() throws Exception {
        java.util.List<String> names = new java.util.ArrayList<>();
        names.add("高血压");
        when(localTripleStore.allNames()).thenReturn(names);

        MedicalVocabulary vocabulary = mock(MedicalVocabulary.class);
        when(vocabulary.matchAliases("血压高")).thenReturn(java.util.List.of("高血压"));
        setField(tool, "vocabulary", vocabulary);

        assertEquals("高血压", tool.resolveEntity("血压高"));
    }
}
