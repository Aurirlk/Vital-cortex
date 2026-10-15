package cn.kmbeast.crm.rag;

import org.junit.jupiter.api.Test;

import java.time.Duration;
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

    private static String sentences(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append("这是第").append(i).append("句关于健康饮食与运动建议的内容。");
        }
        return sb.toString();
    }

    @Test
    void chunkSizeIsHonouredPerKnowledgeBase() {
        // 块大小改成可按知识库配置，是为了让科普长文用大块、药品说明书用小块。
        // 若参数没真正生效，这个能力就只是表单上的一个装饰。
        String text = sentences(60);
        int many = ChunkUtil.split(text, 60, 10).size();
        int few = ChunkUtil.split(text, 400, 50).size();
        assertTrue(many > few, "小块应产生更多块：size=60 得 " + many + "，size=400 得 " + few);
    }

    @Test
    void extremeChunkParamsAreClampedNotRejected() {
        // 管理端表单可能填 0 或超大值。钳制而不是抛异常：
        // 保存知识库不该失败在一个与用户意图无关的细节上。
        String text = sentences(40);
        assertFalse(ChunkUtil.split(text, 1, 999).isEmpty());
        assertFalse(ChunkUtil.split(text, 99999, -5).isEmpty());
        // 重叠被钳到 0 时不应产生重复块
        assertEquals(ChunkUtil.split(text, 400, 0).stream().distinct().count(),
                ChunkUtil.split(text, 400, 0).size());
    }

    @Test
    void oversizeOverlapDoesNotHang() {
        // overlap == chunkSize 时步长为 0，硬切会死循环。钳位后必须立即返回。
        String digits = "1234567890".repeat(60);
        List<String> chunks = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> ChunkUtil.split(digits, 20, 20));
        assertFalse(chunks.isEmpty());
    }

    @Test
    void unpunctuatedLongSegmentIsSplitInsteadOfBecomingOneOversizeChunk() {
        // PDF 抽出来的表格/指标对照表普遍没有句读。整段塞进一个块，
        // 超出嵌入模型上下文后会被静默截断，后面的内容等于没入库。
        String table = "血压收缩压舒张压心率体温血糖早餐午餐晚餐".repeat(30); // 无句读，约 600 字
        List<String> chunks = ChunkUtil.split(table, 200, 50);

        assertTrue(chunks.size() > 1, "无句读长段也应被切成多块，实际 " + chunks.size() + " 块");
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 220, "块长度不应超过 chunkSize，实际 " + chunk.length());
        }
        // 尾部内容不能被丢掉
        assertTrue(String.join("", chunks).contains("晚餐"));
    }
}
