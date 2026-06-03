package cn.kmbeast.service.impl;

import cn.kmbeast.pojo.entity.AiChatRecord;
import cn.kmbeast.pojo.entity.AiConversation;
import cn.kmbeast.service.HistoryStorageService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 历史会话JSON存储实现
 * 
 * 目录结构：
 *   chat_history/
 *     {userId}/
 *       index.json              -- 会话索引（标题、时间、角色等）
 *       conversation_{id}.json  -- 会话消息详情
 */
@Slf4j
@Service
public class HistoryStorageServiceImpl implements HistoryStorageService {

    private static final String STORAGE_ROOT = "chat_history";

    @PostConstruct
    public void init() {
        File dir = new File(STORAGE_ROOT);
        if (!dir.exists()) {
            dir.mkdirs();
            log.info("[History] 存储目录已创建: {}", dir.getAbsolutePath());
        }
    }

    private String getUserDir(Integer userId) {
        return STORAGE_ROOT + File.separator + userId;
    }

    @Override
    public void saveConversation(Integer userId, AiConversation conversation, List<AiChatRecord> messages) {
        try {
            String userDir = getUserDir(userId);
            File dir = new File(userDir);
            if (!dir.exists()) dir.mkdirs();

            // 1. 保存会话详情
            JSONObject detail = new JSONObject();
            detail.put("conversationId", conversation.getId());
            detail.put("userId", userId);
            detail.put("title", conversation.getTitle());
            detail.put("agentType", conversation.getAgentType());
            detail.put("messageCount", conversation.getMessageCount());
            detail.put("createTime", conversation.getCreateTime() != null ? conversation.getCreateTime().toString() : "");
            detail.put("lastUpdateTime", LocalDateTime.now().toString());

            JSONArray msgArray = new JSONArray();
            if (messages != null) {
                for (AiChatRecord msg : messages) {
                    JSONObject m = new JSONObject();
                    m.put("id", msg.getId());
                    m.put("role", msg.getRole());
                    m.put("content", msg.getContent());
                    m.put("createTime", msg.getCreateTime() != null ? msg.getCreateTime().toString() : "");
                    msgArray.add(m);
                }
            }
            detail.put("messages", msgArray);

            Path detailPath = Paths.get(userDir, "conversation_" + conversation.getId() + ".json");
            Files.write(detailPath, JSON.toJSONString(detail, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat)
                    .getBytes(StandardCharsets.UTF_8));

            // 2. 更新索引文件
            updateIndex(userId, conversation);

            log.info("[History] 保存会话: userId={}, conversationId={}, messages={}", userId, conversation.getId(), messages != null ? messages.size() : 0);
        } catch (Exception e) {
            log.error("[History] 保存失败: userId={}, conversationId={}", userId, conversation.getId(), e);
        }
    }

    private void updateIndex(Integer userId, AiConversation conversation) {
        try {
            JSONArray index = loadIndex(userId);
            
            // 查找是否已存在
            boolean found = false;
            for (int i = 0; i < index.size(); i++) {
                JSONObject item = index.getJSONObject(i);
                if (item.getInteger("id").equals(conversation.getId())) {
                    item.put("title", conversation.getTitle());
                    item.put("messageCount", conversation.getMessageCount());
                    item.put("lastMessageTime", conversation.getLastMessageTime() != null ? 
                        conversation.getLastMessageTime().toString() : "");
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                JSONObject item = new JSONObject();
                item.put("id", conversation.getId());
                item.put("userId", userId);
                item.put("title", conversation.getTitle());
                item.put("agentType", conversation.getAgentType());
                item.put("messageCount", conversation.getMessageCount());
                item.put("createTime", conversation.getCreateTime() != null ? conversation.getCreateTime().toString() : "");
                item.put("lastMessageTime", conversation.getLastMessageTime() != null ? 
                    conversation.getLastMessageTime().toString() : "");
                index.add(item);
            }
            
            Path indexPath = Paths.get(getUserDir(userId), "index.json");
            Files.write(indexPath, JSON.toJSONString(index, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat)
                    .getBytes(StandardCharsets.UTF_8));
                    
        } catch (Exception e) {
            log.error("[History] 更新索引失败: userId={}", userId, e);
        }
    }

    private JSONArray loadIndex(Integer userId) {
        try {
            Path indexPath = Paths.get(getUserDir(userId), "index.json");
            if (Files.exists(indexPath)) {
                String content = new String(Files.readAllBytes(indexPath), StandardCharsets.UTF_8);
                return JSON.parseArray(content);
            }
        } catch (Exception e) {
            log.error("[History] 加载索引失败: userId={}", userId, e);
        }
        return new JSONArray();
    }

    @Override
    public List<AiConversation> loadConversations(Integer userId) {
        List<AiConversation> result = new ArrayList<>();
        try {
            JSONArray index = loadIndex(userId);
            for (int i = 0; i < index.size(); i++) {
                JSONObject item = index.getJSONObject(i);
                AiConversation conv = AiConversation.builder()
                        .id(item.getInteger("id"))
                        .userId(userId)
                        .title(item.getString("title"))
                        .agentType(item.getString("agentType"))
                        .messageCount(item.getInteger("messageCount"))
                        .createTime(parseDateTime(item.getString("createTime")))
                        .lastMessageTime(parseDateTime(item.getString("lastMessageTime")))
                        .build();
                result.add(conv);
            }
            // 按时间倒序
            result.sort((a, b) -> {
                if (a.getLastMessageTime() == null) return 1;
                if (b.getLastMessageTime() == null) return -1;
                return b.getLastMessageTime().compareTo(a.getLastMessageTime());
            });
        } catch (Exception e) {
            log.error("[History] 加载会话列表失败: userId={}", userId, e);
        }
        return result;
    }

    @Override
    public List<AiChatRecord> loadMessages(Integer userId, Integer conversationId) {
        List<AiChatRecord> result = new ArrayList<>();
        try {
            Path detailPath = Paths.get(getUserDir(userId), "conversation_" + conversationId + ".json");
            if (!Files.exists(detailPath)) {
                return result;
            }
            
            String content = new String(Files.readAllBytes(detailPath), StandardCharsets.UTF_8);
            JSONObject detail = JSON.parseObject(content);
            
            JSONArray msgArray = detail.getJSONArray("messages");
            if (msgArray != null) {
                for (int i = 0; i < msgArray.size(); i++) {
                    JSONObject m = msgArray.getJSONObject(i);
                    AiChatRecord record = AiChatRecord.builder()
                            .id(m.getInteger("id"))
                            .conversationId(conversationId)
                            .userId(userId)
                            .role(m.getString("role"))
                            .content(m.getString("content"))
                            .createTime(parseDateTime(m.getString("createTime")))
                            .agentType(detail.getString("agentType"))
                            .build();
                    result.add(record);
                }
            }
            log.info("[History] 加载消息: userId={}, conversationId={}, count={}", userId, conversationId, result.size());
        } catch (Exception e) {
            log.error("[History] 加载消息失败: userId={}, conversationId={}", userId, conversationId, e);
        }
        return result;
    }

    @Override
    public void deleteConversation(Integer userId, Integer conversationId) {
        try {
            // 删除详情文件
            Path detailPath = Paths.get(getUserDir(userId), "conversation_" + conversationId + ".json");
            Files.deleteIfExists(detailPath);
            
            // 从索引中移除
            JSONArray index = loadIndex(userId);
            JSONArray newIndex = new JSONArray();
            for (int i = 0; i < index.size(); i++) {
                JSONObject item = index.getJSONObject(i);
                if (!item.getInteger("id").equals(conversationId)) {
                    newIndex.add(item);
                }
            }
            
            Path indexPath = Paths.get(getUserDir(userId), "index.json");
            Files.write(indexPath, JSON.toJSONString(newIndex, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat)
                    .getBytes(StandardCharsets.UTF_8));
            
            log.info("[History] 删除会话: userId={}, conversationId={}", userId, conversationId);
        } catch (Exception e) {
            log.error("[History] 删除失败: userId={}, conversationId={}", userId, conversationId, e);
        }
    }

    private LocalDateTime parseDateTime(String str) {
        if (str == null || str.isEmpty()) return null;
        try {
            return LocalDateTime.parse(str.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }
}
