package cn.kmbeast.core.agent;

import cn.kmbeast.core.workspace.AgentWorkspace;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * Agent 记忆服务
 * 提供长期记忆、语义记忆、用户偏好记忆
 * 支持跨会话学习和知识积累
 */
@Slf4j
@Component
public class AgentMemoryService {

    @Resource
    private AgentWorkspace agentWorkspace;

    /** 内存缓存：用户偏好 */
    private final Map<Integer, Map<String, String>> userPreferences = new HashMap<>();

    /**
     * 获取用户偏好
     */
    public Map<String, String> getUserPreferences(Integer userId) {
        return userPreferences.computeIfAbsent(userId, id -> {
            JSONObject saved = agentWorkspace.readJson("memory", id, "preferences");
            if (saved != null) {
                Map<String, String> prefs = new HashMap<>();
                saved.forEach((k, v) -> prefs.put(k, String.valueOf(v)));
                return prefs;
            }
            return new HashMap<>();
        });
    }

    /**
     * 保存用户偏好
     */
    public void saveUserPreference(Integer userId, String key, String value) {
        Map<String, String> prefs = getUserPreferences(userId);
        prefs.put(key, value);
        JSONObject json = new JSONObject();
        json.putAll(prefs);
        agentWorkspace.saveJson("memory", userId, "preferences", json);
    }

    /**
     * 获取对话上下文摘要
     */
    public String getConversationContext(Integer userId, String agentType) {
        return agentWorkspace.getConversationSummary(userId, agentType);
    }

    /**
     * 保存对话上下文摘要
     */
    public void saveConversationContext(Integer userId, String agentType, String summary) {
        agentWorkspace.saveConversationSummary(userId, agentType, summary);
    }

    /**
     * 记录用户行为
     */
    public void recordUserAction(Integer userId, String action, String detail) {
        agentWorkspace.logTask("memory", userId, action, detail);
    }
}
