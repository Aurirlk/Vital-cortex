package cn.kmbeast.core.nl2sql;

import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.ColumnSchema;
import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.ForeignKey;
import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.TableSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 阶段 4（DataAgent 资产摘取）· 提示词构建器。
 *
 * <p>移植自 DataAgent {@code PromptHelper}(337)，<b>丢弃</b> Spring AI 的 {@code PromptTemplate}，
 * 自研约 25 行的 {@code {key}} 占位符替换（见 {@link #render(String, Map)}）。</p>
 *
 * <p>模板文本直接复用 {@code resources/ai-prompts/*.txt}（阶段 4 / 5.1 自 DataAgent 复制，Apache 2.0）。</p>
 */
@Slf4j
@Component
public class Nl2SqlPromptBuilder {

    /** 模板占位符（与 Spring AI PromptTemplate 同语法：{name}）。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

    /** 各提示词模板文本。 */
    private final Map<String, String> templates = new HashMap<>();

    @PostConstruct
    public void init() {
        load("new-sql-generate");
        load("query-enhancement");
        load("semantic-consistency");
        load("sql-error-fixer");
        load("intent-recognition");
        load("evidence-query-rewrite");
        load("mix-selector");
        load("report-generator-plain");
    }

    private void load(String name) {
        String path = "ai-prompts/" + name + ".txt";
        InputStream in = getClass().getClassLoader().getResourceAsStream(path);
        if (in == null) {
            log.error("[Nl2SqlPromptBuilder] 模板缺失: {}", path);
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            templates.put(name, reader.lines().collect(Collectors.joining("\n")));
        } catch (IOException e) {
            log.error("[Nl2SqlPromptBuilder] 模板读取失败: {}", path, e);
        }
    }

    /**
     * 已加载的模板名（供管理端「提示词/段落查询」展示）。
     */
    public java.util.Set<String> templateNames() {
        return new java.util.TreeSet<>(templates.keySet());
    }

    /** 模板原文（未渲染），不存在时返回 null。 */
    public String raw(String name) {
        return templates.get(name);
    }

    /**
     * 自研占位符替换：先解开转义 {@code \{ \} }，再替换 {@code {key}}（缺省置空）。
     * 不依赖 Spring AI，零新依赖。
     */
    public String render(String templateName, Map<String, String> params) {
        String tpl = templates.get(templateName);
        if (tpl == null) {
            return "";
        }
        // 1) 解开模板中的转义花括号（示例 JSON 块）
        String text = tpl.replace("\\{", "{").replace("\\}", "}");
        // 2) 替换 {key}
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String val = params != null && params.containsKey(key) ? params.get(key) : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ---- schema 文本（对应 PromptHelper.buildMixMacSqlDbPrompt / buildMixMacSqlTablePrompt）----

    /** 把一组表拼成紧凑 schema 文本（用于 prompt 的 {schema_info}）。 */
    public String buildSchemaText(String dbId, List<TableSchema> tables) {
        StringBuilder sb = new StringBuilder();
        sb.append("【DB_ID】 ").append(dbId == null ? "" : dbId).append("\n");
        for (TableSchema t : tables) {
            sb.append(buildTableText(t)).append("\n");
        }
        if (!tables.isEmpty() && hasForeignKeys(tables)) {
            // 本库实库外键数 = 0，这些关系由 JdbcSchemaIntrospector.inferRelations() 按列名约定
            // 与人工提示推断而来。明确告知模型"不确定时优先单表"，避免错误 JOIN 被当成硬事实。
            sb.append("【Foreign keys】（由列名约定推断，非数据库外键约束；"
                    + "若与列注释冲突以列注释为准，不确定时优先单表查询）\n");
            for (TableSchema t : tables) {
                for (ForeignKey fk : t.getForeignKeys()) {
                    sb.append(fk.toString()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private boolean hasForeignKeys(List<TableSchema> tables) {
        for (TableSchema t : tables) {
            if (!t.getForeignKeys().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String buildTableText(TableSchema t) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Table: ").append(t.getName());
        if (!t.getName().equalsIgnoreCase(t.getComment()) && !t.getComment().isEmpty()) {
            sb.append(", ").append(t.getComment());
        }
        sb.append("\n[\n");
        List<String> lines = new ArrayList<>();
        for (ColumnSchema c : t.getColumns()) {
            StringBuilder line = new StringBuilder();
            line.append("(").append(c.getName());
            line.append(":").append(c.getType() == null ? "" : c.getType().toUpperCase(Locale.ROOT));
            if (!c.getName().equalsIgnoreCase(c.getComment()) && !c.getComment().isEmpty()) {
                line.append(", ").append(c.getComment());
            }
            if (c.isPrimary()) {
                line.append(", Primary Key");
            }
            line.append(")");
            lines.add(line.toString());
        }
        sb.append(String.join(",\n", lines));
        sb.append("\n]");
        return sb.toString();
    }

    // ---- 各场景 render 封装 ----

    public String buildSchemaSelectorPrompt(String schemaText, String question, String evidence) {
        Map<String, String> p = new HashMap<>();
        p.put("schema_info", schemaText);
        p.put("question", nullToEmpty(question));
        p.put("evidence", nullToEmpty(evidence));
        return render("mix-selector", p);
    }

    public String buildSqlGeneratePrompt(String dialect, String question, String schemaText,
                                        String evidence, String executionDescription, String previousStepResults) {
        Map<String, String> p = new HashMap<>();
        p.put("dialect", nullToEmpty(dialect));
        p.put("question", nullToEmpty(question));
        p.put("schema_info", schemaText);
        p.put("evidence", nullToEmpty(evidence));
        p.put("execution_description", nullToEmpty(executionDescription));
        p.put("previous_step_results", nullToEmpty(previousStepResults));
        return render("new-sql-generate", p);
    }

    public String buildSqlErrorFixerPrompt(String dialect, String question, String schemaText, String evidence,
                                          String errorSql, String errorMessage, String executionDescription,
                                          String previousStepResults) {
        Map<String, String> p = new HashMap<>();
        p.put("dialect", nullToEmpty(dialect));
        p.put("question", nullToEmpty(question));
        p.put("schema_info", schemaText);
        p.put("evidence", nullToEmpty(evidence));
        p.put("error_sql", nullToEmpty(errorSql));
        p.put("error_message", nullToEmpty(errorMessage));
        p.put("execution_description", nullToEmpty(executionDescription));
        p.put("previous_step_results", nullToEmpty(previousStepResults));
        return render("sql-error-fixer", p);
    }

    public String buildSemanticConsistencyPrompt(String dialect, String executionDescription, String userQuery,
                                                String evidence, String schemaText, String sql) {
        Map<String, String> p = new HashMap<>();
        p.put("dialect", nullToEmpty(dialect));
        p.put("execution_description", nullToEmpty(executionDescription));
        p.put("user_query", nullToEmpty(userQuery));
        p.put("evidence", nullToEmpty(evidence));
        p.put("schema_info", schemaText);
        p.put("sql", nullToEmpty(sql));
        p.put("format", SEMANTIC_FORMAT);
        return render("semantic-consistency", p);
    }

    public String buildQueryEnhancePrompt(String multiTurn, String latestQuery, String evidence) {
        Map<String, String> p = new HashMap<>();
        p.put("current_time_info", now());
        p.put("evidence", nullToEmpty(evidence));
        p.put("multi_turn", nullToEmpty(multiTurn));
        p.put("latest_query", nullToEmpty(latestQuery));
        p.put("format", QUERY_ENHANCE_FORMAT);
        return render("query-enhancement", p);
    }

    public String buildIntentRecognitionPrompt(String multiTurn, String latestQuery) {
        Map<String, String> p = new HashMap<>();
        p.put("multi_turn", nullToEmpty(multiTurn));
        p.put("latest_query", nullToEmpty(latestQuery));
        p.put("format", INTENT_FORMAT);
        return render("intent-recognition", p);
    }

    public String buildEvidenceQueryRewritePrompt(String multiTurn, String latestQuery) {
        Map<String, String> p = new HashMap<>();
        p.put("multi_turn", nullToEmpty(multiTurn));
        p.put("latest_query", nullToEmpty(latestQuery));
        p.put("format", EVIDENCE_FORMAT);
        return render("evidence-query-rewrite", p);
    }

    /**
     * 报告生成提示词（对应 DataAgent {@code PromptHelper.buildReportGeneratorPromptWithOptimization}）。
     *
     * <p>事实优先级由模板本身约束：只允许基于真实 SQL 结果撰写，不得编造同比/趋势/建议。</p>
     */
    public String buildReportPrompt(String question, String canonicalQuery, String sql,
                                    String tenantColumn, java.util.List<java.util.Map<String, Object>> rows) {
        StringBuilder plan = new StringBuilder();
        plan.append("用户原始问题：").append(nullToEmpty(question)).append("\n");
        plan.append("规范化查询：").append(nullToEmpty(canonicalQuery)).append("\n");
        plan.append("目标：用真实查询结果回答用户问题，并给出必要说明。");

        StringBuilder data = new StringBuilder();
        data.append("执行的 SQL：\n").append(nullToEmpty(sql)).append("\n\n");
        if (tenantColumn != null) {
            data.append("数据范围：仅当前主体的数据（").append(tenantColumn).append(" = 调用者身份）\n\n");
        } else {
            data.append("数据范围：主表无租户列（公共/字典数据）\n\n");
        }
        data.append("查询结果（").append(rows == null ? 0 : rows.size()).append(" 行，最多展示 200 行）：\n");
        if (rows == null || rows.isEmpty()) {
            data.append("（空结果）\n");
        } else {
            int limit = Math.min(rows.size(), 200);
            try {
                data.append(com.alibaba.fastjson2.JSON.toJSONString(rows.subList(0, limit)));
            } catch (Exception e) {
                data.append(rows.subList(0, limit).toString());
            }
            data.append("\n");
        }

        Map<String, String> p = new HashMap<>();
        p.put("user_requirements_and_plan", plan.toString());
        p.put("analysis_steps_and_data", data.toString());
        p.put("summary_and_recommendations", "无需额外总结要求；只输出 Markdown 报告。");
        p.put("optimization_section", "");
        p.put("json_example", "{}");
        return render("report-generator-plain", p);
    }

    // ---- JSON 输出协议（替代 Spring AI BeanOutputConverter.getFormat()）----

    private static final String SEMANTIC_FORMAT =
            "{\"passed\": true 或 false, \"reason\": \"若未通过，简短指出具体子句/字段/逻辑及期望修改\"}";
    private static final String QUERY_ENHANCE_FORMAT =
            "{\"canonical_query\": \"规范化查询\", \"expanded_queries\": [\"等价扩展1\", \"等价扩展2\"]}";
    private static final String INTENT_FORMAT =
            "{\"classification\": \"《闲聊或无关指令》\" 或 \"《可能的数据分析请求》\", \"response\": \"\"}";
    private static final String EVIDENCE_FORMAT =
            "{\"standalone_query\": \"适合向量检索的独立查询\"}";

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String now() {
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
