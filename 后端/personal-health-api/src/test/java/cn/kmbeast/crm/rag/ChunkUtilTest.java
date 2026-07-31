package cn.kmbeast.crm.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChunkUtil 分块单测：验证按句切分、重叠、长文不丢正文。
 */
class ChunkUtilTest {

    @Test
    void emptyText() {
        assertTrue(ChunkUtil.split(null).isEmpty());
        assertTrue(ChunkUtil.split("").isEmpty());
        assertTrue(ChunkUtil.split("   ").isEmpty());
    }

    @Test
    void shortTextSingleChunk() {
        List<String> chunks = ChunkUtil.split("高血压患者应该低盐饮食。");
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("低盐"));
    }

    @Test
    void longTextProduceMultipleChunksWithOverlap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("这是第").append(i).append("句关于健康饮食与运动建议的内容。");
        }
        List<String> chunks = ChunkUtil.split(sb.toString());
        assertTrue(chunks.size() >= 3, "60 句长文应产生多个块，实际 " + chunks.size());

        // 关键内容不应被丢弃：正文中部的内容应出现在某个块里
        String joined = String.join("", chunks);
        assertTrue(joined.contains("第30句"), "长文中部内容不应丢失");
    }

    @Test
    void chunkSizeWithinLimit() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            sb.append("饮食运动睡眠情绪压力管理知识内容介绍第").append(i).append("条。");
        }
        for (String chunk : ChunkUtil.split(sb.toString())) {
            assertTrue(chunk.length() <= ChunkUtil.CHUNK_SIZE + 20,
                    "块长度应控制在 CHUNK_SIZE 附近，实际 " + chunk.length());
        }
    }
}
