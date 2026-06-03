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
}
