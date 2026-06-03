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

    /**
     * 是否启用联网搜索
     */
    private Boolean enableWebSearch;

    /**
     * 是否启用深度思考
     */
    private Boolean enableDeepThink;

    /**
     * 是否启用知识库参考（平台文章）
     */
    private Boolean enableKnowledgeBase;

    /**
     * 是否读取用户健康指标数据
     */
    private Boolean enableHealthData;

    /**
     * 前端提取的关键词列表（用于RAG搜索）
     */
    private List<String> keywords;
}
