package cn.kmbeast.core.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 知识图谱 Schema —— 节点标签、关系类型、约束 DDL 的唯一定义处。
 *
 * <p><b>2026-10-07 新增（阶段 0-B / B1）</b>。此前项目里"知识图谱"只有一个
 * {@link GraphRAG}，它假设图谱里已经有数据（{@code MATCH (n) RETURN n.name}），
 * 但全项目<b>没有任何地方定义过节点标签、关系类型或约束</b>，
 * 也没有任何写入路径 —— 于是 {@code cache/knowledge_graph/} 至今是空目录、图谱 0 个节点。
 *
 * <h3>硬性契约（改动前必读）</h3>
 * <ol>
 *   <li><b>所有节点必须带 {@code name} 属性</b> —— {@link GraphRAG#extractEntities(String)}
 *       用 {@code MATCH (n) RETURN n.name} 拉全量实体名做包含匹配，
 *       {@link GraphRAG#queryRelatedKnowledge(String)} 用 {@code WHERE n.name=$name}。
 *       缺 name 的节点等于不存在。</li>
 *   <li><b>关系类型必须取自 {@link Relation}</b> —— 不得在业务代码里硬编码字符串，
 *       否则 Cypher 拼错在运行期才炸。</li>
 *   <li><b>约束全部 {@code IF NOT EXISTS}</b> —— 导入必须幂等，
 *       "连跑两次节点数不变"是阶段 0-B 的验收标准。</li>
 * </ol>
 *
 * <p>语法按 <b>Neo4j 5.x</b>（驱动 5.28.x）。Neo4j 4.4 的
 * {@code CREATE CONSTRAINT ... ON (d:Disease) ASSERT d.name IS UNIQUE} 在 5.x 已废弃，
 * 本项目不再兼容。
 */
public final class GraphSchema {

    private GraphSchema() {
    }

    // ==================== 节点标签 ====================

    /**
     * 节点标签。
     *
     * <p>{@code code()} 即 Cypher 里的标签名。<b>不要</b>用 {@code name()} ——
     * {@code Enum.name()} 在重构改名时会变，而标签名一旦写进图库就是数据，改了就查不到。
     */
    public enum Label {
        /** 疾病（高血压、2型糖尿病…） */
        DISEASE("Disease"),
        /** 症状（头痛、咳嗽…） */
        SYMPTOM("Symptom"),
        /** 药品通用名（苯磺酸氨氯地平片…） */
        DRUG("Drug"),
        /** 健康指标（收缩压、空腹血糖…），来自 health_model_config */
        INDICATOR("Indicator"),
        /** 过敏原 */
        ALLERGEN("Allergen"),
        /** 手术/术式 */
        SURGERY("Surgery"),
        /** 患者（本项目内的档案持有者） */
        PATIENT("Patient"),
        /** 就诊记录 */
        VISIT("Visit"),
        /** 商城商品（药品的可购买实体） */
        PRODUCT("Product"),
        /** 药品/商品分类 */
        CATEGORY("Category"),
        /** 健康资讯文章 */
        ARTICLE("Article");

        private final String code;

        Label(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        /**
         * Cypher 标签字符串 → 枚举（大小写不敏感）。
         *
         * <p>Neo4j 的 {@code labels(n)} 返回的是字符串列表。本地三元组落盘时
         * 也存的是 code，故两侧可共用这一个还原方法。
         */
        public static java.util.Optional<Label> of(String code) {
            if (code == null) {
                return java.util.Optional.empty();
            }
            for (Label l : values()) {
                if (l.code.equalsIgnoreCase(code.trim())) {
                    return java.util.Optional.of(l);
                }
            }
            return java.util.Optional.empty();
        }
    }

    // ==================== 关系类型 ====================

    /**
     * 关系类型。
     *
     * <p>方向是固定的，写入时不得反转 —— {@link GraphRAG} 查的是
     * {@code (n)-[r]->(m)} 单向一跳。
     */
    public enum Relation {
        /** 患者 → 指标：有过测量（关系上带 value / time） */
        HAS_MEASUREMENT("HAS_MEASUREMENT"),
        /** 患者 → 疾病：确诊 / 慢性病史 */
        HAS_DIAGNOSIS("HAS_DIAGNOSIS"),
        /** 患者 → 过敏原：过敏 */
        ALLERGIC_TO("ALLERGIC_TO"),
        /** 患者 → 药品：正在服用 */
        TAKES_DRUG("TAKES_DRUG"),
        /** 患者 → 手术：既往手术史 */
        HAD_SURGERY("HAD_SURGERY"),
        /** 患者 → 疾病：家族病史 */
        FAMILY_HISTORY_OF("FAMILY_HISTORY_OF"),
        /** 患者 → 就诊：有过就诊 */
        HAD_VISIT("HAD_VISIT"),
        /** 就诊 → 疾病：诊断结果 */
        DIAGNOSED_WITH("DIAGNOSED_WITH"),
        /** 就诊 → 药品：处方 */
        PRESCRIBED("PRESCRIBED"),
        /** 疾病 → 药品：可用药物治疗 */
        TREATED_BY("TREATED_BY"),
        /** 疾病 → 症状：临床表现 */
        HAS_SYMPTOM("HAS_SYMPTOM"),
        /** 药品 → 商品：在商城有售 */
        SOLD_AS("SOLD_AS"),
        /** 药品 → 分类：归属 */
        BELONGS_TO("BELONGS_TO"),
        /** 文章 → 疾病/药品/症状：文中提及 */
        MENTIONS("MENTIONS"),
        /** 子类 → 父类：如"2型糖尿病"-[:IS_A]->"糖尿病"，支撑按父类泛化检索 */
        IS_A("IS_A");

        private final String code;

        Relation(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        /**
         * Cypher 关系类型字符串 → 枚举（大小写不敏感）。
         *
         * <p>存在理由：Neo4j 返回的 {@code type(r)} 是运行期字符串，
         * 业务侧（如 {@code QueryGraphTool}）要把它还原成枚举才能复用
         * {@link Triple} 的强类型结构。识别不了必须返回 empty，
         * 由调用方决定跳过还是报错 —— 绝不允许"猜一个近似值"。
         */
        public static java.util.Optional<Relation> of(String code) {
            if (code == null) {
                return java.util.Optional.empty();
            }
            for (Relation r : values()) {
                if (r.code.equalsIgnoreCase(code.trim())) {
                    return java.util.Optional.of(r);
                }
            }
            return java.util.Optional.empty();
        }
    }

    // ==================== 约束 ====================

    /**
     * 需要唯一性约束的标签（MERGE 幂等导入的基础）。
     *
     * <p>{@code Product} / {@code Visit} 不建唯一约束：{@code Product} 的名字可重复
     * （同名不同规格），{@code Visit} 的 name 是"就诊#id"本就全局唯一但无需约束；
     * 二者都改用 {@code MERGE on id}。此处只列"按 name 唯一"的标签。
     */
    private static final List<Label> UNIQUE_BY_NAME = Arrays.asList(
            Label.DISEASE,
            Label.SYMPTOM,
            Label.DRUG,
            Label.INDICATOR,
            Label.ALLERGEN,
            Label.SURGERY,
            Label.PATIENT,
            Label.VISIT,
            Label.CATEGORY,
            Label.ARTICLE
    );

    /**
     * 生成全部约束 DDL（幂等，可重复执行）。
     *
     * <p>唯一约束在 Neo4j 5.x 会<b>自动附带一个索引</b>，因此无需再单独
     * {@code CREATE INDEX ... ON (n.name)} —— 那会报
     * "There already exists an index called ..."（同名冲突）。
     *
     * @return 可直接执行的 Cypher 列表
     */
    public static List<String> constraintStatements() {
        List<String> stmts = new ArrayList<>();
        for (Label label : UNIQUE_BY_NAME) {
            stmts.add("CREATE CONSTRAINT " + constraintName(label)
                    + " IF NOT EXISTS FOR (n:" + label.code() + ") REQUIRE n.name IS UNIQUE");
        }
        return Collections.unmodifiableList(stmts);
    }

    /**
     * 约束名。Neo4j 5.x 允许 {@code IF NOT EXISTS} 时省略名字，但显式命名便于
     * 后续按名 {@code DROP}（回滚用）。
     */
    public static String constraintName(Label label) {
        return label.code().toLowerCase() + "_name_unique";
    }

    /**
     * 全部标签的 Cypher 名（供 {@code /graph/stats} 统计与测试断言使用）。
     */
    public static List<String> allLabelCodes() {
        List<String> out = new ArrayList<>();
        for (Label l : Label.values()) {
            out.add(l.code());
        }
        return out;
    }

    /**
     * 全部关系类型的 Cypher 名。
     */
    public static List<String> allRelationCodes() {
        List<String> out = new ArrayList<>();
        for (Relation r : Relation.values()) {
            out.add(r.code());
        }
        return out;
    }
}
