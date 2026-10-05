package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.AiChatRecordMapper;
import cn.kmbeast.mapper.AiConversationMapper;
import cn.kmbeast.pojo.entity.AiChatRecord;
import cn.kmbeast.pojo.entity.AiConversation;
import cn.kmbeast.service.AiChatCacheService;
import cn.kmbeast.service.HistoryStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI对话缓存服务
 * 数据流：MySQL(主存储) + 用户隔离JSON存储(备份)
 */
@Service
@Slf4j
public class AiChatCacheServiceImpl implements AiChatCacheService {

    @Resource
    private AiConversationMapper conversationMapper;

    @Resource
    private AiChatRecordMapper chatRecordMapper;

    @Resource
    private HistoryStorageService historyStorage;

    private final ConcurrentHashMap<Integer, List<AiChatRecord>> messageCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, AiConversation> conversationCache = new ConcurrentHashMap<>();

    /**
     * 缓存容量上限（2026-10-03 新增）。
     *
     * <p>此前两个 {@code ConcurrentHashMap} <b>无上限、无淘汰、无 TTL</b>，
     * 而类中又没有定时清理，导致每次 AI 对话都往里 put，长期运行必然 OOM。
     * 配合启动类新加的 {@code @EnableScheduling}，现由 {@link #evictOversizedCache()} 定期兜底。
     */
    private static final int MAX_CACHED_CONVERSATIONS = 500;

    /** 单个会话最多缓存的消息条数，超出后丢弃最旧的（完整历史仍在 MySQL 中） */
    private static final int MAX_MESSAGES_PER_CONVERSATION = 100;

    /**
     * 定时清理超限缓存（每 10 分钟）。
     *
     * <p>⚠️ 本方法此前不存在，且启动类缺少 {@code @EnableScheduling}，
     * 是「无界缓存必然 OOM」隐患的关键一环。开启后请观察日志中的淘汰记录。
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 60 * 1000)
    public void evictOversizedCache() {
        try {
            int evictedConversations = 0;
            int truncatedSessions = 0;

            // 1) 会话数超限：按 lastMessageTime 由旧到新淘汰，保证最近活跃的会话留在内存
            if (conversationCache.size() > MAX_CACHED_CONVERSATIONS) {
                List<Integer> ids = new ArrayList<>(conversationCache.keySet());
                ids.sort(Comparator.comparing(
                        (Integer id) -> {
                            AiConversation c = conversationCache.get(id);
                            return c == null || c.getLastMessageTime() == null
                                    ? LocalDateTime.MIN : c.getLastMessageTime();
                        },
                        Comparator.nullsFirst(Comparator.naturalOrder())));
                int removeCount = conversationCache.size() - MAX_CACHED_CONVERSATIONS;
                for (int i = 0; i < removeCount && i < ids.size(); i++) {
                    conversationCache.remove(ids.get(i));
                    messageCache.remove(ids.get(i));
                    evictedConversations++;
                }
            }

            // 2) 单会话消息数超限：截断保留最近 N 条（完整历史可从 MySQL 重新加载）
            for (Map.Entry<Integer, List<AiChatRecord>> entry : messageCache.entrySet()) {
                List<AiChatRecord> msgs = entry.getValue();
                if (msgs != null && msgs.size() > MAX_MESSAGES_PER_CONVERSATION) {
                    int overflow = msgs.size() - MAX_MESSAGES_PER_CONVERSATION;
                    for (int i = 0; i < overflow; i++) {
                        // List.remove(int) 返回「被移除的那个元素」而非 boolean，
                        // 写成 if (!msgs.remove(0)) 会编译失败（!AiChatRecord 不是布尔表达式），
                        // 且语义也不对：返回的元素可能是 null 但删除其实成功了。
                        // 这里只关心「删没删掉」，用 isEmpty 兜底。
                        if (msgs.isEmpty()) {
                            break;
                        }
                        msgs.remove(0);
                    }
                    truncatedSessions++;
                }
            }

            if (evictedConversations > 0 || truncatedSessions > 0) {
                log.info("[Cache] 缓存清理: 淘汰会话={}, 截断会话={}, 当前会话数={}/{}, 当前消息数={}",
                        evictedConversations, truncatedSessions,
                        conversationCache.size(), MAX_CACHED_CONVERSATIONS,
                        messageCache.values().stream().mapToInt(List::size).sum());
            }
        } catch (Exception e) {
            // 清理失败绝不能影响主流程
            log.warn("[Cache] 缓存清理异常: {}", e.getMessage());
        }
    }

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
    public List<AiConversation> getConversationList(Integer userId, String agentType) {
        List<AiConversation> list = conversationMapper.queryByUserId(userId, agentType);
        if (list == null || list.isEmpty()) {
            // 从JSON文件加载
            list = historyStorage.loadConversations(userId);
            if (list != null && !list.isEmpty()) {
                log.info("[Cache] 从JSON加载了{}个会话: userId={}", list.size(), userId);
            }
        }
        return list != null ? list : new ArrayList<>();
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

        // 4. 持久化到用户隔离的JSON文件
        if (conversation != null && conversation.getUserId() != null) {
            historyStorage.saveConversation(conversation.getUserId(), conversation, messages);
        }
    }

    /**
     * SEC-03：会话归属校验。以 MySQL 中的 user_id 为准，
     * 缓存命中也要保证缓存对象本身带有正确的 userId。
     */
    @Override
    public boolean isOwnedBy(Integer conversationId, Integer userId) {
        if (conversationId == null || userId == null) {
            return false;
        }
        AiConversation conv = conversationCache.get(conversationId);
        if (conv == null || conv.getUserId() == null) {
            conv = conversationMapper.getById(conversationId);
            if (conv != null) {
                conversationCache.put(conversationId, conv);
            }
        }
        // 会话不存在时同样返回 false，避免通过"存在/不存在"的响应差异探测他人会话ID
        return conv != null && userId.equals(conv.getUserId());
    }

    @Override
    public List<AiChatRecord> getMessages(Integer conversationId) {
        List<AiChatRecord> messages = messageCache.get(conversationId);
        if (messages != null && !messages.isEmpty()) {
            return new ArrayList<>(messages);
        }

        messages = chatRecordMapper.getByConversationId(conversationId);
        if (messages != null && !messages.isEmpty()) {
            messageCache.put(conversationId, new ArrayList<>(messages));
            return messages;
        }

        // 从JSON加载
        AiConversation conv = conversationMapper.getById(conversationId);
        if (conv != null && conv.getUserId() != null) {
            messages = historyStorage.loadMessages(conv.getUserId(), conversationId);
            if (messages != null && !messages.isEmpty()) {
                messageCache.put(conversationId, new ArrayList<>(messages));
            }
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
    public AiConversation getConversation(Integer conversationId) {
        AiConversation conv = conversationCache.get(conversationId);
        if (conv != null) return conv;
        conv = conversationMapper.getById(conversationId);
        if (conv != null) conversationCache.put(conversationId, conv);
        return conv;
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

    // 2026-10-03 移除了 persistToFile / loadFromFile / restoreAllFromJson 三个空实现，
    // 原因与接口中的说明一致：真实持久化由 HistoryStorageService 承担，
    // 保留空壳只会让「从 JSON 备份恢复」接口返回假成功（详见 AiChatCacheService 注释）。

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
