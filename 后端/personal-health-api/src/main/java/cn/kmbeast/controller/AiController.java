package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.config.AiPromptConfig;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
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

    // ==================== 聊天接口 ====================

    /**
     * 发送聊天消息
     *
     * @param chatRequest 聊天请求（含 conversationId, message, role 等）
     * @return AI回复（含 conversationId）
     */
    @Protector
    @PostMapping(value = "/chat")
    public Result<Map<String, String>> chat(@RequestBody AiChatRequest chatRequest) {
        Integer userId = LocalThreadHolder.getUserId();
        return aiService.chat(chatRequest, userId);
    }

    /**
     * AI医生流式对话（SSE）
     */
    @Protector
    @PostMapping(value = "/chat/stream")
    public void chatStream(@RequestBody AiChatRequest chatRequest,
                           HttpServletResponse response) {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        Integer userId = LocalThreadHolder.getUserId();

        try {
            PrintWriter writer = response.getWriter();

            AiService.StreamCallback callback = (eventName, jsonData) -> {
                writer.write("event: " + eventName + "\n");
                writer.write("data: " + jsonData + "\n\n");
                writer.flush();
            };

            aiService.chatStream(chatRequest, userId, callback);
            writer.close();
        } catch (IOException e) {
            log.error("AI流式响应异常", e);
        }
    }

    // ==================== 会话管理接口 ====================

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
     * 分页查询聊天记录
     *
     * @param queryDto 查询参数
     * @return 聊天记录列表
     */
    @Pager
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

        String systemPrompt = (String) configData.getOrDefault("systemPrompt", existing.getSystemPrompt());
        Double temperature = configData.containsKey("temperature")
                ? ((Number) configData.get("temperature")).doubleValue() : existing.getTemperature();
        Double topP = configData.containsKey("topP")
                ? ((Number) configData.get("topP")).doubleValue() : existing.getTopP();

        // 参数校验
        if (temperature < 0 || temperature > 2) {
            return ApiResult.error("Temperature 必须在 0-2 之间");
        }
        if (topP < 0 || topP > 1) {
            return ApiResult.error("Top-P 必须在 0-1 之间");
        }

        AiPromptConfig.updateConfig(role, new AiPromptConfig.PresetConfig(systemPrompt, temperature, topP));
        log.info("[AI配置] 角色 {} 配置已更新: temp={}, topP={}", role, temperature, topP);
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
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/restore-from-json")
    public Result<Map<String, Object>> restoreFromJson() {
        log.info("[AI] 开始从JSON备份文件恢复数据到数据库");
        Map<String, Object> result = chatCacheService.restoreAllFromJson();
        return ApiResult.success(result);
    }
}
