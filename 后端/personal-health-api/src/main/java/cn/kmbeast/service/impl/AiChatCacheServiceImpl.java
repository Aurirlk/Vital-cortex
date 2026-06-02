package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.AiChatRecordMapper;
import cn.kmbeast.mapper.AiConversationMapper;
import cn.kmbeast.pojo.entity.AiChatRecord;
import cn.kmbeast.pojo.entity.AiConversation;
import cn.kmbeast.service.AiChatCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI对话缓存服务
 * 
 * 数据流：
 *   写操作 → MySQL(主存储) + 内存缓存
 *   读操作 → 内存缓存 → MySQL
 */
@Service
@Slf4j
public class AiChatCacheServiceImpl implements AiChatCacheService {

    @Resource
    private AiConversationMapper conversationMapper;

    @Resource
    private AiChatRecordMapper chatRecordMapper;

    /** 内存缓存：conversationId -> 消息列表 */
    private final ConcurrentHashMap<Integer, List<AiChatRecord>> messageCache = new ConcurrentHashMap<>();

    /** 内存缓存：conversationId -> 会话信息 */
    private final ConcurrentHashMap<Integer, AiConversation> conversationCache = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public AiConversation createConversation(Integer userId, String agentType, String title) {
        if (title == null || title.isEmpty()) {
            title = getRoleName(agentType) + " - " + LocalDateTime.now().toString().substring(0, 16).replace("T", " ");
        }

        AiConversation conversation = AiConversation.builder()
                .userId(userId)
                .title(title)
                .agentType(agentType)
                .messageCount(0)
                .lastMessageTime(LocalDateTime.now())
                .build();

        conversationMapper.save(conversation);
        conversationCache.put(conversation.getId(), conversation);
        messageCache.put(conversation.getId(), new ArrayList<>());

        log.info("[Cache] 创建会话: id={}, userId={}, agentType={}", conversation.getId(), userId, agentType);
        return conversation;
    }

    @Override
    public AiConversation getConversation(Integer conversationId) {
        AiConversation conversation = conversationCache.get(conversationId);
        if (conversation != null) return conversation;

        conversation = conversationMapper.getById(conversationId);
        if (conversation != null) {
            conversationCache.put(conversationId, conversation);
        }
        return conversation;
    }

    @Override
    public List<AiConversation> getConversationList(Integer userId, String agentType) {
        return conversationMapper.queryByUserId(userId, agentType);
    }

    @Override
    @Transactional
    public void addMessage(Integer conversationId, AiChatRecord record) {
        record.setConversationId(conversationId);

        // 1. 写入MySQL
        chatRecordMapper.save(record);
        log.info("[Cache] 保存消息: conversationId={}, role={}", conversationId, record.getRole());

        // 2. 更新内存缓存
        List<AiChatRecord> messages = messageCache.computeIfAbsent(conversationId, k -> new ArrayList<>());
        messages.add(record);

        // 3. 更新会话信息
        AiConversation conversation = conversationCache.get(conversationId);
        if (conversation == null) {
            conversation = conversationMapper.getById(conversationId);
        }
        if (conversation != null) {
            conversation.setMessageCount(conversation.getMessageCount() + 1);
            conversation.setLastMessageTime(LocalDateTime.now());
            if (conversation.getMessageCount() == 1 && "user".equals(record.getRole())) {
                conversation.setTitle(generateTitle(record.getContent()));
            }
            conversationMapper.update(AiConversation.builder()
                    .id(conversationId)
                    .messageCount(conversation.getMessageCount())
                    .lastMessageTime(conversation.getLastMessageTime())
                    .title(conversation.getTitle())
                    .build());
            conversationCache.put(conversationId, conversation);
        }
    }

    @Override
    public List<AiChatRecord> getMessages(Integer conversationId) {
        // 1. 先查缓存
        List<AiChatRecord> messages = messageCache.get(conversationId);
        if (messages != null && !messages.isEmpty()) {
            return new ArrayList<>(messages);
        }

        // 2. 查MySQL
        messages = chatRecordMapper.getByConversationId(conversationId);
        if (messages != null && !messages.isEmpty()) {
            messageCache.put(conversationId, new ArrayList<>(messages));
        }
        return messages != null ? messages : new ArrayList<>();
    }

    @Override
    @Transactional
    public void deleteConversation(Integer conversationId) {
        chatRecordMapper.deleteByConversationId(conversationId);
        conversationMapper.deleteById(conversationId);
        messageCache.remove(conversationId);
        conversationCache.remove(conversationId);
        log.info("[Cache] 删除会话: conversationId={}", conversationId);
    }

    @Override
    @Transactional
    public void batchDeleteConversations(List<Integer> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) return;
        chatRecordMapper.batchDeleteByConversationIds(conversationIds);
        conversationMapper.batchDelete(conversationIds);
        conversationIds.forEach(id -> {
            messageCache.remove(id);
            conversationCache.remove(id);
        });
        log.info("[Cache] 批量删除: count={}", conversationIds.size());
    }

    @Override
    public void evictCache(Integer conversationId) {
        messageCache.remove(conversationId);
        conversationCache.remove(conversationId);
    }

    @Override
    public void evictAllCache() {
        messageCache.clear();
        conversationCache.clear();
    }

    @Override
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("cachedConversations", conversationCache.size());
        stats.put("cachedMessages", messageCache.values().stream().mapToInt(List::size).sum());
        return stats;
    }

    @Override
    public void persistToFile(Integer conversationId) {
        // 不再需要，保留接口兼容
    }

    @Override
    public boolean loadFromFile(Integer conversationId) {
        // 不再需要，保留接口兼容
        return false;
    }

    @Override
    public Map<String, Object> restoreAllFromJson() {
        // 不再需要，保留接口兼容
        return Collections.emptyMap();
    }

    private String getRoleName(String agentType) {
        switch (agentType != null ? agentType : "") {
            case "doctor": return "全科医生";
            case "nutritionist": return "营养师";
            case "psychologist": return "心理咨询";
            case "analyst": return "报告分析";
            case "general_assistant": return "全能助手";
            case "consultant": return "健康助手";
            default: return "AI助手";
        }
    }

    private String generateTitle(String content) {
        if (content == null || content.isEmpty()) return "新会话";
        return content.length() > 20 ? content.substring(0, 20) + "..." : content;
    }
}
