package cn.kmbeast.core.agent.run;

import cn.kmbeast.core.agent.Audience;
import cn.kmbeast.crm.agent.tool.ToolContext;
import cn.kmbeast.mapper.AgentRunMapper;
import cn.kmbeast.mapper.AgentRunStepMapper;
import cn.kmbeast.pojo.entity.AgentRun;
import cn.kmbeast.pojo.entity.AgentRunStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link AgentRunRecorder} 的嵌套、配平与降级行为的单测。
 *
 * <p>这个类是全项目<b>唯一一个「故意不在乎失败」</b>的组件 —— 它写库全部包在
 * try/catch 里，失败只打 debug 日志。这是对的（观测手段不该拖垮业务），
 * 但它带来一个反直觉的后果：<b>这里出的错不会以异常的形式出现</b>。
 * 唯一的症状是「管理端运行记录里有些行是空的/串的」，而那时已经离现场很远了。</p>
 *
 * <p>三件必须锁住的事：</p>
 * <ol>
 *   <li><b>委派是嵌套的</b>。A 调 B 时两个 Agent 在同一线程上先后运行。
 *       如果用单个 ThreadLocal 存 runId，B 的 begin 会覆盖 A 的，
 *       于是 B 的步骤记到 A 名下、A 的 finish 写进 B 的行 —— 数据全串，
 *       而且因为不报错，会一路错到有人去看报表。</li>
 *   <li><b>入栈出栈必须配平</b>，即使写库失败也要配平。否则线程复用时
 *       脏帧会留给下一个完全无关的请求。</li>
 *   <li><b>子运行靠 {@code ToolContext} 认父</b>。子 Agent 跑在工具线程池上，
 *       那里的运行栈是空的，认不出父运行就会变成一棵孤立的新树，
 *       「A 委派给 B」这条链在数据上断掉。</li>
 * </ol>
 */
@DisplayName("AgentRunRecorder 嵌套与降级")
class AgentRunRecorderTest {

    private AgentRunMapper runMapper;
    private AgentRunStepMapper stepMapper;
    private AgentRunRecorder recorder;

    private AgentRunRecorder newRecorder() {
        AgentRunRecorder r = new AgentRunRecorder();
        ReflectionTestUtils.setField(r, "agentRunMapper", runMapper);
        ReflectionTestUtils.setField(r, "agentRunStepMapper", stepMapper);
        return r;
    }

    private void initMappers() {
        runMapper = mock(AgentRunMapper.class);
        stepMapper = mock(AgentRunStepMapper.class);
        recorder = newRecorder();
    }

    /**
     * 运行栈是 {@code private static final}，跨用例活在同一线程上。
     * 每个用例结束必须把它整个摘掉，否则上一个用例的脏帧会污染下一个。
     */
    @AfterEach
    @SuppressWarnings("unchecked")
    void clearRunStack() {
        ThreadLocal<?> stack = (ThreadLocal<?>) ReflectionTestUtils.getField(AgentRunRecorder.class, "RUN_STACK");
        if (stack != null) {
            stack.remove();
        }
        ToolContext.clear();
    }

    private AgentRun lastSavedRun() {
        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(runMapper).save(captor.capture());
        return captor.getValue();
    }

    private List<AgentRun> savedRuns() {
        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(runMapper, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private List<AgentRunStep> savedSteps() {
        ArgumentCaptor<AgentRunStep> captor = ArgumentCaptor.forClass(AgentRunStep.class);
        verify(stepMapper, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    // ==================== 基本生命周期 ====================

    @Test
    @DisplayName("T1 begin 落一行 RUNNING；finish 把同一 runId 原地收敛为终态")
    void beginThenFinishPatchesSameRow() {
        initMappers();
        String runId = recorder.begin("doctor", "我头痛三天了", 42);

        assertNotNull(runId);
        assertEquals(32, runId.length(), "父运行是标准 32 位十六进制串");
        assertTrue(runId.matches("[0-9a-f]{32}"));

        AgentRun started = lastSavedRun();
        assertEquals(runId, started.getRunId());
        assertEquals("RUNNING", started.getStatus());
        assertEquals("doctor", started.getAgentKey());
        assertEquals(42, started.getUserId());
        assertEquals("我头痛三天了", started.getQuestion());
        assertNull(started.getParentRunId(), "根运行没有父");

        recorder.finish("SUCCESS", "建议多休息", null, 1234L);

        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(runMapper).update(captor.capture());
        AgentRun patch = captor.getValue();
        assertEquals(runId, patch.getRunId(), "终态必须写回同一行，否则「运行中」的行永远不收敛");
        assertEquals("SUCCESS", patch.getStatus());
        assertEquals(1234, patch.getLatencyMs());
        assertEquals("建议多休息", patch.getAnswerDigest());
        assertNull(patch.getErrorMsg());
    }

    @Test
    @DisplayName("T2 finish 在栈空时安全（不该 begin 的路径绕过了入口时不能 NPE）")
    void finishWithoutBeginIsSafe() {
        initMappers();
        assertDoesNotThrow(() -> recorder.finish("FAILED", null, "无主", 10L));
        assertNull(recorder.currentRunId());
    }

    @Test
    @DisplayName("T3 latencyMs<=0 时由记录器按入栈时刻自行计算，不写 0")
    void latencyIsSelfComputedWhenNotGiven() {
        initMappers();
        recorder.begin("doctor", "q", null);
        recorder.finish("SUCCESS", "a", null, -1L);

        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(runMapper).update(captor.capture());
        assertTrue(captor.getValue().getLatencyMs() >= 0);
    }

    // ==================== 嵌套与配平 ====================

    @Test
    @DisplayName("T4 嵌套运行：子 runId 前缀取自父 id，parentRunId 指向父（LIKE 前缀能捞出整条链）")
    void nestedRunLinksToParent() {
        initMappers();
        String parent = recorder.begin("general_assistant", "我头痛还失眠", null);
        String child = recorder.begin("doctor", "我头痛三天了", null);

        assertNotEquals(parent, child, "子运行绝不能复用父的 runId");
        assertTrue(child.startsWith(parent.substring(0, 8) + "-"),
                "子 id = 父 id 前 8 位 + '-' + 随机，这样 LIKE 'xxxxxxxx%' 一次捞出整条委派链");

        List<AgentRun> runs = savedRuns();
        assertEquals(2, runs.size());
        assertEquals(parent, runs.get(1).getParentRunId());
        assertEquals(child, runs.get(1).getRunId());

        // 出栈顺序：先结算子，再结算父 —— 各自回到自己的行
        ArgumentCaptor<AgentRun> patches = ArgumentCaptor.forClass(AgentRun.class);
        recorder.finish("SUCCESS", "子的回答", null, 5L);
        recorder.finish("SUCCESS", "父的回答", null, 9L);
        verify(runMapper, times(2)).update(patches.capture());
        assertEquals(child, patches.getAllValues().get(0).getRunId());
        assertEquals("子的回答", patches.getAllValues().get(0).getAnswerDigest());
        assertEquals(parent, patches.getAllValues().get(1).getRunId());
        assertEquals("父的回答", patches.getAllValues().get(1).getAnswerDigest(),
                "单值 ThreadLocal 会在这里把子的回答写到父的行上（或用父的覆盖子的）");
    }

    @Test
    @DisplayName("T5 子 Agent 跑在池线程上：本线程栈空时靠 ToolContext 认父")
    void childOnPoolThreadFindsParentThroughToolContext() {
        initMappers();
        String parent = recorder.begin("general_assistant", "问题", null);

        // 模拟工具线程池：栈是空的，只有 ToolContext 里透传的 runId
        clearRunStackOnly();
        ToolContext.set(ToolContext.KEY_RUN_ID, parent);

        String child = recorder.begin("doctor", "子问题", null);
        assertEquals(parent, savedRuns().get(1).getParentRunId(),
                "认不出父运行，委派链在数据上就断了 —— 事后看是两个毫不相关的运行");
        assertTrue(child.startsWith(parent.substring(0, 8) + "-"));
    }

    @Test
    @DisplayName("T6 写库失败（表未建）→ runId 为 null，但栈仍配平")
    void dbFailureStillBalancesTheStack() {
        runMapper = mock(AgentRunMapper.class);
        stepMapper = mock(AgentRunStepMapper.class);
        when(runMapper.save(any())).thenThrow(new RuntimeException("Table 'agent_run' doesn't exist"));
        recorder = newRecorder();

        assertNull(recorder.begin("doctor", "q", null), "记录未启用时如实返回 null，调用方无需判断");

        // 关键：即使 runId 是 null，帧也必须压进去了，否则 finish 会去 poll 上一个运行的帧
        recorder.finish("SUCCESS", "a", null, 1L);
        assertNull(recorder.currentRunId(), "配平失败会让脏帧留给复用该线程的下一个请求");
    }

    @Test
    @DisplayName("T7 收尾不留脏帧：同一线程的下一个请求必须是全新的根运行")
    void noDirtyFrameLeaksToTheNextRequest() {
        initMappers();
        @SuppressWarnings("unchecked")
        ThreadLocal<java.util.Deque<?>> stack =
                (ThreadLocal<java.util.Deque<?>>) ReflectionTestUtils.getField(AgentRunRecorder.class, "RUN_STACK");

        String first = recorder.begin("doctor", "第一个请求", null);
        Object stackDuringRun = stack.get();
        recorder.finish("SUCCESS", "a", null, 1L);
        assertNull(recorder.currentRunId());

        // 不能直接断言「ThreadLocal 被 remove 了」：它是 withInitial 的，
        // 任何一次 get() 都会在值为空时原地重建一个空 Deque —— 包括断言自己那次 get()。
        // 能观察到的等价事实是「拿到的是另一个实例」，即旧的那份确实已经被丢了。
        assertNotSame(stackDuringRun, stack.get(),
                "栈空时若只 clear 而不 remove，线程池复用该线程时会把上一轮的 Deque 带给下一个请求");

        // 更要紧的是行为：下一个请求不能莫名其妙挂在上一轮运行下面
        String second = recorder.begin("nutritionist", "第二个请求", null);
        assertNotEquals(first, second);
        assertNull(savedRuns().get(1).getParentRunId(),
                "脏帧会让一个全新请求变成上一轮的子运行，委派树从此就是错的");
        assertEquals("nutritionist", savedRuns().get(1).getAgentKey());
        recorder.finish("SUCCESS", "b", null, 1L);
    }

    // ==================== 降级为 no-op ====================

    @Test
    @DisplayName("T8 两个 Mapper 都没装配 → 全部方法静默 no-op，不 NPE")
    void noMappersMeansNoOp() {
        recorder = new AgentRunRecorder();   // 什么都不注入

        assertDoesNotThrow(() -> {
            recorder.begin("doctor", "q", null);
            recorder.recordLlm(10L);
            recorder.recordTool("web_search", "{}", "r", 10L);
            recorder.recordHandoff("doctor", "q", "r", 10L);
            recorder.addTokens(100, 50);
            recorder.finish("SUCCESS", "a", null, 1L);
        });
    }

    @Test
    @DisplayName("T9 步骤表读不到时只丢步骤，run 本身照常收敛")
    void stepFailureDoesNotBreakRun() {
        runMapper = mock(AgentRunMapper.class);
        stepMapper = mock(AgentRunStepMapper.class);
        when(stepMapper.save(any())).thenThrow(new RuntimeException("Table 'agent_run_step' doesn't exist"));
        recorder = newRecorder();

        String runId = recorder.begin("doctor", "q", null);
        assertNotNull(runId, "步骤表的问题不该让整个运行变成不记录");
        assertDoesNotThrow(() -> {
            recorder.recordLlm(10L);
            recorder.recordTool("web_search", null, null, 10L);
            recorder.finish("SUCCESS", "a", null, 1L);
        });
        verify(runMapper).update(any());
    }

    // ==================== 步骤与统计 ====================

    @Test
    @DisplayName("T10 步骤序号单调递增，llm/tool/handoff 共用一条序号线")
    void stepIndexIsSharedAndMonotonic() {
        initMappers();
        recorder.begin("doctor", "q", null);
        recorder.recordLlm(10L);
        recorder.recordTool("web_search", "{\"q\":\"x\"}", "结果摘要", 20L);
        recorder.recordHandoff("nutritionist", "子问题", "子结论", 30L);
        recorder.recordLlm(11L);

        List<AgentRunStep> steps = savedSteps();
        assertEquals(4, steps.size());
        for (int i = 0; i < steps.size(); i++) {
            assertEquals(i, steps.get(i).getStepIndex(),
                    "序号不连续的话，管理端按序号排序的轨迹会缺行，看不出中间漏了什么");
        }
        assertEquals("llm", steps.get(0).getStepType());
        assertEquals("tool", steps.get(1).getStepType());
        assertEquals("web_search", steps.get(1).getToolName());
        assertEquals("handoff", steps.get(2).getStepType());
        assertEquals("nutritionist", steps.get(2).getTargetAgent(),
                "没有 target_agent 这一列，一次委派与一次普通工具调用在事后完全无法区分");
    }

    @Test
    @DisplayName("T11 迭代数 = LLM 调用次数；token 累加（负数按 0 处理）")
    void iterationsAndTokensAccumulate() {
        initMappers();
        recorder.begin("doctor", "q", null);
        recorder.recordLlm(1L);
        recorder.recordLlm(1L);
        recorder.recordLlm(1L);
        recorder.addTokens(100, 50);
        recorder.addTokens(-5, 20);   // 流式链路拿不到 usage 时会传 0/异常值
        recorder.finish("SUCCESS", "a", null, 1L);

        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(runMapper).update(captor.capture());
        AgentRun patch = captor.getValue();
        assertEquals(3, patch.getIterations(), "ReAct 的一轮就是一次 LLM 调用");
        assertEquals(100, patch.getPromptTokens());
        assertEquals(70, patch.getCompletionTokens());
    }

    @Test
    @DisplayName("T12 回答摘要截断到列宽，换行折成空格，空内容归一为 null")
    void answerDigestIsTruncatedAndFlattened() {
        initMappers();
        recorder.begin("doctor", "q", null);
        recorder.finish("SUCCESS", "第一行\n第二行\r\n第三行", null, 1L);

        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(runMapper).update(captor.capture());
        String digest = captor.getValue().getAnswerDigest();
        assertFalse(digest.contains("\n"));
        assertFalse(digest.contains("\r"));
        assertTrue(digest.startsWith("第一行 第二行"),
                "保留换行会让管理端表格行高失控：实际=" + digest);

        // 超长与空白
        initMappers();
        recorder.begin("doctor", "q", null);
        recorder.finish("SUCCESS", "很".repeat(900), null, 1L);
        ArgumentCaptor<AgentRun> longPatch = ArgumentCaptor.forClass(AgentRun.class);
        verify(runMapper).update(longPatch.capture());
        assertEquals(500, longPatch.getValue().getAnswerDigest().length(),
                "超过列宽会在写库时抛异常，而这里抛异常只会被 catch 掉、静默丢记录");

        initMappers();
        recorder.begin("doctor", "q", null);
        recorder.finish("SUCCESS", "   \n  ", null, 1L);
        ArgumentCaptor<AgentRun> blankPatch = ArgumentCaptor.forClass(AgentRun.class);
        verify(runMapper).update(blankPatch.capture());
        assertNull(blankPatch.getValue().getAnswerDigest());
    }

    @Test
    @DisplayName("T13 无运行在跑时记步骤是 no-op，不会抛也不会串到别处")
    void stepsOutsideARunAreNoOp() {
        initMappers();
        assertDoesNotThrow(() -> {
            recorder.recordLlm(1L);
            recorder.recordTool("t", null, null, 1L);
            recorder.recordHandoff("d", null, null, 1L);
            recorder.addTokens(1, 1);
        });
        verifyNoInteractions(stepMapper);
        verifyNoInteractions(runMapper);
    }

    // ==================== 调用端（多端解耦的审计面） ====================

    @Test
    @DisplayName("T14 运行记录带上调用端，取自工具层身份而不是调用方的自述")
    void audienceComesFromToolLayerIdentity() {
        initMappers();
        ToolContext.setIdentity(3, null);
        recorder.begin("doctor", "这位患者的血糖趋势", 7);
        ToolContext.setIdentity(2, null);
        recorder.begin("doctor", "我的血糖趋势", 7);

        List<AgentRun> runs = savedRuns();
        assertEquals(Audience.DOCTOR, runs.get(0).getAudience(),
                "roleId=3 是医生端 —— 记成 user 会让「医生端到底跑了什么」无从查起");
        assertEquals(Audience.USER, runs.get(1).getAudience());

        // 委派子运行跑在池线程上，读到的是整体快照过去的那份 ToolContext；
        // 这里没有模拟出身份，端就应当是未知，而不是沿用上一次运行的值。
        ToolContext.setIdentity(null, null);
        recorder.begin("doctor", "第三个请求", 7);
        assertNull(savedRuns().get(2).getAudience(),
                "认不出端时留 NULL（= 未知），不猜一个端填进去");
    }

    @Test
    @DisplayName("T15 身份未装配时不写端，也不影响其余字段落库")
    void audienceIsNullWhenNoIdentity() {
        initMappers();
        recorder.begin("doctor", "q", null);

        AgentRun run = lastSavedRun();
        assertNull(run.getAudience());
        assertEquals("RUNNING", run.getStatus(), "端只是附加列，取不到不能拖累整行");
        assertEquals("doctor", run.getAgentKey());
    }

    /** 只摘运行栈（模拟池线程），保留 ToolContext。 */
    @SuppressWarnings("unchecked")
    private static void clearRunStackOnly() {
        ThreadLocal<?> stack = (ThreadLocal<?>) ReflectionTestUtils.getField(AgentRunRecorder.class, "RUN_STACK");
        if (stack != null) {
            stack.remove();
        }
    }
}
