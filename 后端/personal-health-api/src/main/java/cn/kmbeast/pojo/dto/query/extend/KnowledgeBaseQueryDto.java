package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库查询 DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class KnowledgeBaseQueryDto extends QueryDto {

    private Integer id;

    /** 知识库标识（模糊） */
    private String code;

    /** 展示名（模糊） */
    private String name;

    /** 适用端 */
    private String audience;

    /** 是否启用 */
    private Boolean enabled;
}
