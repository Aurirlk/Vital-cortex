package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.AiChatRecordQueryDto;
import cn.kmbeast.pojo.dto.update.AiChatRequest;

import java.util.List;
import java.util.Map;

public interface AiService {

    Result<Map<String, String>> chat(AiChatRequest chatRequest, Integer userId);

    void chatStream(AiChatRequest chatRequest, Integer userId, StreamCallback callback);

    Result<?> queryRecords(AiChatRecordQueryDto queryDto);

    Result<Map<String, Object>> getStats();

    /**
     * 提取关键词（用于RAG知识库搜索）
     */
    List<String> extractKeywords(String userMessage);

    @FunctionalInterface
    interface StreamCallback {
        void onEvent(String eventName, String jsonData);
    }
}
