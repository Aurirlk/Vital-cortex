package cn.kmbeast.core.graph;

/**
 * 三元组溯源标记常量。
 *
 * <p>写进每条边的 {@code source} 属性，用于：
 * <ul>
 *   <li>{@code /graph/stats} 的 {@code sources} 段（哪个来源贡献了多少）</li>
 *   <li>出问题时定位是哪条抽取链路脏了</li>
 *   <li>按批次回滚（配合 {@code importBatch}）</li>
 * </ul>
 *
 * <p>全部从<b>既有表/既有文件</b>抽取，符合「强耦合原则」（不新增任何表）。
 */
public final class GraphSources {

    /** 人工词表（resources/ai-data/seed-triples.json） */
    public static final String SEED = "seed";
    /** patient_profile 的 7 个 JSON 列（慢病/过敏/用药/手术/家族史） */
    public static final String PATIENT_PROFILE_JSON = "patient_profile_json";
    /** user_health（EAV 指标时序，160 条） */
    public static final String USER_HEALTH = "user_health";
    /** visit_record（诊断/处方，3 条） */
    public static final String VISIT_RECORD = "visit_record";
    /** health_model_config（12 项指标字典） */
    public static final String HEALTH_MODEL_CONFIG = "health_model_config";
    /** ai_data/drugs.json（55 条药品说明） */
    public static final String DRUGS_JSON = "drugs_json";
    /** mall_product（107 件药品类商品） */
    public static final String MALL_PRODUCT = "mall_product";
    /** news（NEWS 29 篇资讯） */
    public static final String NEWS = "news";

    private GraphSources() {
    }
}
