package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RAG 评测运行记录（追加型事件表）。
 *
 * <p>保留历次运行是为了看趋势：改了分块大小/权重以后，召回率是涨了还是跌了。
 * 只留最新一次的话，「优化」就成了不可证伪的说法。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagEvalRun {

    /** 主键 */
    private Long id;

    /** 本次评测的唯一 id */
    private String runId;

    /** 参与评测的知识库，逗号分隔 */
    private String kbIds;

    /** 评测所用端 */
    private String audience;

    /** 评测截断深度 k */
    private Integer topK;

    /** 实际评测的题目数 */
    private Integer caseTotal;

    /** 至少命中一条金标的题目数 */
    private Integer caseHit;

    /** 召回率（宏平均） */
    private BigDecimal recall;

    /** 准确率 @k（宏平均） */
    private BigDecimal precisionAtK;

    /** 平均倒数排名 */
    private BigDecimal mrr;

    /** 命中率 */
    private BigDecimal hitRate;

    /** 单题平均检索耗时 */
    private Integer avgLatencyMs;

    /** 退化为仅稀疏路的题数 */
    private Integer sparseOnly;

    /** 逐题明细 JSON */
    private String detailJson;

    /** 触发方式：manual / scheduled */
    private String triggerBy;

    /** 创建时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
