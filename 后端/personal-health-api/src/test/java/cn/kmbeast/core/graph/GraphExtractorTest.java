package cn.kmbeast.core.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 规则抽取器的纯函数逻辑测试（阶段 0-B / B5）。
 *
 * <p>这里只测<b>不需要连库</b>的部分：诊断归一化、处方药品行筛选、
 * JSON 列解析、亲属前缀剥离。数据库查询部分由 {@link GraphExtractorDbTest}
 * 用 Mockito 覆盖。
 *
 * <p>这些函数之所以值得单独测，是因为它们直接决定图谱里会出现什么节点：
 * 归一化错了会造出永远匹配不到的孤节点，处方筛选漏了会把"每日快走 30 分钟"
 * 当成药名建 {@code PRESCRIBED} 边 —— 两者都会静默污染图谱。
 */
class GraphExtractorTest {

    @Test
    @DisplayName("诊断归一化：去掉分级、括号与修饰前缀")
    void normalizeDiagnosis() {
        assertEquals("高血压",
                first(GraphExtractor.normalizeDiagnosis("原发性高血压 1 级（需复测确认）")));
        assertEquals("空腹血糖受损",
                first(GraphExtractor.normalizeDiagnosis("空腹血糖受损（IFG）")));
        assertEquals("脂肪肝",
                first(GraphExtractor.normalizeDiagnosis("轻度脂肪肝（考虑非酒精性）")));
        assertTrue(GraphExtractor.normalizeDiagnosis(null).isEmpty());
        assertTrue(GraphExtractor.normalizeDiagnosis("   ").isEmpty());
    }

    private String first(List<String> list) {
        return list.isEmpty() ? "" : list.get(0);
    }

    @Test
    @DisplayName("处方行筛选：只留药品，滤掉生活建议")
    void pickDrugLines() {
        List<String> drugs = GraphExtractor.pickDrugLines(
                "苯磺酸氨氯地平片 5mg 口服 每日一次\\n低盐饮食（<5g/日）\\n每日快走 30 分钟");
        assertEquals(1, drugs.size(), "只应挑出药品行，生活建议必须被过滤: " + drugs);
        assertEquals("苯磺酸氨氯地平片", drugs.get(0));

        // 纯生活建议 → 一条都不该出（否则"每日快走"会被当成药名写进图谱）
        assertTrue(GraphExtractor.pickDrugLines(
                "饮食控制为主，暂不予降糖药物\\n减少精制碳水与含糖饮料").isEmpty());

        // 不含剂型后缀但命中已知药名
        List<String> known = GraphExtractor.pickDrugLines("继续服用氨氯地平，观察血压");
        assertEquals(1, known.size());
        assertEquals("氨氯地平", known.get(0));

        assertTrue(GraphExtractor.pickDrugLines(null).isEmpty());
    }

    @Test
    @DisplayName("JSON 数组列解析：兼容数组、对象数组与裸串")
    void parseList() {
        assertEquals(List.of("高血压", "骨质疏松"),
                GraphExtractor.parseList("[\"高血压\", \"骨质疏松\"]"));
        assertEquals(List.of("高血压"),
                GraphExtractor.parseList("[{\"name\":\"高血压\"}]"));
        assertEquals(List.of("高血压"), GraphExtractor.parseList("高血压"));
        assertTrue(GraphExtractor.parseList(null).isEmpty());
        assertTrue(GraphExtractor.parseList("[]").isEmpty());
    }

    @Test
    @DisplayName("家族史亲属前缀剥离")
    void stripKinship() {
        assertEquals("高血压", GraphExtractor.stripKinship("父亲高血压"));
        assertEquals("糖尿病", GraphExtractor.stripKinship("母亲糖尿病"));
        assertEquals("乳腺癌", GraphExtractor.stripKinship("姐姐乳腺癌"));
        assertEquals("高血压", GraphExtractor.stripKinship("高血压"));
        assertEquals("", GraphExtractor.stripKinship(null));
    }

    @Test
    @DisplayName("患者节点名仅由 ID 推导，不含真实身份")
    void patientName() {
        // 节点名会进入 GraphRAG 的包含匹配，用真实姓名会导致"提到同名者"即误命中
        assertEquals("患者#3", GraphExtractor.patientName(3));
        assertEquals("患者#3001", GraphExtractor.patientName(3001));
        assertEquals(GraphExtractor.patientName(3), GraphExtractor.patientName(3),
                "同名必须稳定，否则 MERGE 幂等失效");
    }

    @Test
    @DisplayName("批次号格式稳定（yyyyMMdd-HHmmss）")
    void batchId() {
        String batch = GraphExtractor.newBatchId();
        assertNotNull(batch);
        assertTrue(batch.matches("\\d{8}-\\d{6}"), "批次号格式应为 yyyyMMdd-HHmmss: " + batch);
    }
}
