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

    /**
     * 将缓存持久化到JSON文件
     *
     * @param conversationId 会话ID
     */
    void persistToFile(Integer conversationId);

    /**
     * 从JSON文件加载缓存
     *
     * @param conversationId 会话ID
     * @return 是否加载成功
     */
    boolean loadFromFile(Integer conversationId);
}
