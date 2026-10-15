package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识库。
 *
 * <p>分块参数（{@link #chunkSize}/{@link #chunkOverlap}）与检索参数
 * （{@link #topK}/{@link #weight}）<b>落在库上而非全局配置</b>：
 * 法规条文要小块精确定位、科普长文要大块保上下文，
 * 全局一个值必然有一半场景是错的。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeBase {

    /** 主键 */
    private Integer id;

    /** 知识库唯一标识（同时用作向量库 collection 名后缀） */
    private String code;

    /** 展示名 */
    private String name;

    /** 用途说明 */
    private String description;

    /** 知识块存放的数据源；为空视为本应用主库 */
    private Integer dataSourceId;

    /** 适用端：user / doctor / admin / all */
    private String audience;

    /** 分块目标长度（字符） */
    private Integer chunkSize;

    /** 相邻块重叠长度（字符） */
    private Integer chunkOverlap;

    /** 本库默认召回条数 */
    private Integer topK;

    /** 多库融合时的权重 */
    private BigDecimal weight;

    /** 本库使用的嵌入模型（为空用全局） */
    private String embeddingModel;

    /** 是否启用 */
    private Boolean enabled;

    /** 已入库文档数（冗余计数） */
    private Integer docCount;

    /** 已入库块数（冗余计数） */
    private Integer chunkCount;

    /** 创建时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
