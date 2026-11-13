package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.AiChatRecordMapper;
import cn.kmbeast.mapper.AiConversationMapper;
import cn.kmbeast.pojo.entity.AiConversation;
import cn.kmbeast.service.HistoryStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 删除会话时的磁盘副本清理（2026-10-20 补）。
 *
 * <p><b>锁的是「删了但又回来了」这个用户可见症状</b>。会话同时存在两处：
 * MySQL（主存储）与 {@code chat_history/} 下的 JSON（备份）。
 * {@code getConversationList} 在 MySQL 查空时会回落到
 * {@code historyStorage.loadConversations(userId)}，所以只删 MySQL 而不删磁盘，
 * 结果是：逐条把会话删完 → 最后一次删完的那一刻，MySQL 已经空了 →
 * 回落分支生效，<b>被删掉的整批会话（标题和全部消息）重新出现在列表里</b>，
 * 要等 24 小时的孤儿文件清理任务跑过才消失。
 *
 * <p>批量删除在 2026-10-07 就为同样的问题打过补丁（见
 * {@code batchDeleteConversations} 里的注释），单条删除是漏掉的那一支。
 * 这里还锁一个容易写错的细节：<b>属主必须在删除之前取</b>，
 * 删完再 {@code getById} 只剩 null，磁盘副本就永远找不到归属、清不掉。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AI 会话删除：磁盘副本与 MySQL 同步")
class AiChatCacheDiskCleanupTest {

    @Mock
    private AiConversationMapper conversationMapper;

    @Mock
    private AiChatRecordMapper chatRecordMapper;

    @Mock
    private HistoryStorageService historyStorage;

    @InjectMocks
    private AiChatCacheServiceImpl service;

    @Test
    @DisplayName("单条删除会连带删磁盘副本，且属主在删除之前取")
    void singleDeleteAlsoRemovesDiskCopy() {
        when(conversationMapper.getById(9)).thenReturn(
                AiConversation.builder().id(9).userId(7).title("血压咨询").build());

        service.deleteConversation(9);

        verify(historyStorage).deleteConversation(7, 9);
        InOrder order = inOrder(conversationMapper, historyStorage);
        order.verify(conversationMapper).getById(9);
        order.verify(conversationMapper).deleteById(9);
        order.verify(historyStorage).deleteConversation(7, 9);
    }

    @Test
    @DisplayName("属主定位不到时不抛异常，跳过磁盘清理但仍删 MySQL")
    void unknownOwnerSkipsDiskCleanupWithoutBreaking() {
        when(conversationMapper.getById(anyInt())).thenReturn(null);

        assertDoesNotThrow(() -> service.deleteConversation(11));

        verify(conversationMapper).deleteById(11);
        verify(historyStorage, never()).deleteConversation(any(), any());
    }

    @Test
    @DisplayName("消息记录同样按会话删掉（避免只剩孤儿消息）")
    void messagesDeletedWithConversation() {
        when(conversationMapper.getById(12)).thenReturn(
                AiConversation.builder().id(12).userId(7).build());

        service.deleteConversation(12);

        verify(chatRecordMapper).deleteByConversationId(12);
        verify(historyStorage).deleteConversation(eq(7), eq(12));
    }

    /**
     * 这条是「回落」机制本身的护栏：MySQL 查空时列表来自磁盘，
     * 所以磁盘没删干净就等于没删。测试把它写明白，免得将来有人
     * 反过来把回落分支删掉，导致备份文件彻底读不出来。
     */
    @Test
    @DisplayName("MySQL 为空时列表回落到磁盘副本（这正是必须同步删磁盘的原因）")
    void emptyMysqlFallsBackToDisk() {
        when(conversationMapper.queryByUserId(7, null)).thenReturn(List.of());
        when(historyStorage.loadConversations(7)).thenReturn(
                List.of(AiConversation.builder().id(9).userId(7).build()));

        List<AiConversation> list = service.getConversationList(7, null);

        assertTrue(list.size() == 1 && list.get(0).getId() == 9,
                "回落成立 → 单条删除若不清磁盘，这条已删除的会话会重新出现在列表里");
    }
}
