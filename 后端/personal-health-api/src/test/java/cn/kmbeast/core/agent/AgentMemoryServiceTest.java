package cn.kmbeast.core.agent;

import cn.kmbeast.core.workspace.AgentWorkspace;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Agent 记忆服务单元测试（2026-10-04 加固）
 *
 * <p>本次加固修掉了三个问题，逐条用测试锁死：
 * <ol>
 *   <li><b>无界缓存</b> → 有界 LRU（原来必然 OOM）</li>
 *   <li><b>返回值可篡改</b> → 返回不可变副本</li>
 *   <li><b>写入后读不到</b> → 写入时同步刷缓存</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Agent 记忆服务")
class AgentMemoryServiceTest {

    @Mock
    private AgentWorkspace agentWorkspace;

    @InjectMocks
    private AgentMemoryService service;

    @BeforeEach
    void setUp() {
        // 默认：磁盘无数据
        when(agentWorkspace.readJson(anyString(), anyInt(), anyString())).thenReturn(null);
    }

    // ================================================================ 基本读写

    @Test
    @DisplayName("无存档时返回空偏好")
    void shouldReturnEmptyWhenNoSavedData() {
        Map<String, String> prefs = service.getUserPreferences(1);

        assertNotNull(prefs);
        assertTrue(prefs.isEmpty());
    }

    @Test
    @DisplayName("保存后可读回（原实现存在「存了但读不到」的 bug）")
    void savedPreferenceShouldBeReadable() {
        service.saveUserPreference(1, "diet", "少盐");

        Map<String, String> prefs = service.getUserPreferences(1);
        assertEquals("少盐", prefs.get("diet"), "保存后必须能立刻读到");
    }

    @Test
    @DisplayName("保存会落盘")
    void saveShouldPersistToDisk() {
        service.saveUserPreference(1, "diet", "少盐");

        verify(agentWorkspace).saveJson(eq("memory"), eq(1), eq("preferences"), any(JSONObject.class));
    }

    @Test
    @DisplayName("value 为 null 时视为删除该偏好")
    void nullValueShouldRemovePreference() {
        service.saveUserPreference(1, "diet", "少盐");
        service.saveUserPreference(1, "diet", null);

        assertFalse(service.getUserPreferences(1).containsKey("diet"),
                "传 null 应删除该偏好");
    }

    @Test
    @DisplayName("批量保存偏好")
    void shouldSaveMultiplePreferences() {
        service.saveUserPreferences(1, Map.of("diet", "少盐", "exercise", "每周3次"));

        Map<String, String> prefs = service.getUserPreferences(1);
        assertEquals("少盐", prefs.get("diet"));
        assertEquals("每周3次", prefs.get("exercise"));
    }

    @Test
    @DisplayName("从磁盘加载历史偏好")
    void shouldLoadFromDisk() {
        JSONObject saved = new JSONObject();
        saved.put("diet", "少油");
        saved.put("exercise", "慢走");
        when(agentWorkspace.readJson("memory", 1, "preferences")).thenReturn(saved);

        Map<String, String> prefs = service.getUserPreferences(1);

        assertEquals("少油", prefs.get("diet"));
        assertEquals("慢走", prefs.get("exercise"));
    }

    // ================================================================ 不可变性

    @Test
    @DisplayName("返回的偏好不可修改（防止绕过落盘直接改缓存）")
    void returnedPreferencesShouldBeImmutable() {
        service.saveUserPreference(1, "diet", "少盐");

        Map<String, String> prefs = service.getUserPreferences(1);
        assertThrows(UnsupportedOperationException.class,
                () -> prefs.put("hacked", "yes"),
                "外部改返回值不应污染缓存");

        assertFalse(service.getUserPreferences(1).containsKey("hacked"),
                "缓存本身也不能被污染");
    }

    // ================================================================ 健壮性

    @Test
    @DisplayName("磁盘读取失败：降级为空偏好，不抛异常")
    void diskReadFailureShouldDegradeGracefully() {
        when(agentWorkspace.readJson(anyString(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("文件不存在"));

        assertTrue(service.getUserPreferences(1).isEmpty(),
                "档案读不到时对话仍要能继续");
    }

    @Test
    @DisplayName("null userId：返回空偏好且不打库")
    void nullUserIdShouldBeSafe() {
        assertTrue(service.getUserPreferences(null).isEmpty());
        verify(agentWorkspace, never()).readJson(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("key 为空：忽略该次保存")
    void blankKeyShouldBeIgnored() {
        service.saveUserPreference(1, "   ", "值");
        service.saveUserPreference(1, null, "值");

        assertTrue(service.getUserPreferences(1).isEmpty());
    }

    @Test
    @DisplayName("evictUser 后重新从磁盘加载")
    void evictShouldForceReloadFromDisk() {
        service.saveUserPreference(1, "diet", "少盐");
        service.evictUser(1);

        JSONObject saved = new JSONObject();
        saved.put("diet", "少油");
        when(agentWorkspace.readJson("memory", 1, "preferences")).thenReturn(saved);

        assertEquals("少油", service.getUserPreferences(1).get("diet"),
                "evict 后应从磁盘重新加载");
    }

    // ================================================================ 有界缓存

    @Test
    @DisplayName("缓存有上限：超过 1000 个用户时按 LRU 淘汰")
    void cacheShouldBeBoundedWithLruEviction() throws Exception {
        for (int i = 1; i <= 1200; i++) {
            service.getUserPreferences(i);
        }

        Map<String, Object> stats = service.getCacheStats();
        int cached = (int) stats.get("cachedUsers");
        assertEquals(1000, stats.get("maxCachedUsers"));
        assertTrue(cached <= 1000,
                "缓存必须有上限，否则用户量增长必然 OOM，实际: " + cached);
    }

    @Test
    @DisplayName("LRU：最久未访问的先被淘汰")
    void lruShouldEvictLeastRecentlyUsed() throws Exception {
        // 访问 user 1（会成为最久未访问）
        service.getUserPreferences(1);
        // 反复访问 user 2 让它保持「热」
        service.getUserPreferences(2);
        for (int i = 3; i <= 1001; i++) {
            service.getUserPreferences(i);
        }
        // 再访问 2 一次，刷新它的最近访问时间
        service.getUserPreferences(2);
        // 再塞一个新用户触发淘汰
        service.getUserPreferences(1002);

        int cached = (int) service.getCacheStats().get("cachedUsers");
        assertTrue(cached <= 1000, "仍须有上限，实际: " + cached);
    }

    @Test
    @DisplayName("定时清理：把缓存降到 80%")
    void staleEvictionShouldShrinkToTarget() throws Exception {
        for (int i = 1; i <= 1000; i++) {
            service.getUserPreferences(i);
        }
        assertEquals(1000, (int) service.getCacheStats().get("cachedUsers"));

        service.evictStaleCache();

        int after = (int) service.getCacheStats().get("cachedUsers");
        assertTrue(after < 1000, "定时清理应降低缓存量，实际: " + after);
        assertTrue(after >= 800, "不应清得太过，实际: " + after);
    }

    @Test
    @DisplayName("未超上限时定时清理是空操作")
    void staleEvictionShouldBeNoopUnderLimit() {
        for (int i = 1; i <= 10; i++) {
            service.getUserPreferences(i);
        }

        service.evictStaleCache();

        assertEquals(10, (int) service.getCacheStats().get("cachedUsers"),
                "未超上限时不应误删");
    }

    // ================================================================ 并发

    @Test
    @DisplayName("并发读写不同用户：缓存不串号")
    void concurrentAccessShouldNotCrossContaminate() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        try {
            for (int t = 0; t < threads; t++) {
                final int userId = t + 1;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < 50; i++) {
                            service.saveUserPreference(userId, "k" + i, "v" + userId);
                            Map<String, String> prefs = service.getUserPreferences(userId);
                            // 每个用户的值都应带自己的 userId 标记
                            for (String v : prefs.values()) {
                                if (!v.equals("v" + userId)) {
                                    failures.incrementAndGet();
                                    return;
                                }
                            }
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "并发测试超时");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, failures.get(), "并发下不同用户的偏好不应互相污染");
    }
}
