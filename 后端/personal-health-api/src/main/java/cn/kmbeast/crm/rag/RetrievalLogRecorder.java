package cn.kmbeast.crm.rag;

import cn.kmbeast.crm.config.RagProperties;
import cn.kmbeast.mapper.RetrievalLogMapper;
import cn.kmbeast.pojo.entity.RetrievalLog;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索日志的记录与汇总（召回/精确度统计的数据来源）。
 *
 * <h3>这个类解决的是「看不见」的问题</h3>
 * <p>改造前，RAG 链路只有一个「检索了 N 条」的日志行。于是下面这些问题一个都答不上来：
 * 向量路到底有没有在工作？稀疏路是不是一直在走 LIKE 兜底？并发上来后延迟是多少？
 * 召回为空的比例有多高？—— 不是难以回答，而是<b>没有任何数据</b>。</p>
 *
 * <h3>写日志绝不能影响检索本身</h3>
 * <p>所有写入都吞掉异常。理由是这个模块的价值排序非常明确：检索结果 &gt; 观测数据。
 * 一条日志写不进去（表不存在、磁盘满、字段超长）如果冒泡出去，
 * 会把一次本来成功的检索变成用户可见的失败——为了记录「发生了什么」而制造新的故障，
 * 是本末倒置。失败只留 warn，且失败率本身可以通过应用日志发现。</p>
 */
@Slf4j
@Service
public class RetrievalLogRecorder {

    /** {@code top_json} 列宽（VARCHAR(2000)）：序列化前主动截断，不依赖数据库报错。 */
    private static final int TOP_JSON_MAX = 2000;

    /** 明细里保留的头部条数。 */
    private static final int TOP_KEEP = 5;

    @Resource
    private RetrievalLogMapper retrievalLogMapper;

    @Resource
    private RagProperties ragProperties;

    /**
     * 记录一次检索。
     *
     * @param query       原始问题
     * @param audience    受众（doctor / user / ...）
     * @param userId      发起用户
     * @param kbIds       本次限定的知识库
     * @param topK        请求条数
     * @param sparseHits  稀疏路召回条数
     * @param denseHits   稠密路召回条数
     * @param bothHits    两路都召回的条数
     * @param fusedCount  融合后最终返回条数
     * @param path        路径标记：hybrid / sparse_only / dense_only / sparse_like / none
     * @param latencyMs   耗时
     * @param tops        头部明细（可为 null）
     */
    public void record(String query, String audience, Integer userId, List<Integer> kbIds,
                       int topK, int sparseHits, int denseHits, int bothHits, int fusedCount,
                       String path, long latencyMs, List<Map<String, Object>> tops) {
        if (!ragProperties.isRetrievalLogEnabled()) {
            return;
        }
        try {
            RetrievalLog entity = new RetrievalLog();
            entity.setQueryText(truncate(query, 500));
            entity.setAudience(audience == null ? "" : audience);
            entity.setUserId(userId);
            entity.setKbIds(joinIds(kbIds));
            entity.setTopK(topK);
            entity.setSparseHits(sparseHits);
            entity.setDenseHits(denseHits);
            entity.setFusedHits(fusedCount);
            entity.setBothHits(bothHits);
            entity.setPath(path);
            entity.setLatencyMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, latencyMs)));
            entity.setTopJson(serializeTops(tops));
            entity.setFromCache(false);
            retrievalLogMapper.save(entity);
        } catch (Exception e) {
            log.warn("[RAG-log] 检索日志写入失败（不影响本次检索）: {}", e.getMessage());
        }
    }

    /**
     * 总览：各路径占比、平均延迟、空结果占比。
     *
     * @param days 统计最近 N 天；&le;0 表示全量
     */
    public Map<String, Object> summarize(int days) {
        try {
            Map<String, Object> raw = retrievalLogMapper.summarize(since(days), null);
            Map<String, Object> out = new LinkedHashMap<>();
            if (raw == null) {
                raw = Collections.emptyMap();
            }
            long total = longOf(raw.get("total"));
            out.put("total", total);
            out.put("hybrid", longOf(raw.get("hybrid")));
            out.put("sparseOnly", longOf(raw.get("sparseOnly")));
            out.put("denseOnly", longOf(raw.get("denseOnly")));
            out.put("emptyResult", longOf(raw.get("emptyResult")));
            out.put("sparseLikeFallback", longOf(raw.get("sparseLikeFallback")));
            out.put("avgLatency", round1(doubleOf(raw.get("avgLatency"))));
            // 退化率：稀疏兜底占比。这个数长期偏高说明向量路其实一直没在工作，
            // 系统表面正常，只是悄悄退化成了一台关键词检索。
            out.put("degradeRate", total == 0 ? 0.0
                    : round4(longOf(raw.get("sparseLikeFallback")) / (double) total));
            return out;
        } catch (Exception e) {
            // 迁移脚本未执行时表不存在，这里必须回落而不是让管理页 500
            log.warn("[RAG-log] 检索日志汇总失败（11_knowledge_base.sql 是否已执行？）: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** 按路径分组的统计。 */
    public List<Map<String, Object>> byPath(int days) {
        try {
            List<Map<String, Object>> rows = retrievalLogMapper.aggregateByPath(since(days), null);
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception e) {
            log.warn("[RAG-log] 检索日志分组统计失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 清理过期日志。
     *
     * @return 删除条数（失败返回 0）
     */
    public int purgeExpired() {
        try {
            return retrievalLogMapper.deleteBefore(since(ragProperties.getRetrievalLogKeepDays()));
        } catch (Exception e) {
            log.warn("[RAG-log] 清理过期检索日志失败: {}", e.getMessage());
            return 0;
        }
    }

    /** 统计起点：最近 N 天；N &le; 0 时返回 null（全量）。 */
    private static LocalDateTime since(int days) {
        return days <= 0 ? null : LocalDate.now().minusDays(days).atStartOfDay();
    }

    private static String joinIds(List<Integer> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Integer id : kbIds) {
            if (id == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return truncate(sb.toString(), 255);
    }

    private static String serializeTops(List<Map<String, Object>> tops) {
        if (tops == null || tops.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> head = tops.size() > TOP_KEEP ? tops.subList(0, TOP_KEEP) : tops;
        try {
            return truncate(JSON.toJSONString(head), TOP_JSON_MAX);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static long longOf(Object v) {
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    private static double doubleOf(Object v) {
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
