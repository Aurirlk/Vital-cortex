package cn.kmbeast.core.nl2sql;

import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.TableMeta;
import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.TableSchema;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 阶段 4（DataAgent 资产摘取）· MySQL 受控 NL2SQL 编排服务。
 *
 * <p>移植自 DataAgent {@code Nl2SqlServiceImpl}(206)，关键改造：
 * <ul>
 *   <li>{@code Flux<ChatResponse>} → <b>同步返回 String</b>（本项目是 Servlet MVC，不引 WebFlux）；</li>
 *   <li>LLM 走既有 {@link LLMProvider#chat} + {@code DeepSeekProvider}，不带 Spring AI；</li>
 *   <li>保留 {@code IS_ONLY_NL2SQL} 语义：只做「选表 → 生成 → 校验 → 重写 → 执行」，
 *       不做 DataAgent 的 planner/Python 沙箱/报告生成。</li>
 * </ul>
 * </p>
 *
 * <p><b>默认关闭</b>（{@code crm.ai.nl2sql.enabled=false}）。开启前必须配好 {@code ai_ro} 只读账号
 * 并通过 20 条恶意 SQL 拦截测试。</p>
 *
 * <p><b>⚠️ 整类死代码（2026-10-07 复核标注）</b>：全仓<b>无任何调用点</b>，仅
 * {@code MarkdownParserUtil} 与 {@code Nl2SqlLlmClient} 的 Javadoc 提及。已被
 * {@link DataAgentPipeline}（十步流水线 + 完整安全闸门）完全取代，保留仅作为
 * DataAgent 移植过程的对照实现。</p>
 *
 * <p><b>⛔ 禁止启用本类</b>：尤其 {@link #resolveTenantColumn} 是<b>旧的单表口径</b> ——
 * 它只解析「第一个 FROM 的表」上的租户列。而现役的
 * {@link DataAgentPipeline#safeRewrite} / {@link TenantSqlRewriter#rewriteAll} 是
 * <b>逐表</b>限定租户列，并额外做了白名单校验、子查询 / 隐式逗号连接 fail-closed、
 * 「有租户列但无执行主体」拒绝等五道闸门。一旦误启用本类，等于<b>绕过全部租户加固</b>
 * → 跨租户数据泄漏。如需恢复功能，请改用 {@code DataAgentPipeline}。</p>
 */
@Slf4j
@Deprecated
@Service
public class MySqlText2SqlService {

    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[[^\\]]*\\]");
    private static final String[] TENANT_CANDIDATE_COLUMNS = {"user_id", "patient_id", "doctor_id"};

    @Resource
    private Nl2SqlProperties properties;

    @Resource
    private JdbcSchemaIntrospector introspector;

    @Resource
    private Nl2SqlSchemaService schemaService;

    @Resource
    private Nl2SqlPromptBuilder promptBuilder;

    @Resource
    private Nl2SqlLlmClient llmClient;

    @Resource
    private DataSource dataSource;

    /** 查询结果。 */
    public static class Nl2SqlResult {
        private final String sql;
        private final List<Map<String, Object>> rows;
        private final boolean tenantApplied;

        public Nl2SqlResult(String sql, List<Map<String, Object>> rows, boolean tenantApplied) {
            this.sql = sql;
            this.rows = rows;
            this.tenantApplied = tenantApplied;
        }

        public String getSql() { return sql; }
        public List<Map<String, Object>> getRows() { return rows; }
        public boolean isTenantApplied() { return tenantApplied; }
    }

    /**
     * 自然语言 → 查询结果。
     *
     * @param question      用户问题
     * @param tenantUserId  当前登录者 id（来自 {@code LocalThreadHolder}，<b>永不来自 SQL 文本</b>）
     */
    public Nl2SqlResult query(String question, Long tenantUserId) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("受控 NL2SQL 未开启（crm.ai.nl2sql.enabled=false）");
        }
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }

        // 1) 全库 schema（去黑名单）→ 交给 LLM 选表
        List<TableMeta> allTables = schemaService.listAllowedTables();
        List<String> allNames = allTables.stream().map(TableMeta::getName).collect(Collectors.toList());
        List<TableSchema> full = schemaService.loadSchema(allNames);
        String selectionSchemaText = promptBuilder.buildSchemaText(dbId(), full);

        // 2) 选表（mix-selector）
        String selectorPrompt = promptBuilder.buildSchemaSelectorPrompt(selectionSchemaText, question, "");
        List<String> selected = selectTables(selectorPrompt, allNames);

        // 3) 取选中表 schema → 生成 SQL
        List<TableSchema> schemas = schemaService.loadSchema(selected);
        if (schemas.isEmpty()) {
            throw new IllegalStateException("未能为该问题匹配到可用的表");
        }
        String schemaText = promptBuilder.buildSchemaText(dbId(), schemas);

        String sql = generateSql(question, schemaText, null, null);

        // 4) 硬校验 + 5) 租户重写
        MySqlAiSqlGuard.assertSafe(sql);
        String primaryTable = MySqlAiSqlGuard.extractPrimaryTable(sql);
        String tenantColumn = resolveTenantColumn(schemas, primaryTable);

        String lastError = null;
        for (int attempt = 0; attempt <= properties.getMaxSqlRetryCount(); attempt++) {
            String finalSql = sql;
            try {
                String rewritten = TenantSqlRewriter.rewrite(finalSql, tenantColumn).getSql();
                MySqlAiSqlGuard.assertSafe(rewritten);
                List<Map<String, Object>> rows = execute(rewritten, tenantUserId, tenantColumn);
                return new Nl2SqlResult(rewritten, rows, tenantColumn != null);
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("[NL2SQL] 执行失败，尝试修复 (attempt {}): {}", attempt, lastError);
                if (attempt >= properties.getMaxSqlRetryCount()) {
                    break;
                }
                // 6) 故障修复（sql-error-fixer）
                String fixPrompt = promptBuilder.buildSqlErrorFixerPrompt(
                        properties.getDialect(), question, schemaText, "",
                        finalSql, lastError == null ? "" : lastError, question, "");
                sql = stripFence(callLlm(fixPrompt, true, question)).trim();
                MySqlAiSqlGuard.assertSafe(sql);
            }
        }
        throw new IllegalStateException("NL2SQL 执行失败且重试耗尽: " + lastError);
    }

    // ---- 内部步骤 ----

    private String generateSql(String question, String schemaText, String executionDescription,
                               String previousStepResults) {
        String prompt = promptBuilder.buildSqlGeneratePrompt(
                properties.getDialect(), question, schemaText, "",
                executionDescription == null ? question : executionDescription,
                previousStepResults == null ? "无" : previousStepResults);
        return stripFence(callLlm(prompt, true, question)).trim();
    }

    private List<String> selectTables(String prompt, List<String> allNames) {
        String raw = stripFence(callLlm(prompt, false, "")).trim();
        List<String> parsed = new ArrayList<>();
        try {
            parsed.addAll(JSON.parseArray(raw).toList(String.class));
        } catch (Exception e) {
            Matcher m = JSON_ARRAY_PATTERN.matcher(raw);
            if (m.find()) {
                try {
                    parsed.addAll(JSON.parseArray(m.group()).toList(String.class));
                } catch (Exception ignored) {
                    log.warn("[NL2SQL] 表选择结果解析失败，回退为全库: {}", raw);
                }
            }
        }
        if (parsed.isEmpty()) {
            log.warn("[NL2SQL] 表选择为空，回退为全库表");
            parsed = allNames;
        }
        return schemaService.sanitizeSelectedTables(parsed);
    }

    private List<Map<String, Object>> execute(String sql, Long tenantUserId, String tenantColumn) {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate().setMaxRows(properties.getMaxRows());
        jdbc.getJdbcTemplate().setQueryTimeout(properties.getQueryTimeout());
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (tenantColumn != null && tenantUserId != null) {
            params.addValue("__tenantUserId", tenantUserId);
        }
        return jdbc.queryForList(sql, params);
    }

    /**
     * 判定主表上的租户列（user_id 优先，其次 patient_id / doctor_id）。
     *
     * @deprecated ⛔ 旧<b>单表</b>口径，只看「第一个 FROM 的表」。误用会绕过
     *             {@code DataAgentPipeline.safeRewrite} 的逐表租户加固与四道 fail-closed
     *             闸门 → 跨租户数据泄漏。请勿调用。
     */
    @Deprecated
    private String resolveTenantColumn(List<TableSchema> schemas, String primaryTable) {
        if (primaryTable == null) {
            return null;
        }
        for (TableSchema ts : schemas) {
            if (ts.getName().equalsIgnoreCase(primaryTable)) {
                for (String col : TENANT_CANDIDATE_COLUMNS) {
                    if (ts.hasColumn(col)) {
                        return col;
                    }
                }
                return null;
            }
        }
        return null;
    }

    /**
     * 调用 LLM（委托 {@link Nl2SqlLlmClient}，与数据智能体共用同一实现）。
     *
     * @param prompt   完整提示词
     * @param asSystem true 走 system 角色（生成/修复），false 走 user 角色（选表）
     */
    private String callLlm(String prompt, boolean asSystem, String userMessage) {
        return llmClient.chat(prompt, asSystem, userMessage);
    }

    /** 去掉模型偶尔输出的 Markdown 代码围栏（委托 {@link Nl2SqlLlmClient#stripFence}）。 */
    private static String stripFence(String text) {
        return Nl2SqlLlmClient.stripFence(text);
    }

    private String dbId() {
        return "personal_health";
    }
}
