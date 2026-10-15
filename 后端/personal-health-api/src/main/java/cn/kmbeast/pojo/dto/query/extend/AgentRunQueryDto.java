package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 智能体运行记录查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AgentRunQueryDto extends QueryDto {

    /**
     * 本次运行的唯一标识
     */
    private String runId;

    /**
     * 父运行标识
     */
    private String parentRunId;

    /**
     * 智能体标识
     */
    private String agentKey;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 运行状态
     */
    private String status;
}
