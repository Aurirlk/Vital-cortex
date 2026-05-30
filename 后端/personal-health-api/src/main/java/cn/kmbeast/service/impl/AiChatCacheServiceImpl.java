package cn.kmbeast.service.impl;

import cn.kmbeast.config.AiConfig;
import cn.kmbeast.mapper.AiChatRecordMapper;
import cn.kmbeast.mapper.AiConversationMapper;
import cn.kmbeast.pojo.entity.AiChatRecord;
import cn.kmbeast.pojo.entity.AiConversation;
import cn.kmbeast.service.AiChatCacheService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI对话缓存服务实现
 * 使用 ConcurrentHashMap 内存缓存 + JSON文件持久化
 */
@Service
@Slf4j
public class AiChatCacheServiceImpl implements AiChatCacheService {

    @Resource
    private AiConversationMapper conversationMapper;

    @Resource
    private AiChatRecordMapper chatRecordMapper;

    @Resource
    private AiConfig aiConfig;

    /**
     * 内存缓存：conversationId -> List<AiChatRecord>
     */
    private final ConcurrentHashMap<Integer, List<AiChatRecord>> messageCache = new ConcurrentHashMap<>();

    /**
     * 内存缓存：conversationId -> AiConversation
     */
    private final ConcurrentHashMap<Integer, AiConversation> conversationCache = new ConcurrentHashMap<>();

    /**
     * 缓存最大会话数（超过后自动淘汰最久未访问的）
     */
    private static final int MAX_CACHE_SIZE = 1000;

    /**
     * 每个会话最大缓存消息数
     */
    private static final int MAX_MESSAGES_PER_CONVERSATION = 100;

    /**
     * JSON缓存目录
     */
    private String cacheDir;

    @PostConstruct
    public void init() {
        cacheDir = aiConfig.getVectorCacheDir().replace("vector_cache", "chat_cache");
        File dir = new File(cacheDir);
        if (!dir.exists()) {
            dir.mkdirs();
            log.info("[Cache] 对话缓存目录已创建: {}", dir.getAbsolutePath());
        }
        log.info("[Cache] AI对话缓存服务初始化完成, 缓存目录: {}", cacheDir);
    }

    @Override
    @Transactional
    public AiConversation createConversation(Integer userId, String agentType, String title) {
        // 自动生成标题：角色名 + 时间
        if (title == null || title.isEmpty()) {
            String roleName = getRoleName(agentType);
            title = roleName + " - " + LocalDateTime.now().toString().substring(0, 16).replace("T", " ");
        }

        AiConversation conversation = AiConversation.builder()
                .userId(userId)
                .title(title)
                .agentType(agentType)
                .messageCount(0)
                .lastMessageTime(LocalDateTime.now())
                .build();

        // 保存到数据库
        conversationMapper.save(conversation);

        // 放入缓存
        conversationCache.put(conversation.getId(), conversation);
        messageCache.put(conversation.getId(), new ArrayList<>());

        log.info("[Cache] 创建新会话: id={}, userId={}, agentType={}", conversation.getId(), userId, agentType);
        return conversation;
    }

    @Override
    public AiConversation getConversation(Integer conversationId) {
        // 1. 先从缓存取
        AiConversation conversation = conversationCache.get(conversationId);
        if (conversation != null) {
            return conversation;
        }

        // 2. 缓存未命中，从数据库取
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
        // 1. 设置会话ID
        record.setConversationId(conversationId);

        // 2. 保存到数据库
        chatRecordMapper.save(record);

        // 3. 更新内存缓存
        List<AiChatRecord> messages = messageCache.computeIfAbsent(conversationId, k -> new ArrayList<>());
        messages.add(record);

        // 4. 如果缓存消息数超过限制，移除最早的
        if (messages.size() > MAX_MESSAGES_PER_CONVERSATION) {
            messages.remove(0);
        }

        // 5. 更新会话信息
        AiConversation conversation = conversationCache.get(conversationId);
        if (conversation != null) {
            conversation.setMessageCount(conversation.getMessageCount() + 1);
            conversation.setLastMessageTime(LocalDateTime.now());

            // 6. 如果是第一条用户消息，自动生成标题
            if (conversation.getMessageCount() == 1 && "user".equals(record.getRole())) {
                String title = generateTitle(record.getContent());
                conversation.setTitle(title);
            }
        } else {
            conversation = conversationMapper.getById(conversationId);
            if (conversation != null) {
                conversation.setMessageCount(conversation.getMessageCount() + 1);
                conversation.setLastMessageTime(LocalDateTime.now());
                conversationCache.put(conversationId, conversation);
            }
        }

        // 7. 异步更新数据库中的会话信息
        if (conversation != null) {
            AiConversation update = AiConversation.builder()
                    .id(conversationId)
                    .messageCount(conversation.getMessageCount())
                    .lastMessageTime(conversation.getLastMessageTime())
                    .title(conversation.getTitle())
                    .build();
            conversationMapper.update(update);
        }

        log.debug("[Cache] 添加消息: conversationId={}, role={}", conversationId, record.getRole());
    }

    @Override
    public List<AiChatRecord> getMessages(Integer conversationId) {
        // 1. 先从缓存取
        List<AiChatRecord> messages = messageCache.get(conversationId);
        if (messages != null && !messages.isEmpty()) {
            return new ArrayList<>(messages);
        }

        // 2. 缓存未命中，从数据库取
        messages = chatRecordMapper.getByConversationId(conversationId);
        if (messages != null && !messages.isEmpty()) {
            // 放入缓存
            messageCache.put(conversationId, new ArrayList<>(messages));
        }
        return messages != null ? messages : new ArrayList<>();
    }

    @Override
    @Transactional
    public void deleteConversation(Integer conversationId) {
        // 1. 删除数据库记录
        chatRecordMapper.deleteByConversationId(conversationId);
        conversationMapper.deleteById(conversationId);

        // 2. 清除缓存
        evictCache(conversationId);

        // 3. 删除JSON文件
        File jsonFile = new File(cacheDir, conversationId + ".json");
        if (jsonFile.exists()) {
            jsonFile.delete();
        }

        log.info("[Cache] 删除会话: conversationId={}", conversationId);
    }

    @Override
    @Transactional
    public void batchDeleteConversations(List<Integer> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return;
        }

        // 1. 批量删除数据库记录
        chatRecordMapper.batchDeleteByConversationIds(conversationIds);
        conversationMapper.batchDelete(conversationIds);

        // 2. 清除缓存
        for (Integer id : conversationIds) {
            evictCache(id);
            File jsonFile = new File(cacheDir, id + ".json");
            if (jsonFile.exists()) {
                jsonFile.delete();
            }
        }

        log.info("[Cache] 批量删除会话: count={}", conversationIds.size());
    }

    @Override
    public void evictCache(Integer conversationId) {
        messageCache.remove(conversationId);
        conversationCache.remove(conversationId);
        log.debug("[Cache] 清除缓存: conversationId={}", conversationId);
    }

    @Override
    public void evictAllCache() {
        messageCache.clear();
        conversationCache.clear();
        log.info("[Cache] 清除所有缓存");
    }

    @Override
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("缓存会话数", conversationCache.size());
        stats.put("缓存消息总数", messageCache.values().stream().mapToInt(List::size).sum());
        stats.put("最大缓存会话数", MAX_CACHE_SIZE);
        stats.put("每会话最大消息数", MAX_MESSAGES_PER_CONVERSATION);
        stats.put("缓存目录", cacheDir);
        return stats;
    }

    @Override
    public void persistToFile(Integer conversationId) {
        try {
            List<AiChatRecord> messages = messageCache.get(conversationId);
            if (messages == null || messages.isEmpty()) {
                return;
            }

            AiConversation conversation = conversationCache.get(conversationId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("conversation", conversation);
            data.put("messages", messages);

            String json = JSON.toJSONString(data, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);
            Path filePath = Paths.get(cacheDir, conversationId + ".json");
            Files.write(filePath, json.getBytes(StandardCharsets.UTF_8));

            log.info("[Cache] 持久化到文件: conversationId={}, size={}", conversationId, messages.size());
        } catch (IOException e) {
            log.error("[Cache] 持久化文件失败: conversationId={}", conversationId, e);
        }
    }

    @Override
    public boolean loadFromFile(Integer conversationId) {
        try {
            Path filePath = Paths.get(cacheDir, conversationId + ".json");
            if (!Files.exists(filePath)) {
                return false;
            }

            String json = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            Map<String, Object> data = JSON.parseObject(json, new TypeReference<Map<String, Object>>() {});

            AiConversation conversation = JSON.parseObject(JSON.toJSONString(data.get("conversation")), AiConversation.class);
            List<AiChatRecord> messages = JSON.parseArray(JSON.toJSONString(data.get("messages")), AiChatRecord.class);

            if (conversation != null) {
                conversationCache.put(conversationId, conversation);
            }
            if (messages != null) {
                messageCache.put(conversationId, new ArrayList<>(messages));
            }

            log.info("[Cache] 从文件加载: conversationId={}, messages={}", conversationId, messages != null ? messages.size() : 0);
            return true;
        } catch (Exception e) {
            log.error("[Cache] 加载文件失败: conversationId={}", conversationId, e);
            return false;
        }
    }

    /**
     * 根据AI角色类型获取中文名称
     */
    private String getRoleName(String agentType) {
        Map<String, String> roleNames = new HashMap<>();
        roleNames.put("doctor", "全科医生");
        roleNames.put("nutritionist", "营养师");
        roleNames.put("psychologist", "心理咨询");
        roleNames.put("analyst", "报告分析");
        roleNames.put("general_assistant", "全能助手");
        return roleNames.getOrDefault(agentType, "AI助手");
    }

    /**
     * 从用户消息自动生成会话标题
     * 取前20个字符作为标题
     */
    private String generateTitle(String content) {
        if (content == null || content.isEmpty()) {
            return "新对话";
        }
        // 去除换行符和多余空格
        String clean = content.replaceAll("[\\r\\n]+", " ").trim();
        if (clean.length() <= 20) {
            return clean;
        }
        return clean.substring(0, 20) + "...";
    }
}
