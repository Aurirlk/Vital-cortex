package cn.kmbeast.core.nl2sql;

import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.ColumnSchema;
import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.TableMeta;
import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.TableSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 数据智能体流水线关键路径测试（<b>不发真实 LLM、不连数据库</b>）。
 *
 * <p>覆盖：开关门禁、闲聊短路、安全守卫接线、租户强制重写、无主体时 fail-closed、
 * SSE 分帧、步骤事件模型。</p>
 */
@DisplayName("数据智能体流水线：门禁/短路/守卫/租户隔离")
class DataAgentPipelineTest {

    // ---------------- 桩件 ----------------

    /** 按队列返回响应，耗尽后返回空串。 */
    private static class StubLlm extends Nl2SqlLlmClient {
        final Deque<String> queue = new ArrayDeque<>();

        StubLlm enqueue(String... responses) {
            queue.addAll(Arrays.asList(responses));
            return this;
        }

        @Override
        public String chat(String prompt, boolean asSystem, String userMessage) {
            return queue.isEmpty() ? "" : queue.poll();
        }
    }

    private static class StubConfig extends DataAgentConfigHolder {
        boolean enabled = true;
        /** 报告开关改为可配 —— 步骤 10 REPORT 此前完全无覆盖就是因为它被硬编码 false。 */
        boolean reportEnabled = false;

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public boolean isReportEnabled() {
            return reportEnabled;
        }

        @Override
        public int getMaxRows() {
            return 200;
        }

        @Override
        public int getQueryTimeout() {
            return 5;
        }

        /**
         * 2026-10-07 新增：方言与修复重试上限改为可配置后，父类实现会去读未注入的
         * {@code properties}（单测里为 null）。这里显式覆盖，与生产默认一致。
         */
        @Override
        public String getDialect() {
            return "MySQL";
        }

        @Override
        public int getMaxFixRetry() {
            return 2;
        }

        /**
         * 2026-10-07：流水线改用「按使用者分层」的读取（{@code *For(audience)}）。
         * 父类实现会去读未注入的 {@code properties}（单测里为 null）→ NPE，
         * 故显式覆盖全部分层方法，与无 audience 时行为一致。
         */
        @Override
        public boolean isEnabledFor(String audience) {
            return enabled;
        }

        @Override
        public boolean isReportEnabledFor(String audience) {
            return reportEnabled;
        }

        @Override
        public int getMaxRowsFor(String audience) {
            return 200;
        }

        @Override
        public int getQueryTimeoutFor(String audience) {
            return 5;
        }

        /** 沙箱字节上限：给较大值，避免单测里被误截断。 */
        @Override
        public int getSandboxMaxBytes() {
            return 512 * 1024;
        }
    }

    /** 术语 Evidence 桩：不连库，返回固定文本。 */
    private static class StubEvidenceService extends Nl2SqlEvidenceService {
        @Override
        public String evidence() {
            return "收缩压(SBP, mmHg, 正常范围 90~140)";
        }
    }

    /** 返回固定模板文本，规避资源加载。 */
    private static class StubPromptBuilder extends Nl2SqlPromptBuilder {
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

    /** 固定 schema：user_health 有 user_id 租户列。 */
    private static class StubSchemaService extends Nl2SqlSchemaService {
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

    /** 两张表：mall_product（无租户列）+ user_health（有 user_id），用于 JOIN 覆盖测试。 */
    private static class StubJoinSchemaService extends Nl2SqlSchemaService {
        @Override
        public List<TableMeta> listAllowedTables() {
            return List.of(new TableMeta("mall_product", "健康商城商品"),
                    new TableMeta("user_health", "用户健康数据"));
        }

        @Override
        public List<TableSchema> loadSchema(List<String> tableNames) {
            TableSchema product = new TableSchema("mall_product", "健康商城商品", List.of(
                    new ColumnSchema("id", "主键", "bigint", true, true),
                    new ColumnSchema("name", "商品名", "varchar", false, false)));
            TableSchema health = new TableSchema("user_health", "用户健康数据", List.of(
                    new ColumnSchema("id", "主键", "bigint", true, true),
                    new ColumnSchema("user_id", "用户ID", "int", false, true),
                    new ColumnSchema("value", "指标值", "varchar", false, false)));
            return List.of(product, health);
        }

        @Override
        public List<String> sanitizeSelectedTables(List<String> selected) {
            return selected;
        }
    }

    private DataAgentPipeline pipeline(StubConfig config, StubLlm llm) throws Exception {
        return pipeline(config, llm, new StubSchemaService());
    }

    private DataAgentPipeline pipeline(StubConfig config, StubLlm llm, Nl2SqlSchemaService schemaService)
            throws Exception {
        DataAgentPipeline p = new DataAgentPipeline();
        inject(p, "config", config);
        inject(p, "llm", llm);
        inject(p, "promptBuilder", new StubPromptBuilder());
        inject(p, "schemaService", schemaService);
        inject(p, "introspector", new JdbcSchemaIntrospector());
        inject(p, "primaryDataSource", null);
        inject(p, "evidenceService", new StubEvidenceService());
        return p;
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static List<AgentStepEvent> collect(DataAgentPipeline p, DataAgentQaRequest req, Long owner) {
        List<AgentStepEvent> events = new ArrayList<>();
        p.run(req, owner, events::add);
        return events;
    }

    private static DataAgentQaRequest req(String question) {
        DataAgentQaRequest r = new DataAgentQaRequest();
        r.setQuestion(question);
        return r;
    }

    /**
     * 构造返回固定 2 行的 mock 数据源，让步骤 9 EXECUTE 能真正走通。
     *
     * <p>此前 {@code dataSource} 恒为 null，{@code execute} 必然 NPE，
     * 于是步骤 9（执行）与步骤 10（报告）<b>完全没有测试覆盖</b>。</p>
     */
    private static DataSource stubDataSource() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData md = mock(ResultSetMetaData.class);
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

    /** 走完 1~5 步所需的 LLM 应答（意图/增强/选表/SQL/语义校验）。 */
    private static StubLlm llmForHappyPath(String... tail) {
        StubLlm llm = new StubLlm().enqueue(
                "{\"classification\":\"《可能的数据分析请求》\"}",
                "{\"canonical_query\":\"我的血压记录\"}",
                "[\"user_health\"]",
                "SELECT id, value FROM user_health",
                "{\"passed\":true}");
        for (String s : tail) {
            llm.enqueue(s);
        }
        return llm;
    }

    private static AgentStepEvent lastStep(List<AgentStepEvent> events, AgentStep step) {
        return events.stream()
                .filter(e -> step.name().equals(e.getStep()))
                .reduce((a, b) -> b).orElse(null);
    }

    // ---------------- 用例 ----------------

    @Test
    @DisplayName("开关关闭时不执行任何步骤，直接返回未开启")
    void disabledGate() throws Exception {
        StubConfig config = new StubConfig();
        config.enabled = false;
        DataAgentPipeline p = pipeline(config, new StubLlm());
        List<AgentStepEvent> events = new ArrayList<>();
        DataAgentQaResult result = p.run(req("查一下我的血压"), 1L, events::add);

        assertFalse(result.isSuccess(), "关闭时应失败");
        assertTrue(result.getMessage().contains("未开启"), "应提示未开启，实际: " + result.getMessage());
        assertEquals(1, events.size(), "不应执行任何后续步骤");
        assertEquals(AgentStepEvent.FAILED, events.get(0).getStatus());
        assertEquals(AgentStep.INTENT.name(), events.get(0).getStep());
    }

    @Test
    @DisplayName("空问题直接拒绝")
    void emptyQuestion() throws Exception {
        DataAgentPipeline p = pipeline(new StubConfig(), new StubLlm());
        DataAgentQaResult result = p.run(req("   "), 1L, StepSink.NOOP);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("不能为空"));
    }

    @Test
    @DisplayName("闲聊意图短路：不进入 schema 召回")
    void chitchatShortCircuit() throws Exception {
        StubLlm llm = new StubLlm().enqueue(
                "{\"classification\":\"《闲聊或无关指令》\",\"response\":\"你好，我是小健。\"}");
        DataAgentPipeline p = pipeline(new StubConfig(), llm);
        List<AgentStepEvent> events = new ArrayList<>();
        DataAgentQaResult result = p.run(req("你好呀"), 1L, events::add);

        assertTrue(result.isSuccess());
        assertEquals("CHITCHAT", result.getIntent());
        assertEquals("你好，我是小健。", result.getAnswer());
        assertEquals(2, events.size(), "闲聊只应产生 INTENT 的 RUNNING + SUCCESS 两个事件");
        assertEquals(AgentStepEvent.SUCCESS, events.get(events.size() - 1).getStatus());
        assertTrue(events.stream().noneMatch(e -> AgentStep.SCHEMA_RECALL.name().equals(e.getStep())),
                "闲聊不应触发 schema 召回");
    }

    @Test
    @DisplayName("恶意 SQL 被安全守卫拦截，流程在 SECURITY_GUARD 终止")
    void maliciousSqlBlockedByGuard() throws Exception {
        StubLlm llm = new StubLlm().enqueue(
                "{\"classification\":\"《可能的数据分析请求》\"}",          // INTENT
                "{\"canonical_query\":\"删除健康数据\"}",                    // QUERY_ENHANCE
                "[\"user_health\"]",                                        // SCHEMA_RECALL
                "DELETE FROM user_health",                                  // SQL_GENERATE（恶意）
                "{\"passed\":true}");                                       // SEMANTIC_CHECK
        DataAgentPipeline p = pipeline(new StubConfig(), llm);
        List<AgentStepEvent> events = new ArrayList<>();
        DataAgentQaResult result = p.run(req("删除所有健康数据"), 1L, events::add);

        assertFalse(result.isSuccess(), "恶意 SQL 必须失败");
        AgentStepEvent guard = events.stream()
                .filter(e -> AgentStep.SECURITY_GUARD.name().equals(e.getStep()))
                .reduce((a, b) -> b).orElse(null);
        assertNotNull(guard, "应产生 SECURITY_GUARD 事件");
        assertEquals(AgentStepEvent.FAILED, guard.getStatus(), "守卫应判失败");
        assertEquals(0, result.getRowCount(), "不得返回任何行");
    }

    @Test
    @DisplayName("租户条件被强制重写：无 WHERE 也会追加租户等于主体")
    void tenantRewriteAlwaysApplied() throws Exception {
        StubLlm llm = new StubLlm().enqueue(
                "{\"classification\":\"《可能的数据分析请求》\"}",
                "{\"canonical_query\":\"我的健康记录\"}",
                "[\"user_health\"]",
                "SELECT id, value FROM user_health",
                "{\"passed\":true}");
        DataAgentPipeline p = pipeline(new StubConfig(), llm);
        List<AgentStepEvent> events = new ArrayList<>();
        DataAgentQaResult result = p.run(req("看看我的健康记录"), 9527L, events::add);

        AgentStepEvent tenant = events.stream()
                .filter(e -> AgentStep.TENANT_REWRITE.name().equals(e.getStep()))
                .reduce((a, b) -> b).orElse(null);
        assertNotNull(tenant, "应产生 TENANT_REWRITE 事件");
        assertEquals(AgentStepEvent.SUCCESS, tenant.getStatus());
        assertEquals(Boolean.TRUE, tenant.getPayload().get("tenantApplied"));
        assertEquals("user_id", tenant.getPayload().get("tenantColumn"));
        String finalSql = String.valueOf(tenant.getPayload().get("finalSql"));
        assertTrue(finalSql.replaceAll("\\s+", " ").contains("user_id = :__tenantUserId"),
                "最终 SQL 必须带租户条件，实际: " + finalSql);
        assertTrue(result.getSql().contains("__tenantUserId"));
        assertTrue(result.isTenantApplied());
    }

    @Test
    @DisplayName("有租户列但无执行主体 → fail-closed，绝不执行")
    void failClosedWhenNoOwner() throws Exception {
        StubLlm llm = new StubLlm().enqueue(
                "{\"classification\":\"《可能的数据分析请求》\"}",
                "{\"canonical_query\":\"健康记录\"}",
                "[\"user_health\"]",
                "SELECT id FROM user_health",
                "{\"passed\":true}");
        DataAgentPipeline p = pipeline(new StubConfig(), llm);
        List<AgentStepEvent> events = new ArrayList<>();
        DataAgentQaResult result = p.run(req("健康记录"), null, events::add);

        assertFalse(result.isSuccess(), "无主体必须失败");
        AgentStepEvent tenant = events.stream()
                .filter(e -> AgentStep.TENANT_REWRITE.name().equals(e.getStep()))
                .reduce((a, b) -> b).orElse(null);
        assertNotNull(tenant);
        assertEquals(AgentStepEvent.FAILED, tenant.getStatus());
        assertTrue(tenant.getSummary().contains("拒绝执行"));
    }

    @Test
    @DisplayName("SSE 分帧：event/data 成对，换行逐行加 data: 前缀")
    void sseFraming() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        DataAgentSse.send(pw, DataAgentSse.EVENT_STEP, AgentStepEvent.running(AgentStep.INTENT));
        pw.flush();
        String out = sw.toString();

        assertTrue(out.startsWith("event: step\n"), "首行应为 event: step，实际: " + out);
        assertTrue(out.contains("\ndata: {"), "应有 data 行");
        assertTrue(out.endsWith("\n\n"), "帧必须以空行结束");

        // 多行文本必须逐行加前缀，否则前端 JSON.parse 会失败
        StringWriter sw2 = new StringWriter();
        PrintWriter pw2 = new PrintWriter(sw2);
        DataAgentSse.send(pw2, DataAgentSse.EVENT_ERROR, "line1\nline2");
        pw2.flush();
        String out2 = sw2.toString();
        assertTrue(out2.contains("data: line1\n"), out2);
        assertTrue(out2.contains("data: line2\n"), out2);
        assertFalse(out2.contains("data: line1\nline2"), "换行不得原样保留在单个 data 行内");
    }

    @Test
    @DisplayName("步骤事件模型：状态/序号/工具名齐全")
    void stepEventModel() {
        AgentStepEvent running = AgentStepEvent.running(AgentStep.SQL_GENERATE);
        assertEquals(5, running.getOrder());
        assertEquals("SQL 生成", running.getTitle());
        assertEquals("sql_generator", running.getTool());
        assertEquals(AgentStepEvent.RUNNING, running.getStatus());

        AgentStepEvent ok = AgentStepEvent.success(AgentStep.EXECUTE, 12L, "返回 3 行")
                .withPayload("rowCount", 3).withThinking("think");
        assertEquals(AgentStepEvent.SUCCESS, ok.getStatus());
        assertEquals(12L, ok.getElapsedMs());
        assertEquals(3, ok.getPayload().get("rowCount"));
        assertEquals("think", ok.getThinking());

        assertEquals(AgentStepEvent.SKIPPED, AgentStepEvent.skipped(AgentStep.REPORT, "未开启").getStatus());
    }

    // ---------------- 2026-10-07 加固：JOIN / 子查询下的租户覆盖 ----------------

    @Test
    @DisplayName("JOIN 加固：主表无租户列、JOIN 表有 → 仍必须限定租户（旧实现会跳过致泄漏）")
    void joinToTenantTableStillEnforced() throws Exception {
        StubLlm llm = new StubLlm().enqueue(
                "{\"classification\":\"《可能的数据分析请求》\"}",
                "{\"canonical_query\":\"商品与健康数据关联\"}",
                "[\"mall_product\",\"user_health\"]",
                "SELECT m.name, h.value FROM mall_product m JOIN user_health h ON m.id = h.user_id",
                "{\"passed\":true}");
        DataAgentPipeline p = pipeline(new StubConfig(), llm, new StubJoinSchemaService());
        List<AgentStepEvent> events = new ArrayList<>();
        DataAgentQaResult result = p.run(req("看看商品和我的健康数据"), 9527L, events::add);

        AgentStepEvent tenant = events.stream()
                .filter(e -> AgentStep.TENANT_REWRITE.name().equals(e.getStep()))
                .reduce((a, b) -> b).orElse(null);
        assertNotNull(tenant, "应产生 TENANT_REWRITE 事件");
        assertEquals(AgentStepEvent.SUCCESS, tenant.getStatus());
        @SuppressWarnings("unchecked")
        List<String> cols = (List<String>) tenant.getPayload().get("tenantColumns");
        assertEquals(List.of("h.user_id"), cols, "JOIN 表的租户列必须被发现并带别名限定");
        String finalSql = String.valueOf(tenant.getPayload().get("finalSql"));
        assertTrue(finalSql.replaceAll("\\s+", " ").contains("h.user_id = :__tenantUserId"),
                "必须逐表限定租户，实际: " + finalSql);
        assertTrue(result.isTenantApplied());
    }

    @Test
    @DisplayName("子查询 fail-closed：保守档位无法定位内部租户列 → 拒绝执行")
    void subqueryFailsClosed() throws Exception {
        StubLlm llm = new StubLlm().enqueue(
                "{\"classification\":\"《可能的数据分析请求》\"}",
                "{\"canonical_query\":\"子查询\"}",
                "[\"mall_product\",\"user_health\"]",
                "SELECT * FROM mall_product WHERE id IN (SELECT user_id FROM user_health)",
                "{\"passed\":true}");
        DataAgentPipeline p = pipeline(new StubConfig(), llm, new StubJoinSchemaService());
        List<AgentStepEvent> events = new ArrayList<>();
        DataAgentQaResult result = p.run(req("用子查询看看"), 9527L, events::add);

        assertFalse(result.isSuccess(), "子查询必须 fail-closed");
        AgentStepEvent tenant = events.stream()
                .filter(e -> AgentStep.TENANT_REWRITE.name().equals(e.getStep()))
                .reduce((a, b) -> b).orElse(null);
        assertNotNull(tenant);
        assertEquals(AgentStepEvent.FAILED, tenant.getStatus());
        assertTrue(result.getMessage().contains("子查询"), "应提示不支持子查询，实际: " + result.getMessage());
        assertEquals(0, result.getRowCount(), "不得返回任何行");
    }

    // ---------------- 2026-10-07 二次加固：白名单表 / 逗号连接 ----------------

    @Test
    @DisplayName("白名单外表的 SQL 必须 fail-closed（原实现会静默跳过租户过滤 → 跨租户泄漏）")
    void unknownTableFailsClosed() throws Exception {
        // 只有 user_health 在白名单里，SQL 却引用了 mall_product
        StubLlm llm = new StubLlm().enqueue(
                "{\"classification\":\"《可能的数据分析请求》\"}",
                "{\"canonical_query\":\"商品\"}",
                "[\"user_health\"]",
                "SELECT * FROM mall_product",
                "{\"passed\":true}");
        DataAgentPipeline p = pipeline(new StubConfig(), llm, new StubSchemaService());
        List<AgentStepEvent> events = new ArrayList<>();
        DataAgentQaResult result = p.run(req("看看商品"), 9527L, events::add);

        assertFalse(result.isSuccess(), "引用白名单外表必须 fail-closed");
        AgentStepEvent tenant = events.stream()
                .filter(e -> AgentStep.TENANT_REWRITE.name().equals(e.getStep()))
                .reduce((a, b) -> b).orElse(null);
        assertNotNull(tenant);
        assertEquals(AgentStepEvent.FAILED, tenant.getStatus());
        assertTrue(result.getMessage().contains("未授权"), "应提示未授权数据表，实际: " + result.getMessage());
        assertEquals(0, result.getRowCount(), "不得返回任何行");
    }

    @Test
    @DisplayName("隐式逗号连接 FROM a, b 必须 fail-closed（第二张表不被词法识别 → 会漏租户）")
    void implicitCommaJoinFailsClosed() throws Exception {
        StubLlm llm = new StubLlm().enqueue(
                "{\"classification\":\"《可能的数据分析请求》\"}",
                "{\"canonical_query\":\"逗号连接\"}",
                "[\"mall_product\",\"user_health\"]",
                "SELECT m.name, h.value FROM mall_product m, user_health h WHERE m.id = h.user_id",
                "{\"passed\":true}");
        DataAgentPipeline p = pipeline(new StubConfig(), llm, new StubJoinSchemaService());
        List<AgentStepEvent> events = new ArrayList<>();
        DataAgentQaResult result = p.run(req("商品和健康数据"), 9527L, events::add);

        assertFalse(result.isSuccess(), "逗号连接必须 fail-closed");
        AgentStepEvent tenant = events.stream()
                .filter(e -> AgentStep.TENANT_REWRITE.name().equals(e.getStep()))
                .reduce((a, b) -> b).orElse(null);
        assertNotNull(tenant);
        assertEquals(AgentStepEvent.FAILED, tenant.getStatus());
        assertTrue(result.getMessage().contains("逗号"), "应提示改用显式 JOIN，实际: " + result.getMessage());
        assertEquals(0, result.getRowCount(), "不得返回任何行");
    }

    @Test
    @DisplayName("显式 JOIN 仍被放行：逐表限定租户列（加固不得误伤正常 JOIN）")
    void explicitJoinStillAllowed() throws Exception {
        StubLlm llm = new StubLlm().enqueue(
                "{\"classification\":\"《可能的数据分析请求》\"}",
                "{\"canonical_query\":\"JOIN\"}",
                "[\"mall_product\",\"user_health\"]",
                "SELECT m.name, h.value FROM mall_product m JOIN user_health h ON m.id = h.user_id",
                "{\"passed\":true}");
        DataAgentPipeline p = pipeline(new StubConfig(), llm, new StubJoinSchemaService());
        List<AgentStepEvent> events = new ArrayList<>();
        DataAgentQaResult result = p.run(req("商品和健康数据"), 9527L, events::add);

        AgentStepEvent tenant = events.stream()
                .filter(e -> AgentStep.TENANT_REWRITE.name().equals(e.getStep()))
                .reduce((a, b) -> b).orElse(null);
        assertNotNull(tenant);
        assertEquals(AgentStepEvent.SUCCESS, tenant.getStatus(),
                "显式 JOIN 必须放行，实际: " + tenant.getSummary());
        assertTrue(String.valueOf(tenant.getPayload().get("finalSql"))
                .replaceAll("\\s+", " ").contains("h.user_id = :__tenantUserId"));
    }

    // ---------------- 步骤 9 / 10（2026-10-07 新增覆盖） ----------------

    @Test
    @DisplayName("步骤 9 EXECUTE：查询成功时返回 rowCount 与预览")
    void executeSucceedsWithRows() throws Exception {
        DataAgentPipeline p = pipeline(new StubConfig(), llmForHappyPath());
        inject(p, "primaryDataSource", stubDataSource());
        DataAgentQaRequest r = req("看看我的血压");
        r.setEnableReport(false);

        List<AgentStepEvent> events = new ArrayList<>();
        DataAgentQaResult result = p.run(r, 9527L, events::add);

        assertTrue(result.isSuccess(), "查询应成功");
        assertEquals(2, result.getRowCount());
        AgentStepEvent exec = lastStep(events, AgentStep.EXECUTE);
        assertNotNull(exec, "应产生 EXECUTE 事件");
        assertEquals(AgentStepEvent.SUCCESS, exec.getStatus());
        assertEquals(2, exec.getPayload().get("rowCount"));
        assertNotNull(exec.getPayload().get("columns"));
    }

    @Test
    @DisplayName("步骤 10 REPORT：开启时生成 Markdown 报告")
    void reportGeneratedWhenEnabled() throws Exception {
        StubConfig config = new StubConfig();
        config.reportEnabled = true;
        DataAgentPipeline p = pipeline(config, llmForHappyPath("## 分析结论\n共 2 条记录，血压偏高。"));
        inject(p, "primaryDataSource", stubDataSource());
        DataAgentQaRequest r = req("看看我的血压");
        r.setEnableReport(true);

        List<AgentStepEvent> events = new ArrayList<>();
        DataAgentQaResult result = p.run(r, 9527L, events::add);

        assertTrue(result.isSuccess());
        assertNotNull(result.getReport(), "报告不应为空");
        assertTrue(result.getReport().contains("分析结论"), "实际: " + result.getReport());
        AgentStepEvent rep = lastStep(events, AgentStep.REPORT);
        assertNotNull(rep);
        assertEquals(AgentStepEvent.SUCCESS, rep.getStatus());
    }

    @Test
    @DisplayName("步骤 10 REPORT：关闭时标记 SKIPPED（不调 LLM）")
    void reportSkippedWhenDisabled() throws Exception {
        StubConfig config = new StubConfig();
        config.reportEnabled = false;
        DataAgentPipeline p = pipeline(config, llmForHappyPath());
        inject(p, "primaryDataSource", stubDataSource());
        DataAgentQaRequest r = req("看看我的血压");
        r.setEnableReport(false);

        List<AgentStepEvent> events = new ArrayList<>();
        DataAgentQaResult result = p.run(r, 9527L, events::add);

        assertTrue(result.isSuccess());
        assertNull(result.getReport(), "关闭时不应产出报告");
        AgentStepEvent rep = lastStep(events, AgentStep.REPORT);
        assertNotNull(rep);
        assertEquals(AgentStepEvent.SKIPPED, rep.getStatus());
    }

    @Test
    @DisplayName("同步链路：result.steps 回填十步过程（此前恒为空数组）")
    void stepsFilledOnSyncPath() throws Exception {
        DataAgentPipeline p = pipeline(new StubConfig(), llmForHappyPath());
        inject(p, "primaryDataSource", stubDataSource());
        DataAgentQaRequest r = req("看看我的血压");
        r.setEnableReport(false);

        // 传 StepSink.NOOP 即同步链路
        DataAgentQaResult result = p.run(r, 9527L, StepSink.NOOP);

        assertNotNull(result.getSteps(), "同步链路应回填 steps");
        assertFalse(result.getSteps().isEmpty(), "steps 不应为空");
        assertTrue(result.getSteps().stream()
                        .anyMatch(e -> AgentStep.SQL_GENERATE.name().equals(e.getStep())),
                "steps 里应能看到 SQL_GENERATE");
    }
}
