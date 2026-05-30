package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI聊天记录表
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiChatRecord {

    /**
     * ID
     */
    private Integer id;

    /**
     * 会话ID
     */
    private Integer conversationId;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 发送者角色：user / assistant
     */
    private String role;

    /**
     * 聊天内容
     */
    private String content;

    /**
     * AI角色类型：doctor / nutritionist / psychologist / analyst / general_assistant
     */
    private String agentType;

    /**
     * 创建时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
