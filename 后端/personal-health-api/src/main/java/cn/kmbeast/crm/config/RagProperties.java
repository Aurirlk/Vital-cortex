package cn.kmbeast.crm.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * RAG 检索链路可调参数（阶段 2 整改）。
 *
 * <h3>术语纠正（本次整改的起因）</h3>
 * <p>需求原文用的是「稀疏/稠密」两个词，但方向说反了。按业界标准语义：</p>
 * <ul>
 *   <li><b>稀疏（sparse）</b>＝词法检索。中文场景下用 MySQL {@code FULLTEXT WITH PARSER ngram}
 *       做 n-gram 倒排，未命中索引时回落 {@code LIKE}。特征：词形精确、可解释、
 *       对疾病名/药品名/缩写这类术语命中强，但对「我最近老头晕」这种口语提问无能为力。</li>
 *   <li><b>稠密（dense）</b>＝向量语义检索。embedding + 余弦相似度。
 *       特征：能跨表述召回（「头晕」↔「眩晕」↔「head spinning」），但对术语拼写不敏感。</li>
 * </ul>
 * <p>本类按<b>标准语义</b>实现（sparse=词法、dense=向量），并把两路权重做成配置项。
 * 这样无论当初的口头表述如何，行为都是行业通行的那个意思，且调参不需要改代码。</p>
 *
 * <h3>为什么权重放在配置里而不是写死 1:1</h3>
 * <p>两路的强弱高度依赖语料：法规/说明书类语料术语密集，稀疏路更准；
 * 科普长文语料表述发散，稠密路更准。写死权重等于赌一个语料，
 * 换一批数据就只能靠改代码。默认仍是 1:1（RRF 的经典配置），保证不调参时行为可预期。</p>
 *
 * <h3>字段初始化器</h3>
 * <p>每个 {@code @Value} 字段都带同值初始化器。{@code @Value} 只在 Spring 容器里生效，
 * 纯单测下会落到 {@code 0}/{@code null} —— 权重变 0 会让融合分数整体塌陷成 0、
 * 排序退化成「先到先得」，且不报任何错。这类「不报错的错」最难查，故双重给定默认值。</p>
 */
@Data
@Component
public class RagProperties {

    // ------------------------------------------------------------------ 融合

    /** 稀疏（词法）路权重。0 表示禁用该路（不是「不参与排序」而是「不参与融合」）。 */
    @Value("${crm.rag.fusion.sparse-weight:1.0}")
    private double sparseWeight = 1.0;

    /** 稠密（向量）路权重。 */
    @Value("${crm.rag.fusion.dense-weight:1.0}")
    private double denseWeight = 1.0;

    /**
     * RRF 平滑常数 k。
     *
     * <p>标准取 60。它的作用是压低头部排名的权重差：{@code 1/(60+1)} 与 {@code 1/(60+2)}
     * 几乎相等，于是「两路都排第 5」比「单路排第 1」得分更高 —— 这正是 RRF 抗单路噪声的原理。</p>
     */
    @Value("${crm.rag.fusion.k:60}")
    private int rrfK = 60;

    /** 每路检索时取候选的倍数（相对最终 topK）。取 2 倍给融合留选择余地。 */
    @Value("${crm.rag.candidate-factor:2}")
    private int candidateFactor = 2;

    // ------------------------------------------------------------------ 各路开关

    /**
     * 是否启用稠密（向量）路。
     *
     * <p>关掉它就是纯词法检索，用来做 A/B 对照 —— 判断「向量路到底有没有在起作用」
     * 时这是唯一干净的实验手段。也可在嵌入服务故障期临时关闭止血。</p>
     */
    @Value("${crm.rag.dense.enabled:true}")
    private boolean denseEnabled = true;

    /**
     * ngram 全文索引不可用时是否回落 {@code LIKE}。
     *
     * <p>默认 true：宁可召回质量降级，也不要整个检索直接空结果。
     * 但回落<b>必须留痕</b>（{@code retrieval_log.path = sparse_like}），
     * 否则系统会长期以「看起来正常」的姿态退化成关键词检索而无人察觉。</p>
     */
    @Value("${crm.rag.sparse.like-fallback-enabled:true}")
    private boolean likeFallbackEnabled = true;

    // ------------------------------------------------------------------ 检索日志

    /**
     * 是否写检索日志。
     *
     * <p>写日志是<b>观测能力</b>的前提：没有 {@code retrieval_log} 就只能猜。
     * 但每问一次写一行，高并发下会成为写热点，故留开关。</p>
     */
    @Value("${crm.rag.retrieval.log-enabled:true}")
    private boolean retrievalLogEnabled = true;

    /** 检索日志保留天数（清理任务用）。 */
    @Value("${crm.rag.retrieval.log-keep-days:30}")
    private int retrievalLogKeepDays = 30;

    // ------------------------------------------------------------------ 灌库

    /** 单文档切块上限：超过此数的文档多半是误传（整本书/日志文件），拒绝以避免拖垮灌库。 */
    @Value("${crm.rag.ingest.max-chunks:5000}")
    private int maxChunksPerDocument = 5000;

    /** 灌库时每批 embedding 的块数。批量太小则请求数暴涨，太大则单次超时/被限流。 */
    @Value("${crm.rag.ingest.batch-size:32}")
    private int ingestBatchSize = 32;

    // ---------------------------------------------------------------- 生效值

    /**
     * 生效的稀疏路权重（负值钳为 0）。
     *
     * <p>负权重在 RRF 里没有意义 —— 它会把「命中的文档」往下压，等于奖励未命中。
     * 钳为 0 而不是抛异常：权重来自配置文件，为一个手滑的负号让服务起不来不划算。</p>
     */
    public double effectiveSparseWeight() {
        return Math.max(0.0, sparseWeight);
    }

    /** 生效的稠密路权重（负值钳为 0）。 */
    public double effectiveDenseWeight() {
        return Math.max(0.0, denseWeight);
    }

    /** 生效的候选倍数（至少 1，否则取不到候选）。 */
    public int effectiveCandidateFactor() {
        return Math.max(1, candidateFactor);
    }

    /** 生效的批次大小（至少 1）。 */
    public int effectiveIngestBatchSize() {
        return Math.max(1, ingestBatchSize);
    }
}
