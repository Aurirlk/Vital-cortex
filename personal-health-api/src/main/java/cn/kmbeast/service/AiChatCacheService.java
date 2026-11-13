package cn.kmbeast.service;

import cn.kmbeast.pojo.entity.AiChatRecord;
import cn.kmbeast.pojo.entity.AiConversation;

import java.util.List;
import java.util.Map;

/**
 * AI对话缓存服务接口
 * 提供基于JSON的本地缓存机制
 */
public interface AiChatCacheService {

    /**
     * 创建新会话
     *
     * @param userId    用户ID
     * @param agentType AI角色类型
     * @param title     会话标题（可选）
     * @return 会话信息
     */
    AiConversation createConversation(Integer userId, String agentType, String title);

    /**
     * 获取会话详情（从缓存或数据库）
     *
     * @param conversationId 会话ID
     * @return 会话信息
     */
    AiConversation getConversation(Integer conversationId);

    /**
     * 获取用户的会话列表
     *
     * @param userId    用户ID
     * @param agentType AI角色类型（可选）
     * @return 会话列表
     */
    List<AiConversation> getConversationList(Integer userId, String agentType);

    /**
     * 添加消息到会话（同时写入缓存和数据库）
     *
     * @param conversationId 会话ID
     * @param record        聊天记录
     */
    void addMessage(Integer conversationId, AiChatRecord record);

    /**
     * 获取会话的消息列表（优先从缓存读取）
     *
     * @param conversationId 会话ID
     * @return 消息列表
     */
    List<AiChatRecord> getMessages(Integer conversationId);

    /**
     * 校验会话是否属于指定用户（SEC-03 越权防护）。
     *
     * <p>会话相关接口此前只按路径中的 conversationId 取数，不校验归属，
     * 任意登录用户改一个数字即可读取/删除他人的问诊记录。所有涉及
     * conversationId 的操作都必须先过这道校验。
     *
     * @param conversationId 会话ID
     * @param userId         当前登录用户ID
     * @return true 表示归属该用户
     */
    boolean isOwnedBy(Integer conversationId, Integer userId);

    /**
     * 删除会话及其所有消息
     *
     * @param conversationId 会话ID
     */
    void deleteConversation(Integer conversationId);

    /**
     * 批量删除会话
     *
     * @param conversationIds 会话ID列表
     */
    void batchDeleteConversations(List<Integer> conversationIds);

    /**
     * 清除指定会话的缓存
     *
     * @param conversationId 会话ID
     */
    void evictCache(Integer conversationId);

    /**
     * 清除所有缓存
     */
    void evictAllCache();

    /**
     * 获取缓存统计信息
     *
     * @return 统计信息
     */
    Map<String, Object> getCacheStats();

    // =========================================================================
    // 2026-10-03 移除的接口：persistToFile / loadFromFile / restoreAllFromJson
    //
    // 移除原因：这三个方法的实现全是空壳（{} / return false / return emptyMap）。
    //   其中 restoreAllFromJson 还被 POST /ai/restore-from-json 调用，
    //   管理员点「从 JSON 备份恢复」会拿到 code=200 的「成功」响应但什么都没发生
    //   ——典型的静默失败，比直接报错更危险。
    //
    // 实际持久化早已由 HistoryStorageService 承担
    //   （saveConversation / loadConversations / loadMessages 均为真实实现，
    //     且它就是主存储、并非「备份」），因此不存在需要「从 JSON 备份恢复」的场景。
    //
    // 如未来确需离线备份能力，应重新设计为独立的 BackupService，
    // 而不是在缓存类里挂三个没有实现的方法。
    // =========================================================================
}
