package cn.kmbeast.core.workspace;

import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.mapper.UserHealthMapper;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.pojo.entity.UserHealth;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 工作空间
 * 为 Agent 提供本地存储能力，包括：
 * - 用户画像缓存
 * - 对话历史摘要
 * - 临时计算结果
 * - Agent 协作状态
 * - 任务执行日志
 */
@Slf4j
@Component
public class AgentWorkspace {

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserHealthMapper userHealthMapper;

    /** 工作空间根目录 */
    private static final String WORKSPACE_ROOT = "./cache/agent_workspace";

    /** 用户画像缓存 */
    private final ConcurrentHashMap<Integer, JSONObject> userProfileCache = new ConcurrentHashMap<>();

    /** Agent 协作状态 */
    private final ConcurrentHashMap<String, JSONObject> agentStates = new ConcurrentHashMap<>();

    /**
     * 获取 Agent 工作目录
     */
    public Path getAgentWorkDir(String agentType) {
        Path dir = Paths.get(WORKSPACE_ROOT, agentType);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("创建 Agent 工作目录失败: {}", dir, e);
        }
        return dir;
    }

    /**
     * 获取用户专属工作目录
     */
    public Path getUserWorkDir(String agentType, Integer userId) {
        Path dir = Paths.get(WORKSPACE_ROOT, agentType, "user_" + userId);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("创建用户工作目录失败: {}", dir, e);
        }
        return dir;
    }

    // ========== 用户画像 ==========

    /**
     * 获取用户画像（带缓存）
     */
    public JSONObject getUserProfile(Integer userId) {
        return userProfileCache.computeIfAbsent(userId, id -> {
            JSONObject profile = new JSONObject();
            User user = userMapper.getUserById(id);
            if (user != null) {
                profile.put("userId", id);
                profile.put("userName", user.getUserName());
                profile.put("userAccount", user.getUserAccount());
                profile.put("userRole", user.getUserRole());
            }
            // 加载健康数据
            List<UserHealth> healthData = userHealthMapper.getRecentByUserId(id, 30);
            profile.put("healthRecordCount", healthData.size());
            profile.put("lastHealthRecordTime", healthData.isEmpty() ? null :
                    healthData.get(0).getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            return profile;
        });
    }

    /**
     * 更新用户画像缓存
     */
    public void updateUserProfile(Integer userId, JSONObject updates) {
        JSONObject profile = getUserProfile(userId);
        profile.putAll(updates);
        userProfileCache.put(userId, profile);
    }

    /**
     * 清除用户画像缓存
     */
    public void clearUserProfileCache(Integer userId) {
        userProfileCache.remove(userId);
    }

    // ========== 本地文件存储 ==========

    /**
     * 保存数据到文件
     */
    public void saveToFile(String agentType, Integer userId, String fileName, String content) {
        Path filePath = getUserWorkDir(agentType, userId).resolve(fileName);
        try {
            Files.write(filePath, content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("AgentWorkspace: 保存文件 {}", filePath);
        } catch (IOException e) {
            log.error("保存文件失败: {}", filePath, e);
        }
    }

    /**
     * 读取文件内容
     */
    public String readFromFile(String agentType, Integer userId, String fileName) {
        Path filePath = getUserWorkDir(agentType, userId).resolve(fileName);
        try {
            if (Files.exists(filePath)) {
                return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("读取文件失败: {}", filePath, e);
        }
        return null;
    }

    /**
     * 保存 JSON 数据
     */
    public void saveJson(String agentType, Integer userId, String key, JSONObject data) {
        data.put("_savedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        saveToFile(agentType, userId, key + ".json", data.toJSONString());
    }

    /**
     * 读取 JSON 数据
     */
    public JSONObject readJson(String agentType, Integer userId, String key) {
        String content = readFromFile(agentType, userId, key + ".json");
        if (content != null) {
            try {
                return JSON.parseObject(content);
            } catch (Exception e) {
                log.error("解析 JSON 失败: {}", key, e);
            }
        }
        return null;
    }

    // ========== 对话历史摘要 ==========

    /**
     * 保存对话摘要
     */
    public void saveConversationSummary(Integer userId, String agentType, String summary) {
        JSONObject data = new JSONObject();
        data.put("summary", summary);
        data.put("agentType", agentType);
        data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        saveJson("history", userId, "summary_" + agentType, data);
    }

    /**
     * 获取对话摘要
     */
    public String getConversationSummary(Integer userId, String agentType) {
        JSONObject data = readJson("history", userId, "summary_" + agentType);
        return data != null ? data.getString("summary") : null;
    }

    // ========== Agent 协作状态 ==========

    /**
     * 设置 Agent 状态
     */
    public void setAgentState(String agentType, JSONObject state) {
        agentStates.put(agentType, state);
    }

    /**
     * 获取 Agent 状态
     */
    public JSONObject getAgentState(String agentType) {
        return agentStates.getOrDefault(agentType, new JSONObject());
    }

    /**
     * 获取所有活跃 Agent
     */
    public Set<String> getActiveAgents() {
        return agentStates.keySet();
    }

    // ========== 任务日志 ==========

    /**
     * 记录任务执行日志
     */
    public void logTask(String agentType, Integer userId, String taskType, String detail) {
        JSONObject logEntry = new JSONObject();
        logEntry.put("taskType", taskType);
        logEntry.put("detail", detail);
        logEntry.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        String fileName = "task_log_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String existing = readFromFile(agentType, userId, fileName + ".json");
        List<JSONObject> logs = new ArrayList<>();
        if (existing != null) {
            try {
                logs = JSON.parseArray(existing, JSONObject.class);
            } catch (Exception ignored) {}
        }
        logs.add(logEntry);
        saveToFile(agentType, userId, fileName + ".json", JSON.toJSONString(logs));
    }

    /**
     * 获取任务日志
     */
    public List<JSONObject> getTaskLogs(String agentType, Integer userId, String date) {
        String content = readFromFile(agentType, userId, "task_log_" + date + ".json");
        if (content != null) {
            try {
                return JSON.parseArray(content, JSONObject.class);
            } catch (Exception ignored) {}
        }
        return Collections.emptyList();
    }

    // ========== 临时数据 ==========

    /**
     * 保存临时数据（自动过期）
     */
    public void saveTempData(String key, JSONObject data, long ttlMillis) {
        data.put("_expiresAt", System.currentTimeMillis() + ttlMillis);
        Path filePath = Paths.get(WORKSPACE_ROOT, "temp", key + ".json");
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, data.toJSONString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("保存临时数据失败: {}", key, e);
        }
    }

    /**
     * 获取临时数据
     */
    public JSONObject getTempData(String key) {
        Path filePath = Paths.get(WORKSPACE_ROOT, "temp", key + ".json");
        try {
            if (Files.exists(filePath)) {
                String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
                JSONObject data = JSON.parseObject(content);
                // 检查是否过期
                Long expiresAt = data.getLong("_expiresAt");
                if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
                    Files.deleteIfExists(filePath);
                    return null;
                }
                return data;
            }
        } catch (Exception e) {
            log.error("读取临时数据失败: {}", key, e);
        }
        return null;
    }
}
