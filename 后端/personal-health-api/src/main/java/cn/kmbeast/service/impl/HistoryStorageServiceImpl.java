package cn.kmbeast.service.impl;

import cn.kmbeast.pojo.entity.AiChatRecord;
import cn.kmbeast.pojo.entity.AiConversation;
import cn.kmbeast.service.HistoryStorageService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

            Path detailPath = Path.of(userDir, "conversation_" + conversation.getId() + ".json");
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
            
            Path indexPath = Path.of(getUserDir(userId), "index.json");
            Files.write(indexPath, JSON.toJSONString(index, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat)
                    .getBytes(StandardCharsets.UTF_8));
                    
        } catch (Exception e) {
            log.error("[History] 更新索引失败: userId={}", userId, e);
        }
    }

    private JSONArray loadIndex(Integer userId) {
        try {
            Path indexPath = Path.of(getUserDir(userId), "index.json");
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
            Path detailPath = Path.of(getUserDir(userId), "conversation_" + conversationId + ".json");
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
            Path detailPath = Path.of(getUserDir(userId), "conversation_" + conversationId + ".json");
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
            
            Path indexPath = Path.of(getUserDir(userId), "index.json");
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

    // ==================== 批量删除与磁盘清理（2026-10-07 新增） ====================

    @Override
    public void batchDeleteConversations(Integer userId, List<Integer> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return;
        }
        for (Integer cid : conversationIds) {
            try {
                Path detailPath = Path.of(getUserDir(userId), "conversation_" + cid + ".json");
                Files.deleteIfExists(detailPath);
            } catch (Exception e) {
                log.warn("[History] 批量删除磁盘文件失败: userId={}, conversationId={}", userId, cid, e);
            }
        }
        try {
            JSONArray index = loadIndex(userId);
            JSONArray newIndex = new JSONArray();
            for (int i = 0; i < index.size(); i++) {
                JSONObject item = index.getJSONObject(i);
                if (!conversationIds.contains(item.getInteger("id"))) {
                    newIndex.add(item);
                }
            }
            Path indexPath = Path.of(getUserDir(userId), "index.json");
            Files.write(indexPath, JSON.toJSONString(newIndex, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("[History] 批量删除更新索引失败: userId={}", userId, e);
        }
        log.info("[History] 批量删除本地副本: userId={}, count={}", userId, conversationIds.size());
    }

    @Override
    public int cleanup(Integer retentionDays, int maxTotalFiles,
                       java.util.function.IntFunction<java.util.Set<Integer>> liveConversationIdsFn) {
        File root = new File(STORAGE_ROOT);
        if (!root.isDirectory()) {
            return 0;
        }
        File[] userDirs = root.listFiles(File::isDirectory);
        if (userDirs == null) {
            return 0;
        }

        // 1. 收集全部会话详情文件，按最后修改时间升序（老的在前，便于超限时先淘汰）
        List<long[]> entries = new ArrayList<>();   // {lastModified, size}
        Map<Integer, List<Path>> filesByUser = new LinkedHashMap<>();
        for (File userDir : userDirs) {
            Integer userId = parseUserDir(userDir.getName());
            if (userId == null) {
                continue;
            }
            List<Path> list = new ArrayList<>();
            File[] files = userDir.listFiles((d, n) -> n.startsWith("conversation_") && n.endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    list.add(f.toPath());
                    entries.add(new long[]{f.lastModified(), f.length()});
                }
            }
            filesByUser.put(userId, list);
        }
        if (entries.isEmpty()) {
            return 0;
        }
        entries.sort(Comparator.comparingLong(a -> a[0]));

        Set<Integer> expiredIds = new HashSet<>();
        int deleted = 0;

        // 2. 按保留天数淘汰过旧文件（只保留最近一次修改超过阈值的）
        long cutoff = retentionDays != null && retentionDays > 0
                ? System.currentTimeMillis() - retentionDays * 24L * 3600_000L
                : Long.MAX_VALUE;
        for (File userDir : userDirs) {
            Integer userId = parseUserDir(userDir.getName());
            if (userId == null) {
                continue;
            }
            for (Path p : filesByUser.getOrDefault(userId, Collections.emptyList())) {
                try {
                    if (Files.getLastModifiedTime(p).toMillis() < cutoff) {
                        Integer cid = parseConversationId(p.getFileName().toString());
                        if (Files.deleteIfExists(p)) {
                            deleted++;
                            if (cid != null) {
                                expiredIds.add(cid);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("[History] 清理过期文件跳过: {}", p, e);
                }
            }
        }

        // 3. 按总文件数上限淘汰（连同孤儿文件一并清理）
        long total = entries.size() - deleted;
        if (maxTotalFiles > 0 && total > maxTotalFiles) {
            for (long[] entry : entries) {
                if (total <= maxTotalFiles) {
                    break;
                }
                for (File userDir : userDirs) {
                    Integer userId = parseUserDir(userDir.getName());
                    if (userId == null) {
                        continue;
                    }
                    for (Path p : filesByUser.getOrDefault(userId, Collections.emptyList())) {
                        try {
                            if (Files.exists(p) && Files.getLastModifiedTime(p).toMillis() == entry[0]) {
                                Integer cid = parseConversationId(p.getFileName().toString());
                                if (Files.deleteIfExists(p)) {
                                    deleted++;
                                    total--;
                                    if (cid != null) {
                                        expiredIds.add(cid);
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                            // 单个文件失败不中断整体清理
                        }
                    }
                }
            }
        }

        // 4. 清掉索引里已无磁盘文件的条目，并删掉空的会话文件目录
        for (Map.Entry<Integer, List<Path>> e : filesByUser.entrySet()) {
            pruneIndex(e.getKey());
            File dir = new File(getUserDir(e.getKey()));
            File[] remain = dir.listFiles((d, n) -> n.startsWith("conversation_") && n.endsWith(".json"));
            if (remain != null && remain.length == 0) {
                try {
                    Files.deleteIfExists(dir.toPath());
                } catch (Exception ignored) {
                    // 非空目录（含 index.json）保留
                }
            }
        }

        // 5. 孤儿文件（MySQL 已无记录但磁盘还在）—— 单独一轮
        if (liveConversationIdsFn != null) {
            for (Map.Entry<Integer, List<Path>> e : filesByUser.entrySet()) {
                Set<Integer> live;
                try {
                    live = liveConversationIdsFn.apply(e.getKey());
                } catch (Exception ex) {
                    log.warn("[History] 取有效会话失败，跳过孤儿清理: userId={}", e.getKey(), ex);
                    continue;
                }
                if (live == null) {
                    continue;
                }
                for (Path p : e.getValue()) {
                    Integer cid = parseConversationId(p.getFileName().toString());
                    if (cid == null || live.contains(cid)) {
                        continue;
                    }
                    try {
                        if (Files.deleteIfExists(p)) {
                            deleted++;
                            expiredIds.add(cid);
                        }
                    } catch (Exception ignored) {
                        // 忽略
                    }
                }
                if (!expiredIds.isEmpty()) {
                    pruneIndex(e.getKey());
                }
            }
        }

        if (deleted > 0) {
            log.info("[History] 磁盘清理完成: 删除 {} 个文件（保留天数={}, 上限={}）", deleted, retentionDays, maxTotalFiles);
        }
        return deleted;
    }

    /** 重写用户索引：只保留磁盘上确实存在详情文件的会话。 */
    private void pruneIndex(Integer userId) {
        try {
            JSONArray index = loadIndex(userId);
            if (index.isEmpty()) {
                return;
            }
            JSONArray newIndex = new JSONArray();
            for (int i = 0; i < index.size(); i++) {
                JSONObject item = index.getJSONObject(i);
                Integer cid = item.getInteger("id");
                Path detailPath = Path.of(getUserDir(userId), "conversation_" + cid + ".json");
                if (Files.exists(detailPath)) {
                    newIndex.add(item);
                }
            }
            if (newIndex.size() == index.size()) {
                return;
            }
            Path indexPath = Path.of(getUserDir(userId), "index.json");
            Files.write(indexPath, JSON.toJSONString(newIndex, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("[History] 修剪索引失败: userId={}", userId, e);
        }
    }

    private Integer parseUserDir(String name) {
        try {
            return Integer.valueOf(name);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseConversationId(String fileName) {
        try {
            String mid = fileName.substring("conversation_".length(), fileName.length() - ".json".length());
            return Integer.valueOf(mid);
        } catch (Exception e) {
            return null;
        }
    }
}
