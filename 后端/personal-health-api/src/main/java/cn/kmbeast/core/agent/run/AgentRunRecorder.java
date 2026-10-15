package cn.kmbeast.core.agent.run;

import cn.kmbeast.core.agent.Audience;
import cn.kmbeast.crm.agent.tool.ToolContext;
import cn.kmbeast.mapper.AgentRunMapper;
import cn.kmbeast.mapper.AgentRunStepMapper;
import cn.kmbeast.pojo.entity.AgentRun;
import cn.kmbeast.pojo.entity.AgentRunStep;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Agent 运行记录器：把「谁、在什么问题上、调了哪些工具、耗时多久、花了多少 token」
 * 落成 {@code agent_run} + {@code agent_run_step} 两级的可观测数据。
 *
 * <p><b>三个设计约束</b>：</p>
 * <ol>
 *   <li><b>绝不影响主链路</b>。本类所有写库操作都包在 try/catch 里，
 *       失败只 debug 日志。记录是观测手段，不是业务能力 ——
 *       它挂掉不能连累用户拿不到回答。</li>
 *   <li><b>表不存在时静默降级为 no-op</b>。建表脚本由使用者自行执行，
 *       在表还没建的窗口期里，{@link #begin} 写库失败后仍会压一个空帧入栈，
 *       保证 {@link #finish} 能配平地出栈，不会错位。</li>
 *   <li><b>用栈而非单值</b>。委派（A 调 B）意味着同一线程上嵌套运行，
 *       单值 ThreadLocal 会让子运行覆盖父运行的 runId，
 *       结果是 B 的步骤被记到 A 名下、A 的结束写到了 B 的行上。</li>
 * </ol>
 *
 * <p><b>run_id 的形态</b>：父运行是一个 32 位十六进制串；子运行是
 * 「父 id 前 8 位 + '-' + 8 位随机」，因此 {@code LIKE 'xxxxxxxx%'}
 * 能一次捞出整条委派链（对应 {@code AgentRunMapper.listByRunIdPrefix}）。
 * 层级由委派深度限制（见 {@code CallAgentTool.MAX_DEPTH}），不会无限加长。</p>
 */
@Slf4j
@Component
public class AgentRunRecorder {

    /** 回答摘要长度上限，与 agent_run.answer_digest 的列宽一致。 */
    private static final int ANSWER_DIGEST_MAX = 500;

    /** 步骤结果摘要长度上限，与 agent_run_step.result_digest 的列宽一致。 */
    private static final int RESULT_DIGEST_MAX = 1000;

    /** run_id 列宽 64；留出余量，超长就退回全新 id，宁可断链也不写不进去。 */
    private static final int RUN_ID_MAX = 60;

    private static final int CHILD_PREFIX_LEN = 8;
    private static final int CHILD_RANDOM_LEN = 8;

    @Resource
    private AgentRunMapper agentRunMapper;

    @Resource
    private AgentRunStepMapper agentRunStepMapper;

    /**
     * 当前线程的运行栈。栈顶是本线程正在跑的 Agent 运行，
     * 其下方是它的委派方（如果有）。
     */
    private static final ThreadLocal<Deque<Frame>> RUN_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    /** 一次运行的记账单元。即使 DB 不可用也会入栈，只为保持出栈配平。 */
    private static final class Frame {
        private String runId;
        private final long startMs = System.currentTimeMillis();
        private long toolMs;
        private int llmCount;
        private int promptTokens;
        private int completionTokens;
        /** 步骤计数：llm / tool / handoff 共用一条递增序号。 */
        private int stepIndex;
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    /**
     * 开始一次 Agent 运行。
     *
     * <p>父子关系是**自动**推断的：如果当前线程已经有在跑的运行，
     * 新的这次就是它的子运行。委派场景下这个推断恰好就是事实，
     * 不需要调用方显式传 parentRunId —— 少一个可以传错的地方。</p>
     *
     * @return 本次运行的 runId；为 null 表示记录未启用（写库失败/表未建），
     *         调用方无需判断，后续 recordStep/finish 会自动变成 no-op
     */
    public String begin(String agentKey, String question, Integer userId) {
        Frame frame = new Frame();
        Deque<Frame> stack = RUN_STACK.get();
        // 父运行优先取本线程的运行栈顶。但委派的子 Agent 跑在工具线程池上，
        // 那里的栈是空的（父运行的帧在请求线程的栈里）—— 此时退回
        // ToolContext 里透传过来的 run_id，否则子运行会变成一棵孤立的新树，
        // 「A 委派给 B」这条链在数据上就断了。
        Frame parent = stack.peek();
        String parentRunId = parent != null ? parent.runId : ToolContext.getRunId();
        try {
            frame.runId = newRunId(parentRunId);
            if (agentRunMapper != null) {
                AgentRun run = new AgentRun();
                run.setRunId(frame.runId);
                run.setParentRunId(parentRunId);
                run.setAgentKey(agentKey);
                run.setUserId(userId);
                // 调用端：取自已透传的工具层身份（roleId），而不是让每条链路自己再传一遍。
                // 该列此前恒为 NULL —— 建表时注释写的是「调用端：user/doctor/admin」，
                // 于是「这次运行来自用户端还是医生端」在数据上根本无从查起。
                // 委派的子运行跑在池线程上，那份 ToolContext 是整体快照过去的，
                // 所以这里不需要额外的父子继承逻辑。
                run.setAudience(Audience.fromRoleId(ToolContext.getRoleId()));
                run.setQuestion(question);
                run.setStatus("RUNNING");
                agentRunMapper.save(run);
            }
        } catch (Exception e) {
            // 表未建 / 连不上：降级为不记录，但帧照压，保证配平
            log.debug("[AgentRun] 运行记录写入失败，本轮不记录: {}", rootMessage(e));
            frame.runId = null;
        }
        stack.push(frame);
        return frame.runId;
    }

    /**
     * 结束当前运行并落库最终状态。
     *
     * @param status       SUCCESS / FAILED
     * @param answer       最终回答（只存摘要，全文已在对话历史里）
     * @param errorMsg     失败原因，成功时传 null
     * @param latencyMs    端到端耗时；传 &lt;= 0 表示由记录器按入栈时刻自行计算
     */
    public void finish(String status, String answer, String errorMsg, long latencyMs) {
        Deque<Frame> stack = RUN_STACK.get();
        Frame frame = stack.poll();
        if (frame == null) {
            // 没有 begin 却被 finish：说明有代码路径绕过了 runWithContext。
            // 清空整个栈防止线程复用时把脏帧带给下一个请求。
            stack.clear();
            RUN_STACK.remove();
            return;
        }
        if (frame.runId == null || agentRunMapper == null) {
            cleanupIfEmpty(stack);
            return;
        }
        try {
            AgentRun patch = new AgentRun();
            patch.setRunId(frame.runId);
            patch.setStatus(status);
            patch.setIterations(frame.llmCount);
            patch.setPromptTokens(frame.promptTokens);
            patch.setCompletionTokens(frame.completionTokens);
            patch.setLatencyMs((int) (latencyMs > 0 ? latencyMs : System.currentTimeMillis() - frame.startMs));
            patch.setAnswerDigest(digest(answer, ANSWER_DIGEST_MAX));
            patch.setErrorMsg(digest(errorMsg, RESULT_DIGEST_MAX));
            agentRunMapper.update(patch);
        } catch (Exception e) {
            log.debug("[AgentRun] 结束状态写入失败: {}", rootMessage(e));
        }
        cleanupIfEmpty(stack);
    }

    /** 当前线程正在记录的 runId；无运行中则返回 null。 */
    public String currentRunId() {
        Frame frame = RUN_STACK.get().peek();
        return frame == null ? null : frame.runId;
    }

    // ------------------------------------------------------------------
    // 步骤记录
    // ------------------------------------------------------------------

    /** 记一次模型调用。迭代数由它累计 —— ReAct 的一轮就是一次 LLM 调用。 */
    public void recordLlm(long latencyMs) {
        Frame frame = currentFrame();
        if (frame == null) {
            return;
        }
        frame.llmCount++;
        saveStep(frame, "llm", null, null, null, null, latencyMs);
    }

    /** 记一次工具调用。 */
    public void recordTool(String toolName, String argsJson, String resultDigest, long latencyMs) {
        Frame frame = currentFrame();
        if (frame == null) {
            return;
        }
        frame.toolMs += Math.max(latencyMs, 0);
        saveStep(frame, "tool", toolName, null, argsJson, resultDigest, latencyMs);
    }

    /**
     * 记一次委派。{@code targetAgent} 落的是目标 Agent 的 key。
     * 这是「A 把问题交给了 B」在数据上唯一的痕迹 —— 没有它，
     * 一次委派与一次普通工具调用在事后完全无法区分。
     */
    public void recordHandoff(String targetAgent, String question, String resultDigest, long latencyMs) {
        Frame frame = currentFrame();
        if (frame == null) {
            return;
        }
        saveStep(frame, "handoff", null, targetAgent, question, resultDigest, latencyMs);
    }

    /** 累加一次 LLM 调用的 token 用量（取自响应的 usage；流式链路可能拿不到，则为 0）。 */
    public void addTokens(int promptTokens, int completionTokens) {
        Frame frame = currentFrame();
        if (frame == null) {
            return;
        }
        frame.promptTokens += Math.max(promptTokens, 0);
        frame.completionTokens += Math.max(completionTokens, 0);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private Frame currentFrame() {
        if (agentRunMapper == null && agentRunStepMapper == null) {
            return null;
        }
        return RUN_STACK.get().peek();
    }

    private void saveStep(Frame frame, String stepType, String toolName, String targetAgent,
                          String argsJson, String resultDigest, long latencyMs) {
        if (frame.runId == null || agentRunStepMapper == null) {
            return;
        }
        try {
            AgentRunStep step = new AgentRunStep();
            step.setRunId(frame.runId);
            step.setStepIndex(frame.stepIndex++);
            step.setStepType(stepType);
            step.setToolName(toolName);
            step.setTargetAgent(targetAgent);
            step.setArgsJson(digest(argsJson, RESULT_DIGEST_MAX));
            step.setResultDigest(digest(resultDigest, RESULT_DIGEST_MAX));
            step.setLatencyMs((int) Math.max(latencyMs, 0));
            agentRunStepMapper.save(step);
        } catch (Exception e) {
            log.debug("[AgentRun] 步骤写入失败(type={}): {}", stepType, rootMessage(e));
        }
    }

    /**
     * 生成 run_id。
     *
     * <p>有父运行时沿用父 id 前缀 + 一段随机后缀 —— 既能一眼看出归属，
     * 又能靠前缀 LIKE 一次捞出整条委派链。拼接后若超长（层级过深），
     * 退回一个全新 id：宁可断链，也不能写出一个超列宽的值让写库失败。</p>
     */
    private static String newRunId(String parentRunId) {
        if (parentRunId == null || parentRunId.isEmpty()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        String prefix = parentRunId.length() > CHILD_PREFIX_LEN
                ? parentRunId.substring(0, CHILD_PREFIX_LEN)
                : parentRunId;
        String candidate = prefix + "-" + shortRandom();
        return candidate.length() <= RUN_ID_MAX
                ? candidate
                : UUID.randomUUID().toString().replace("-", "");
    }

    private static String shortRandom() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, CHILD_RANDOM_LEN);
    }

    /** 栈空时清掉 ThreadLocal 本身，不留空 Deque 给下一个复用该线程的请求。 */
    private static void cleanupIfEmpty(Deque<Frame> stack) {
        if (stack.isEmpty()) {
            RUN_STACK.remove();
        }
    }

    /**
     * 截断成摘要。换行折成空格 —— 摘要是给人扫一眼的，
     * 保留换行会让管理端表格里的行高失控。
     */
    private static String digest(String s, int max) {
        if (s == null) {
            return null;
        }
        String flat = s.replace('\n', ' ').replace('\r', ' ').trim();
        if (flat.isEmpty()) {
            return null;
        }
        return flat.length() <= max ? flat : flat.substring(0, max);
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null ? cur.getClass().getSimpleName() : msg;
    }
}
