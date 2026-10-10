package cn.kmbeast.core.nl2sql;

import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.ColumnSchema;
import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.TableMeta;
import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.TableSchema;
import cn.kmbeast.core.nl2sql.Nl2SqlLlmClient;
import cn.kmbeast.core.nl2sql.DataAgentConfigHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DataAgentPipeline audience 并发不串测试（P1-1 / T9，2026-10-07）。
 *
 * <p><b>背景</b>：{@code DataAgentPipeline} 是单例 {@code @Service}。P1-1 之前
 * {@code audience} 被存为实例字段（{@code private volatile String audience}），在
 * {@code run()} 里 set、{@code finally} 清空。ReAct 多轮 + 工具线程池并发时，A 轮的
 * doctor 配置会被 B 轮的 user 读到，导致分层开关/行数/超时全部错乱（风险 R3）。</p>
 *
 * <p><b>本测试如何复现生产形态</b>：用<b>单个共享</b> {@code DataAgentPipeline} 实例
 * （与单例 @Service 一致），20 个线程并发跑，每个线程固定自己的 audience（user/doctor
 * 交替）。{@link RecordingConfig#getMaxRowsFor} / {@link RecordingConfig#getQueryTimeoutFor} /
 * {@link RecordingConfig#isEnabledFor} 记录「当前线程实际读到的 audience」。
 * 若任一线程观察到的 audience 与自己的预期不一致 → 串端。</p>
 *
 * <p>🔴 <b>反向验证（务必执行一次）</b>：把 {@code DataAgentPipeline.run} 临时改回
 * <pre>
 *   this.audience = audience;   // 旧 bug：写实例字段
 *   ...
 *   doRun(req, dataOwnerId, sink);   // 内部读 this.audience
 * </pre>
 * 重编主代码后再跑本测试，<b>必须变红</b>（部分线程会同时观察到 "user" 与 "doctor"）。
 * 验证完立即还原为形参透传（现在的实现），再重编。</p>
 */
@DisplayName("DataAgentPipeline：audience 形参透传，并发不串端")
class DataAgentPipelineAudienceConcurrencyTest {

    /** 线程数。 */
    private static final int THREADS = 20;
    /** 每线程迭代次数（放大碰撞窗口，让回归必然可测）。 */
    private static final int ITERATIONS = 60;

    /** 用户端 / 医生端 audience 常量（与 DataAgentConfigHolder 对齐）。 */
    private static final String AUDIENCE_USER = DataAgentConfigHolder.AUDIENCE_USER;
    private static final String AUDIENCE_DOCTOR = DataAgentConfigHolder.AUDIENCE_DOCTOR;

    /** 每线程预期 audience。 */
    private final ConcurrentHashMap<Long, String> expectedByThread = new ConcurrentHashMap<>();
    /** 每线程实际观察到的 audience 集合（去重，用于检测串端：出现 2 个即串端）。 */
    private final ConcurrentHashMap<Long, Set<String>> seenByThread = new ConcurrentHashMap<>();
    /** 每线程 audience 读取总次数（不去重，用于足量性 sanity 检查）。 */
    private final ConcurrentHashMap<Long, AtomicInteger> callCountByThread = new ConcurrentHashMap<>();
    /** 是否有任意线程观察到非预期 audience（串端）。 */
    private final AtomicBoolean violated = new AtomicBoolean(false);
    /** 收集断言失败信息。 */
    private final StringBuilder violationDetail = new StringBuilder();

    /** 记录 audience 的 Config 桩：每个 audience 读取方法都登记当前线程看到的 audience。 */
    private class RecordingConfig extends DataAgentConfigHolder {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean isReportEnabled() {
            return false;
        }

        @Override
        public int getMaxRows() {
            return 200;
        }

        @Override
        public int getQueryTimeout() {
            return 5;
        }

        @Override
        public String getDialect() {
            return "MySQL";
        }

        @Override
        public int getMaxFixRetry() {
            return 2;
        }

        @Override
        public int getSandboxMaxBytes() {
            return 512 * 1024;
        }

        /** 放大竞态窗口：让出 CPU，使旧 bug 的串端必然暴露。 */
        private void record(String audience) {
            long tid = Thread.currentThread().getId();
            String expected = expectedByThread.get(tid);
            Set<String> seen = seenByThread.computeIfAbsent(tid, k -> ConcurrentHashMap.newKeySet());
            seen.add(audience);
            callCountByThread.computeIfAbsent(tid, k -> new AtomicInteger()).incrementAndGet();
            if (expected != null && !expected.equals(audience)) {
                // 当前线程预期 audience 与实际不一致 → 串端
                violated.set(true);
                violationDetail.append(String.format(
                        "线程%d 预期=%s 却读到=%s%n", tid, expected, audience));
            }
            Thread.yield();
        }

        @Override
        public boolean isEnabledFor(String audience) {
            record(audience);
            return true;
        }

        @Override
        public boolean isReportEnabledFor(String audience) {
            return false;
        }

        @Override
        public int getMaxRowsFor(String audience) {
            record(audience);
            return 200;
        }

        @Override
        public int getQueryTimeoutFor(String audience) {
            record(audience);
            return 5;
        }
    }

    /** 按调用次序返回固定应答的 LLM 桩（每个线程独立计数，并发安全）。 */
    private static class PositionalLlm extends Nl2SqlLlmClient {
        private final ThreadLocal<AtomicInteger> counter = ThreadLocal.withInitial(AtomicInteger::new);

        @Override
        public String chat(String prompt, boolean asSystem, String userMessage) {
            int step = counter.get().getAndIncrement() % 5;
            switch (step) {
                case 0:
                    return "{\"classification\":\"《可能的数据分析请求》\"}";
                case 1:
                    return "{\"canonical_query\":\"我的健康记录\"}";
                case 2:
                    return "[\"user_health\"]";
                case 3:
                    return "SELECT id, value FROM user_health";
                case 4:
                default:
                    return "{\"passed\":true}";
            }
        }
    }

    /** 固定 schema：user_health 含 user_id 租户列。 */
    private static class FixedSchemaService extends Nl2SqlSchemaService {
        @Override
        public List<TableMeta> listAllowedTables() {
            return List.of(new TableMeta("user_health", "用户健康数据"));
        }

        @Override
        public List<TableSchema> loadSchema(List<String> tableNames) {
            TableSchema ts = new TableSchema("user_health", "用户健康数据", List.of(
                    new ColumnSchema("id", "主键", "bigint", true, true),
                    new ColumnSchema("user_id", "用户ID", "int", false, true),
                    new ColumnSchema("value", "指标值", "varchar", false, false)));
            return List.of(ts);
        }

        @Override
        public List<String> sanitizeSelectedTables(List<String> selected) {
            return selected;
        }
    }

    /** 术语桩：返回固定文本。 */
    private static class FixedEvidenceService extends Nl2SqlEvidenceService {
        @Override
        public String evidence() {
            return "收缩压(SBP, mmHg, 正常范围 90~140)";
        }
    }

    /** prompt 桩：返回固定字符串，规避资源加载。 */
    private static class FixedPromptBuilder extends Nl2SqlPromptBuilder {
        @Override
        public String buildIntentRecognitionPrompt(String multiTurn, String latestQuery) {
            return "INTENT_PROMPT";
        }

        @Override
        public String buildQueryEnhancePrompt(String multiTurn, String latestQuery, String evidence) {
            return "ENHANCE_PROMPT";
        }

        @Override
        public String buildSchemaSelectorPrompt(String schemaText, String question, String evidence) {
            return "SELECTOR_PROMPT";
        }

        @Override
        public String buildSqlGeneratePrompt(String dialect, String question, String schemaText, String evidence,
                                             String executionDescription, String previousStepResults) {
            return "GEN_PROMPT";
        }

        @Override
        public String buildSemanticConsistencyPrompt(String dialect, String executionDescription, String userQuery,
                                                     String evidence, String schemaText, String sql) {
            return "SEMANTIC_PROMPT";
        }

        @Override
        public String buildSqlErrorFixerPrompt(String dialect, String question, String schemaText, String evidence,
                                               String errorSql, String errorMessage, String executionDescription,
                                               String previousStepResults) {
            return "FIX_PROMPT";
        }

        @Override
        public String buildSchemaText(String dbId, List<TableSchema> tables) {
            return "SCHEMA_TEXT";
        }
    }

    /** 构造返回固定 2 行的 mock 数据源，让步骤 9 EXECUTE 能真正走通。 */
    private static DataSource stubDataSource() throws Exception {
        DataSource ds = mock(DataSource.class);
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.PreparedStatement ps = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
        java.sql.ResultSetMetaData md = mock(java.sql.ResultSetMetaData.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.getMetaData()).thenReturn(md);
        when(md.getColumnCount()).thenReturn(2);
        when(md.getColumnLabel(1)).thenReturn("id");
        when(md.getColumnLabel(2)).thenReturn("value");
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getObject(1)).thenReturn(1, 2);
        when(rs.getObject(2)).thenReturn("128", "132");
        return ds;
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("20 线程并发传 user/doctor，每个线程读到的 audience 恒定等于自身预期（不串端）")
    void audience_not_crossed_under_concurrency() throws Exception {
        // 🔴 单个共享的 pipeline 实例，精确复现单例 @Service 的并发形态
        DataAgentPipeline sharedPipeline = new DataAgentPipeline();
        RecordingConfig config = new RecordingConfig();
        inject(sharedPipeline, "config", config);
        inject(sharedPipeline, "llm", new PositionalLlm());
        inject(sharedPipeline, "promptBuilder", new FixedPromptBuilder());
        inject(sharedPipeline, "schemaService", new FixedSchemaService());
        inject(sharedPipeline, "introspector", new JdbcSchemaIntrospector());
        inject(sharedPipeline, "primaryDataSource", stubDataSource());
        inject(sharedPipeline, "evidenceService", new FixedEvidenceService());

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        Thread[] pool = new Thread[THREADS];

        for (int i = 0; i < THREADS; i++) {
            final boolean doctor = (i % 2 == 1);
            final String expected = doctor ? AUDIENCE_DOCTOR : AUDIENCE_USER;
            final long ownerId = doctor ? 999L : 1L;
            Thread t = new Thread(() -> {
                expectedByThread.put(Thread.currentThread().getId(), expected);
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    for (int k = 0; k < ITERATIONS; k++) {
                        DataAgentQaRequest req = new DataAgentQaRequest();
                        req.setQuestion("查一下我的健康记录");
                        // 4 参 run：audience 作为形参传入（P1-1 修复后）
                        sharedPipeline.run(req, ownerId, StepSink.NOOP, expected);
                    }
                } catch (Exception e) {
                    violationDetail.append("线程执行异常: ").append(e).append("\n");
                    violated.set(true);
                } finally {
                    done.countDown();
                }
            }, "aud-" + i);
            pool[i] = t;
            t.start();
        }

        start.countDown();
        boolean finished = done.await(60, TimeUnit.SECONDS);
        assertTrue(finished, "并发测试超时（可能存在死锁/活锁）");
        assertFalse(violated.get(),
                "🔴 audience 串端！部分线程读到了非预期 audience。\n" + violationDetail);

        // 每个线程必须且只观察到自身预期的 audience（user 线程只看到 user，doctor 线程只看到 doctor）
        for (Map.Entry<Long, String> e : expectedByThread.entrySet()) {
            Long tid = e.getKey();
            String expected = e.getValue();
            Set<String> seen = seenByThread.get(tid);
            assertTrue(seen != null && seen.size() == 1 && seen.contains(expected),
                    String.format("线程%d 应只观察到 audience=%s，实际=%s", tid, expected, seen));
        }
        // 总调用量应足够大，避免「恰好没跑够」的假绿
        int totalCalls = callCountByThread.values().stream().mapToInt(AtomicInteger::get).sum();
        assertTrue(totalCalls >= THREADS * ITERATIONS,
                "audience 读取次数不足，可能部分 run 未到达 config 读取阶段: " + totalCalls);
    }
}
