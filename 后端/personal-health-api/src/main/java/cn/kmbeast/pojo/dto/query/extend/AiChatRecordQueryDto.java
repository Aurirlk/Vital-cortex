package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI聊天记录查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AiChatRecordQueryDto extends QueryDto {

    /**
     * 会话ID
     */
    private Integer conversationId;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * AI角色类型
     */
    private String agentType;
}
