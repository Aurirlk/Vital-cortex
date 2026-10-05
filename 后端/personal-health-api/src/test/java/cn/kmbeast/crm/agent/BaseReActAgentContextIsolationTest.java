package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.model.ReActResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BaseReActAgent} 对话上下文隔离的回归测试。
 *
 * <p><b>为什么这个测试必须存在</b>：本类是 {@code @Service} 单例，角色与数据引导指令
 * 存在 {@link ThreadLocal} 里。Tomcat 线程池会复用线程，一旦某次请求没清理，
 * 下一个复用该线程的用户就会带着上一位的角色指令对话——在健康咨询场景下，
 * 把营养问题当心理问题回应、或带着别人的追问指令，是实打实的误导。
 *
 * <p>2026-10-04 改成「读取即清除」后，即使调用方忘记清理，最坏也只是本轮没有角色增强，
 * 而不会把别人的上下文带进来。本测试锁死这个性质。
 *
 * <p>用 {@link ReActAgent}（具体子类）作为被测对象；{@code enhanceSystemPrompt} 是
 * protected，通过反射调用，避免真的发起 LLM 请求。
 */
@DisplayName("BaseReActAgent 角色与引导指令的一次性消费与线程隔离")
class BaseReActAgentContextIsolationTest {

    /** 直接读 enhanceSystemPrompt 的产出（protected 方法） */
    private String promptOf(BaseReActAgent agent) throws Exception {
        Method m = BaseReActAgent.class.getDeclaredMethod("enhanceSystemPrompt");
        m.setAccessible(true);
        return (String) m.invoke(agent);
    }

    private BaseReActAgent newAgent() {
        // ReActAgent 无显式构造，直接 new；不注入依赖也没关系，
        // enhanceSystemPrompt 只读 roleHint/guidancePrompt 与 crmConfig.getReactPrompt()，
        // crmConfig 为 null 时 getSystemPrompt() 会走 DEFAULT_SYSTEM_PROMPT。
        return new ReActAgent();
    }

    @Test
    @DisplayName("设了角色后 prompt 带上该角色指令")
    void roleInstructionAppears() throws Exception {
        BaseReActAgent agent = newAgent();
        agent.setRoleHint("doctor");
        String p = promptOf(agent);
        assertTrue(p.contains("全科医生"), "角色指令未注入 prompt");
    }

    @Test
    @DisplayName("角色指令只生效一次——第二次读取必须拿不到（一次性消费）")
    void roleIsConsumedOnce() throws Exception {
        BaseReActAgent agent = newAgent();
        agent.setRoleHint("nutritionist");
        String first = promptOf(agent);
        assertTrue(first.contains("营养师"), "首次读取应带上角色指令");

        // 关键断言：不清理 ThreadLocal，直接再读一次
        String second = promptOf(agent);
        assertNotEquals(first, second, "第二次读取竟与第一次相同，说明角色指令没有真正失效");
        assertFalse(second.contains("## 本轮角色：营养师"),
                "角色指令被重复消费了——这会导致同一线程的下一轮对话仍带着上一位的角色，"
                        + "属于上下文泄露。读取即清除的语义未生效");
    }

    @Test
    @DisplayName("引导指令同样是一次性的")
    void guidanceIsConsumedOnce() throws Exception {
        BaseReActAgent agent = newAgent();
        agent.setGuidancePrompt("\n\n【引导】请告诉我你的身高和体重。");
        String first = promptOf(agent);
        assertTrue(first.contains("【引导】"), "引导指令未注入");

        String second = promptOf(agent);
        assertFalse(second.contains("【引导】"), "引导指令被重复消费");
    }

    @Test
    @DisplayName("没设角色时不注入任何角色段落")
    void noRoleNoInstruction() throws Exception {
        BaseReActAgent agent = newAgent();
        String p = promptOf(agent);
        assertFalse(p.contains("## 本轮角色"), "未设角色时不应出现角色段落");
    }

    @Test
    @DisplayName("未知角色码安全降级，不抛异常也不注入错误角色")
    void unknownRoleFallsBackSafely() throws Exception {
        BaseReActAgent agent = newAgent();
        agent.setRoleHint("不存在的角色");
        String p = promptOf(agent);
        // setRoleHint 内部已把 blank/null 归一为 general_assistant，
        // 非空但未知的值不应导致崩溃，也不该匹配到别的角色
        assertFalse(p.contains("全科医生"), "未知角色码错误地匹配到了医生角色");
        assertFalse(p.contains("营养师"), "未知角色码错误地匹配到了营养师角色");
    }

    @Test
    @DisplayName("显式 clearConversationContext 后上下文立即失效")
    void explicitClearWorks() throws Exception {
        BaseReActAgent agent = newAgent();
        agent.setRoleHint("psychologist");
        agent.setGuidancePrompt("\n\n【引导】最近情绪如何？");
        agent.clearConversationContext();
        String p = promptOf(agent);
        assertFalse(p.contains("心理咨询师"), "clear 后仍残留角色");
        assertFalse(p.contains("【引导】"), "clear 后仍残留引导");
    }

    @Test
    @DisplayName("并发下不同线程的角色互不干扰（核心防串号断言）")
    void concurrentThreadsDoNotLeak() throws Exception {
        BaseReActAgent shared = newAgent();   // 故意共用同一个单例，模拟 Spring 单例
        String[] roles = {"doctor", "nutritionist", "psychologist", "analyst",
                "consultant", "general_assistant"};
        String[] keywords = {"全科医生", "营养师", "心理咨询师", "报告分析师", "健康助手", "全能助手"};

        int n = roles.length;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger wrong = new AtomicInteger();
        AtomicReference<String> firstError = new AtomicReference<>();

        for (int i = 0; i < n; i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    for (int r = 0; r < 50; r++) {
                        shared.setRoleHint(roles[idx]);
                        String p = promptOf(shared);
                        // 本线程只应看到自己角色的指令
                        if (!p.contains(keywords[idx])) {
                            wrong.incrementAndGet();
                        }
                        // 不应看到任何其他角色的指令
                        for (int k = 0; k < n; k++) {
                            if (k != idx && p.contains(keywords[k])) {
                                wrong.incrementAndGet();
                            }
                        }
                    }
                } catch (Exception e) {
                    firstError.compareAndSet(null, e.toString());
                } finally {
                    done.countDown();
                }
            });
            t.setDaemon(true);
            t.start();
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "并发测试超时");
        if (firstError.get() != null) {
            throw new AssertionError("并发执行抛异常: " + firstError.get());
        }
        assertEquals(0, wrong.get(),
                "并发下出现了角色串号（单例 + ThreadLocal 的经典失效场景）");
    }

    @Test
    @DisplayName("同一线程连续两次完整对话，第二次不携带第一次的角色")
    void sequentialTurnsDoNotAccumulate() throws Exception {
        BaseReActAgent agent = newAgent();
        // 模拟：请求1 设为医生并完成一轮（不显式清理）
        agent.setRoleHint("doctor");
        promptOf(agent);
        // 模拟：请求2 走 CRM 老链路（从不设 roleHint）
        String second = promptOf(agent);
        assertFalse(second.contains("全科医生"),
                "上一轮的角色泄漏到了下一轮——CrmChatController 这类不设角色的调用方会中招");
    }
}
