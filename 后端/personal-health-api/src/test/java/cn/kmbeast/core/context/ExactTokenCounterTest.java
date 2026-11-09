package cn.kmbeast.core.context;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 阶段 9：精确 token 计数（真实 BPE，非 len*0.6 粗估）。 */
@DisplayName("精确 token 计数")
class ExactTokenCounterTest {

    @Test
    @DisplayName("离线仓 jtokkit 可用，模式必须是 tokenizer 级精确而非降级")
    void modeIsTokenizerExact() {
        ExactTokenCounter counter = new ExactTokenCounter();
        assertTrue(counter.isExact(), "离线仓已有 jtokkit 1.1.0 + tiktoken 资源，不应降级");
        assertTrue(counter.mode().startsWith("tokenizer:"), "实际模式: " + counter.mode());
        assertEquals(ExactTokenCounter.EncodingName.CL100K, counter.getEncodingName());
    }

    @Test
    @DisplayName("可切换 o200k_base")
    void canSwitchEncoding() {
        ExactTokenCounter counter = new ExactTokenCounter();
        counter.use(ExactTokenCounter.EncodingName.O200K);
        assertEquals(ExactTokenCounter.EncodingName.O200K, counter.getEncodingName());
        assertTrue(counter.mode().contains("o200k"), "实际模式: " + counter.mode());
    }

    @Test
    @DisplayName("null / 空串恒为 0")
    void nullAndEmptyAreZero() {
        ExactTokenCounter counter = new ExactTokenCounter();
        assertEquals(0, counter.count(null));
        assertEquals(0, counter.count(""));
    }

    @Test
    @DisplayName("保守上界恒不低估且为整数")
    void upperBoundNeverUnderestimates() {
        String cn = "高血压患者每日血压监测记录".repeat(20);
        int bound = ExactTokenCounter.conservativeUpperBound(cn);
        assertTrue(bound > 0);
        assertTrue(bound >= cn.length() / 2, "中文 3 字节/字，bytes/2 上界应大于字数的一半");
    }

    @Test
    @DisplayName("messages 计数含多模态图片占位")
    void messagesIncludeImageTokens() {
        ExactTokenCounter counter = new ExactTokenCounter();
        JSONArray msgs = new JSONArray();
        JSONObject text = new JSONObject();
        text.put("role", "user");
        text.put("content", "hello");
        msgs.add(text);
        int withoutImage = counter.countMessages(msgs, 100);
        JSONArray parts = new JSONArray();
        JSONObject p = new JSONObject();
        p.put("type", "image_url");
        p.put("image_url", new JSONObject());
        parts.add(p);
        JSONObject multi = new JSONObject();
        multi.put("role", "user");
        multi.put("content", parts);
        msgs.add(multi);
        int withImage = counter.countMessages(msgs, 100);
        assertTrue(withImage >= withoutImage + 100, "图片占位应计入");
    }

    @Test
    @DisplayName("usage 事后校验只记录偏差，不改变计数结果")
    void usageReconcileIsObservationOnly() {
        ExactTokenCounter counter = new ExactTokenCounter();
        int before = counter.count("血压趋势");
        counter.recordUsage(before, before + 10);
        assertEquals(before, counter.count("血压趋势"));
        assertTrue(counter.getLastDeltaRatioX100() > 0);
    }

    /**
     * 反向验证专用：这是唯一断言「精确值 ≠ len*0.6 粗估」的用例。
     * 探针把 count() 换回粗估时，本类应恰好 1 例真红。
     */
    @Test
    @DisplayName("🔴 精确计数不得回退为 len*0.6 粗估")
    void preciseCountIsNotRoughEstimate() {
        ExactTokenCounter counter = new ExactTokenCounter();
        String english = "The patient blood pressure trend is stable over the last thirty days";
        int exact = counter.count(english);
        int rough = (int) Math.ceil(english.length() * 0.6);
        assertNotEquals(rough, exact,
                "cl100k_base 的真实 BPE 计数与 len*0.6 粗估必须不同；若相同说明回退成了粗估");
        assertTrue(counter.isExact());
    }
}
