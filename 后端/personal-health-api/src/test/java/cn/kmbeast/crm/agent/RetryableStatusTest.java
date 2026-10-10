package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.CrmException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T6：LLM 失败的可重试判定改为按 HTTP 状态码，而非中文字符串嗅探。
 *
 * <p><b>为什么要测一个「判定函数」</b>：重试逻辑的失败模式是
 * <b>静默丧失重试能力</b> —— 代码不报错，只是悄悄不重试了，
 * 用户表现为「AI 偶尔不回话」，日志里也看不出异常。
 * 这类退化只能靠针对性单测守住。</p>
 */
class RetryableStatusTest {

    /** 反射拿到私有的 isRetryableStatus（它是纯函数，无副作用，测它最直接）。 */
    private static boolean retryable(CrmException e) throws Exception {
        Object instance = new MinimalAgent();
        Method m = BaseReActAgent.class.getDeclaredMethod("isRetryableStatus", CrmException.class);
        m.setAccessible(true);
        return (boolean) m.invoke(instance, e);
    }

    /** 仅为实例化 BaseReActAgent（抽象类）而存在的最小实现。 */
    static class MinimalAgent extends BaseReActAgent {
        @Override
        protected cn.kmbeast.crm.agent.model.ReActResponse
        doRun(java.util.List<java.util.Map<String, String>> userMessages) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    @DisplayName("T6.1 429 限流可重试")
    void tooManyRequestsRetryable() throws Exception {
        assertTrue(retryable(CrmException.aiUnavailable("rate limited", 429)));
    }

    @Test
    @DisplayName("T6.2 5xx 服务端故障可重试")
    void serverErrorRetryable() throws Exception {
        for (int status : new int[]{500, 502, 503, 504}) {
            assertTrue(retryable(CrmException.aiUnavailable("boom", status)),
                    "HTTP " + status + " 应可重试");
        }
    }

    @Test
    @DisplayName("T6.3 4xx 参数/鉴权错误不可重试（重试无意义，只会拖长用户等待）")
    void clientErrorNotRetryable() throws Exception {
        for (int status : new int[]{400, 401, 403, 404, 422}) {
            assertFalse(retryable(CrmException.aiUnavailable("bad", status)),
                    "🔴 HTTP " + status + " 不应重试 —— 参数错误重试 3 次只会让用户多等 3 个退避周期");
        }
    }

    @Test
    @DisplayName("T6.4 协议层失败（响应结构不对 / 被截断）不可重试")
    void protocolFailureNotRetryable() throws Exception {
        assertFalse(retryable(CrmException.badResponse("choices为空")));
        assertFalse(retryable(CrmException.truncatedByLength(2048)));
        assertTrue(CrmException.badResponse("x").isProtocolFailure());
        assertTrue(CrmException.truncatedByLength(2048).isProtocolFailure());
    }

    @Test
    @DisplayName("T6.5 无状态码异常（网络超时/连接中断）可重试（旧实现会漏判）")
    void networkFailureRetryable() throws Exception {
        // 这是本次重写修掉的真实缺陷：旧实现靠 contains("HTTP") 判断，
        // 网络异常不含该字样 → 不重试 → 一次网络抖动整个请求就失败
        assertTrue(retryable(CrmException.aiUnavailable("网络异常: timeout")),
                "🔴 网络层失败应可重试（旧实现会漏判导致一次抖动即整体失败）");
    }

    @Test
    @DisplayName("T6.6 httpStatus 正确透传（旧实现完全没有这个能力）")
    void httpStatusCarried() {
        CrmException e = CrmException.aiUnavailable("boom", 503);
        assertEquals(Integer.valueOf(503), e.getHttpStatus());
        assertTrue(e.getMessage().contains("503"));
        // 无状态码的工厂方法仍是 null（兼容既有调用方）
        assertNull(CrmException.aiUnavailable("x").getHttpStatus());
    }

    @Test
    @DisplayName("T6.7 截断异常带明确提示（用户应知道内容不完整，而不是以为自己看到了全部）")
    void truncatedMessageIsExplicit() {
        CrmException e = CrmException.truncatedByLength(2048);
        assertEquals("AI_TRUNCATED", e.getErrorCode());
        assertTrue(e.getMessage().contains("截断"), "提示必须点明「截断」: " + e.getMessage());
        assertTrue(e.getMessage().contains("2048"), "提示应带上 max_tokens 便于排查");
        // 截断不是 HTTP 层失败，因此没有状态码 —— 这也正是它被单列为
        // protocolFailure（不可重试）的原因
        assertNull(e.getHttpStatus());
        assertTrue(e.isProtocolFailure());
    }
}