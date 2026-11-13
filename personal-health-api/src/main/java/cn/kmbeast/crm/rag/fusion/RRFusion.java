package cn.kmbeast.crm.rag.fusion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 加权 RRF（Reciprocal Rank Fusion）融合。
 *
 * <h3>为什么用 RRF 而不是「分数归一化后加权求和」</h3>
 * <p>两路的分数不在同一量纲上：稀疏路是 MySQL 的 {@code MATCH...AGAINST} 相关度
 * （无上界、受文档长度影响），稠密路是余弦相似度（[-1,1]）。想加权求和就必须先归一化，
 * 而归一化的方式（min-max？z-score？除以 top1？）都会引入一个新的、无法验证的假设。</p>
 * <p>RRF 只用<b>排名</b>，不看分数量纲：{@code score(d) = Σ weight_r / (k + rank_r(d))}。
 * 它换来的好处是「两路都进前 10」天然强于「单路第 1」——
 * 两个互相独立的方法同时认为某文档相关，这个信号比任何单路的高分都可靠。</p>
 *
 * <h3>本类的三个设计决定</h3>
 * <ol>
 *   <li><b>纯函数、无依赖</b>：不注入任何 Bean，输入输出都是普通对象。
 *       融合是整条链路里最容易被「调参调坏」的一环，必须能脱离容器单测。</li>
 *   <li><b>保留名次来源</b>：{@link FusedItem} 同时带回 {@code sparseRank}/{@code denseRank}。
 *       这既让 {@code both_hits} 这类统计指标可以直接算出来，也让排查
 *       「这条结果是谁召回的」不必再跑一遍检索。</li>
 *   <li><b>确定性</b>：同输入必得同输出。排序只在分数上做稳定排序，
 *       分数相同时保持首次出现顺序（稀疏在先、稠密在后），不引入随机或哈希序。</li>
 * </ol>
 */
public final class RRFusion {

    /** RRF 标准平滑常数。 */
    public static final int DEFAULT_K = 60;

    private RRFusion() {
    }

    /**
     * 一条融合结果。
     *
     * <p>{@code key} 是调用方给的稳定标识（本项目里是 {@code kb_chunk.id} 的字符串形式）——
     * 融合层不关心它是什么，只要求同一文档在两路里给同一个 key。</p>
     */
    public static final class FusedItem {

        private final String key;
        private final double score;
        private final int sparseRank;
        private final int denseRank;

        FusedItem(String key, double score, int sparseRank, int denseRank) {
            this.key = key;
            this.score = score;
            this.sparseRank = sparseRank;
            this.denseRank = denseRank;
        }

        public String getKey() {
            return key;
        }

        public double getScore() {
            return score;
        }

        /** 在稀疏路里的名次（1 起）；0 表示该路未召回。 */
        public int getSparseRank() {
            return sparseRank;
        }

        /** 在稠密路里的名次（1 起）；0 表示该路未召回。 */
        public int getDenseRank() {
            return denseRank;
        }

        public boolean fromSparse() {
            return sparseRank > 0;
        }

        public boolean fromDense() {
            return denseRank > 0;
        }

        /** 两路都召回 —— RRF 里最值得信任的一类结果。 */
        public boolean fromBoth() {
            return sparseRank > 0 && denseRank > 0;
        }

        /**
         * 来源标记，与 {@code HybridRetriever.RetrievedDoc.source} 的取值保持一致，
         * 便于前端与既有代码沿用同一套展示逻辑。
         */
        public String source() {
            if (fromBoth()) {
                return "both";
            }
            return fromSparse() ? "sparse" : "dense";
        }

        @Override
        public String toString() {
            return "FusedItem{key=" + key + ", score=" + score
                    + ", sparseRank=" + sparseRank + ", denseRank=" + denseRank + '}';
        }
    }

    /**
     * 加权 RRF 融合。
     *
     * @param sparseKeys   稀疏路结果 key，按名次排列（index 0 为第 1 名）；可为 null
     * @param denseKeys    稠密路结果 key，按名次排列；可为 null
     * @param sparseWeight 稀疏路权重（负值钳为 0）
     * @param denseWeight  稠密路权重（负值钳为 0）
     * @param k            RRF 平滑常数，&le;0 时取 {@link #DEFAULT_K}
     * @param topK         返回条数；&le;0 表示不截断
     * @return 按融合分数降序的结果（永不返回 null）
     */
    public static List<FusedItem> fuse(List<String> sparseKeys, List<String> denseKeys,
                                       double sparseWeight, double denseWeight,
                                       int k, int topK) {
        double sw = Math.max(0.0, sparseWeight);
        double dw = Math.max(0.0, denseWeight);
        // 双权重都为 0 时退化为均权：此时所有分数恒为 0，排序完全由「谁先出现」决定，
        // 结果看似正常实则毫无意义。宁可回到经典 1:1，也不要这种静默失效。
        if (sw == 0.0 && dw == 0.0) {
            sw = 1.0;
            dw = 1.0;
        }
        int kk = k <= 0 ? DEFAULT_K : k;

        Map<String, Double> scores = new java.util.LinkedHashMap<>();
        Map<String, Integer> sparseRank = new HashMap<>();
        Map<String, Integer> denseRank = new HashMap<>();

        absorb(sparseKeys, sw, kk, sparseRank, scores);
        absorb(denseKeys, dw, kk, denseRank, scores);

        List<FusedItem> items = new ArrayList<>(scores.size());
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            String key = entry.getKey();
            items.add(new FusedItem(key, entry.getValue(),
                    sparseRank.getOrDefault(key, 0),
                    denseRank.getOrDefault(key, 0)));
        }

        // 只按分数降序（稳定排序）：同分时保持 scores 的插入顺序 = 稀疏先、稠密后。
        // 不额外按 key 排序——那会把两路的次序打乱，反而更难解释「为什么这条在前」。
        items.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        if (topK > 0 && items.size() > topK) {
            return new ArrayList<>(items.subList(0, topK));
        }
        return items;
    }

    /** 累加一路的 RRF 贡献。 */
    private static void absorb(List<String> keys, double weight, int k,
                               Map<String, Integer> rankMap, Map<String, Double> scores) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        int rank = 1;
        for (String key : keys) {
            if (key == null || key.isEmpty()) {
                continue;
            }
            // 同一路内重复：保留首次（最好）名次，且不为重复项消耗名次 ——
            // 与 RetrievalMetrics 的去重口径保持一致，否则评测指标和线上排序会对不上。
            if (rankMap.containsKey(key)) {
                continue;
            }
            rankMap.put(key, rank);
            scores.merge(key, weight / (k + rank), Double::sum);
            rank++;
        }
    }

    /** 两路都召回的条数（对应 {@code retrieval_log.both_hits}）。 */
    public static int countBoth(List<FusedItem> items) {
        if (items == null) {
            return 0;
        }
        int n = 0;
        for (FusedItem item : items) {
            if (item.fromBoth()) {
                n++;
            }
        }
        return n;
    }

    /**
     * 把多路「带分数的候选」合并成一条降序 key 列表（同 key 取最高分）。
     *
     * <p>用于稠密路的跨知识库合并：向量库是<b>按知识库分集合</b>存的，
     * 一次查询要打多个集合，各自返回自己内部的 topN。必须先合成一条统一名次
     * 才好交给 RRF —— 否则「A 库第 1 名」和「B 库第 1 名」会同时占据第 1 名，
     * RRF 的排名语义直接失效。</p>
     *
     * @param scoredKeys key → 分数（分数越高越相关）；插入顺序用于同分时定序
     * @return 按分数降序的 key 列表（同分保持插入顺序）
     */
    public static List<String> mergeByScoreDesc(Map<String, Double> scoredKeys) {
        List<String> keys = new ArrayList<>();
        if (scoredKeys == null || scoredKeys.isEmpty()) {
            return keys;
        }
        for (Map.Entry<String, Double> entry : scoredKeys.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                keys.add(entry.getKey());
            }
        }
        keys.sort((a, b) -> Double.compare(scoredKeys.get(b), scoredKeys.get(a)));
        return keys;
    }
}
