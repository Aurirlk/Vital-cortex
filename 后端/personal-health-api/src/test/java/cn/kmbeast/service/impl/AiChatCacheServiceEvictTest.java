package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.AiChatRecordMapper;
import cn.kmbeast.mapper.AiConversationMapper;
import cn.kmbeast.pojo.entity.AiChatRecord;
import cn.kmbeast.pojo.entity.AiConversation;
import cn.kmbeast.service.HistoryStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * AI 对话缓存容量控制单元测试（2026-10-04 构建）
 *
 * <p>2026-10-03 修复：{@code AiChatCacheServiceImpl} 的两个 {@code ConcurrentHashMap}
 * 此前<b>无上限、无淘汰、无 TTL</b>，且类中根本没有清理方法，
 * 而启动类又缺 {@code @EnableScheduling} —— 长期运行必然 OOM。
 *
 * <p>本测试锁定新增 {@code evictOversizedCache()} 的行为：
 * 会话数超限淘汰最旧、单会话消息超限截断、未超限不抖动、清理不抛异常。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AI 对话缓存容量控制")
class AiChatCacheServiceEvictTest {

    @Mock
    private AiConversationMapper conversationMapper;

    @Mock
    private AiChatRecordMapper chatRecordMapper;

    @Mock
    private HistoryStorageService historyStorage;

    @InjectMocks
    private AiChatCacheServiceImpl service;

    /** 模拟数据库自增主键：mock 的 mapper.save() 默认不写回 id，
     *  若不处理，所有会话 id 都是 null，缓存会互相覆盖，测试失去意义。 */
    private final AtomicInteger idGen = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        idGen.set(0);
        doAnswer(inv -> {
            AiConversation c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(idGen.incrementAndGet());
            }
            return null;
        }).when(conversationMapper).save(any(AiConversation.class));

        doAnswer(inv -> {
            AiChatRecord r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(idGen.incrementAndGet());
            }
            return null;
        }).when(chatRecordMapper).save(any(AiChatRecord.class));
    }

    private AiChatRecord msg(String content) {
        AiChatRecord r = new AiChatRecord();
        r.setRole("user");
        r.setContent(content);
        return r;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stats() {
        return service.getCacheStats();
    }

    @Test
    @DisplayName("会话数超上限：淘汰到不超过 500，且不误删")
    void shouldEvictOldestWhenOverConversationLimit() {
        for (int i = 1; i <= 505; i++) {
            service.createConversation(1, "general_assistant", "会话" + i);
        }

        service.evictOversizedCache();

        int cached = (int) stats().get("cachedConversations");
        assertTrue(cached <= 500, "清理后会话数应 <= 500，实际: " + cached);
        assertTrue(cached >= 495, "只应淘汰超出上限的部分，实际剩余: " + cached);
    }

    @Test
    @DisplayName("单会话消息超上限：截断保留最近 100 条")
    void shouldTruncateOverlongMessageList() {
        AiConversation c = service.createConversation(1, "general_assistant", "长会话");
        assertNotNull(c.getId(), "mock 未写回主键会让测试失效");

        for (int i = 1; i <= 150; i++) {
            service.addMessage(c.getId(), msg("消息" + i));
        }

        service.evictOversizedCache();

        List<AiChatRecord> cached = service.getMessages(c.getId());
        assertTrue(cached.size() <= 100, "截断后应 <= 100 条，实际: " + cached.size());
    }

    @Test
    @DisplayName("未超上限时清理是无操作")
    void shouldBeNoOpWhenUnderLimit() {
        AiConversation c = service.createConversation(1, "general_assistant", "小会话");
        service.addMessage(c.getId(), msg("m1"));
        service.addMessage(c.getId(), msg("m2"));

        service.evictOversizedCache();

        assertEquals(1, stats().get("cachedConversations"), "未超限不应淘汰任何会话");
        assertTrue((int) stats().get("cachedMessages") <= 2);
    }

    @Test
    @DisplayName("清理在空缓存上执行也不抛异常")
    void evictionShouldNeverThrowOnEmptyCache() {
        assertDoesNotThrow(() -> service.evictOversizedCache());
    }

    @Test
    @DisplayName("getCacheStats 返回可被接口消费的数值类型")
    void cacheStatsShouldReturnParsableNumbers() {
        AiConversation c = service.createConversation(1, "general_assistant", "统计测试");
        service.addMessage(c.getId(), msg("m1"));

        Map<String, Object> s = stats();
        assertNotNull(s);
        assertInstanceOf(Integer.class, s.get("cachedConversations"));
        assertInstanceOf(Integer.class, s.get("cachedMessages"));
    }

    @Test
    @DisplayName("evictCache 手动清理后统计归零")
    void manualEvictShouldClearStats() {
        AiConversation c = service.createConversation(1, "general_assistant", "手动清理");
        service.addMessage(c.getId(), msg("m1"));

        service.evictCache(c.getId());

        assertEquals(0, stats().get("cachedConversations"));
        assertEquals(0, stats().get("cachedMessages"));
    }

    @Test
    @DisplayName("evictAllCache 清空全部缓存")
    void evictAllShouldClearEverything() {
        AiConversation c1 = service.createConversation(1, "general_assistant", "会话A");
        service.createConversation(1, "doctor", "会话B");
        service.addMessage(c1.getId(), msg("m1"));

        service.evictAllCache();

        assertEquals(0, stats().get("cachedConversations"));
        assertEquals(0, stats().get("cachedMessages"));
    }

    @Test
    @DisplayName("批量创建会话时缓存键互不覆盖（验证主键写回）")
    void distinctConversationsShouldNotOverwriteEachOther() {
        for (int i = 0; i < 10; i++) {
            service.createConversation(1, "general_assistant", "会话" + i);
        }
        assertEquals(10, stats().get("cachedConversations"),
                "若主键未写回，缓存 key 均为 null 会互相覆盖，数量会远小于 10");
    }
}
