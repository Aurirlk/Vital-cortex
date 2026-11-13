package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 智能体配置查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AgentConfigQueryDto extends QueryDto {

    /**
     * ID
     */
    private Integer id;

    /**
     * 智能体唯一标识
     */
    private String agentKey;

    /**
     * 智能体名称
     */
    private String name;

    /**
     * 面向人群
     */
    private String audience;

    /**
     * 是否启用
     */
    private Boolean enabled;
}
