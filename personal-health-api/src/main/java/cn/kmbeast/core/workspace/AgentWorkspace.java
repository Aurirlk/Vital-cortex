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
     *
     * @return 是否真的写进磁盘。原先返回 void 并把 {@code IOException} 只打成日志，
     *         调用方无从分辨「写成功」与「写失败」，于是 {@code AgentMemoryService}
     *         注释里那句「落盘成功后再刷缓存」根本执行不出来，接口也一律回成功 ——
     *         用户看到的「已保存」其实只存在于内存里，重启就没了。
     */
    public boolean saveToFile(String agentType, Integer userId, String fileName, String content) {
        Path filePath = getUserWorkDir(agentType, userId).resolve(fileName);
        try {
            Files.write(filePath, content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("AgentWorkspace: 保存文件 {}", filePath);
            return true;
        } catch (IOException e) {
            log.error("保存文件失败: {}", filePath, e);
            return false;
        }
    }

    /**
     * 读取文件内容；<b>不区分</b>「文件不存在」与「读失败」，两者都返回 {@code null}。
     *
     * <p>需要区分时用 {@link #readJson}，它会把去向写在 {@link ReadOutcome#status()} 里。
     * 保留这个有损签名只因为它是既有的裸文本入口，不要再往新代码里传。
     */
    public String readFromFile(String agentType, Integer userId, String fileName) {
        RawRead raw = readRaw(agentType, userId, fileName);
        return raw.status == RawStatus.FOUND ? raw.content : null;
    }

    /**
     * 读取 JSON 数据，并如实报告「读到了什么去向」。
     *
     * <p>2026-10-24：改前签名是 {@code JSONObject}，把<b>文件不存在</b>、<b>磁盘读失败</b>、
     * <b>内容写坏了</b>三种情况压成同一个 {@code null}。调用方只能统一按「用户从没设置过」处理，
     * 于是磁盘故障期间用户的偏好会「看起来像被清空了」，而任何一次保存还会以空集合为基准
     * 覆盖磁盘 —— 静默删掉他全部偏好。
     */
    public ReadOutcome readJson(String agentType, Integer userId, String key) {
        RawRead raw = readRaw(agentType, userId, key + ".json");
        if (raw.status == RawStatus.MISSING) {
            return new ReadOutcome(ReadStatus.MISSING, null);
        }
        if (raw.status == RawStatus.IO_FAILED) {
            return new ReadOutcome(ReadStatus.IO_FAILED, null);
        }
        try {
            JSONObject parsed = JSON.parseObject(raw.content);
            if (parsed == null) {
                log.error("AgentWorkspace: 内容不是 JSON 对象（空文件?）key={}", key);
                return new ReadOutcome(ReadStatus.PARSE_FAILED, null);
            }
            return new ReadOutcome(ReadStatus.FOUND, parsed);
        } catch (Exception e) {
            log.error("解析 JSON 失败: {}", key, e);
            return new ReadOutcome(ReadStatus.PARSE_FAILED, null);
        }
    }

    /** 读盘去向。{@link #MISSING} 与另外两种失败含义完全不同，不许合并。 */
    public enum ReadStatus {
        /** 文件在、读到了、JSON 也解析成功。 */
        FOUND,
        /** 文件不存在：这是「从没设置过」，可以按空值处理并回填缓存。 */
        MISSING,
        /** 读盘失败（磁盘故障 / 只读挂载 / 目录建不出来）：不能当成「没设置过」。 */
        IO_FAILED,
        /** 文件在但内容不是合法 JSON：写坏了。 */
        PARSE_FAILED
    }

    /**
     * 读取结果。{@code json} 仅在 {@link ReadStatus#FOUND} 时非空。
     */
    public record ReadOutcome(ReadStatus status, JSONObject json) {

        public boolean found() {
            return status == ReadStatus.FOUND;
        }

        /** 读不出内容时如实报告原因，让调用方能把它和「没有数据」区分开。 */
        public String failureReason() {
            return switch (status) {
                case FOUND -> null;
                case MISSING -> "文件不存在";
                case IO_FAILED -> "磁盘读取失败";
                case PARSE_FAILED -> "文件内容无法解析";
            };
        }
    }

    private enum RawStatus { FOUND, MISSING, IO_FAILED }

    private record RawRead(RawStatus status, String content) { }

    private RawRead readRaw(String agentType, Integer userId, String fileName) {
        Path dir = Path.of(WORKSPACE_ROOT, agentType, "user_" + userId);
        boolean dirReady;
        try {
            Files.createDirectories(dir);
            dirReady = Files.isDirectory(dir);
        } catch (IOException e) {
            log.error("准备用户工作目录失败: {}", dir, e);
            dirReady = false;
        }
        if (!dirReady) {
            // 目录都不可用时报 IO_FAILED 而不是 MISSING：后者会被调用方解释成「用户没设置过」
            return new RawRead(RawStatus.IO_FAILED, null);
        }
        Path filePath = dir.resolve(fileName);
        if (!Files.exists(filePath)) {
            return new RawRead(RawStatus.MISSING, null);
        }
        try {
            return new RawRead(RawStatus.FOUND,
                    new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("读取文件失败（这不代表文件不存在）: {}", filePath, e);
            return new RawRead(RawStatus.IO_FAILED, null);
        }
    }

    /**
     * 保存 JSON 数据（自动补 {@code _savedAt} 时间戳）。
     *
     * <p>写入的是副本：原实现直接往调用方传入的 {@link JSONObject} 里
     * {@code put("_savedAt", ...)}，调用方之后读自己的对象会莫名多出一个字段。</p>
     *
     * @return 是否写盘成功，语义同 {@link #saveToFile}
     */
    public boolean saveJson(String agentType, Integer userId, String key, JSONObject data) {
        JSONObject copy = data == null ? new JSONObject() : new JSONObject(data);
        copy.put("_savedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return saveToFile(agentType, userId, key + ".json", copy.toJSONString());
    }
}
