package cn.kmbeast.core.graph;

import cn.kmbeast.mapper.HealthModelConfigMapper;
import cn.kmbeast.mapper.MallProductMapper;
import cn.kmbeast.mapper.NewsMapper;
import cn.kmbeast.mapper.PatientProfileMapper;
import cn.kmbeast.mapper.UserHealthMapper;
import cn.kmbeast.mapper.VisitRecordMapper;
import cn.kmbeast.pojo.dto.query.extend.HealthModelConfigQueryDto;
import cn.kmbeast.pojo.dto.query.extend.MallProductQueryDto;
import cn.kmbeast.pojo.dto.query.extend.UserHealthQueryDto;
import cn.kmbeast.pojo.entity.PatientProfile;
import cn.kmbeast.pojo.entity.VisitRecord;
import cn.kmbeast.pojo.vo.HealthModelConfigVO;
import cn.kmbeast.pojo.vo.MallProductVO;
import cn.kmbeast.pojo.vo.NewsVO;
import cn.kmbeast.pojo.vo.UserHealthVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 抽取器的数据库查询链路测试（阶段 0-B / B5）。
 *
 * <p>沿用项目既有约定：纯 Mockito 单测，<b>零 {@code @SpringBootTest}</b>、
 * 不连库。这样"改了 Mapper 却没同步抽取逻辑"会被这里拦住，
 * 而不是等到线上导入报 {@code Table doesn't exist}。
 */
class GraphExtractorDbTest {

    @Mock
    private PatientProfileMapper patientProfileMapper;

    @Mock
    private UserHealthMapper userHealthMapper;

    @Mock
    private VisitRecordMapper visitRecordMapper;

    @Mock
    private HealthModelConfigMapper healthModelConfigMapper;

    @Mock
    private MallProductMapper mallProductMapper;

    @Mock
    private NewsMapper newsMapper;

    @Mock
    private MedicalVocabulary vocabulary;

    @InjectMocks
    private GraphExtractor extractor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // canonical() 默认原样返回；个别用例会单独 stub
        when(vocabulary.canonical(any())).thenAnswer(inv -> inv.getArgument(0));
        when(vocabulary.matchIn(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("指标字典：12 项全部建成 Indicator 节点")
    void extractIndicators() {
        when(healthModelConfigMapper.query(any(HealthModelConfigQueryDto.class)))
                .thenReturn(List.of(config(1, "收缩压", "mmHg", "90,140"),
                        config(2, "空腹血糖", "mmol/L", "{\"min\":3.9,\"max\":6.1}")));

        GraphPayload p = extractor.extractIndicators();

        assertEquals(2, p.nodeCount());
        assertTrue(p.getNodes().stream().allMatch(n -> n.getLabel() == GraphSchema.Label.INDICATOR));
        GraphNode sbp = p.getNodes().stream()
                .filter(n -> "收缩压".equals(n.getName())).findFirst().orElseThrow();
        assertEquals("mmHg", sbp.getProps().get("unit"));
        assertEquals("90,140", sbp.getProps().get("valueRange"));
        assertTrue(p.getWarnings().isEmpty());
    }

    @Test
    @DisplayName("指标字典查询失败：告警而不抛异常（导入端点不能因单表故障整体失败）")
    void extractIndicatorsFailure() {
        when(healthModelConfigMapper.query(any(HealthModelConfigQueryDto.class)))
                .thenThrow(new RuntimeException("boom"));

        GraphPayload p = extractor.extractIndicators();

        assertEquals(0, p.nodeCount());
        assertEquals(1, p.getWarnings().size());
        assertTrue(p.getWarnings().get(0).contains("HEALTH_MODEL_CONFIG_QUERY_FAILED"));
    }

    @Test
    @DisplayName("患者档案：慢病/过敏/用药/手术/家族史各自建边")
    void extractPatientProfiles() {
        PatientProfile profile = new PatientProfile();
        profile.setUserId(3);
        profile.setAge(58);
        profile.setGender("male");
        profile.setChronicDiseases("[\"高血压\", \"骨质疏松\"]");
        profile.setAllergies("[\"青霉素\"]");
        profile.setMedications("[\"氨氯地平\"]");
        profile.setSurgeries("[\"阑尾切除\"]");
        profile.setFamilyHistory("[\"父亲高血压\"]");
        when(patientProfileMapper.listAll()).thenReturn(List.of(profile));

        GraphPayload p = extractor.extractPatientProfiles();

        String patient = "患者#3";
        assertTrue(hasEdge(p, patient, GraphSchema.Relation.HAS_DIAGNOSIS, "高血压"));
        assertTrue(hasEdge(p, patient, GraphSchema.Relation.HAS_DIAGNOSIS, "骨质疏松"));
        assertTrue(hasEdge(p, patient, GraphSchema.Relation.ALLERGIC_TO, "青霉素"));
        assertTrue(hasEdge(p, patient, GraphSchema.Relation.TAKES_DRUG, "氨氯地平"));
        assertTrue(hasEdge(p, patient, GraphSchema.Relation.HAD_SURGERY, "阑尾切除"));
        // "父亲高血压" 必须剥掉亲属前缀，落到既有的"高血压"节点而不是新造一个
        assertTrue(hasEdge(p, patient, GraphSchema.Relation.FAMILY_HISTORY_OF, "高血压"));
        assertFalse(hasEdge(p, patient, GraphSchema.Relation.FAMILY_HISTORY_OF, "父亲高血压"));
    }

    @Test
    @DisplayName("指标测量：同一患者-指标对只产生一条边（防图爆炸）")
    void extractMeasurements() {
        when(userHealthMapper.query(any(UserHealthQueryDto.class)))
                .thenReturn(List.of(measure(3, "收缩压", "138", "mmHg"),
                        measure(3, "收缩压", "142", "mmHg"),
                        measure(3, "空腹血糖", "6.5", "mmol/L")));

        GraphPayload p = extractor.extractMeasurements();

        // 两条收缩压测量按 Triple.key() 合并为一条边，属性保留最后一次的值
        assertEquals(2, p.edgeCount(), "160 条测量不应产生 160 条平行边");
        long sbpEdges = p.getEdges().stream()
                .filter(t -> "收缩压".equals(t.getObject())
                        && t.getRelation() == GraphSchema.Relation.HAS_MEASUREMENT)
                .count();
        assertEquals(1, sbpEdges);
        assertEquals("142", p.getEdges().stream()
                .filter(t -> "收缩压".equals(t.getObject())).findFirst()
                .orElseThrow().getProps().get("value"));
    }

    @Test
    @DisplayName("就诊记录：诊断归一化 + 处方只留药品")
    void extractVisits() {
        VisitRecord record = new VisitRecord();
        record.setId(11);
        record.setPatientId(4);
        record.setDiagnosis("原发性高血压 1 级（需复测确认）");
        record.setPrescription("苯磺酸氨氯地平片 5mg 口服 每日一次\\n低盐饮食（<5g/日）");
        when(visitRecordMapper.listAll()).thenReturn(List.of(record));

        GraphPayload p = extractor.extractVisits();

        assertTrue(hasEdge(p, "就诊#11", GraphSchema.Relation.DIAGNOSED_WITH, "高血压"),
                "诊断必须归一化到词表名，否则造出匹配不到的孤节点");
        assertTrue(hasEdge(p, "就诊#11", GraphSchema.Relation.PRESCRIBED, "苯磺酸氨氯地平片"));
        assertFalse(hasEdge(p, "就诊#11", GraphSchema.Relation.PRESCRIBED, "低盐饮食（<5g/日）"),
                "生活建议不是药，不能被写成处方");
        assertTrue(hasEdge(p, "患者#4", GraphSchema.Relation.HAD_VISIT, "就诊#11"));
    }

    @Test
    @DisplayName("药品：商城商品建 Product 与 SOLD_AS，两线不重合时告警")
    void extractDrugs() {
        MallProductVO product = new MallProductVO();
        product.setId(101);
        product.setName("苯磺酸氨氯地平片");
        product.setPrice(new BigDecimal("25.00"));
        product.setStock(200);
        when(mallProductMapper.query(any(MallProductQueryDto.class)))
                .thenReturn(List.of(product));

        GraphPayload p = extractor.extractDrugs();

        assertTrue(p.getNodes().stream()
                .anyMatch(n -> n.getLabel() == GraphSchema.Label.PRODUCT
                        && "苯磺酸氨氯地平片".equals(n.getName())));
        assertTrue(hasEdge(p, "苯磺酸氨氯地平片", GraphSchema.Relation.SOLD_AS, "苯磺酸氨氯地平片"));
        // 两条数据线（drugs.json 55 条 vs 商城 1 件）必然不重合，
        // 或 drugs.json 在 CI 上不存在 —— 两种情形都必须有告警，
        // 否则运维会误以为"药品数据已完整"，而 AI 可能推荐商城买不到的药。
        assertFalse(p.getWarnings().isEmpty(),
                "药品两线不一致 / 资产缺失必须有告警，实际 warnings=" + p.getWarnings());
    }

    @Test
    @DisplayName("文章抽取：词表命中建 MENTIONS 边，单篇上限 8")
    void extractArticles() {
        NewsVO news = new NewsVO();
        news.setId(7);
        news.setTitle("高血压患者的日常管理");
        news.setContent("控制血压需要长期服药与低盐饮食。");
        when(newsMapper.selectAllForRag()).thenReturn(List.of(news));
        when(vocabulary.matchIn(any())).thenReturn(List.of(
                new GraphNode("高血压", GraphSchema.Label.DISEASE, "", null, null, GraphSources.SEED)));

        GraphPayload p = extractor.extractArticles();

        assertTrue(hasEdge(p, "高血压患者的日常管理", GraphSchema.Relation.MENTIONS, "高血压"));
        assertTrue(p.getNodes().stream()
                .anyMatch(n -> n.getLabel() == GraphSchema.Label.ARTICLE));
    }

    @Test
    @DisplayName("文章抽取：词表无命中时不建节点（避免灌入空壳文章节点）")
    void extractArticlesNoHit() {
        NewsVO news = new NewsVO();
        news.setId(9);
        news.setTitle("夏季防晒小常识");
        when(newsMapper.selectAllForRag()).thenReturn(List.of(news));
        when(vocabulary.matchIn(any())).thenReturn(List.of());

        GraphPayload p = extractor.extractArticles();

        assertEquals(0, p.nodeCount());
        assertEquals(0, p.edgeCount());
    }

    // ==================== 辅助 ====================

    private boolean hasEdge(GraphPayload p, String subject,
                            GraphSchema.Relation rel, String object) {
        return p.getEdges().stream().anyMatch(t ->
                subject.equals(t.getSubject())
                        && t.getRelation() == rel
                        && object.equals(t.getObject()));
    }

    private HealthModelConfigVO config(int id, String name, String unit, String range) {
        HealthModelConfigVO c = new HealthModelConfigVO();
        c.setId(id);
        c.setName(name);
        c.setUnit(unit);
        c.setValueRange(range);
        return c;
    }

    private UserHealthVO measure(int userId, String name, String value, String unit) {
        UserHealthVO v = new UserHealthVO();
        v.setUserId(userId);
        v.setName(name);
        v.setValue(value);
        v.setUnit(unit);
        return v;
    }
}
