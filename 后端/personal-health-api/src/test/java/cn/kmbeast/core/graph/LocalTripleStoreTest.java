package cn.kmbeast.core.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地三元组存储测试（阶段 0-B / B2）。
 *
 * <p>本机 Neo4j 长期未启动，{@link LocalTripleStore} 是图谱成果的<b>唯一落地处</b>，
 * 它的幂等性与持久化正确性直接决定"导入两次统计不变"这条验收标准能否成立。
 */
class LocalTripleStoreTest {

    private LocalTripleStore newStore(Path dir, String name) {
        LocalTripleStore store = new LocalTripleStore();
        store.setFilePath(dir.resolve(name).toString());
        store.init();
        return store;
    }

    private Triple t(String s, GraphSchema.Relation r, String o) {
        return Triple.of(s, GraphSchema.Label.DISEASE, r, o, GraphSchema.Label.DRUG, "seed");
    }

    @Test
    @DisplayName("幂等：同一批三元组写两次，节点数与边数不变")
    void upsertIsIdempotent(@TempDir Path dir) {
        LocalTripleStore store = newStore(dir, "t1.json");
        List<Triple> batch = List.of(
                t("高血压", GraphSchema.Relation.TREATED_BY, "苯磺酸氨氯地平片"),
                t("高血压", GraphSchema.Relation.TREATED_BY, "缬沙坦胶囊"));

        assertEquals(2, store.upsert(batch), "首次写入应新增 2 条");
        assertEquals(0, store.upsert(batch), "重复写入应新增 0 条");
        assertEquals(2, store.size());
        assertEquals(3, store.nodeCount(), "高血压 + 两种药 = 3 个节点");
    }

    @Test
    @DisplayName("属性更新：重复写入保留最新 value，但不增加边数")
    void upsertUpdatesProps(@TempDir Path dir) {
        LocalTripleStore store = newStore(dir, "t2.json");
        Map<String, Object> p1 = new LinkedHashMap<>();
        p1.put("value", "138");
        Triple first = Triple.with("患者#3", GraphSchema.Label.PATIENT,
                GraphSchema.Relation.HAS_MEASUREMENT, "收缩压", GraphSchema.Label.INDICATOR,
                p1, "user_health");
        store.upsert(List.of(first));

        Map<String, Object> p2 = new LinkedHashMap<>();
        p2.put("value", "142");
        Triple second = Triple.with("患者#3", GraphSchema.Label.PATIENT,
                GraphSchema.Relation.HAS_MEASUREMENT, "收缩压", GraphSchema.Label.INDICATOR,
                p2, "user_health");
        assertEquals(0, store.upsert(List.of(second)));
        assertEquals(1, store.size());

        List<Triple> edges = store.neighbors("患者#3", 10);
        assertEquals(1, edges.size());
        assertEquals("142", edges.get(0).getProps().get("value"), "属性应更新为最新值");
    }

    @Test
    @DisplayName("非法三元组（空名/空标签）被丢弃，不写僵尸节点")
    void invalidTriplesRejected(@TempDir Path dir) {
        LocalTripleStore store = newStore(dir, "t3.json");
        store.upsert(List.of(
                Triple.of("", GraphSchema.Label.DISEASE, GraphSchema.Relation.TREATED_BY,
                        "阿司匹林肠溶片", GraphSchema.Label.DRUG, "seed"),
                Triple.of("高血压", GraphSchema.Label.DISEASE, GraphSchema.Relation.TREATED_BY,
                        null, GraphSchema.Label.DRUG, "seed")));
        assertEquals(0, store.size());
        assertEquals(0, store.nodeCount());
    }

    @Test
    @DisplayName("一跳检索：出边与入边都能命中（无向 related）")
    void neighborsAndRelated(@TempDir Path dir) {
        LocalTripleStore store = newStore(dir, "t4.json");
        store.upsert(List.of(t("高血压", GraphSchema.Relation.TREATED_BY, "苯磺酸氨氯地平片")));

        // "高血压"是主体 → 出边命中
        assertEquals(1, store.neighbors("高血压", 8).size());
        // 药是客体 → 只查入边才能命中，related() 必须覆盖这个方向
        assertEquals(0, store.neighbors("苯磺酸氨氯地平片", 8).size());
        assertEquals(1, store.related("苯磺酸氨氯地平片", 8).size(),
                "用户问『氨氯地平治什么』时，药是客体，必须能反向召回");
    }

    @Test
    @DisplayName("落盘后重新加载，数据完整恢复")
    void saveAndLoad(@TempDir Path dir) {
        LocalTripleStore store = newStore(dir, "t5.json");
        store.upsert(List.of(
                t("高血压", GraphSchema.Relation.TREATED_BY, "苯磺酸氨氯地平片"),
                t("2型糖尿病", GraphSchema.Relation.IS_A, "糖尿病")));
        store.save();

        LocalTripleStore reloaded = newStore(dir, "t5.json");
        assertEquals(2, reloaded.size(), "边应完整恢复");
        assertEquals(4, reloaded.nodeCount(), "节点标签也应恢复（否则 allNames 为空）");
        assertTrue(reloaded.allNames().contains("高血压"));
        assertEquals(1, reloaded.neighbors("高血压", 8).size());
    }

    @Test
    @DisplayName("文件不存在时以空图谱启动，不抛异常")
    void missingFileDegradesGracefully(@TempDir Path dir) {
        LocalTripleStore store = newStore(dir, "not-exist.json");
        assertEquals(0, store.size());
        assertTrue(store.allNames().isEmpty());
    }

    @Test
    @DisplayName("统计结构与 /graph/stats 的 graph 段对齐")
    void stats(@TempDir Path dir) {
        LocalTripleStore store = newStore(dir, "t6.json");
        store.upsert(List.of(t("高血压", GraphSchema.Relation.TREATED_BY, "苯磺酸氨氯地平片")));

        Map<String, Object> s = store.stats();
        assertEquals(2, s.get("nodeCount"));
        assertEquals(1, s.get("relationshipCount"));
        assertTrue(s.containsKey("labels"));
        assertTrue(s.containsKey("relationshipTypes"));
        assertEquals(1, store.countBySource().get("seed"));
    }

    @Test
    @DisplayName("全量实体名为不可变副本（GraphRAG 会长期持有并缓存）")
    void allNamesIsImmutable(@TempDir Path dir) {
        LocalTripleStore store = newStore(dir, "t7.json");
        store.upsert(List.of(t("高血压", GraphSchema.Relation.TREATED_BY, "缬沙坦胶囊")));
        List<String> names = store.allNames();
        assertThrows(UnsupportedOperationException.class, () -> names.add("x"));
    }
}
