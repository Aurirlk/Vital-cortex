package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 检索轨迹日志（追加型事件表）。
 *
 * <p>只在离线评测里算指标是不够的：真实流量里的失败
 * （向量服务超时导致的单路退化、某个知识库没有块、查询词全是停用词）
 * 在离线集里一条都测不出来。这张表是那些失败唯一能浮出水面的地方。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RetrievalLog {

    /** 主键 */
    private Long id;

    /** 检索词原文（截断存） */
    private String queryText;

    /** 调用端 */
    private String audience;

    /** 提问用户 id */
    private Integer userId;

    /** 参与检索的知识库 id，逗号分隔 */
    private String kbIds;

    /** 请求的召回条数 */
    private Integer topK;

    /** 稀疏路原始命中数 */
    private Integer sparseHits;

    /** 稠密路原始命中数 */
    private Integer denseHits;

    /** 融合后返回条数 */
    private Integer fusedHits;

    /** 两路同时命中的条数 */
    private Integer bothHits;

    /** 实际走的路径：hybrid / sparse_only / dense_only / none */
    private String path;

    /** 检索耗时毫秒 */
    private Integer latencyMs;

    /** 返回结果的 {id,score,source} 列表（截断存） */
    private String topJson;

    /** 是否命中检索缓存 */
    private Boolean fromCache;

    /** 创建时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
