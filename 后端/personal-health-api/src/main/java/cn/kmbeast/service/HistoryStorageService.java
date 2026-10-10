package cn.kmbeast.service;

import cn.kmbeast.pojo.entity.AiChatRecord;
import cn.kmbeast.pojo.entity.AiConversation;

import java.util.List;
import java.util.Map;

/**
 * 历史会话JSON存储服务
 * 按用户ID隔离存储
 */
public interface HistoryStorageService {

    /**
     * 保存会话到用户文件夹
     */
    void saveConversation(Integer userId, AiConversation conversation, List<AiChatRecord> messages);

    /**
     * 加载用户的所有会话索引
     */
    List<AiConversation> loadConversations(Integer userId);

    /**
     * 加载指定会话的消息
     */
    List<AiChatRecord> loadMessages(Integer userId, Integer conversationId);

    /**
     * 删除会话
     */
    void deleteConversation(Integer userId, Integer conversationId);

    /**
     * 批量删除会话的本地副本。
     *
     * <p>2026-10-07 新增。{@code batchDeleteConversations} 此前只删 MySQL 与内存缓存，
     * 不删磁盘 JSON，导致「批量删除会话」在 {@code chat_history/} 留下永不回收的孤儿文件。</p>
     *
     * @param userId         属主（用于定位用户目录）
     * @param conversationIds 会话 id 列表
     */
    void batchDeleteConversations(Integer userId, List<Integer> conversationIds);

    /**
     * 清理磁盘上已无 MySQL 对应记录的孤儿文件，并按保留天数与容量上限淘汰过旧会话。
     *
     * <p>2026-10-07 新增。此前本服务零清理策略，用户不删会话则 JSON 永久累积。</p>
     *
     * @param liveConversationIdsFn 供给「当前有效会话 id」的函数（避免本层反向依赖 Mapper）
     * @param retentionDays         保留天数，≤0 表示不按天淘汰
     * @param maxTotalFiles         总文件数上限，≤0 表示不按容量淘汰
     * @return 被删除的文件数
     */
    int cleanup(Integer retentionDays, int maxTotalFiles,
                java.util.function.IntFunction<java.util.Set<Integer>> liveConversationIdsFn);
}
