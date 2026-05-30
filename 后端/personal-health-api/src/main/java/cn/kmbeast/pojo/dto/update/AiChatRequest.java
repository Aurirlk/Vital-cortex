package cn.kmbeast.pojo.dto.update;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiChatRequest {

    private Integer conversationId;

    private String message;

    private String role;

    private Double temperature;

    private Double topP;

    private List<String> files;

    private Integer userId;

    private Map<String, Object> context;

    private Double repetitionPenalty;

    private Integer contextRounds;

    private Integer maxReplyLength;

    private Integer maxReasoningLength;

    private Boolean longMemory;
}
