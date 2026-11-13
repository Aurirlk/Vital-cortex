package cn.kmbeast.crm.sqlite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * SQLite 抖动时的降级方向测试（2026-10-20 补）。
 *
 * <p><b>锁的是「猜错方向会不会把事情放大」</b>。这类方法在 catch 之后必须返回一个
 * 默认值，而默认值有两种选法，选错的那一侧不会报错，只会静默改变用户看到的东西：
 *
 * <ul>
 *   <li>{@code isNewUser} 原先返回 {@code true}（=「这是新用户」）。一旦 SQLite 抖动，
 *       一个有多轮记录的老用户会被当成第一次来的人：{@code SeaChatWorkflow} 见
 *       {@code isNewUser=true} 就<b>整段不读历史</b>，并把欢迎语拼进提示词 ——
 *       上一轮刚说过的身高体重当场消失，接口还回 {@code isNewUser: true}。
 *       现在返回 {@code false}：最坏只是本次没有欢迎语，历史读回来是空列表也不造假。</li>
 *   <li>{@code saveMessage} 必须回 {@code false} 而不是悄悄吞掉（AG-10 的约定），
 *       调用方据此打告警留痕。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("聊天历史存储：连接失败时的降级方向")
class SqliteChatHistoryFailureFallbackTest {

    @Mock
    private SqliteConnectionManager connectionManager;

    @InjectMocks
    private SqliteChatHistoryService service;

    private void failOnAnyConnection() throws SQLException {
        when(connectionManager.getReadOnlyConnection()).thenThrow(new SQLException("database is locked"));
        when(connectionManager.getReadWriteConnection()).thenThrow(new SQLException("database is locked"));
    }

    private void failOnPrepare() throws SQLException {
        Connection conn = org.mockito.Mockito.mock(Connection.class);
        when(connectionManager.getReadOnlyConnection()).thenReturn(conn);
        when(connectionManager.getReadWriteConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenThrow(new SQLException("no such table: chat_history"));
    }

    @Test
    @DisplayName("取连接失败：isNewUser 按「老用户」降级，不把多轮上下文抹成空白")
    void isNewUserFallsBackToExistingUser() throws SQLException {
        failOnAnyConnection();

        assertFalse(service.isNewUser("13800000000"),
                "查不出来时说「这是新用户」会让调用方跳过整段历史并塞欢迎语，破坏面比反过来大得多");
    }

    @Test
    @DisplayName("SQL 预备失败：isNewUser 同样按「老用户」降级，且不抛异常")
    void isNewUserFallsBackWhenStatementFails() throws SQLException {
        failOnPrepare();

        assertDoesNotThrow(() -> assertFalse(service.isNewUser("13800000000")));
    }

    @Test
    @DisplayName("连接失败：getHistory 回空列表而不是 null（拼装处直接遍历，不给 NPE 机会）")
    void getHistoryReturnsEmptyListOnFailure() throws SQLException {
        failOnAnyConnection();

        List<Map<String, Object>> history = service.getHistory("13800000000", 10);
        assertTrue(history.isEmpty(), "失败时应回空列表");
    }

    @Test
    @DisplayName("连接失败：saveMessage 如实回 false（AG-10：不静默丢失，由调用方记告警）")
    void saveMessageReportsFailure() throws SQLException {
        failOnAnyConnection();

        assertFalse(service.saveMessage("13800000000", "s-1", "user", "血压高", null, null),
                "写不进去必须回 false，否则这一轮聊天记录无声消失");
    }

    @Test
    @DisplayName("连接失败：total 计数回 0 而不是抛异常（统计是非关键路径）")
    void totalMessagesSurvivesFailure() throws SQLException {
        failOnAnyConnection();

        assertEquals(0, service.getTotalMessages("13800000000"));
    }
}
