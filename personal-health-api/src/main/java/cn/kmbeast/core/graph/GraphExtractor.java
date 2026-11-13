package cn.kmbeast.core.graph;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.kmbeast.mapper.MallProductMapper;
import cn.kmbeast.mapper.PatientProfileMapper;
import cn.kmbeast.mapper.UserHealthMapper;
import cn.kmbeast.mapper.VisitRecordMapper;
import cn.kmbeast.mapper.HealthModelConfigMapper;
import cn.kmbeast.mapper.NewsMapper;
import cn.kmbeast.pojo.dto.query.extend.HealthModelConfigQueryDto;
import cn.kmbeast.pojo.dto.query.extend.MallProductQueryDto;
import cn.kmbeast.pojo.dto.query.extend.UserHealthQueryDto;
import cn.kmbeast.pojo.entity.PatientProfile;
import cn.kmbeast.pojo.entity.VisitRecord;
import cn.kmbeast.pojo.vo.HealthModelConfigVO;
import cn.kmbeast.pojo.vo.MallProductVO;
import cn.kmbeast.pojo.vo.NewsVO;
import cn.kmbeast.pojo.vo.UserHealthVO;

/**
 * 规则抽取器 —— 从既有 MySQL 表与既有 JSON 资产抽取三元组，<b>零 LLM</b>。
 *
 * <p><b>2026-10-07 新增（阶段 0-B / B5）</b>。抽取策略刻意选"规则优先"：
 * <ul>
 *   <li>现有原料量级很小（档案 7、就诊 3、指标 160），规则可覆盖约 85%，
 *       而 LLM 抽取要额外 N 次调用、还可能抽错污染图谱；</li>
 *   <li>规则抽取<b>确定性</b>：同样的输入永远得到同样的图，可幂等验收；</li>
 *   <li>{@code ai_config.api_key} 当前为空，LLM 链路此刻根本跑不通。</li>
 * </ul>
 *
 * <p>遵守「强耦合原则」：<b>未新增任何数据表</b>，全部复用既有表
 * （patient_profile / user_health / visit_record / health_model_config /
 * mall_product / news）与既有文件（ai_data/drugs.json）。
 *
 * <p>⚠️ 含敏感数据（诊断、处方、过敏史）。所有调用方必须是管理员端点或离线任务，
 * <b>产物只写图谱，不经由聊天链路回传原文</b>。
 */
@Slf4j
@Component
public class GraphExtractor {

    /** 药品 JSON 资产路径（相对运行目录） */
    private static final String DRUGS_JSON = "ai_data/drugs.json";

    /** user_health 单次抽取上限（防拖库，也防图爆炸） */
    private static final int MAX_MEASUREMENTS = 2000;

    /** 单篇文章最多抽取的实体数（防止长文把图灌满垃圾边） */
    private static final int MAX_ENTITIES_PER_ARTICLE = 8;

    /**
     * 药品 JSON 路径。
     *
     * <p>给默认值而不是只靠 {@code @Value}：本类在单测里会被直接 new 出来，
     * 若字段为 null 则 {@code Paths.get(null)} 直接 NPE。
     */
    @Value("${graph.drugs.file:" + DRUGS_JSON + "}")
    private String drugsJsonPath = DRUGS_JSON;

    @Resource
    private PatientProfileMapper patientProfileMapper;

    @Resource
    private UserHealthMapper userHealthMapper;

    @Resource
    private VisitRecordMapper visitRecordMapper;

    @Resource
    private HealthModelConfigMapper healthModelConfigMapper;

    @Resource
    private MallProductMapper mallProductMapper;

    @Resource
    private NewsMapper newsMapper;

    @Resource
    private MedicalVocabulary vocabulary;

    // ==================== 1. 指标字典 ====================

    /**
     * health_model_config（12 行）→ Indicator 节点。
     *
     * <p>只建节点不建边：指标与"检查项"之间没有既有的中间表，
     * 硬造 {@code MEASURED_BY} 到不存在的 Examination 节点会产出无意义边。
     * 单位与正常范围作为节点属性保留 —— 这是后续阶段 2 参数化查询的字典来源。
     */
    public GraphPayload extractIndicators() {
        GraphPayload p = new GraphPayload();
        List<HealthModelConfigVO> configs;
        try {
            // ⚠️ 不用 getGlobalModels()：它带 WHERE is_global=1，漏掉私有指标。
            // 图谱要的是完整指标字典，故走 query() 全量。
            HealthModelConfigQueryDto q = new HealthModelConfigQueryDto();
            q.setCurrent(0);
            q.setSize(MAX_MEASUREMENTS);
            configs = healthModelConfigMapper.query(q);
        } catch (Exception e) {
            log.error("[GraphExtractor] health_model_config 查询失败: {}", e.getMessage());
            p.warn("HEALTH_MODEL_CONFIG_QUERY_FAILED: " + e.getMessage());
            return p;
        }
        if (configs == null || configs.isEmpty()) {
            p.warn("HEALTH_MODEL_CONFIG_EMPTY: 指标字典为空，图谱将缺少指标节点");
            return p;
        }
        for (cn.kmbeast.pojo.entity.HealthModelConfig c : configs) {
            if (c.getName() == null || c.getName().trim().isEmpty()) {
                continue;
            }
            Map<String, Object> props = new LinkedHashMap<>();
            if (c.getUnit() != null) {
                props.put("unit", c.getUnit());
            }
            if (c.getValueRange() != null) {
                props.put("valueRange", c.getValueRange());
            }
            if (c.getSymbol() != null) {
                props.put("symbol", c.getSymbol());
            }
            props.put("configId", c.getId());
            p.addNode(new GraphNode(c.getName().trim(), GraphSchema.Label.INDICATOR,
                    c.getDetail() == null ? "" : c.getDetail(), null, props,
                    GraphSources.HEALTH_MODEL_CONFIG));
        }
        return p;
    }

    // ==================== 2. 患者档案（JSON 列） ====================

    /**
     * patient_profile 的 7 个 JSON 列 → 患者画像边。
     *
     * <p>原方案写的是 MySQL {@code JSON_TABLE}。实测后改为 <b>Java 侧解析</b>：
     * <ul>
     *   <li>MySQL 9.7 存储过程传中文参数会乱码，SQL 侧解析不可靠；</li>
     *   <li>{@code chronic_diseases} 等列的实际形态是 {@code ["高血压"]} 这样的
     *       字符串数组，也可能退化为裸串，Java 侧更好兼容（见 {@link #parseList}）。</li>
     * </ul>
     */
    public GraphPayload extractPatientProfiles() {
        GraphPayload p = new GraphPayload();
        List<PatientProfile> profiles;
        try {
            profiles = patientProfileMapper.listAll();
        } catch (Exception e) {
            log.error("[GraphExtractor] patient_profile 查询失败: {}", e.getMessage());
            p.warn("PATIENT_PROFILE_QUERY_FAILED: " + e.getMessage());
            return p;
        }
        if (profiles == null || profiles.isEmpty()) {
            p.warn("PATIENT_PROFILE_EMPTY");
            return p;
        }

        for (PatientProfile profile : profiles) {
            if (profile.getUserId() == null) {
                continue;
            }
            String patient = patientName(profile.getUserId());
            Map<String, Object> patientProps = new LinkedHashMap<>();
            patientProps.put("userId", profile.getUserId());
            if (profile.getAge() != null) {
                patientProps.put("age", profile.getAge());
            }
            if (profile.getGender() != null) {
                patientProps.put("gender", profile.getGender());
            }
            p.addNode(new GraphNode(patient, GraphSchema.Label.PATIENT,
                    "平台健康档案持有者（用户ID " + profile.getUserId() + "）", null,
                    patientProps, GraphSources.PATIENT_PROFILE_JSON));

            // 慢病 → Disease（先过词表做规范名归并，如 "血压高" → "高血压"）
            for (String d : parseList(profile.getChronicDiseases())) {
                String canon = vocabulary.canonical(d);
                p.addNode(GraphNode.of(canon, GraphSchema.Label.DISEASE, GraphSources.PATIENT_PROFILE_JSON));
                p.addEdge(Triple.of(patient, GraphSchema.Label.PATIENT,
                        GraphSchema.Relation.HAS_DIAGNOSIS, canon, GraphSchema.Label.DISEASE,
                        GraphSources.PATIENT_PROFILE_JSON));
            }
            // 过敏 → Allergen
            for (String a : parseList(profile.getAllergies())) {
                p.addNode(GraphNode.of(a, GraphSchema.Label.ALLERGEN, GraphSources.PATIENT_PROFILE_JSON));
                p.addEdge(Triple.of(patient, GraphSchema.Label.PATIENT,
                        GraphSchema.Relation.ALLERGIC_TO, a, GraphSchema.Label.ALLERGEN,
                        GraphSources.PATIENT_PROFILE_JSON));
            }
            // 用药 → Drug
            for (String m : parseList(profile.getMedications())) {
                p.addNode(GraphNode.of(m, GraphSchema.Label.DRUG, GraphSources.PATIENT_PROFILE_JSON));
                p.addEdge(Triple.of(patient, GraphSchema.Label.PATIENT,
                        GraphSchema.Relation.TAKES_DRUG, m, GraphSchema.Label.DRUG,
                        GraphSources.PATIENT_PROFILE_JSON));
            }
            // 手术 → Surgery
            for (String s : parseList(profile.getSurgeries())) {
                p.addNode(GraphNode.of(s, GraphSchema.Label.SURGERY, GraphSources.PATIENT_PROFILE_JSON));
                p.addEdge(Triple.of(patient, GraphSchema.Label.PATIENT,
                        GraphSchema.Relation.HAD_SURGERY, s, GraphSchema.Label.SURGERY,
                        GraphSources.PATIENT_PROFILE_JSON));
            }
            // 家族史 → Disease（去掉"父亲/母亲/姐姐"等亲属前缀，只留病名）
            for (String f : parseList(profile.getFamilyHistory())) {
                String disease = stripKinship(f);
                if (disease.isEmpty()) {
                    continue;
                }
                String canon = vocabulary.canonical(disease);
                p.addNode(GraphNode.of(canon, GraphSchema.Label.DISEASE, GraphSources.PATIENT_PROFILE_JSON));
                p.addEdge(Triple.with(patient, GraphSchema.Label.PATIENT,
                        GraphSchema.Relation.FAMILY_HISTORY_OF, canon, GraphSchema.Label.DISEASE,
                        Map.of("raw", f), GraphSources.PATIENT_PROFILE_JSON));
            }
        }
        return p;
    }

    /**
     * 患者节点名。
     *
     * <p>用 {@code 患者#3} 而非真实姓名 —— 图谱节点名会进入
     * {@link GraphRAG#extractEntities} 的包含匹配，若用真实姓名，
     * 用户提到任何同名者都会被误判为命中该患者节点。
     */
    static String patientName(Integer userId) {
        return "患者#" + userId;
    }

    /**
     * 去掉家族史里的亲属前缀："父亲高血压" → "高血压"。
     */
    static String stripKinship(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        String[] kinship = {"父亲", "母亲", "祖父", "祖母", "外祖父", "外祖母",
                "哥哥", "姐姐", "弟弟", "妹妹", "儿子", "女儿", "爷爷", "奶奶", "外公", "外婆"};
        for (String k : kinship) {
            if (s.startsWith(k) && s.length() > k.length()) {
                return s.substring(k.length()).trim();
            }
        }
        return s;
    }

    /**
     * 解析 JSON 数组列。兼容三种历史形态：
     * {@code ["高血压"]}、{@code [{"name":"高血压"}]}、以及非 JSON 的裸串 {@code 高血压}。
     */
    static List<String> parseList(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) {
            return out;
        }
        try {
            JSONArray arr = JSON.parseArray(json.trim());
            for (int i = 0; i < arr.size(); i++) {
                Object item = arr.get(i);
                String s;
                if (item instanceof JSONObject obj) {
                    s = obj.getString("name") != null ? obj.getString("name") : obj.getString("value");
                } else {
                    s = item == null ? null : String.valueOf(item);
                }
                if (s != null && !s.trim().isEmpty()) {
                    out.add(s.trim());
                }
            }
        } catch (Exception e) {
            // 不是 JSON 数组：按顿号/逗号切分的裸串
            String[] parts = json.replace('，', ',').split(",");
            for (String part : parts) {
                if (!part.trim().isEmpty()) {
                    out.add(part.trim());
                }
            }
        }
        return out;
    }

    // ==================== 3. 指标测量（EAV） ====================

    /**
     * user_health（160 条 EAV）→ {@code 患者#x -[:HAS_MEASUREMENT]-> 指标}。
     *
     * <p>每条测量都带 {@code value} / {@code time} 属性。
     * 幂等键不含 value（见 {@link Triple#key()}），
     * 因此同一个"患者-指标"对只会有<b>一条边</b>，属性保留最后一次写入的值 ——
     * 否则 160 条测量会生成 160 条平行边，图会爆炸且检索全是噪声。
     */
    public GraphPayload extractMeasurements() {
        GraphPayload p = new GraphPayload();
        UserHealthQueryDto dto = new UserHealthQueryDto();
        dto.setCurrent(0);
        dto.setSize(MAX_MEASUREMENTS);
        List<UserHealthVO> rows;
        try {
            rows = userHealthMapper.query(dto);
        } catch (Exception e) {
            log.error("[GraphExtractor] user_health 查询失败: {}", e.getMessage());
            p.warn("USER_HEALTH_QUERY_FAILED: " + e.getMessage());
            return p;
        }
        if (rows == null || rows.isEmpty()) {
            p.warn("USER_HEALTH_EMPTY");
            return p;
        }
        for (UserHealthVO v : rows) {
            if (v.getUserId() == null || v.getName() == null || v.getName().trim().isEmpty()) {
                continue;
            }
            String patient = patientName(v.getUserId());
            String indicator = v.getName().trim();
            p.addNode(GraphNode.of(patient, GraphSchema.Label.PATIENT, GraphSources.USER_HEALTH));
            p.addNode(GraphNode.of(indicator, GraphSchema.Label.INDICATOR, GraphSources.USER_HEALTH));

            Map<String, Object> props = new LinkedHashMap<>();
            if (v.getValue() != null) {
                props.put("value", v.getValue());
            }
            if (v.getUnit() != null) {
                props.put("unit", v.getUnit());
            }
            if (v.getCreateTime() != null) {
                props.put("time", v.getCreateTime().toString());
            }
            p.addEdge(Triple.with(patient, GraphSchema.Label.PATIENT,
                    GraphSchema.Relation.HAS_MEASUREMENT, indicator, GraphSchema.Label.INDICATOR,
                    props, GraphSources.USER_HEALTH));
        }
        return p;
    }

    // ==================== 4. 就诊记录 ====================

    /**
     * visit_record → 诊断/处方边。
     *
     * <p>{@code diagnosis} 实际形态是 {@code "原发性高血压 1 级（需复测确认）"} ——
     * 带分级与括号补充，必须归一化才能与词表的"高血压"对上，
     * 否则会造出一个永远匹配不到的孤节点。见 {@link #normalizeDiagnosis}。
     *
     * <p>{@code prescription} 是 {@code \n} 分隔的多行文本，含非药品建议
     * （"低盐饮食（<5g/日）"、"每日快走 30 分钟"），必须过滤，见 {@link #pickDrugLines}。
     */
    public GraphPayload extractVisits() {
        GraphPayload p = new GraphPayload();
        List<VisitRecord> records;
        try {
            records = visitRecordMapper.listAll();
        } catch (Exception e) {
            log.error("[GraphExtractor] visit_record 查询失败: {}", e.getMessage());
            p.warn("VISIT_RECORD_QUERY_FAILED: " + e.getMessage());
            return p;
        }
        if (records == null || records.isEmpty()) {
            p.warn("VISIT_RECORD_EMPTY");
            return p;
        }
        for (VisitRecord r : records) {
            String visit = "就诊#" + r.getId();
            Map<String, Object> visitProps = new LinkedHashMap<>();
            if (r.getPatientId() != null) {
                visitProps.put("patientId", r.getPatientId());
            }
            if (r.getCreateTime() != null) {
                visitProps.put("visitTime", r.getCreateTime().toString());
            }
            p.addNode(new GraphNode(visit, GraphSchema.Label.VISIT,
                    r.getChiefComplaint() == null ? "" : r.getChiefComplaint(),
                    null, visitProps, GraphSources.VISIT_RECORD));

            if (r.getPatientId() != null) {
                String patient = patientName(r.getPatientId());
                p.addNode(GraphNode.of(patient, GraphSchema.Label.PATIENT, GraphSources.VISIT_RECORD));
                p.addEdge(Triple.of(patient, GraphSchema.Label.PATIENT,
                        GraphSchema.Relation.HAD_VISIT, visit, GraphSchema.Label.VISIT,
                        GraphSources.VISIT_RECORD));
            }

            // 诊断 → Disease
            for (String d : normalizeDiagnosis(r.getDiagnosis())) {
                String canon = vocabulary.canonical(d);
                p.addNode(GraphNode.of(canon, GraphSchema.Label.DISEASE, GraphSources.VISIT_RECORD));
                p.addEdge(Triple.with(visit, GraphSchema.Label.VISIT,
                        GraphSchema.Relation.DIAGNOSED_WITH, canon, GraphSchema.Label.DISEASE,
                        Map.of("raw", r.getDiagnosis()), GraphSources.VISIT_RECORD));
            }

            // 处方 → Drug
            for (String drug : pickDrugLines(r.getPrescription())) {
                p.addNode(GraphNode.of(drug, GraphSchema.Label.DRUG, GraphSources.VISIT_RECORD));
                p.addEdge(Triple.of(visit, GraphSchema.Label.VISIT,
                        GraphSchema.Relation.PRESCRIBED, drug, GraphSchema.Label.DRUG,
                        GraphSources.VISIT_RECORD));
            }
        }
        return p;
    }

    /**
     * 诊断文本归一化。
     *
     * <p>实测样本：
     * <ul>
     *   <li>{@code 原发性高血压 1 级（需复测确认）} → {@code 高血压}</li>
     *   <li>{@code 空腹血糖受损（IFG）} → {@code 空腹血糖受损}</li>
     *   <li>{@code 轻度脂肪肝（考虑非酒精性）} → {@code 脂肪肝}</li>
     * </ul>
     *
     * <p>做法：先去掉括号内容 → 去掉"1 级/2 级"分级 → 去掉"原发性/轻度"等修饰前缀，
     * 再交给词表 {@link MedicalVocabulary#canonical} 做别名归并。
     * 若词表里查不到，<b>保留归一化后的原文</b>而不是丢弃 ——
     * 宁可多一个孤节点，也不能丢掉真实诊断。
     */
    static List<String> normalizeDiagnosis(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        String s = raw.trim()
                .replaceAll("[（(][^）)]*[）)]", "")   // 去括号及内容
                .replaceAll("[0-9]+\\s*级", "")        // 去分级 "1 级"
                .trim();
        // 去修饰前缀
        for (String prefix : new String[]{"原发性", "继发性", "轻度", "中度", "重度", "急性", "慢性"}) {
            if (s.startsWith(prefix) && s.length() > prefix.length()) {
                s = s.substring(prefix.length()).trim();
                break;
            }
        }
        if (!s.isEmpty()) {
            out.add(s);
        }
        return out;
    }

    /**
     * 从处方多行文本里挑出<b>药品行</b>。
     *
     * <p>实测样本里混着非药物建议（"低盐饮食（<5g/日）"、"每日快走 30 分钟"、
     * "饮食控制为主，暂不予降糖药物"），若不过滤会把"每日快走"当成药名建
     * {@code PRESCRIBED} 边，直接污染图谱。
     *
     * <p>判据（保守，宁漏勿错）：
     * <ol>
     *   <li>行内含有典型剂型后缀（片/胶囊/颗粒/口服液/注射液/软膏/丸/散/滴剂/胶囊）</li>
     *   <li>或该行能命中词表里的已知药名</li>
     * </ol>
     */
    static List<String> pickDrugLines(String prescription) {
        List<String> out = new ArrayList<>();
        if (prescription == null || prescription.trim().isEmpty()) {
            return out;
        }
        // 实际存储里换行被写成了字面量 "\n"
        String[] lines = prescription.replace("\\n", "\n").split("\n");
        String[] dosage = {"片", "胶囊", "颗粒", "口服液", "注射液", "软膏", "丸", "散",
                "滴剂", "缓释片", "肠溶片", "分散片", "气雾剂", "乳膏"};
        for (String line : lines) {
            String s = line.trim();
            if (s.isEmpty()) {
                continue;
            }
            // 去掉用量后缀："苯磺酸氨氯地平片 5mg 口服 每日一次" → "苯磺酸氨氯地平片"
            String head = s.split("\\s+")[0];
            boolean looksLikeDrug = false;
            for (String d : dosage) {
                if (head.endsWith(d)) {
                    looksLikeDrug = true;
                    break;
                }
            }
            if (!looksLikeDrug) {
                // 退而求其次：整行里是否出现已知药名
                for (String known : DRUG_HINTS) {
                    if (s.contains(known)) {
                        head = known;
                        looksLikeDrug = true;
                        break;
                    }
                }
            }
            if (looksLikeDrug) {
                out.add(head);
            }
        }
        return out;
    }

    /**
     * 处方文本里可能出现、但不含剂型后缀的药名（极小的兜底表）。
     * 只在剂型判据失败时启用，避免"每日快走 30 分钟"这类建议被误判。
     */
    private static final List<String> DRUG_HINTS = List.of("氨氯地平", "二甲双胍");

    // ==================== 5. 药品 ====================

    /**
     * 药品资产 → Drug / Product / Category 节点与边。
     *
     * <p>两条数据线：
     * <ol>
     *   <li>{@code ai_data/drugs.json}（55 条，含适应症描述、通用名、厂家、规格）→ Drug 节点 + BELONGS_TO 分类</li>
     *   <li>{@code mall_product} 中 {@code product_type='drug'}（107 行）→ Product 节点 + SOLD_AS 边</li>
     * </ol>
     *
     * <p>两线名称不重合的部分会进 {@code warnings}
     * （已知 4 种药只在 JSON 里、商城买不到），避免 AI 推荐买不到的药。
     */
    public GraphPayload extractDrugs() {
        GraphPayload p = new GraphPayload();

        // ---- 线一：drugs.json ----
        Set<String> jsonDrugNames = new LinkedHashSet<>();
        GraphPayload fromJson = extractDrugsFromJson();
        jsonDrugNames.addAll(fromJson.getNodes().stream()
                .filter(n -> n.getLabel() == GraphSchema.Label.DRUG)
                .map(GraphNode::getName)
                .toList());
        p.merge(fromJson);

        // ---- 线二：mall_product ----
        MallProductQueryDto dto = new MallProductQueryDto();
        dto.setProductType("drug");
        dto.setCurrent(0);
        dto.setSize(2000);
        List<MallProductVO> products;
        try {
            products = mallProductMapper.query(dto);
        } catch (Exception e) {
            log.error("[GraphExtractor] mall_product 查询失败: {}", e.getMessage());
            p.warn("MALL_PRODUCT_QUERY_FAILED: " + e.getMessage());
            products = List.of();
        }
        Set<String> mallDrugNames = new LinkedHashSet<>();
        for (MallProductVO v : products) {
            if (v.getName() == null || v.getName().trim().isEmpty()) {
                continue;
            }
            String name = v.getName().trim();
            mallDrugNames.add(name);

            Map<String, Object> prodProps = new LinkedHashMap<>();
            if (v.getPrice() != null) {
                prodProps.put("price", v.getPrice());
            }
            if (v.getStock() != null) {
                prodProps.put("stock", v.getStock());
            }
            prodProps.put("productId", v.getId());
            p.addNode(new GraphNode(name, GraphSchema.Label.PRODUCT,
                    v.getDescription() == null ? "" : v.getDescription(),
                    null, prodProps, GraphSources.MALL_PRODUCT));

            // Drug 与 Product 同名：SOLD_AS 边把"知识实体"与"可购买商品"连起来
            p.addNode(GraphNode.of(name, GraphSchema.Label.DRUG, GraphSources.MALL_PRODUCT));
            p.addEdge(Triple.of(name, GraphSchema.Label.DRUG,
                    GraphSchema.Relation.SOLD_AS, name, GraphSchema.Label.PRODUCT,
                    GraphSources.MALL_PRODUCT));

            // 分类边。注意 mall_product 存在大量同名商品（测试数据重复），
            // 这里按 name 去重建边，故重复行只会让 BELONGS_TO 命中已有边（幂等）。
            String category = medicalCategoryOf(v);
            if (category != null && !category.isEmpty()) {
                p.addNode(GraphNode.of(category, GraphSchema.Label.CATEGORY, GraphSources.MALL_PRODUCT));
                p.addEdge(Triple.of(name, GraphSchema.Label.DRUG,
                        GraphSchema.Relation.BELONGS_TO, category, GraphSchema.Label.CATEGORY,
                        GraphSources.MALL_PRODUCT));
            }
        }

        // ---- 两线不一致告警 ----
        List<String> onlyJson = new ArrayList<>(jsonDrugNames);
        onlyJson.removeAll(mallDrugNames);
        if (!onlyJson.isEmpty()) {
            p.warn("DRUG_PRODUCT_UNMATCHED: " + onlyJson.size()
                    + " 个药品只存在于 ai_data/drugs.json 而商城无售：" + String.join("、", onlyJson));
        }
        return p;
    }

    /**
     * 读 {@code ai_data/drugs.json}。文件缺失/损坏时降级为空并告警，<b>不抛异常</b> ——
     * 该文件是历史遗留资产，不是运行必需项。
     */
    GraphPayload extractDrugsFromJson() {
        GraphPayload p = new GraphPayload();
        Path path = Paths.get(drugsJsonPath);
        if (!Files.exists(path)) {
            p.warn("DRUGS_JSON_MISSING: 未找到 " + drugsJsonPath);
            return p;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(text);
            JSONArray drugs = root.getJSONArray("drugs");
            if (drugs == null) {
                p.warn("DRUGS_JSON_EMPTY: drugs 数组为空");
                return p;
            }
            for (int i = 0; i < drugs.size(); i++) {
                JSONObject d = drugs.getJSONObject(i);
                String name = d.getString("name");
                if (name == null || name.trim().isEmpty()) {
                    continue;
                }
                name = name.trim();
                Map<String, Object> props = new LinkedHashMap<>();
                String generic = d.getString("genericName");
                if (generic != null && !generic.isEmpty() && !generic.equals(name)) {
                    props.put("genericName", generic);
                }
                String spec = d.getString("specification");
                if (spec != null) {
                    props.put("specification", spec);
                }
                String manufacturer = d.getString("manufacturer");
                if (manufacturer != null) {
                    props.put("manufacturer", manufacturer);
                }
                Boolean otc = d.getBoolean("isOtc");
                if (otc != null) {
                    props.put("isOtc", otc);
                }
                p.addNode(new GraphNode(name, GraphSchema.Label.DRUG,
                        d.getString("description") == null ? "" : d.getString("description"),
                        null, props, GraphSources.DRUGS_JSON));

                String category = d.getString("category");
                if (category != null && !category.trim().isEmpty()) {
                    p.addNode(GraphNode.of(category.trim(), GraphSchema.Label.CATEGORY,
                            GraphSources.DRUGS_JSON));
                    p.addEdge(Triple.of(name, GraphSchema.Label.DRUG,
                            GraphSchema.Relation.BELONGS_TO, category.trim(),
                            GraphSchema.Label.CATEGORY, GraphSources.DRUGS_JSON));
                }

                // 适应症 → 疾病/症状（规则：描述里出现词表词即建边）
                String desc = d.getString("description");
                if (desc != null && !desc.isEmpty()) {
                    for (GraphNode hit : vocabulary.matchIn(desc)) {
                        if (hit.getLabel() != GraphSchema.Label.DISEASE
                                && hit.getLabel() != GraphSchema.Label.SYMPTOM) {
                            continue;
                        }
                        p.addNode(hit);
                        p.addEdge(Triple.of(hit.getName(), hit.getLabel(),
                                GraphSchema.Relation.TREATED_BY, name, GraphSchema.Label.DRUG,
                                GraphSources.DRUGS_JSON));
                    }
                }
            }
        } catch (IOException e) {
            log.error("[GraphExtractor] drugs.json 读取失败: {}", e.getMessage());
            p.warn("DRUGS_JSON_READ_FAILED: " + e.getMessage());
        } catch (Exception e) {
            log.error("[GraphExtractor] drugs.json 解析失败: {}", e.getMessage());
            p.warn("DRUGS_JSON_PARSE_FAILED: " + e.getMessage());
        }
        return p;
    }

    /**
     * 取商品的医学分类。{@code medical_category} 是 10-06 新增列，
     * VO 里未必有对应字段，因此用反射兜底读取（避免为取一个字段改 VO 影响前端契约）。
     */
    private String medicalCategoryOf(Object vo) {
        try {
            java.lang.reflect.Method m = vo.getClass().getMethod("getMedicalCategory");
            Object v = m.invoke(vo);
            return v == null ? null : String.valueOf(v).trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    // ==================== 6. 文章实体 ====================

    /**
     * news（NEWS 29 篇）→ {@code 文章 -[:MENTIONS]-> 疾病/症状/药品}。
     *
     * <p>纯词表命中，零 LLM。LLM 抽取留到后续离线任务 ——
     * 绝不在 {@code /user/chat} 同步链路里跑（N 次调用会把首字延迟从秒级拖到分钟级）。
     */
    public GraphPayload extractArticles() {
        GraphPayload p = new GraphPayload();
        List<NewsVO> articles;
        try {
            articles = newsMapper.selectAllForRag();
        } catch (Exception e) {
            log.error("[GraphExtractor] news 查询失败: {}", e.getMessage());
            p.warn("NEWS_QUERY_FAILED: " + e.getMessage());
            return p;
        }
        if (articles == null || articles.isEmpty()) {
            p.warn("NEWS_EMPTY");
            return p;
        }
        for (NewsVO a : articles) {
            String title = a.getTitle() != null ? a.getTitle()
                    : (a.getName() == null ? "" : a.getName());
            if (title.trim().isEmpty()) {
                continue;
            }
            String text = title + " " + nullToEmpty(a.getSummary()) + " " + nullToEmpty(a.getContent());
            List<GraphNode> hits = vocabulary.matchIn(text);
            if (hits.isEmpty()) {
                continue;
            }
            int added = 0;
            Map<String, Object> articleProps = new LinkedHashMap<>();
            articleProps.put("newsId", a.getId());
            if (a.getContentType() != null) {
                articleProps.put("contentType", a.getContentType());
            }
            GraphNode article = new GraphNode(title.trim(), GraphSchema.Label.ARTICLE,
                    nullToEmpty(a.getSummary()), null, articleProps, GraphSources.NEWS);
            for (GraphNode hit : hits) {
                if (added >= MAX_ENTITIES_PER_ARTICLE) {
                    break;
                }
                p.addNode(article);
                p.addNode(hit);
                p.addEdge(Triple.of(title.trim(), GraphSchema.Label.ARTICLE,
                        GraphSchema.Relation.MENTIONS, hit.getName(), hit.getLabel(),
                        GraphSources.NEWS));
                added++;
            }
        }
        return p;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 当前批次号（溯源用，格式 yyyyMMdd-HHmmss）。
     */
    static String newBatchId() {
        return java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .format(LocalDateTime.now());
    }
}
