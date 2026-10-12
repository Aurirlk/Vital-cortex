package cn.kmbeast.service.impl;

import cn.kmbeast.config.AiConfig;
import cn.kmbeast.core.http.HttpClientFactory;
import cn.kmbeast.config.AiPromptConfig;
import cn.kmbeast.core.graph.KnowledgeGraphService;
import cn.kmbeast.core.guard.OutputValidator;
import cn.kmbeast.core.guard.SignalDetector;
import cn.kmbeast.core.guard.SynthesisGuard;
import cn.kmbeast.core.notify.NotifyTemplates;
import cn.kmbeast.core.notify.UserNotifier;
import cn.kmbeast.crm.agent.tool.AiSessionContext;
import cn.kmbeast.crm.rag.HybridRetriever;
import cn.kmbeast.mapper.AiChatRecordMapper;
import cn.kmbeast.mapper.NewsMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.AiChatRecordQueryDto;
import cn.kmbeast.pojo.dto.update.AiChatRequest;
import cn.kmbeast.pojo.em.NotificationSource;
import cn.kmbeast.pojo.entity.AiChatRecord;
import cn.kmbeast.pojo.entity.AiConversation;
import cn.kmbeast.pojo.vo.AiStatsVO;
import cn.kmbeast.pojo.vo.NewsVO;
import cn.kmbeast.service.AiChatCacheService;
import cn.kmbeast.service.AiHealthDataService;
import cn.kmbeast.service.AiService;
import cn.kmbeast.crm.dto.WebSearchResult;
import cn.kmbeast.crm.service.WebSearchService;
import cn.kmbeast.service.DifyWorkflowService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AiServiceImpl implements AiService {

    @Resource
    private AiConfig aiConfig;

    /**
     * 2026-10-07 新增：数据智能体配置持有者（{@code system_config} 的 {@code data_agent} 分组）。
     *
     * <p>AI 问诊链路的<b>上下文压缩</b>参数也从这里读（{@code context-rounds} /
     * {@code context-max-chars} / {@code context-summary-threshold}），
     * 与数据智能体的开关配置<b>共用同一配置体系</b>，避免建两套表。
     * 10s TTL，管理员改完最多 10 秒生效。</p>
     */
    @Resource
    private cn.kmbeast.core.nl2sql.DataAgentConfigHolder agentConfig;

    /** 2026-10-04：共享 HTTP 客户端（统一超时/连接池/线程池上限，避免各处自建导致线程与连接被放大 N 倍） */
    @jakarta.annotation.Resource
    private HttpClientFactory httpClientFactory;

    @Resource
    private AiChatRecordMapper aiChatRecordMapper;

    @Resource
    private AiHealthDataService aiHealthDataService;

    @Resource
    private AiChatCacheService chatCacheService;

    @Resource
    private NewsMapper newsMapper;

    @Resource
    private WebSearchService webSearchService;

    @Resource
    private DifyWorkflowService difyWorkflowService;

    @Resource
    private cn.kmbeast.crm.rag.HybridRetriever hybridRetriever;

    @Resource
    private KnowledgeGraphService knowledgeGraphService;

    @Resource
    private SynthesisGuard synthesisGuard;

    @Resource
    private OutputValidator outputValidator;

    @Resource
    private SignalDetector signalDetector;

    @Resource
    private cn.kmbeast.mapper.AiUsageMapper aiUsageMapper;

    @Resource
    private cn.kmbeast.core.provider.LLMProviderFactory llmProviderFactory;

    @Resource
    private cn.kmbeast.core.context.TokenBudgetManager tokenBudgetManager;

    @Resource
    private cn.kmbeast.core.auth.AuthSessionManager authSessionManager;

    @Resource
    private UserNotifier userNotifier;

    private OkHttpClient httpClient;

    private static final int RAG_ARTICLE_LIMIT = 6;
    private static final int RAG_CONTENT_MAX_LENGTH = 300;

    /**
     * 客户端断开后仍允许累积的回复长度上限（2026-10-10 新增）。
     *
     * <p>见 {@code chatStream} 里「客户端断开后继续生成」的说明：
     * 保留继续生成是为了把答案存下来并通知用户，但不能无上限地跑下去。
     * 超过这个长度就停止累积并跳出读取循环，避免用户反复「开一个深度思考
     * 再立刻关掉页面」把 Tomcat 工作线程长期占住（这是 SEC-10 当初急于断开的理由）。
     * 4000 字 ≈ 一篇完整的健康科普回答，够用且可控。
     */
    private static final int ABANDONED_REPLY_MAX_CHARS = 4000;

    @jakarta.annotation.PostConstruct
    public void init() {
        // 2026-10-04：统一走共享工厂
        this.httpClient = httpClientFactory.llmClient();
    }

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    @Override
    public List<String> extractKeywords(String userMessage) {
        return difyWorkflowService.extractKeywords(userMessage);
    }

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
            Integer maxTokens = chatRequest.getMaxReplyLength() != null && chatRequest.getMaxReplyLength() > 0
                    ? chatRequest.getMaxReplyLength()
                    : (AiPromptConfig.getMaxTokens(agentType) != null
                            ? AiPromptConfig.getMaxTokens(agentType) : aiConfig.getMaxTokens());
            Double presencePenalty = AiPromptConfig.getPresencePenalty(agentType);
            Double frequencyPenalty = chatRequest.getRepetitionPenalty() != null
                    ? (chatRequest.getRepetitionPenalty() > 1.0 ? chatRequest.getRepetitionPenalty() - 1.0 : 0.0)
                    : AiPromptConfig.getFrequencyPenalty(agentType);

            if (conversationId == null) {
                AiConversation newConversation = chatCacheService.createConversation(userId, agentType, null);
                conversationId = newConversation.getId();
            } else if (!chatCacheService.isOwnedBy(conversationId, userId)) {
                // SEC-03：conversationId 由前端传入。不校验归属的话，攻击者只需在聊天
                // 请求里填别人的会话ID，就能把他人的问诊历史读进自己的模型上下文，
                // 再让模型原样复述出来——比直接读接口更隐蔽。
                throw new IllegalArgumentException("会话不存在或无权访问");
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

            // 根据设置决定是否读取用户健康数据
            String healthContext = "";
            if (chatRequest.getEnableHealthData() == null || Boolean.TRUE.equals(chatRequest.getEnableHealthData())) {
                healthContext = buildHealthContext(userId);
            }
            
            // 根据设置决定是否启用知识库（文章 RAG + 知识图谱 GraphRAG 双路召回）
            String articleContext = "";
            String graphContext = "";
            if (chatRequest.getEnableKnowledgeBase() == null || Boolean.TRUE.equals(chatRequest.getEnableKnowledgeBase())) {
                articleContext = buildArticleRagContext(userMessage, chatRequest.getKeywords());
                // GraphRAG 接入主链路：向量/关键词召回文章之外，追加知识图谱实体关系上下文。
                // Neo4j 未连接时 getRelatedContext 返回空串，自动降级为纯文章检索（不抛错）。
                graphContext = knowledgeGraphService.getRelatedContext(userMessage);
            }
            
            // 联网搜索上下文
            String webSearchContext = "";
            if (Boolean.TRUE.equals(chatRequest.getEnableWebSearch())) {
                List<WebSearchResult> searchResults = webSearchService.search(userMessage);
                if (searchResults != null && !searchResults.isEmpty()) {
                    webSearchContext = webSearchService.formatResultsAsText(searchResults);
                }
            }

            String systemPrompt = AiPromptConfig.getSystemPrompt(agentType);
            String fullContext = healthContext + articleContext + graphContext + buildDrugContext() + webSearchContext;
            // Phase B：按 VIP 判定窗口档位与图片上限（128K/3张 vs 512K/10张）
            boolean vip = authSessionManager.isVip(userId);
            JSONArray messages = buildMessagesArray(systemPrompt, fullContext,
                    historyRecords, userMessage, chatRequest.getFiles(), vip);
            // Phase A/B：上下文预算校验（用户档位预算 ∩ 模型上限；含图片 token 估算）
            tokenBudgetManager.assertWithinBudget(messages, "chat", vip);

            // 构建消息后再保存用户消息到缓存
            chatCacheService.addMessage(conversationId, userRecord);

            JSONObject requestBody = new JSONObject();
            
            // 根据是否启用深度思考选择模型和API
            String apiUrl;
            String apiKey;
            if (Boolean.TRUE.equals(chatRequest.getEnableDeepThink())) {
                requestBody.put("model", aiConfig.getReasonerModel());
                apiUrl = aiConfig.getReasonerApiUrl();
                apiKey = aiConfig.getReasonerApiKey() != null && !aiConfig.getReasonerApiKey().isEmpty()
                        ? aiConfig.getReasonerApiKey() : aiConfig.getApiKey();
            } else {
                requestBody.put("model", aiConfig.getModel());
                apiUrl = aiConfig.getApiUrl();
                apiKey = aiConfig.getApiKey();
            }
            
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            requestBody.put("top_p", topP);
            requestBody.put("max_tokens", maxTokens);
            if (presencePenalty != null) {
                requestBody.put("presence_penalty", presencePenalty);
            }
            if (frequencyPenalty != null) {
                requestBody.put("frequency_penalty", frequencyPenalty);
            }

            log.info("AI请求: userId={}, conversationId={}, role={}, model={}, webSearch={}, deepThink={}",
                    userId, conversationId, agentType, 
                    requestBody.getString("model"),
                    chatRequest.getEnableWebSearch(),
                    chatRequest.getEnableDeepThink());

            String aiReply = callDeepSeekApi(requestBody.toJSONString(), apiUrl, apiKey);

            // ============ SynthesisGuard 输出守卫（三层防幻觉接线） ============
            // 层1 质量门：端水/重复/过短检测 —— 不阻断回答，仅记录监控（质量信号）
            if (!synthesisGuard.validateOutput(aiReply)) {
                log.warn("[AI-Guard] 回答质量门未通过（疑似端水/重复/过短）: userId={}, len={}",
                        userId, aiReply != null ? aiReply.length() : 0);
            }
            // 层2 合规门：医疗关键词 → 免责声明；剂量类敏感信息 → 强化"遵医嘱"提示
            StringBuilder guardedReply = new StringBuilder(aiReply != null ? aiReply : "");
            if (outputValidator.needDisclaimer(aiReply)) {
                guardedReply.append("\n\n---\n⚠️ 以上内容由 AI 生成，仅供参考，不能替代专业医生的诊断与治疗建议。");
            }
            if (outputValidator.containsSensitiveInfo(aiReply)) {
                guardedReply.append("\n⚠️ 具体用药剂量请务必遵医嘱或药品说明书，本回答不构成处方建议。");
            }
            // 层3 安全门：紧急情况 → 强制就医提示（医疗场景兜底）
            if (signalDetector.isUrgent(userMessage)) {
                guardedReply.append("\n\n🚨 您描述的情况可能属于紧急医疗状况，请立即拨打 120 或前往就近医院急诊！本对话不能替代紧急救助。");
            }
            aiReply = guardedReply.toString();

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

        } catch (IllegalArgumentException e) {
            // 参数/越权类问题，直接回传原因，不掩盖为"服务异常"
            log.warn("AI聊天请求被拒绝: userId={}, reason={}", userId, e.getMessage());
            return ApiResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("AI聊天请求异常", e);
            return ApiResult.error("AI服务异常，请稍后重试");
        }
    }

    @Override
    public void chatStream(AiChatRequest chatRequest, Integer userId, StreamCallback callback) {
        String userMessage = chatRequest.getMessage();
        String agentType = chatRequest.getRole();
        Integer conversationId = chatRequest.getConversationId();

        log.info("[AI] 流式对话开始: userId={}, agentType={}, message={}", userId, agentType, 
                userMessage != null ? userMessage.substring(0, Math.min(50, userMessage.length())) : "null");

        // 设置会话元数据
        Map<String, Object> sessionMetadata = new LinkedHashMap<>();
        sessionMetadata.put("enableWebSearch", chatRequest.getEnableWebSearch());
        sessionMetadata.put("enableKnowledgeBase", chatRequest.getEnableKnowledgeBase());
        sessionMetadata.put("enableHealthData", chatRequest.getEnableHealthData());
        sessionMetadata.put("enableDeepThink", chatRequest.getEnableDeepThink());
        sessionMetadata.put("temperature", chatRequest.getTemperature());
        sessionMetadata.put("topP", chatRequest.getTopP());
        AiSessionContext.setMetadata(sessionMetadata);

        // 2026-10-10 新增：把回调包装成「客户端断开也不打断生成」的 sink。
        // 原来的行为是：客户端一断开，callback.onEvent 抛
        // RuntimeException("SSE client disconnected")，异常一路冒到下面的
        // catch (Exception)，直接 return —— 于是**这一轮的 AI 回复完全没有落库**。
        // 用户只是切了个标签页，就永久丢掉了那条正在生成的回答，且毫无提示。
        // 现在改为：断开只记标记、不再往前端推，但仍继续读完模型输出并入库，
        // 结束后发一条「AI 回复已生成」通知（受设置页「AI 对话通知」开关控制）。
        StreamSink sink = new StreamSink(callback);

        try {
            Double temperature = chatRequest.getTemperature() != null
                    ? chatRequest.getTemperature()
                    : AiPromptConfig.getTemperature(agentType);
            Double topP = chatRequest.getTopP() != null
                    ? chatRequest.getTopP()
                    : AiPromptConfig.getTopP(agentType);
            Integer maxTokens = chatRequest.getMaxReplyLength() != null && chatRequest.getMaxReplyLength() > 0
                    ? chatRequest.getMaxReplyLength()
                    : (AiPromptConfig.getMaxTokens(agentType) != null
                            ? AiPromptConfig.getMaxTokens(agentType) : aiConfig.getMaxTokens());
            Double presencePenalty = AiPromptConfig.getPresencePenalty(agentType);
            Double frequencyPenalty = chatRequest.getRepetitionPenalty() != null
                    ? (chatRequest.getRepetitionPenalty() > 1.0 ? chatRequest.getRepetitionPenalty() - 1.0 : 0.0)
                    : AiPromptConfig.getFrequencyPenalty(agentType);

            if (conversationId == null) {
                AiConversation newConversation = chatCacheService.createConversation(userId, agentType, null);
                conversationId = newConversation.getId();
            } else if (!chatCacheService.isOwnedBy(conversationId, userId)) {
                // SEC-03：conversationId 由前端传入。不校验归属的话，攻击者只需在聊天
                // 请求里填别人的会话ID，就能把他人的问诊历史读进自己的模型上下文，
                // 再让模型原样复述出来——比直接读接口更隐蔽。
                throw new IllegalArgumentException("会话不存在或无权访问");
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

            // 根据设置决定是否读取用户健康数据
            String healthContext = "";
            boolean enableHealth = chatRequest.getEnableHealthData() == null || Boolean.TRUE.equals(chatRequest.getEnableHealthData());
            if (enableHealth) {
                healthContext = buildHealthContext(userId);
                log.info("[AI] 健康数据查询: userId={}, 数据长度={}, 有数据={}", 
                        userId, healthContext.length(), healthContext.length() > 100);
            } else {
                log.info("[AI] 健康数据查询: 已禁用");
            }
            
            // 根据设置决定是否启用知识库（文章 RAG + 知识图谱 GraphRAG 双路召回）
            String articleContext = "";
            String graphContext = "";
            boolean enableKB = chatRequest.getEnableKnowledgeBase() == null || Boolean.TRUE.equals(chatRequest.getEnableKnowledgeBase());
            if (enableKB) {
                articleContext = buildArticleRagContext(userMessage, chatRequest.getKeywords());
                // GraphRAG 接入主链路（流式）：追加知识图谱实体关系上下文。
                // Neo4j 未连接时 getRelatedContext 返回空串，自动降级为纯文章检索（不抛错）。
                graphContext = knowledgeGraphService.getRelatedContext(userMessage);
                log.info("[AI] 知识库查询: 关键词={}, 结果长度={}, 图谱={}, 有结果={}", 
                        chatRequest.getKeywords() != null ? chatRequest.getKeywords() : userMessage,
                        articleContext.length(), graphContext.length(), !articleContext.isEmpty());
            } else {
                log.info("[AI] 知识库查询: 已禁用");
            }
            
            // 药品数据上下文（始终注入）
            String drugContext = buildDrugContext();
            
            // 联网搜索上下文
            String webSearchContext = "";
            List<WebSearchResult> searchResults = null;
            if (Boolean.TRUE.equals(chatRequest.getEnableWebSearch())) {
                searchResults = webSearchService.search(userMessage);
                if (searchResults != null && !searchResults.isEmpty()) {
                    webSearchContext = webSearchService.formatResultsAsText(searchResults);
                    // 推送搜索结果事件
                    sink.emit("search_results", JSON.toJSONString(
                            buildMap("results", searchResults)));
                }
                log.info("[AI] 联网搜索: 结果数={}", searchResults != null ? searchResults.size() : 0);
            } else {
                log.info("[AI] 联网搜索: 已禁用");
            }

            String systemPrompt = AiPromptConfig.getSystemPrompt(agentType);
            String fullContext = healthContext + articleContext + graphContext + drugContext + webSearchContext;
            // Phase B：按 VIP 判定窗口档位与图片上限
            boolean vip = authSessionManager.isVip(userId);
            JSONArray messages = buildMessagesArray(systemPrompt, fullContext,
                    historyRecords, userMessage, chatRequest.getFiles(), vip);
            // Phase A/B：上下文预算校验（含图片 token 估算）
            tokenBudgetManager.assertWithinBudget(messages, "chatStream", vip);

            log.info("[AI] 上下文构建完成: 健康={}字, 知识库={}字, 药品={}字, 搜索={}字, 总={}字", 
                    healthContext.length(), articleContext.length(), drugContext.length(),
                    webSearchContext.length(), fullContext.length());

            // 构建消息后再保存用户消息到缓存
            chatCacheService.addMessage(conversationId, userRecord);

            JSONObject requestBody = new JSONObject();
            
            // 根据是否启用深度思考选择模型和API
            String apiUrl;
            String apiKey;
            if (Boolean.TRUE.equals(chatRequest.getEnableDeepThink())) {
                // 深度思考模式
                requestBody.put("model", aiConfig.getReasonerModel());
                apiUrl = aiConfig.getReasonerApiUrl();
                apiKey = aiConfig.getReasonerApiKey() != null && !aiConfig.getReasonerApiKey().isEmpty()
                        ? aiConfig.getReasonerApiKey() : aiConfig.getApiKey();
                // 深度思考需要更长的超时时间
                log.info("[AI] 启用深度思考模式: model={}", aiConfig.getReasonerModel());
            } else {
                // 普通模式
                requestBody.put("model", aiConfig.getModel());
                apiUrl = aiConfig.getApiUrl();
                apiKey = aiConfig.getApiKey();
            }
            
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            requestBody.put("top_p", topP);
            requestBody.put("stream", true);
            requestBody.put("max_tokens", maxTokens);
            if (presencePenalty != null) {
                requestBody.put("presence_penalty", presencePenalty);
            }
            if (frequencyPenalty != null) {
                requestBody.put("frequency_penalty", frequencyPenalty);
            }

            StringBuilder fullReply = new StringBuilder();

            // SEC-08: 流式调用走 LLMProvider 工厂（熔断 + 重试 + 厂商切换）
            try (Response response = llmProviderFactory.getProvider().stream(
                    requestBody.toJSONString(), apiUrl, apiKey)) {
                log.info("[AI] API响应状态: code={}, agentType={}", response.code(), agentType);
                
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.error("[AI] API调用失败: code={}, body={}", response.code(), errorBody);
                    sink.emit("error", JSON.toJSONString(
                            buildMap("message", "AI服务异常: HTTP " + response.code())));
                    return;
                }

                if (response.body() == null) {
                    sink.emit("error", JSON.toJSONString(
                            buildMap("message", "AI服务返回为空")));
                    return;
                }

                // SEC-10 整改：BufferedReader 用 try-with-resources，
                // 原实现手动 reader.close() 不在 finally，readLine 抛异常时流不关闭
                //（虽然 Response 关闭会兜底，但显式管理更可靠）。
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    int chunkCount = 0;

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
                                        chunkCount++;
                                        sink.emit("answer_chunk", JSON.toJSONString(
                                                buildMap("content", content, "done", false)));
                                        // 客户端已断开：仍然继续生成以便入库 + 事后通知，
                                        // 但不能无限跑 —— 到上限就收手，避免长连接线程堆积
                                        //（SEC-10 当初立即断开正是为了防这个）。
                                        if (sink.clientGone()
                                                && fullReply.length() >= ABANDONED_REPLY_MAX_CHARS) {
                                            log.info("[AI] 客户端已断开且回复已达上限，提前结束生成: "
                                                            + "userId={}, replyLength={}",
                                                    userId, fullReply.length());
                                            break;
                                        }
                                    }
                                }
                                String finishReason = choices.getJSONObject(0).getString("finish_reason");
                                if ("stop".equals(finishReason)) break;
                            }
                        }
                    }
                    log.info("[AI] 流式响应完成: agentType={}, chunkCount={}, replyLength={}, clientGone={}",
                            agentType, chunkCount, fullReply.length(), sink.clientGone());
                }
            }

            // ============ SynthesisGuard 输出守卫（流式，三层防幻觉接线） ============
            // 层1 质量门：端水/重复/过短检测 —— 不阻断回答，仅记录监控（质量信号）
            String streamReply = fullReply.toString();
            if (!synthesisGuard.validateOutput(streamReply)) {
                log.warn("[AI-Guard] 流式回答质量门未通过（疑似端水/重复/过短）: userId={}, len={}",
                        userId, streamReply.length());
            }
            // 层2 合规门 + 层3 安全门：追加免责/遵医嘱/紧急就医提示（与 chat 一致，前端可见）
            StringBuilder guardedReply = new StringBuilder(streamReply);
            if (outputValidator.needDisclaimer(streamReply)) {
                guardedReply.append("\n\n---\n⚠️ 以上内容由 AI 生成，仅供参考，不能替代专业医生的诊断与治疗建议。");
            }
            if (outputValidator.containsSensitiveInfo(streamReply)) {
                guardedReply.append("\n⚠️ 具体用药剂量请务必遵医嘱或药品说明书，本回答不构成处方建议。");
            }
            if (signalDetector.isUrgent(userMessage)) {
                guardedReply.append("\n\n🚨 您描述的情况可能属于紧急医疗状况，请立即拨打 120 或前往就近医院急诊！本对话不能替代紧急救助。");
            }
            String appendText = guardedReply.substring(streamReply.length());
            if (!appendText.isEmpty()) {
                sink.emit("answer_chunk", JSON.toJSONString(
                        buildMap("content", appendText, "done", false)));
                fullReply.append(appendText);
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
            sink.emit("answer_done", JSON.toJSONString(doneData));

            // 2026-10-10 新增：客户端中途离开，但回答已经生成并入库 —— 通知他回来看。
            // 这里**不能**用 sendOnce：同一个会话里用户可能多次「发起后离开」，
            // 每次都该各收到一条，去重键（userId + 会话ID + 标题）会把第二、三次全吃掉。
            if (sink.clientGone() && fullReply.length() > 0) {
                notifyReplyReady(userId, conversationId, userMessage, fullReply.length());
            }

        } catch (IllegalArgumentException e) {
            // 参数/权限/多模态校验类业务错误：直接回传可读信息，不伪装成"AI服务异常"
            log.warn("[AI] 流式聊天请求被拒绝: userId={}, reason={}", userId, e.getMessage());
            try {
                sink.emit("error", JSON.toJSONString(
                        buildMap("message", e.getMessage())));
            } catch (Exception ignored) {
                // 回调失败（客户端已断开）不再处理
            }
        } catch (Exception e) {
            // SEC-10：客户端断开导致的异常不回调 error（回调本身已不可用），
            // 避免在 catch 块里二次抛异常逃逸到容器。
            // 2026-10-10 之后这条路已基本走不到：onEvent 的断开异常已被 StreamSink
            // 就地吞掉并转成 clientGone 标记。保留它是兜底（例如别处也抛了同一句话）。
            if (e.getMessage() != null && e.getMessage().contains("SSE client disconnected")) {
                log.info("[AI] 客户端断开连接，终止流式输出: userId={}", userId);
                return;
            }
            log.error("AI流式聊天异常", e);
            sink.emit("error", JSON.toJSONString(
                    buildMap("message", "AI服务异常: " + e.getMessage())));
        } finally {
            // 清除会话上下文
            AiSessionContext.clear();
        }
    }

    /**
     * 客户端已离开、但 AI 回答已生成入库时，给用户发一条通知（2026-10-10 新增）。
     *
     * <p>受设置页「AI 对话通知」开关（{@code user.notify_ai_chat}）控制 ——
     * 走 {@code UserNotifier}，用户关掉就不会收到。
     *
     * <p>通知本身失败绝不能影响已经完成的回答：<b>回答是主体，通知是附赠</b>。
     * {@code UserNotifier.send} 内部已吞异常，这里再包一层是为了连
     * 「组装通知文案时抛异常」也一起挡住。
     */
    private void notifyReplyReady(Integer userId, Integer conversationId,
                                  String userMessage, int replyLength) {
        try {
            userNotifier.send(userId, NotificationSource.AI_CHAT,
                    NotifyTemplates.T_AI_CHAT_DONE,
                    NotifyTemplates.aiChatDone(userMessage),
                    conversationId == null ? null : conversationId.longValue(),
                    NotifyTemplates.LINK_AI);
            log.info("[AI] 客户端已离开，回复已入库并发送通知: userId={}, conversationId={}, replyLength={}",
                    userId, conversationId, replyLength);
        } catch (Exception e) {
            log.warn("[AI] AI 回复就绪通知发送失败，回答本身已正常保存: userId={}, conversationId={}",
                    userId, conversationId, e);
        }
    }

    /**
     * 流式输出的「接收端」包装 —— 2026-10-10 新增。
     *
     * <p>解决的问题：{@code AiController} 的 SSE 回调在检测到客户端断开时抛
     * {@code RuntimeException("SSE client disconnected")}。原来这个异常会一路
     * 冒出 {@code chatStream} 的读取循环，导致<b>整轮 AI 回复既不推送也不入库</b>
     * —— 用户切走一次就永久丢掉一条回答（含深度思考模式下几十秒的推理结果）。
     *
     * <p>现在断开只被就地记录：不再尝试推送（推也没人收），但生成继续，
     * 结束时把回复落库并发一条「AI 回复已生成」通知，用户回来还能看到答案。
     *
     * <p><b>只吞「客户端断开」这一种异常</b>：其它异常照旧向上抛，
     * 否则真正的故障会被伪装成「用户离开了」，排查时无从下手。
     */
    static final class StreamSink {

        private final AiService.StreamCallback callback;

        /** 是否已经确认客户端断开；一旦为 true 就不再尝试任何推送 */
        private boolean clientGone;

        StreamSink(AiService.StreamCallback callback) {
            this.callback = callback;
        }

        /** 尽力推送一个 SSE 事件；客户端已断开时静默跳过，不打断生成 */
        void emit(String eventName, String jsonData) {
            if (clientGone) {
                return;
            }
            try {
                callback.onEvent(eventName, jsonData);
            } catch (RuntimeException e) {
                if (isClientGone(e)) {
                    clientGone = true;
                    return;
                }
                throw e;
            }
        }

        boolean clientGone() {
            return clientGone;
        }

        /** 与 AiController 抛出时的判定口径必须一字不差 */
        private static boolean isClientGone(Throwable e) {
            return e.getMessage() != null && e.getMessage().contains("SSE client disconnected");
        }
    }

    @Override
    public Result<?> queryRecords(AiChatRecordQueryDto queryDto) {
        log.info("[AI] 查询咨询记录: userId={}, agentType={}, current={}, size={}", 
                queryDto.getUserId(), queryDto.getAgentType(), queryDto.getCurrent(), queryDto.getSize());
        List<AiChatRecord> records = aiChatRecordMapper.query(queryDto);
        Integer totalCount = aiChatRecordMapper.queryCount(queryDto);
        log.info("[AI] 查询结果: 记录数={}, 总数={}", records != null ? records.size() : 0, totalCount);
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

    /**
     * SEC-12：健康档案出境脱敏。
     * 移除联系方式类字段（phone/idCard/email/address/idCardNo 等），
     * 姓名打码保留姓氏，仅保留健康指标数据用于 AI 分析。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> maskProfileForExternal(Map<String, Object> profile) {
        if (profile == null) {
            return null;
        }
        Map<String, Object> masked = new LinkedHashMap<>(profile);
        Object userInfoObj = masked.get("userInfo");
        if (userInfoObj instanceof Map) {
            Map<String, Object> userInfo = new LinkedHashMap<>((Map<String, Object>) userInfoObj);
            for (String key : new String[]{"phone", "phoneNumber", "idCard", "idCardNo",
                    "email", "address", "idCardNumber", "identityCard"}) {
                userInfo.remove(key);
            }
            // 姓名打码：保留姓氏，名字用 * 代替
            Object nameObj = userInfo.get("userName");
            if (nameObj != null) {
                String name = String.valueOf(nameObj).trim();
                if (name.length() > 1) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(name.charAt(0));
                    for (int i = 1; i < name.length(); i++) sb.append('*');
                    userInfo.put("userName", sb.toString());
                }
            }
            masked.put("userInfo", userInfo);
        }
        return masked;
    }

    private String buildHealthContext(Integer userId) {
        try {
            StringBuilder context = new StringBuilder();
            context.append("\n\n【用户健康档案 - JSON格式】\n");
            context.append("以下是以JSON格式提供的用户健康数据，请严格基于此数据进行分析：\n\n");

            Result<Map<String, Object>> profileResult = aiHealthDataService.getUserHealthProfile(userId);
            if (profileResult != null && profileResult.getCode() == 200) {
                Map<String, Object> profile = (Map<String, Object>) profileResult.getData();
                if (profile != null) {
                    // SEC-12 整改：健康档案含姓名等可识别信息，发往第三方 AI 厂商前
                    // 必须脱敏——剥离联系方式类字段，姓名打码，仅保留健康指标。
                    Map<String, Object> masked = maskProfileForExternal(profile);
                    String profileJson = JSON.toJSONString(masked, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);
                    context.append("```json\n").append(profileJson).append("\n```\n\n");
                    
                    // 添加使用说明
                    context.append("数据说明：\n");
                    context.append("- userInfo: 用户基本信息\n");
                    context.append("- healthIndicators: 健康指标数组，每项包含 name(指标名), value(值), unit(单位), normalRange(正常范围), status(状态: normal/abnormal)\n");
                    context.append("- abnormalIndicators: 异常指标数组\n");
                    context.append("- totalRecords: 记录总数\n\n");
                    context.append("请基于以上JSON数据进行分析，如有异常指标请重点说明。如无数据，请告知用户需要先录入健康数据。\n");
                } else {
                    context.append("暂无健康数据记录。\n");
                    context.append("请告知用户：您还没有录入健康数据，请先在\"健康数据\"页面录入您的健康指标（如血压、血糖、血脂等），然后我就能为您进行个性化分析。\n");
                }
            } else {
                context.append("暂无健康数据记录。\n");
                context.append("请告知用户：您还没有录入健康数据，请先在\"健康数据\"页面录入您的健康指标（如血压、血糖、血脂等），然后我就能为您进行个性化分析。\n");
            }

            return context.toString();

        } catch (Exception e) {
            log.warn("获取用户健康数据失败: userId={}, error={}", userId, e.getMessage());
            return "\n\n【用户健康档案】数据加载失败，请根据用户描述进行分析。\n";
        }
    }

    private String buildArticleRagContext(String userMessage, List<String> keywords) {
        if (userMessage == null || userMessage.trim().isEmpty()) return "";

        try {
            // 使用传入的关键词或AI提取
            List<String> keywordList;
            if (keywords != null && !keywords.isEmpty()) {
                keywordList = keywords;
                log.info("[RAG] 使用前端关键词: {}", keywordList);
            } else {
                // Dify提取 → AI提取 → 直接用原文（不再做本地切割）
                List<String> difyWords = difyWorkflowService.extractKeywords(userMessage);
                if (difyWords != null && !difyWords.isEmpty()) {
                    keywordList = difyWords;
                    log.info("[RAG] Dify提取: {}", keywordList);
                } else {
                    // 回退：直接取原文前10个字作为关键词搜索
                    String raw = userMessage.replaceAll("[？！？\\s]", "").trim();
                    keywordList = Collections.singletonList(raw.length() > 10 ? raw.substring(0, 10) : raw);
                    log.info("[RAG] 回退原文搜索: {}", keywordList);
                }
            }
            
            // RAG-10 整改：从纯 LIKE 升级为 向量+LIKE 混合检索（RRF 融合）。
            // 向量路提供语义召回（症状描述→文章），LIKE 路保证专业术语精准。
            List<HybridRetriever.RetrievedDoc> docs;
            try {
                docs = hybridRetriever.search(userMessage, keywordList, RAG_ARTICLE_LIMIT);
            } catch (Exception e) {
                log.warn("[RAG] 混合检索异常，回退纯 LIKE: {}", e.getMessage());
                List<NewsVO> articles = newsMapper.ragSearch(keywordList, RAG_ARTICLE_LIMIT);
                docs = new ArrayList<>();
                if (articles != null) {
                    for (NewsVO a : articles) {
                        if (a.getId() != null) {
                            docs.add(new HybridRetriever.RetrievedDoc(a.getId(),
                                    a.getName() != null ? a.getName() : "",
                                    a.getTagName() != null ? a.getTagName() : "",
                                    a.getContent() != null ? a.getContent() : "",
                                    1.0, "keyword"));
                        }
                    }
                }
            }

            if (docs == null || docs.isEmpty()) return "";

            StringBuilder context = new StringBuilder();
            context.append("\n\n【相关知识库文章参考（RAG）】\n");
            context.append("以下是从本站知识库检索到的相关文章，请严格基于以下文章内容回答用户：\n\n");

            for (int i = 0; i < docs.size(); i++) {
                HybridRetriever.RetrievedDoc doc = docs.get(i);
                // RAG-13：带文章ID的结构化引用，回答须按 [ID] 标注出处
                context.append("文章").append(i + 1).append(": [ID:").append(doc.articleId)
                        .append("]《").append(doc.title).append("》");
                if (doc.tag != null && !doc.tag.isEmpty()) {
                    context.append(" [").append(doc.tag).append("]");
                }
                context.append("（来源:").append(doc.source).append("）\n");

                if (doc.content != null && !doc.content.isEmpty()) {
                    String summary = extractRelevantChunk(doc.content, keywordList, RAG_CONTENT_MAX_LENGTH);
                    context.append("文章内容: ").append(summary).append("\n\n");
                }
            }

            context.append("【重要要求】\n");
            context.append("1. 必须基于以上文章内容回答，引用时标注 [ID:x]（x 为文章编号）。\n");
            context.append("2. 用自己的话总结文章核心观点，不要直接复制。\n");
            context.append("3. 如果文章中没有相关答案，明确告知用户\"本站暂无相关文章\"。\n");
            context.append("4. 回答末尾列出\"📚 相关文章推荐\"，包含文章标题和分类。\n");

            context.append("请参考以上文章内容，结合用户健康数据，给出专业建议。可以在回复末尾附带相关文章推荐。\n");

            log.info("[RAG] 混合检索到 {} 篇相关文章, keywords={}", docs.size(), keywordList);
            return context.toString();
        } catch (Exception e) {
            log.warn("[RAG] 文章检索失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 构建药品数据上下文
     * 从ai_data/drugs.json读取药品列表注入AI上下文
     */
    private String buildDrugContext() {
        try {
            Path drugsPath = Path.of("ai_data", "drugs.json");
            if (!Files.exists(drugsPath)) {
                return "";
            }
            String content = new String(Files.readAllBytes(drugsPath), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(content);
            JSONArray drugs = root.getJSONArray("drugs");
            if (drugs == null || drugs.isEmpty()) return "";

            StringBuilder ctx = new StringBuilder();
            ctx.append("\n\n【系统药品数据库】\n");
            ctx.append("以下是系统内的药品信息，当用户询问药品时请基于此数据回答：\n\n");
            
            int limit = Math.min(drugs.size(), 30);
            for (int i = 0; i < limit; i++) {
                JSONObject d = drugs.getJSONObject(i);
                ctx.append("- ").append(d.getString("name"))
                   .append(" | ").append(d.getString("category"))
                   .append(" | ¥").append(d.getBigDecimal("price"))
                   .append("/").append(d.getString("unit"))
                   .append(" | ").append(d.getString("specification"))
                   .append(" | ").append(d.getString("manufacturer"))
                   .append("\n");
            }
            ctx.append("\n共").append(drugs.size()).append("种药品。请基于以上数据回答用户。\n");
            
            log.info("[Drug] 药品上下文: {} 种药品, {}字", drugs.size(), ctx.length());
            return ctx.toString();
        } catch (Exception e) {
            log.warn("[Drug] 药品上下文构建失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 使用AI模型提取健康领域关键词
     */
    private String extractKeywordsWithAI(String userMessage) {
        try {
            // 输入减半：最多取前15个字发送给AI
            String shortMsg = userMessage.length() > 15 ? userMessage.substring(0, 15) : userMessage;
            
            JSONObject body = new JSONObject();
            body.put("model", aiConfig.getModel());
            body.put("messages", buildMessagesArray(
                "你是一个医疗健康领域的意图识别助手。请从用户问题中提取2-5个最关键的医学/健康关键词，" +
                "用逗号分隔。只返回关键词，不要任何解释。",
                "", new ArrayList<>(), shortMsg, null, false));
            body.put("temperature", 0.1);
            body.put("max_tokens", 50);
            body.put("top_p", 0.3);

            Request request = new Request.Builder()
                    .url(aiConfig.getApiUrl())
                    .addHeader("Authorization", "Bearer " + aiConfig.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String respBody = response.body().string();
                    JSONObject json = JSON.parseObject(respBody);
                    JSONArray choices = json.getJSONArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
                        if (content != null && !content.isEmpty()) {
                            // 清理关键词：去换行、去多余空格
                            String keywords = content.replace("\n", ",").replace("，", ",").trim();
                            log.info("[RAG] AI提取关键词: \"{}\" -> {}", userMessage, keywords);
                            return keywords;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[RAG] AI关键词提取失败，回退到简单模式: {}", e.getMessage());
        }
        // 失败时回退到本地关键词提取
        return localExtractKeyword(userMessage);
    }

    /**
     * 本地关键词提取（不依赖AI）
     * 移除停用字符，保留核心内容，缩减到原长度1/3（最多10字）
     */
    private String localExtractKeyword(String userMessage) {
        if (userMessage == null || userMessage.length() <= 3) return userMessage;
        
        // 中文停用字（逐字删除）
        Set<String> stops = new HashSet<>(Arrays.asList(
            "我","你","他","她","它","们","的","了","是","在","有","和","就","都","也","还","吗","呢","吧","啊",
            "这","那","么","什","怎","如","何","为","能","会","要","可","以","应","该","需","想","觉得","请问",
            "不","没","否","能","一","下","些","点","办","样","请","问","帮","看","给","让","把","被","对","从",
            "很","非","常","特","别","比","较","太","挺","最近","经","总","已","正","将","刚","都","只","才",
            "搜","索","网","站","中","关","于","帖","子","查","找","浏","览","页","面","回","答","告","诉",
            "啥","哪","怎","怎","办","为","啥","因","所","以","虽","然","但","而","且","与","或","向"
        ));
        
        StringBuilder sb = new StringBuilder();
        for (char c : userMessage.toCharArray()) {
            if (Character.isLetterOrDigit(c) || !stops.contains(String.valueOf(c))) {
                sb.append(c);
            }
        }
        
        String cleaned = sb.toString().replaceAll("[？！？。，；：、\"'（）()【】《》\\s]", "").trim();
        int maxLen = userMessage.length() / 3; // 原长度1/3
        if (maxLen < 3) maxLen = 3;
        if (maxLen > 10) maxLen = 10;
        
        return cleaned.length() > maxLen ? cleaned.substring(0, maxLen) : cleaned;
    }
    
    /**
     * 分割逗号分隔的关键词为列表
     */
    private List<String> splitKeywords(String keywords) {
        if (keywords == null || keywords.trim().isEmpty()) return new ArrayList<>();
        return Arrays.stream(keywords.split("[,，]"))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 组装 OpenAI 兼容 messages。
     * Phase A（docs/multimodal-design.md §4.2）：当前用户消息支持携带图片（files），
     * content 由纯字符串升级为 [{type:text},{type:image_url},...] 数组；历史消息保持字符串。
     * Phase B：图片数量上限按 VIP 分级（普通 3 张 / VIP 10 张）。
     */
    private JSONArray buildMessagesArray(String systemPrompt, String healthContext,
                                          List<AiChatRecord> history, String userMessage,
                                          List<String> files, boolean vip) {
        JSONArray messages = new JSONArray();

        // 2026-10-07 新增：上下文压缩。此前这里是
        //     for (int i = 0; i < history.size(); i++) { messages.add(...); }
        // —— 全量历史无上限塞进 prompt，超预算时 TokenBudgetManager 直接抛异常让用户重开对话。
        // 现在改为「条数上限 + 字符预算 + 更早历史滚动摘要」三重压缩。
        appendCompressedHistory(messages, history);

        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt + healthContext);
        messages.add(systemMsg);

        // 添加当前用户消息（支持多模态图片）
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        if (files != null && !files.isEmpty()) {
            if (!aiConfig.isVisionEnabled()) {
                throw new IllegalArgumentException(
                        "当前模型不支持图片，请在管理端「AI配置」中选择多模态模型后重试");
            }
            int maxImages = vip ? aiConfig.getMaxImagesVip() : aiConfig.getMaxImagesNormal();
            if (files.size() > maxImages) {
                throw new IllegalArgumentException("单次最多上传 " + maxImages + " 张图片");
            }
            JSONArray content = new JSONArray();
            JSONObject textPart = new JSONObject();
            textPart.put("type", "text");
            textPart.put("text", userMessage);
            content.add(textPart);
            for (String f : files) {
                if (f == null || f.trim().isEmpty()) {
                    continue;
                }
                JSONObject imgPart = new JSONObject();
                imgPart.put("type", "image_url");
                JSONObject urlObj = new JSONObject();
                urlObj.put("url", f.trim());
                imgPart.put("image_url", urlObj);
                content.add(imgPart);
            }
            userMsg.put("content", content);
        } else {
            userMsg.put("content", userMessage);
        }
        messages.add(userMsg);

        return messages;
    }

    // ==================== 上下文压缩（2026-10-07 新增） ====================

    /**
     * 把历史消息按「条数上限 + 字符预算 + 更早历史滚动摘要」压缩后追加到 messages。
     *
     * <p><b>为什么此前不做</b>：原实现是 {@code for (int i = 0; i < history.size(); i++)}，
     * 全量历史进 prompt。唯一的兜底是 {@code TokenBudgetManager} 在<b>请求前</b>校验，
     * 超了就<b>抛 IllegalArgumentException 让用户重开会话</b> —— 那不是压缩，是拒绝服务。
     * 管理员在「系统配置」里能配的 {@code contextRounds}（0~50）与 {@code maxHistoryRounds}
     * 都是<b>装饰性配置项，业务层零读取</b>。</p>
     *
     * <p><b>本方法的三层压缩（全部可由管理员在线配置，10s TTL 生效）</b>：</p>
     * <ol>
     *   <li><b>条数截断</b>：只保留最近 {@code context-rounds} × 2 条消息
     *       （一轮 = user + assistant 两条）。</li>
     *   <li><b>字符预算</b>：累计正文超过 {@code context-max-chars} 时，从<b>最旧</b>的消息开始
     *       整条丢弃（而不是从中间截断 —— 那会破坏「最近若干轮」的语义连续性）。</li>
     *   <li><b>滚动摘要</b>：被丢弃的较早历史压缩为一条 {@code system} 摘要消息
     *       （关键词 + 主题 + 轮次数），保留"我们之前聊过什么"的线索。
     *       {@code context-summary-threshold = 0} 时关闭摘要。</li>
     * </ol>
     *
     * <p><b>不调 LLM 做摘要</b>：那会让每次请求多一次模型调用（成本 + 延迟）。
     * 这里用确定性的关键词提取，零额外开销、零失败风险。</p>
     *
     * @param messages 目标消息数组（追加压缩后的历史）
     * @param history  完整历史（按时间升序）
     */
    private void appendCompressedHistory(JSONArray messages, List<AiChatRecord> history) {
        if (history == null || history.isEmpty()) {
            return;
        }

        // 读配置（未注入时回落安全默认值，保证单测与降级路径可用）
        int rounds = 10;
        int maxChars = 4000;
        int summaryThreshold = 0;
        try {
            if (agentConfig != null) {
                rounds = agentConfig.getContextRounds();
                maxChars = agentConfig.getContextMaxChars();
                summaryThreshold = agentConfig.getContextSummaryThreshold();
            }
        } catch (Exception e) {
            log.debug("[AiService] 读取上下文压缩配置失败，使用默认值: {}", e.getMessage());
        }

        if (rounds <= 0) {
            // 管理员显式配置「不携带历史」
            log.debug("[AiService] contextRounds=0，本次不携带历史");
            return;
        }

        int maxMessages = rounds * 2;
        int startIdx = Math.max(0, history.size() - maxMessages);
        int dropped = startIdx;   // 被丢弃的更早历史条数

        // ---- 字符预算：从最旧的一条开始整体丢弃 ----
        long used = 0L;
        int keepFrom = startIdx;
        for (int i = startIdx; i < history.size(); i++) {
            AiChatRecord r = history.get(i);
            if (r == null) {
                continue;
            }
            int cost = r.getContent() == null ? 8 : r.getContent().length();
            if (used + cost > maxChars && i > startIdx) {
                keepFrom = i;
                break;
            }
            used += cost;
        }

        // ---- 滚动摘要：保留被丢弃部分的线索 ----
        if (dropped > 0 && summaryThreshold > 0 && dropped >= summaryThreshold) {
            String summary = summarizeDroppedHistory(history, dropped);
            if (summary != null && !summary.isBlank()) {
                JSONObject s = new JSONObject();
                s.put("role", "system");
                s.put("content", "[早期对话摘要（已压缩 " + dropped + " 条）] " + summary);
                messages.add(s);
            }
        }

        // ---- 追加保留窗口内的历史 ----
        for (int i = keepFrom; i < history.size(); i++) {
            AiChatRecord record = history.get(i);
            if (record == null) {
                continue;
            }
            JSONObject m = new JSONObject();
            m.put("role", record.getRole());
            m.put("content", record.getContent() == null ? "" : record.getContent());
            messages.add(m);
        }

        if (dropped > 0 || keepFrom > startIdx) {
            log.debug("[AiService] 上下文压缩: 历史 {} 条 → 保留 [{}, {}) + 摘要, 预算 {} 字符",
                    history.size(), keepFrom, history.size(), maxChars);
        }
    }

    /**
     * 用关键词提取把较早历史压成一句摘要（零 LLM 调用）。
     *
     * <p>刻意不用模型做摘要：那会让每次请求多一次调用（成本 + 延迟 + 失败风险），
     * 而这里只需要保留"之前聊过哪些主题"的线索，关键词足够。</p>
     *
     * @param history 完整历史
     * @param count   前 count 条为被丢弃的部分
     * @return 摘要文本；无可提取内容时返回 null
     */
    private String summarizeDroppedHistory(List<AiChatRecord> history, int count) {
        int limit = Math.min(count, history.size());
        // 统计关键词频次（去噪：长度 ≥2 的中文/字母词）
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (int i = 0; i < limit; i++) {
            AiChatRecord r = history.get(i);
            if (r == null || r.getContent() == null) {
                continue;
            }
            Matcher m = KEYWORD_PATTERN.matcher(r.getContent());
            while (m.find()) {
                freq.merge(m.group(), 1, Integer::sum);
            }
        }
        if (freq.isEmpty()) {
            return null;
        }
        // 取频次前 8 个，同频次按首次出现顺序（LinkedHashMap 保序）
        List<String> top = freq.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(8)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        // 统计被丢弃部分的用户提问数，让模型知道"我问过几件事"
        long userTurns = history.subList(0, limit).stream()
                .filter(r -> r != null && "user".equals(r.getRole()))
                .count();
        return "讨论过的主题：" + String.join("、", top) + "（用户共提出 " + userTurns + " 个问题）";
    }

    /** 关键词提取正则：连续 2 个以上的汉字或字母。 */
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,}|[A-Za-z]{2,}");

    /**
     * ENG-10：AI token 用量落库（ai_usage 表，见 Data/01_建库建表/ai_usage_schema.sql）。
     * 异步写入不阻塞主流程；表不存在时静默降级为日志。
     */
    private void recordUsage(int promptTokens, int completionTokens, int totalTokens, String scene) {
        try {
            cn.kmbeast.pojo.entity.AiUsage usage = new cn.kmbeast.pojo.entity.AiUsage();
            Integer uid = null;
            try {
                uid = cn.kmbeast.context.LocalThreadHolder.getUserId();
            } catch (Exception ignored) {
            }
            usage.setUserId(uid);
            usage.setScene(scene);
            usage.setModel(aiConfig.getModel());
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setTotalTokens(totalTokens);
            aiUsageMapper.insert(usage);
        } catch (Exception e) {
            log.debug("[AI] token 用量落库失败（表不存在或不可用）: {}", e.getMessage());
        }
    }

    private String callDeepSeekApi(String requestBody) throws IOException {
        return callDeepSeekApi(requestBody, aiConfig.getApiUrl(), aiConfig.getApiKey());
    }

    private String callDeepSeekApi(String requestBody, String apiUrl, String apiKey) throws IOException {
        // SEC-08: route through LLMProviderFactory - vendor switch + circuit breaker + retry
        String responseBody;
        try {
            responseBody = llmProviderFactory.getProvider().chat(requestBody, apiUrl, apiKey);
        } catch (IllegalStateException e) {
            log.warn("[AI] LLM 不可用(熔断): {}", e.getMessage());
            throw new IOException("AI 服务暂时不可用（熔断中），请稍后重试");
        } catch (IOException e) {
            log.error("LLM API调用失败: {}", e.getMessage());
            throw e;
        }
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
                    int prompt = usage.getInteger("prompt_tokens");
                    int completion = usage.getInteger("completion_tokens");
                    int total = usage.getInteger("total_tokens");
                    log.info("Token使用: prompt={}, completion={}, total={}", prompt, completion, total);
                    // ENG-10：token 成本落库（失败不影响主流程）
                    recordUsage(prompt, completion, total, "chat");
                }

                return content;
            }
            throw new IOException("AI服务返回为空");
    }

    /**
     * RAG-11：按句子分块提取与关键词相关的片段，替代整文硬截断。
     * 优先拼接含关键词的句子；不足上限时补相邻句子；仍不足则回退开头截断。
     */
    private String extractRelevantChunk(String content, List<String> keywords, int maxLen) {
        if (content == null || content.isEmpty()) return "";
        if (content.length() <= maxLen) return content;

        // 按中文句子分隔符切分（保留分隔符，避免截断半句）
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : content.toCharArray()) {
            current.append(c);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n') {
                sentences.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) sentences.add(current.toString());
        if (sentences.isEmpty()) {
            return content.substring(0, Math.min(maxLen, content.length())) + "...";
        }

        // 命中关键词的句子优先
        List<String> hit = new ArrayList<>();
        List<String> miss = new ArrayList<>();
        for (String s : sentences) {
            boolean matched = false;
            if (keywords != null) {
                for (String kw : keywords) {
                    if (kw != null && kw.trim().length() >= 2 && s.contains(kw.trim())) {
                        matched = true;
                        break;
                    }
                }
            }
            (matched ? hit : miss).add(s);
        }

        StringBuilder sb = new StringBuilder();
        for (String s : hit) {
            if (sb.length() + s.length() > maxLen) break;
            sb.append(s);
        }
        for (String s : miss) {
            if (sb.length() + s.length() > maxLen) break;
            sb.append(s);
        }
        if (sb.length() > 0) {
            return sb.length() >= content.length() ? sb.toString() : sb + "...";
        }
        return content.substring(0, Math.min(maxLen, content.length())) + "...";
    }

    private Map<String, Object> buildMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
