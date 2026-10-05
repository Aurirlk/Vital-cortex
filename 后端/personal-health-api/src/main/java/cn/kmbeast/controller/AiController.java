package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.config.AiPromptConfig;
import cn.kmbeast.config.SentinelBlockHandlers;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.AiChatRecordQueryDto;
import cn.kmbeast.pojo.dto.update.AiChatRequest;
import cn.kmbeast.pojo.entity.AiChatRecord;
import cn.kmbeast.pojo.entity.AiConversation;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.service.AiChatCacheService;
import cn.kmbeast.service.AiHealthDataService;
import cn.kmbeast.service.AiService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI健康分析 Controller
 * 包含：聊天、会话管理、健康数据查询、管理员配置
 */
@Slf4j
@RestController
@RequestMapping(value = "/ai")
public class AiController {

    @Resource
    private AiService aiService;

    @Resource
    private AiHealthDataService aiHealthDataService;

    @Resource
    private AiChatCacheService chatCacheService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private cn.kmbeast.config.AiConfig aiConfig;

    @Resource
    private cn.kmbeast.core.auth.AuthSessionManager authSessionManager;

    // ==================== 聊天接口 ====================

    /**
     * 发送聊天消息
     *
     * @param chatRequest 聊天请求（含 conversationId, message, role 等）
     * @return AI回复（含 conversationId）
     */
    @Protector
    @SentinelResource(value = "ai:chat",
            blockHandler = "chatBlocked", blockHandlerClass = SentinelBlockHandlers.class)
    @PostMapping(value = "/chat")
    public Result<Map<String, String>> chat(@RequestBody AiChatRequest chatRequest) {
        Integer userId = LocalThreadHolder.getUserId();
        return aiService.chat(chatRequest, userId);
    }

    @Protector
    @PostMapping(value = "/keywords/extract")
    public Result<List<String>> extractKeywords(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ApiResult.success(new ArrayList<>());
        }
        List<String> keywords = aiService.extractKeywords(message);
        log.info("[AI] 关键词提取: \"{}\" -> {}", message, keywords);
        return ApiResult.success(keywords);
    }

    /**
     * AI医生流式对话（SSE）
     */
    @Protector
    @SentinelResource(value = "ai:chatStream",
            blockHandler = "chatStreamBlocked", blockHandlerClass = SentinelBlockHandlers.class)
    @PostMapping(value = "/chat/stream")
    public void chatStream(@RequestBody AiChatRequest chatRequest,
                           HttpServletResponse response) {
        log.info("[AI Controller] 收到流式对话请求: role={}, message={}", 
                chatRequest.getRole(), 
                chatRequest.getMessage() != null ? chatRequest.getMessage().substring(0, Math.min(30, chatRequest.getMessage().length())) : "null");
        
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        Integer userId = LocalThreadHolder.getUserId();
        log.info("[AI Controller] 用户ID: {}", userId);

        PrintWriter writer = null;
        try {
            writer = response.getWriter();
            // lambda 只能引用 effectively-final 变量，取副本供回调使用
            final PrintWriter w = writer;

            AiService.StreamCallback callback = (eventName, jsonData) -> {
                // SEC-10：客户端断开检测——write/checkError 任一失败立即中断流式输出，
                // 避免长连接堆积耗尽 Tomcat 线程。
                if (w.checkError()) {
                    throw new RuntimeException("SSE client disconnected");
                }
                w.write("event: " + eventName + "\n");
                w.write("data: " + jsonData + "\n\n");
                w.flush();
            };

            aiService.chatStream(chatRequest, userId, callback);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("SSE client disconnected")) {
                log.info("[AI Controller] 客户端断开连接: userId={}", userId);
            } else {
                log.error("AI流式响应异常", e);
            }
        } catch (IOException e) {
            log.error("AI流式响应异常", e);
        } finally {
            // SEC-10 整改：writer 关闭移入 finally，异常路径也会释放
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {
                    // 客户端已断开时 close 可能抛异常，忽略即可
                }
            }
        }
    }

    // ==================== 会话管理接口 ====================

    /**
     * 获取 AI 能力开关（Phase A/B：图片多模态 + VIP 分级；语音在 roadmap §1.1 接入后扩展）。
     * 前端据此决定图片入口是否可用、上下文/图片档位（VIP 512K/10张 vs 普通 128K/3张）。
     */
    @Protector
    @GetMapping(value = "/config/capabilities")
    public Result<Map<String, Object>> getCapabilities() {
        Integer userId = LocalThreadHolder.getUserId();
        boolean vip = authSessionManager.isVip(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("visionEnabled", aiConfig.isVisionEnabled());
        data.put("visionModel", aiConfig.getVisionModel());
        data.put("isVip", vip);
        data.put("maxContext", vip ? aiConfig.getContextVip() : aiConfig.getContextNormal());
        data.put("maxImages", vip ? aiConfig.getMaxImagesVip() : aiConfig.getMaxImagesNormal());
        data.put("asrEnabled", false);
        data.put("ttsEnabled", false);
        return ApiResult.success(data);
    }

    /**
     * 获取用户的会话列表
     *
     * @param agentType AI角色类型（可选）
     * @return 会话列表
     */
    @Protector
    @GetMapping(value = "/conversations")
    public Result<List<AiConversation>> getConversations(
            @RequestParam(value = "agentType", required = false) String agentType) {
        Integer userId = LocalThreadHolder.getUserId();
        List<AiConversation> conversations = chatCacheService.getConversationList(userId, agentType);
        return ApiResult.success(conversations);
    }

    /**
     * 获取指定会话的消息列表
     *
     * @param conversationId 会话ID
     * @return 消息列表
     */
    @Protector
    @GetMapping(value = "/conversations/{conversationId}/messages")
    public Result<List<AiChatRecord>> getConversationMessages(
            @PathVariable("conversationId") Integer conversationId) {
        // SEC-03：必须校验会话归属，否则改一个数字即可读取他人问诊记录
        Integer userId = LocalThreadHolder.getUserId();
        if (!chatCacheService.isOwnedBy(conversationId, userId)) {
            return ApiResult.error("会话不存在或无权访问");
        }
        List<AiChatRecord> messages = chatCacheService.getMessages(conversationId);
        return ApiResult.success(messages);
    }

    /**
     * 删除会话
     *
     * @param conversationId 会话ID
     * @return 操作结果
     */
    @Protector
    @DeleteMapping(value = "/conversations/{conversationId}")
    public Result<Void> deleteConversation(
            @PathVariable("conversationId") Integer conversationId) {
        // SEC-03：越权删除防护
        Integer userId = LocalThreadHolder.getUserId();
        if (!chatCacheService.isOwnedBy(conversationId, userId)) {
            return ApiResult.error("会话不存在或无权访问");
        }
        chatCacheService.deleteConversation(conversationId);
        return ApiResult.success("删除成功", (Void) null);
    }

    /**
     * 批量删除会话
     *
     * @param conversationIds 会话ID列表
     * @return 操作结果
     */
    @Protector
    @PostMapping(value = "/conversations/batchDelete")
    public Result<Void> batchDeleteConversations(@RequestBody List<Integer> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return ApiResult.error("请选择要删除的会话");
        }
        // SEC-03：逐个校验归属，任一不属于当前用户则整批拒绝，
        // 不做"过滤掉非法项后继续执行"——那样会掩盖攻击行为
        Integer userId = LocalThreadHolder.getUserId();
        for (Integer id : conversationIds) {
            if (!chatCacheService.isOwnedBy(id, userId)) {
                return ApiResult.error("存在不存在或无权访问的会话，操作已取消");
            }
        }
        chatCacheService.batchDeleteConversations(conversationIds);
        return ApiResult.success("批量删除成功", (Void) null);
    }

    // ==================== 健康数据接口 ====================

    /**
     * 获取当前用户的健康档案（供AI分析使用）
     *
     * @return 健康档案数据
     */
    @Protector
    @GetMapping(value = "/health/profile")
    public Result<Map<String, Object>> getHealthProfile() {
        Integer userId = LocalThreadHolder.getUserId();
        return aiHealthDataService.getUserHealthProfile(userId);
    }

    /**
     * 获取当前用户最近N天的健康记录
     *
     * @param days 天数（默认30天）
     * @return 健康记录列表
     */
    @Protector
    @GetMapping(value = "/health/records")
    public Result<Map<String, Object>> getRecentRecords(
            @RequestParam(value = "days", defaultValue = "30") Integer days) {
        Integer userId = LocalThreadHolder.getUserId();
        return aiHealthDataService.getRecentHealthRecords(userId, days);
    }

    /**
     * 获取当前用户的异常指标
     *
     * @return 异常指标列表
     */
    @Protector
    @GetMapping(value = "/health/abnormal")
    public Result<Map<String, Object>> getAbnormalIndicators() {
        Integer userId = LocalThreadHolder.getUserId();
        return aiHealthDataService.getAbnormalIndicators(userId);
    }

    // ==================== 管理员接口 ====================

    /**
     * 查询聊天记录（管理员）
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/records/query")
    public Result<?> queryRecords(@RequestBody AiChatRecordQueryDto queryDto) {
        return aiService.queryRecords(queryDto);
    }

    /**
     * 获取AI使用统计
     *
     * @return 统计数据
     */
    @Protector(role = "管理员")
    @GetMapping(value = "/stats")
    public Result<Map<String, Object>> getStats() {
        return aiService.getStats();
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计
     */
    @Protector(role = "管理员")
    @GetMapping(value = "/cache/stats")
    public Result<Map<String, Object>> getCacheStats() {
        return ApiResult.success(chatCacheService.getCacheStats());
    }

    /**
     * 清除所有缓存
     *
     * @return 操作结果
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/cache/evict")
    public Result<Void> evictAllCache() {
        chatCacheService.evictAllCache();
        return ApiResult.success("缓存已清除", (Void) null);
    }

    // ==================== AI医生配置管理接口 ====================

    /**
     * 获取所有AI医生角色配置
     */
    @Protector(role = "管理员")
    @GetMapping(value = "/config/list")
    public Result<List<Map<String, Object>>> getAiDoctorConfigs() {
        return ApiResult.success(AiPromptConfig.getAllConfigs());
    }

    /**
     * 获取指定角色配置
     */
    @Protector(role = "管理员")
    @GetMapping(value = "/config/{role}")
    public Result<Map<String, Object>> getAiDoctorConfig(@PathVariable String role) {
        AiPromptConfig.PresetConfig config = AiPromptConfig.getConfig(role);
        if (config == null) {
            return ApiResult.error("角色不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("key", role);
        result.put("systemPrompt", config.getSystemPrompt());
        result.put("temperature", config.getTemperature());
        result.put("topP", config.getTopP());
        result.put("maxTokens", config.getMaxTokens());
        result.put("presencePenalty", config.getPresencePenalty());
        result.put("frequencyPenalty", config.getFrequencyPenalty());
        result.put("repetitionPenalty", config.getRepetitionPenalty());
        result.put("contextRounds", config.getContextRounds());
        result.put("maxReplyLength", config.getMaxReplyLength());
        return ApiResult.success(result);
    }

    /**
     * 更新指定角色配置
     */
    @Protector(role = "管理员")
    @PutMapping(value = "/config/{role}")
    public Result<Void> updateAiDoctorConfig(@PathVariable String role,
                                              @RequestBody Map<String, Object> configData) {
        AiPromptConfig.PresetConfig existing = AiPromptConfig.getConfig(role);
        if (existing == null) {
            return ApiResult.error("角色不存在");
        }

        // 修改配置需要管理员密码验证
        String password = (String) configData.get("password");
        if (password == null || password.isEmpty()) {
            return ApiResult.error("请输入管理员密码");
        }
        Integer userId = LocalThreadHolder.getUserId();
        User admin = userMapper.getByActive(User.builder().id(userId).build());
        if (admin == null || !passwordEncoder.matches(password, admin.getUserPwd())) {
            return ApiResult.error("密码验证失败");
        }

        String systemPrompt = (String) configData.getOrDefault("systemPrompt", existing.getSystemPrompt());
        Double temperature = configData.containsKey("temperature")
                ? ((Number) configData.get("temperature")).doubleValue() : existing.getTemperature();
        Double topP = configData.containsKey("topP")
                ? ((Number) configData.get("topP")).doubleValue() : existing.getTopP();
        Integer maxTokens = configData.containsKey("maxTokens")
                ? (configData.get("maxTokens") == null ? null : ((Number) configData.get("maxTokens")).intValue())
                : existing.getMaxTokens();
        Double presencePenalty = configData.containsKey("presencePenalty")
                ? (configData.get("presencePenalty") == null ? null : ((Number) configData.get("presencePenalty")).doubleValue())
                : existing.getPresencePenalty();
        Double frequencyPenalty = configData.containsKey("frequencyPenalty")
                ? (configData.get("frequencyPenalty") == null ? null : ((Number) configData.get("frequencyPenalty")).doubleValue())
                : existing.getFrequencyPenalty();
        Double repetitionPenalty = configData.containsKey("repetitionPenalty")
                ? (configData.get("repetitionPenalty") == null ? null : ((Number) configData.get("repetitionPenalty")).doubleValue())
                : existing.getRepetitionPenalty();
        Integer contextRounds = configData.containsKey("contextRounds")
                ? (configData.get("contextRounds") == null ? null : ((Number) configData.get("contextRounds")).intValue())
                : existing.getContextRounds();
        Integer maxReplyLength = configData.containsKey("maxReplyLength")
                ? (configData.get("maxReplyLength") == null ? null : ((Number) configData.get("maxReplyLength")).intValue())
                : existing.getMaxReplyLength();

        // 参数校验
        if (temperature == null || temperature < 0 || temperature > 2) {
            return ApiResult.error("Temperature 必须在 0-2 之间");
        }
        if (topP == null || topP < 0 || topP > 1) {
            return ApiResult.error("Top-P 必须在 0-1 之间");
        }
        if (maxTokens != null && (maxTokens < 1 || maxTokens > 8192)) {
            return ApiResult.error("Max Tokens 必须在 1-8192 之间");
        }
        if (presencePenalty != null && (presencePenalty < -2 || presencePenalty > 2)) {
            return ApiResult.error("Presence Penalty 必须在 -2~2 之间");
        }
        if (frequencyPenalty != null && (frequencyPenalty < -2 || frequencyPenalty > 2)) {
            return ApiResult.error("Frequency Penalty 必须在 -2~2 之间");
        }
        if (repetitionPenalty != null && (repetitionPenalty < 0 || repetitionPenalty > 2)) {
            return ApiResult.error("重复惩罚必须在 0~2 之间（>1 减少重复，<1 鼓励重复）");
        }
        if (contextRounds != null && (contextRounds < 0 || contextRounds > 50)) {
            return ApiResult.error("上下文轮数必须在 0~50 之间");
        }
        if (maxReplyLength != null && (maxReplyLength < 0 || maxReplyLength > 32768)) {
            return ApiResult.error("最大回复长度必须在 0~32768 之间（0 = 不限制）");
        }

        AiPromptConfig.updateConfig(role, new AiPromptConfig.PresetConfig(
                systemPrompt, temperature, topP, maxTokens, presencePenalty, frequencyPenalty,
                repetitionPenalty, contextRounds, maxReplyLength));
        log.info("[AI配置] 管理员 {} 更新角色 {} 配置: temp={}, topP={}, maxTokens={}, presencePenalty={}, frequencyPenalty={}, repetitionPenalty={}, contextRounds={}, maxReplyLength={}",
                userId, role, temperature, topP, maxTokens, presencePenalty, frequencyPenalty,
                repetitionPenalty, contextRounds, maxReplyLength);
        return ApiResult.success("配置已更新");
    }

    /**
     * 重置指定角色为默认配置（需要密码验证）
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/config/{role}/reset")
    public Result<Void> resetAiDoctorConfig(@PathVariable String role,
                                             @RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.isEmpty()) {
            return ApiResult.error("请输入管理员密码");
        }

        // 验证当前管理员密码
        Integer userId = LocalThreadHolder.getUserId();
        User admin = userMapper.getByActive(User.builder().id(userId).build());
        if (admin == null || !passwordEncoder.matches(password, admin.getUserPwd())) {
            return ApiResult.error("密码验证失败");
        }

        AiPromptConfig.PresetConfig existing = AiPromptConfig.getConfig(role);
        if (existing == null) {
            return ApiResult.error("角色不存在");
        }

        boolean success = AiPromptConfig.resetToDefault(role);
        if (success) {
            log.info("[AI配置] 管理员 {} 重置角色 {} 为默认配置", userId, role);
            return ApiResult.success("已恢复默认提示词");
        }
        return ApiResult.error("重置失败，角色不存在");
    }

    /**
     * 重置所有角色为默认配置（需要密码验证）
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/config/reset-all")
    public Result<Void> resetAllAiDoctorConfigs(@RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.isEmpty()) {
            return ApiResult.error("请输入管理员密码");
        }

        Integer userId = LocalThreadHolder.getUserId();
        User admin = userMapper.getByActive(User.builder().id(userId).build());
        if (admin == null || !passwordEncoder.matches(password, admin.getUserPwd())) {
            return ApiResult.error("密码验证失败");
        }

        AiPromptConfig.resetAllToDefault();
        log.info("[AI配置] 管理员 {} 重置所有角色为默认配置", userId);
        return ApiResult.success("已恢复所有角色默认提示词");
    }

    /**
     * 从JSON备份文件恢复数据到数据库（管理员）
     *
     * @deprecated 2026-10-03 该功能是空壳：底层 {@code restoreAllFromJson()} 恒返回空集合，
     *     调用本接口只会得到 code=200 的「成功」响应但实际什么都没发生（静默失败）。
     *     真实持久化一直由 {@code HistoryStorageService} 承担，且它就是主存储、并非备份，
     *     因此不存在「从 JSON 备份恢复」的场景。现改为明确返回废弃说明，
     *     待确认无前端依赖后再移除本端点。
     */
    @Deprecated
    @Protector(role = "管理员")
    @PostMapping(value = "/restore-from-json")
    public Result<Map<String, Object>> restoreFromJson() {
        log.warn("[AI] /ai/restore-from-json 被调用，但该功能从未实现，现返回明确的废弃说明");
        Map<String, Object> result = new HashMap<>();
        result.put("deprecated", true);
        result.put("message", "该功能从未实现，已废弃。AI 对话数据直接存储于数据库，无需从 JSON 备份恢复。");
        result.put("action", "如需离线备份，请使用数据库层面的备份工具（mysqldump）。");
        return ApiResult.success(result);
    }
}
