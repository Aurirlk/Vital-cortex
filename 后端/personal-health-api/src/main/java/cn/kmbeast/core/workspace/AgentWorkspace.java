package cn.kmbeast.core.workspace;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Agent 工作空间的<b>文件存储</b>能力。
 *
 * <p><b>2026-10-20（阶段 6）清理：</b>这个类原先还带着用户画像缓存、Agent 协作状态、
 * 对话摘要、任务日志、临时数据五组能力，合计约 200 行；全仓唯一的调用方
 * {@code AgentMemoryService} 只用到 {@link #readJson} / {@link #saveJson}。
 * 其余方法没有任何引用 —— 其中画像缓存还要注入 {@code UserMapper} /
 * {@code UserHealthMapper}，即「没人调，但组件启动时要装配两个 Mapper」。
 * 死代码留在仓库里不只是噪音：它们看起来像现役能力，会让人误以为
 * 「用户画像已经缓存好了」「Agent 之间已经在共享状态」。</p>
 *
 * <p>保留的就是记忆功能真正需要的那条最小链路：
 * {@link #getUserWorkDir} → {@link #saveToFile} / {@link #readFromFile} →
 * {@link #saveJson} / {@link #readJson}。</p>
 */
@Slf4j
@Component
public class AgentWorkspace {

    /** 工作空间根目录。 */
    private static final String WORKSPACE_ROOT = "./cache/agent_workspace";

    /**
     * 获取用户专属工作目录（不存在时创建）。
     */
    public Path getUserWorkDir(String agentType, Integer userId) {
        Path dir = Path.of(WORKSPACE_ROOT, agentType, "user_" + userId);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("创建用户工作目录失败: {}", dir, e);
        }
        return dir;
    }

    /**
     * 保存数据到文件。
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
     * 读取文件内容；文件不存在或读取失败时返回 {@code null}。
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
     * 保存 JSON 数据（自动补 {@code _savedAt} 时间戳）。
     *
     * <p>写入的是副本：原实现直接往调用方传入的 {@link JSONObject} 里
     * {@code put("_savedAt", ...)}，调用方之后读自己的对象会莫名多出一个字段。</p>
     */
    public void saveJson(String agentType, Integer userId, String key, JSONObject data) {
        JSONObject copy = data == null ? new JSONObject() : new JSONObject(data);
        copy.put("_savedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        saveToFile(agentType, userId, key + ".json", copy.toJSONString());
    }

    /**
     * 读取 JSON 数据；不存在或解析失败时返回 {@code null}。
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
}
