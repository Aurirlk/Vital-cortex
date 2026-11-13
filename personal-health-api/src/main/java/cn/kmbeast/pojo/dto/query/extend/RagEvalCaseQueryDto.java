package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RAG 金标用例查询 DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class RagEvalCaseQueryDto extends QueryDto {

    private Integer id;

    /** 问题（模糊） */
    private String question;

    /** 金标粒度：chunk / doc */
    private String matchLevel;

    /** 适用端 */
    private String audience;

    /** 是否启用 */
    private Boolean enabled;
}
