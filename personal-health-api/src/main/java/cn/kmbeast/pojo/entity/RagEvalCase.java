package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * RAG 金标评测用例。
 *
 * <p>{@link #expectedRefs} 是「真正的召回率」唯一可能的算法基础：
 * 召回率 = |检索结果 ∩ 金标| / |金标|。没有金标就只能算
 * 「返回了几条」这类与质量无关的假指标。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagEvalCase {

    /** 主键 */
    private Integer id;

    /** 评测问题 */
    private String question;

    /** 期望命中的 id 列表，逗号分隔 */
    private String expectedRefs;

    /** 金标粒度：chunk / doc */
    private String matchLevel;

    /** 本题限定的知识库 id，逗号分隔 */
    private String kbIds;

    /** 适用端 */
    private String audience;

    /** 参考答案里必须出现的词，逗号分隔 */
    private String expectedKeywords;

    /** 备注 */
    private String remark;

    /** 是否参与评测 */
    private Boolean enabled;

    /** 创建时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
