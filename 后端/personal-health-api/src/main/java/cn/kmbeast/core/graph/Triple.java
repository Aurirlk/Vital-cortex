package cn.kmbeast.core.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 三元组 —— 知识图谱的<b>唯一中间表示</b>。
 *
 * <p>阶段 0-B 的抽取链路设计为：
 * <pre>
 *   MySQL / drugs.json / seed-triples.json / news
 *        ↓  GraphExtractor（规则，零 LLM）
 *   List&lt;Triple&gt;
 *        ↓  GraphImportService（去重 + 打批次）
 *   ├→ Neo4jGraphWriter   （Neo4j 可用时：MERGE 幂等写）
 *   └→ LocalTripleStore   （无论 Neo4j 是否可用都写：本地兜底 + 成果不丢）
 * </pre>
 *
 * <p>所有抽取器只产出 {@code Triple}，不直接碰 Cypher —— 这样：
 * <ul>
 *   <li>Neo4j 长期未启动（本机现状）时，抽取结果依然可落 {@code triples.json} 并被检索；</li>
 *   <li>写 Cypher 的逻辑只有一处，幂等性只需保证一次；</li>
 *   <li>单测可以只断言三元组，不需要起 Neo4j。</li>
 * </ul>
 *
 * <p>⚠️ {@code subject} / {@code object} 是节点的 {@code name}，
 * 这是 {@link GraphRAG} 的硬契约，不可为空。
 */
public class Triple {

    private final String subject;
    private final GraphSchema.Label subjectLabel;
    private final GraphSchema.Relation relation;
    private final String object;
    private final GraphSchema.Label objectLabel;

    /** 关系上的属性（如 HAS_MEASUREMENT 的 value / time）。可为 null。 */
    private final Map<String, Object> props;

    /** 溯源标记：patient_profile_json / user_health / visit_record / drugs_json / seed / news */
    private final String source;

    public Triple(String subject,
                  GraphSchema.Label subjectLabel,
                  GraphSchema.Relation relation,
                  String object,
                  GraphSchema.Label objectLabel,
                  String source) {
        this(subject, subjectLabel, relation, object, objectLabel, null, source);
    }

    public Triple(String subject,
                  GraphSchema.Label subjectLabel,
                  GraphSchema.Relation relation,
                  String object,
                  GraphSchema.Label objectLabel,
                  Map<String, Object> props,
                  String source) {
        this.subject = subject;
        this.subjectLabel = subjectLabel;
        this.relation = relation;
        this.object = object;
        this.objectLabel = objectLabel;
        this.props = props == null || props.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(props));
        this.source = source;
    }

    // ==================== 工厂 ====================

    public static Triple of(String subject, GraphSchema.Label subjectLabel,
                            GraphSchema.Relation relation,
                            String object, GraphSchema.Label objectLabel,
                            String source) {
        return new Triple(subject, subjectLabel, relation, object, objectLabel, null, source);
    }

    /**
     * 带关系属性的三元组。
     *
     * <p>⚠️ 属性 Map 会在这里被<b>拷贝一份</b>（{@code LinkedHashMap}），
     * 调用方之后修改原 Map 不会影响已构造的 Triple —— 避免"复用同一个 Map 循环 add"
     * 这种经典 Bug（最终所有边的属性都等于最后一次的值）。
     */
    public static Triple with(String subject, GraphSchema.Label subjectLabel,
                              GraphSchema.Relation relation,
                              String object, GraphSchema.Label objectLabel,
                              Map<String, Object> props,
                              String source) {
        return new Triple(subject, subjectLabel, relation, object, objectLabel, props, source);
    }

    // ==================== 访问 ====================

    public String getSubject() {
        return subject;
    }

    public GraphSchema.Label getSubjectLabel() {
        return subjectLabel;
    }

    public GraphSchema.Relation getRelation() {
        return relation;
    }

    public String getObject() {
        return object;
    }

    public GraphSchema.Label getObjectLabel() {
        return objectLabel;
    }

    public Map<String, Object> getProps() {
        return props;
    }

    public String getSource() {
        return source;
    }

    /**
     * 幂等键：同一条（主体-关系-客体）只写一次。
     *
     * <p>不含 props —— {@code HAS_MEASUREMENT} 的 value 会随时间变，
     * 但"患者X 测过 收缩压"这条边只应存在一条（属性最后更新）。
     * 若把 value 计入幂等键，160 条测量会生成 160 条边，图会爆炸。
     */
    public String key() {
        return subjectLabel.code() + "|" + subject + "|"
                + relation.code() + "|"
                + objectLabel.code() + "|" + object;
    }

    /**
     * 参数合法性校验。抽取器产出的脏数据（空名称）必须在这里被拦下 ——
     * 一旦写进 Neo4j，{@code MERGE} 会造出一个 {@code name=null} 的僵尸节点，
     * 而 {@link GraphRAG} 的 {@code WHERE n.name=$name} 永远匹配不到它。
     */
    public boolean isValid() {
        return subject != null && !subject.trim().isEmpty()
                && object != null && !object.trim().isEmpty()
                && subjectLabel != null
                && objectLabel != null
                && relation != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Triple other)) {
            return false;
        }
        return Objects.equals(subject, other.subject)
                && subjectLabel == other.subjectLabel
                && relation == other.relation
                && Objects.equals(object, other.object)
                && objectLabel == other.objectLabel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, subjectLabel, relation, object, objectLabel);
    }

    @Override
    public String toString() {
        return "(" + subjectLabel.code() + ":" + subject + ")-["
                + relation.code() + "]->(" + objectLabel.code() + ":" + object + ")";
    }
}
