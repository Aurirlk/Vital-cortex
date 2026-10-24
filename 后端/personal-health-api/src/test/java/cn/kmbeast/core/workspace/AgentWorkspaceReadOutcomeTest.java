package cn.kmbeast.core.workspace;

import cn.kmbeast.core.workspace.AgentWorkspace.ReadOutcome;
import cn.kmbeast.core.workspace.AgentWorkspace.ReadStatus;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工作空间读侧的去向报告（bug.md §7.2 第 7 条，2026-10-24）。
 *
 * <p>这条链路真读写 {@code ./cache/agent_workspace}（已在 {@code .gitignore} 的
 * {@code cache/} 里），用 {@code 990001} 这个不与真实用户重叠的 id，跑完即删。
 *
 * <p>要锁的是「三种失败别再压成同一个 null」：改前 {@code readJson} 返回
 * {@code JSONObject}，文件不存在、磁盘读失败、内容写坏三种情况调用方分不出来，
 * 于是磁盘故障期间用户的偏好「看起来像从没设置过」，而下一次保存会以空集合为基准
 * 覆盖磁盘，把已有偏好一起删掉。
 */
@DisplayName("Agent 工作空间：读失败与文件不存在要分得开")
class AgentWorkspaceReadOutcomeTest {

    private static final int UID = 990001;
    private static final String PARTITION = "memory";
    private static final String KEY = "preferences";

    private final AgentWorkspace workspace = new AgentWorkspace();

    private static Path dir() {
        return Path.of("./cache/agent_workspace", PARTITION, "user_" + UID);
    }

    private static Path file() {
        return dir().resolve(KEY + ".json");
    }

    @AfterEach
    void cleanUp() throws IOException {
        Files.deleteIfExists(file());
        Files.deleteIfExists(dir());
    }

    @Test
    @DisplayName("文件不存在：MISSING，且不算失败")
    void missingFileIsReportedAsMissing() throws IOException {
        Files.deleteIfExists(file());

        ReadOutcome outcome = workspace.readJson(PARTITION, UID, KEY);

        assertEquals(ReadStatus.MISSING, outcome.status());
        assertFalse(outcome.found());
        assertNull(outcome.json());
        assertEquals("文件不存在", outcome.failureReason());
    }

    @Test
    @DisplayName("写完再读：FOUND，内容原样回来")
    void savedJsonReadsBackAsFound() {
        JSONObject data = new JSONObject();
        data.put("diet", "少盐");

        assertTrue(workspace.saveJson(PARTITION, UID, KEY, data));

        ReadOutcome outcome = workspace.readJson(PARTITION, UID, KEY);
        assertEquals(ReadStatus.FOUND, outcome.status());
        assertTrue(outcome.found());
        assertNotNull(outcome.json());
        assertEquals("少盐", outcome.json().getString("diet"));
        assertNull(outcome.failureReason());
    }

    @Test
    @DisplayName("文件内容不是 JSON：PARSE_FAILED，不能冒充「没设置过」")
    void brokenContentIsParseFailure() throws IOException {
        Files.createDirectories(dir());
        Files.write(file(), "这不是 JSON".getBytes(StandardCharsets.UTF_8));

        ReadOutcome outcome = workspace.readJson(PARTITION, UID, KEY);

        assertEquals(ReadStatus.PARSE_FAILED, outcome.status(),
                "🔴 内容写坏了却报 MISSING，调用方会当成空偏好并在下次保存时覆盖掉整个文件");
        assertEquals("文件内容无法解析", outcome.failureReason());
    }

    @Test
    @DisplayName("空文件：同样是 PARSE_FAILED（不是「读到空偏好」）")
    void emptyFileIsParseFailure() throws IOException {
        Files.createDirectories(dir());
        Files.write(file(), new byte[0]);

        assertEquals(ReadStatus.PARSE_FAILED, workspace.readJson(PARTITION, UID, KEY).status());
    }

    @Test
    @DisplayName("有损签名 readFromFile 保持旧契约：两种情况都回 null")
    void legacyReadFromFileStaysLossy() throws IOException {
        Files.deleteIfExists(file());
        assertNull(workspace.readFromFile(PARTITION, UID, KEY + ".json"));

        Files.createDirectories(dir());
        Files.write(file(), "not json".getBytes(StandardCharsets.UTF_8));
        assertEquals("not json", workspace.readFromFile(PARTITION, UID, KEY + ".json"));
    }
}
