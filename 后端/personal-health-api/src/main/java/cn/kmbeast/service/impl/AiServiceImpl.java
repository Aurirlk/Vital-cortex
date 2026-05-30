package cn.kmbeast.service.impl;

import cn.kmbeast.config.AiConfig;
import cn.kmbeast.config.AiPromptConfig;
import cn.kmbeast.mapper.AiChatRecordMapper;
import cn.kmbeast.mapper.NewsMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.AiChatRecordQueryDto;
import cn.kmbeast.pojo.dto.update.AiChatRequest;
import cn.kmbeast.pojo.entity.AiChatRecord;
import cn.kmbeast.pojo.entity.AiConversation;
import cn.kmbeast.pojo.vo.AiStatsVO;
import cn.kmbeast.pojo.vo.NewsVO;
import cn.kmbeast.service.AiChatCacheService;
import cn.kmbeast.service.AiHealthDataService;
import cn.kmbeast.service.AiService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AiServiceImpl implements AiService {

    @Resource
    private AiConfig aiConfig;

    @Resource
    private AiChatRecordMapper aiChatRecordMapper;

    @Resource
    private AiHealthDataService aiHealthDataService;

    @Resource
    private AiChatCacheService chatCacheService;

    @Resource
    private NewsMapper newsMapper;

    private OkHttpClient httpClient;

    private static final int RAG_ARTICLE_LIMIT = 3;
    private static final int RAG_CONTENT_MAX_LENGTH = 300;

    @javax.annotation.PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(aiConfig.getConnectTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(aiConfig.getReadTimeout(), TimeUnit.MILLISECONDS)
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                .build();
    }

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    @Override
    public Result<Map<String, String>> chat(AiChatRequest chatRequest, Integer userId) {
        try {
            String userMessage = chatRequest.getMessage();
            String agentType = chatRequest.getRole();
            Integer conversationId = chatRequest.getConversationId();

            Double temperature = chatRequest.getTemperature() != null
                    ? chatRequest.getTemperature()
                    : AiPromptConfig.getTemperature(agentType);
            Double topP = chatRequest.getTopP() != null
                    ? chatRequest.getTopP()
                    : AiPromptConfig.getTopP(agentType);

            if (conversationId == null) {
                AiConversation newConversation = chatCacheService.createConversation(userId, agentType, null);
                conversationId = newConversation.getId();
            }

            AiChatRecord userRecord = AiChatRecord.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .role("user")
                    .content(userMessage)
                    .agentType(agentType)
                    .build();

            // 先获取历史记录（不含当前用户消息）
            List<AiChatRecord> historyRecords = chatCacheService.getMessages(conversationId);

            String healthContext = buildHealthContext(userId);
            String articleContext = buildArticleRagContext(userMessage);

            String systemPrompt = AiPromptConfig.getSystemPrompt(agentType);
            JSONArray messages = buildMessagesArray(systemPrompt, healthContext + articleContext,
                    historyRecords, userMessage);

            // 构建消息后再保存用户消息到缓存
            chatCacheService.addMessage(conversationId, userRecord);

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", aiConfig.getModel());
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            requestBody.put("top_p", topP);
            requestBody.put("max_tokens", aiConfig.getMaxTokens());

            log.info("AI请求: userId={}, conversationId={}, role={}, temp={}, topP={}, historySize={}",
                    userId, conversationId, agentType, temperature, topP, historyRecords.size());

            String aiReply = callDeepSeekApi(requestBody.toJSONString());

            AiChatRecord aiRecord = AiChatRecord.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .role("assistant")
                    .content(aiReply)
                    .agentType(agentType)
                    .build();
            chatCacheService.addMessage(conversationId, aiRecord);

            Map<String, String> result = new HashMap<>();
            result.put("reply", aiReply);
            result.put("agentType", agentType);
            result.put("conversationId", String.valueOf(conversationId));
            return ApiResult.success(result);

        } catch (Exception e) {
            log.error("AI聊天请求异常", e);
            return ApiResult.error("AI服务异常，请稍后重试：" + e.getMessage());
        }
    }

    @Override
    public void chatStream(AiChatRequest chatRequest, Integer userId, StreamCallback callback) {
        String userMessage = chatRequest.getMessage();
        String agentType = chatRequest.getRole();
        Integer conversationId = chatRequest.getConversationId();

        try {
            Double temperature = chatRequest.getTemperature() != null
                    ? chatRequest.getTemperature()
                    : AiPromptConfig.getTemperature(agentType);
            Double topP = chatRequest.getTopP() != null
                    ? chatRequest.getTopP()
                    : AiPromptConfig.getTopP(agentType);

            if (conversationId == null) {
                AiConversation newConversation = chatCacheService.createConversation(userId, agentType, null);
                conversationId = newConversation.getId();
            }

            AiChatRecord userRecord = AiChatRecord.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .role("user")
                    .content(userMessage)
                    .agentType(agentType)
                    .build();

            // 先获取历史记录（不含当前用户消息）
            List<AiChatRecord> historyRecords = chatCacheService.getMessages(conversationId);

            String healthContext = buildHealthContext(userId);
            String articleContext = buildArticleRagContext(userMessage);

            String systemPrompt = AiPromptConfig.getSystemPrompt(agentType);
            JSONArray messages = buildMessagesArray(systemPrompt, healthContext + articleContext,
                    historyRecords, userMessage);

            // 构建消息后再保存用户消息到缓存
            chatCacheService.addMessage(conversationId, userRecord);

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", aiConfig.getModel());
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            requestBody.put("top_p", topP);
            requestBody.put("stream", true);
            if (chatRequest.getMaxReplyLength() != null && chatRequest.getMaxReplyLength() > 0) {
                requestBody.put("max_tokens", chatRequest.getMaxReplyLength());
            } else {
                requestBody.put("max_tokens", aiConfig.getMaxTokens());
            }
            if (chatRequest.getRepetitionPenalty() != null) {
                requestBody.put("frequency_penalty", chatRequest.getRepetitionPenalty() > 1.0
                        ? chatRequest.getRepetitionPenalty() - 1.0 : 0.0);
            }

            Request request = new Request.Builder()
                    .url(aiConfig.getApiUrl())
                    .addHeader("Authorization", "Bearer " + aiConfig.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .post(RequestBody.create(requestBody.toJSONString(), JSON_MEDIA_TYPE))
                    .build();

            StringBuilder fullReply = new StringBuilder();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    callback.onEvent("error", JSON.toJSONString(
                            buildMap("message", "AI服务异常: HTTP " + response.code())));
                    return;
                }

                if (response.body() == null) {
                    callback.onEvent("error", JSON.toJSONString(
                            buildMap("message", "AI服务返回为空")));
                    return;
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8));
                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String json = line.substring(6);
                        if ("[DONE]".equals(json)) break;

                        JSONObject chunk = JSON.parseObject(json);
                        JSONArray choices = chunk.getJSONArray("choices");
                        if (choices != null && !choices.isEmpty()) {
                            JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                            if (delta.containsKey("content")) {
                                String content = delta.getString("content");
                                if (content != null && !content.isEmpty()) {
                                    fullReply.append(content);
                                    callback.onEvent("answer_chunk", JSON.toJSONString(
                                            buildMap("content", content, "done", false)));
                                }
                            }
                            String finishReason = choices.getJSONObject(0).getString("finish_reason");
                            if ("stop".equals(finishReason)) break;
                        }
                    }
                }
                reader.close();
            }

            AiChatRecord aiRecord = AiChatRecord.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .role("assistant")
                    .content(fullReply.toString())
                    .agentType(agentType)
                    .build();
            chatCacheService.addMessage(conversationId, aiRecord);

            Map<String, Object> doneData = new LinkedHashMap<>();
            doneData.put("done", true);
            doneData.put("conversationId", conversationId);
            doneData.put("agentType", agentType);
            doneData.put("totalLength", fullReply.length());
            callback.onEvent("answer_done", JSON.toJSONString(doneData));

        } catch (Exception e) {
            log.error("AI流式聊天异常", e);
            callback.onEvent("error", JSON.toJSONString(
                    buildMap("message", "AI服务异常: " + e.getMessage())));
        }
    }

    @Override
    public Result<?> queryRecords(AiChatRecordQueryDto queryDto) {
        List<AiChatRecord> records = aiChatRecordMapper.query(queryDto);
        Integer totalCount = aiChatRecordMapper.queryCount(queryDto);
        return PageResult.success(records, totalCount);
    }

    @Override
    public Result<Map<String, Object>> getStats() {
        AiStatsVO stats = AiStatsVO.builder()
                .totalChats(aiChatRecordMapper.countAll())
                .todayChats(aiChatRecordMapper.countToday())
                .userCount(aiChatRecordMapper.countDistinctUsers())
                .avgPerUser(aiChatRecordMapper.avgPerUser())
                .build();

        Map<String, Object> result = new HashMap<>();
        result.put("stats", stats);

        List<Map<String, Object>> roleStats = new ArrayList<>();
        int totalChats = stats.getTotalChats() != null ? stats.getTotalChats() : 1;
        String[] roleNames = {"全科医生", "营养师", "心理咨询", "报告分析", "全能助手"};
        String[] roleKeys = {"doctor", "nutritionist", "psychologist", "analyst", "general_assistant"};
        for (int i = 0; i < roleKeys.length; i++) {
            AiChatRecordQueryDto q = new AiChatRecordQueryDto();
            q.setAgentType(roleKeys[i]);
            Integer count = aiChatRecordMapper.queryCount(q);
            Map<String, Object> roleMap = new HashMap<>();
            roleMap.put("name", roleNames[i]);
            roleMap.put("count", count);
            roleMap.put("percent", totalChats > 0 ? (int) Math.round(count * 100.0 / totalChats) : 0);
            roleStats.add(roleMap);
        }
        result.put("roleStats", roleStats);
        result.put("cacheStats", chatCacheService.getCacheStats());

        return ApiResult.success(result);
    }

    private String buildHealthContext(Integer userId) {
        try {
            StringBuilder context = new StringBuilder();
            context.append("\n\n【用户健康档案】\n");

            Result<Map<String, Object>> profileResult = aiHealthDataService.getUserHealthProfile(userId);
            if (profileResult != null && profileResult.getCode() == 200) {
                Map<String, Object> profile = (Map<String, Object>) profileResult.getData();
                if (profile != null) {
                    Map<String, Object> userInfo = (Map<String, Object>) profile.get("用户信息");
                    if (userInfo != null) {
                        context.append("用户：").append(userInfo.get("用户名")).append("\n");
                    }

                    Map<String, Object> healthSummary = (Map<String, Object>) profile.get("健康指标摘要");
                    if (healthSummary != null && !healthSummary.isEmpty()) {
                        context.append("健康指标：\n");
                        for (Map.Entry<String, Object> entry : healthSummary.entrySet()) {
                            Map<String, Object> indicator = (Map<String, Object>) entry.getValue();
                            context.append("- ").append(entry.getKey()).append(": ")
                                    .append(indicator.get("最新值"))
                                    .append(" (状态: ").append(indicator.getOrDefault("状态", "未知")).append(")")
                                    .append("\n");
                        }
                    }
                }
            }

            Result<Map<String, Object>> abnormalResult = aiHealthDataService.getAbnormalIndicators(userId);
            if (abnormalResult != null && abnormalResult.getCode() == 200) {
                Map<String, Object> abnormalData = (Map<String, Object>) abnormalResult.getData();
                if (abnormalData != null) {
                    List<Map<String, Object>> abnormalList = (List<Map<String, Object>>) abnormalData.get("异常指标列表");
                    if (abnormalList != null && !abnormalList.isEmpty()) {
                        context.append("异常指标：\n");
                        for (Map<String, Object> abnormal : abnormalList) {
                            context.append("- ").append(abnormal.get("指标")).append(": ")
                                    .append(abnormal.get("最新值"))
                                    .append(" (").append(abnormal.get("状态")).append(")")
                                    .append("\n");
                        }
                    }
                }
            }

            context.append("请基于以上用户健康数据进行分析和建议。\n");
            return context.toString();

        } catch (Exception e) {
            log.warn("获取用户健康数据失败: userId={}, error={}", userId, e.getMessage());
            return "\n\n【用户健康档案】暂无健康数据记录，请根据用户描述进行分析。\n";
        }
    }

    private String buildArticleRagContext(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) return "";

        try {
            String keyword = extractKeyword(userMessage);
            List<NewsVO> articles = newsMapper.ragSearch(keyword, RAG_ARTICLE_LIMIT);

            if (articles == null || articles.isEmpty()) return "";

            StringBuilder context = new StringBuilder();
            context.append("\n\n【相关知识库文章参考（RAG）】\n");

            for (int i = 0; i < articles.size(); i++) {
                NewsVO article = articles.get(i);
                context.append("文章").append(i + 1).append(": 《").append(article.getName()).append("》");
                if (article.getTagName() != null) {
                    context.append(" [").append(article.getTagName()).append("]");
                }
                context.append("\n");

                String content = article.getContent();
                if (content != null) {
                    String summary = content.length() > RAG_CONTENT_MAX_LENGTH
                            ? content.substring(0, RAG_CONTENT_MAX_LENGTH) + "..."
                            : content;
                    context.append("摘要: ").append(summary).append("\n\n");
                }
            }

            context.append("请参考以上文章内容，结合用户健康数据，给出专业建议。可以在回复末尾附带相关文章推荐。\n");

            log.info("[RAG] 检索到 {} 篇相关文章, keyword={}", articles.size(), keyword);
            return context.toString();
        } catch (Exception e) {
            log.warn("[RAG] 文章检索失败: {}", e.getMessage());
            return "";
        }
    }

    private String extractKeyword(String userMessage) {
        if (userMessage == null) return "";

        String trimmed = userMessage.trim();
        if (trimmed.length() <= 30) return trimmed;

        int lastPeriod = Math.max(trimmed.lastIndexOf("。"), trimmed.lastIndexOf("？"));
        lastPeriod = Math.max(lastPeriod, trimmed.lastIndexOf("！"));
        if (lastPeriod > 0) {
            return trimmed.substring(Math.max(0, lastPeriod - 30), Math.min(trimmed.length(), lastPeriod + 10));
        }
        return trimmed.substring(0, Math.min(trimmed.length(), 30));
    }

    private JSONArray buildMessagesArray(String systemPrompt, String healthContext,
                                          List<AiChatRecord> history, String userMessage) {
        JSONArray messages = new JSONArray();

        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt + healthContext);
        messages.add(systemMsg);

        // 添加历史消息（不含当前用户消息）
        for (int i = 0; i < history.size(); i++) {
            AiChatRecord record = history.get(i);
            JSONObject historyMsg = new JSONObject();
            historyMsg.put("role", record.getRole());
            historyMsg.put("content", record.getContent());
            messages.add(historyMsg);
        }

        // 添加当前用户消息
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        return messages;
    }

    private String callDeepSeekApi(String requestBody) throws IOException {
        Request request = new Request.Builder()
                .url(aiConfig.getApiUrl())
                .addHeader("Authorization", "Bearer " + aiConfig.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                log.error("DeepSeek API调用失败: code={}, body={}", response.code(), errorBody);
                throw new IOException("AI服务响应异常: HTTP " + response.code());
            }

            String responseBody = response.body().string();
            JSONObject jsonResponse = JSON.parseObject(responseBody);

            if (jsonResponse.containsKey("error")) {
                String errorMsg = jsonResponse.getJSONObject("error").getString("message");
                throw new IOException("AI服务返回错误: " + errorMsg);
            }

            JSONArray choices = jsonResponse.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                String content = message.getString("content");

                if (jsonResponse.containsKey("usage")) {
                    JSONObject usage = jsonResponse.getJSONObject("usage");
                    log.info("Token使用: prompt={}, completion={}, total={}",
                            usage.getInteger("prompt_tokens"),
                            usage.getInteger("completion_tokens"),
                            usage.getInteger("total_tokens"));
                }

                return content;
            }
            throw new IOException("AI服务返回为空");
        }
    }

    private Map<String, Object> buildMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
