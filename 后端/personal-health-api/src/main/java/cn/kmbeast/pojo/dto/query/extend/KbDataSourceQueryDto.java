package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库数据源查询 DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class KbDataSourceQueryDto extends QueryDto {

    private Integer id;

    /** 数据源标识（模糊） */
    private String code;

    /** 展示名（模糊） */
    private String name;

    /** 类型：local / remote */
    private String type;

    /** 是否启用 */
    private Boolean enabled;
}
