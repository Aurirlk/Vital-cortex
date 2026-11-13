package cn.kmbeast.core.nl2sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阶段 4 · MySQL 受控 NL2SQL 的<b>词法校验器</b>（对应迁移方案 §六 第 2 层）。
 *
 * <p><b>绝不修改</b> 既有的 {@code cn.kmbeast.crm.sqlite.SqlGuard} —— 后者 10 个既有用例锁定的是
 * SQLite {@code chat_history} 语义（{@code WHERE phone_number='x'}），与 MySQL 租户隔离模型完全不同。</p>
 *
 * <p>与 DataAgent 的关键差异：DataAgent 的只读约束<b>仅存在于 prompt 软约束</b>
 * （{@code new-sql-generate.txt} 第 39-40 行），本项目在 prompt 之外<b>再加一层硬校验</b>。</p>
 *
 * <p>本类为词法级（正则），不解析 AST；配合保守档位（单表 SELECT、禁 JOIN/子查询/UNION）使用。</p>
 */
public final class MySqlAiSqlGuard {

    /** 写操作 / DDL / 危险函数调用（词法拦截）。 */
    private static final Set<String> FORBIDDEN_KEYWORDS = new HashSet<>(Arrays.asList(
            "insert", "update", "delete", "merge", "replace", "truncate",
            "drop", "create", "alter", "rename", "grant", "revoke",
            "call", "exec", "execute", "lock", "unlock", "commit", "rollback",
            "outfile", "dumpfile", "load_file", "sleep", "benchmark",
            "union", "handler", "shutdown"));

    /** 禁止访问的系统库（元数据 / 权限库）。 */
    private static final Set<String> SYSTEM_SCHEMAS = new HashSet<>(Arrays.asList(
            "information_schema", "mysql", "performance_schema", "sys"));

    /**
     * 敏感表黑名单（鉴权 / 配置 / 审计数据，NL2SQL 永不可触及）。
     *
     * <p>直接引用 {@link Nl2SqlSchemaService#TABLE_BLACKLIST}，避免两处各写一份而改不一致 ——
     * 若两处口径不同，会出现「schema 允许召回、守卫却整条拦死」的诡异 bug。</p>
     */
    private static final Set<String> TABLE_BLACKLIST = Nl2SqlSchemaService.TABLE_BLACKLIST;

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-z_][a-z0-9_]*");
    private static final Pattern FROM_PATTERN = Pattern.compile(
            "(?i)\\bfrom\\s+`?([a-z_][a-z0-9_]*)`?");

    /**
     * {@code FROM / JOIN} 后的表引用：可选 {@code schema.} 前缀 + 表名 + 可选别名（支持 {@code AS}）。
     *
     * <p>2026-10-07 加固：原正则会把握写成 {@code FROM personal_health.user_health} 的表名
     * 误判为 {@code personal_health}（库名）→ 被判为「未授权表」而误拦。现允许带库名前缀，
     * 只取最后一段作为真实表名。</p>
     */
    private static final Pattern TABLE_REF_PATTERN = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+(?:`?[a-z_][a-z0-9_]*`?\\s*\\.\\s*)?"
                    + "`?([a-z_][a-z0-9_]*)`?(?:\\s+(?:as\\s+)?([a-z_][a-z0-9_]*))?");

    /** 独立的 {@code from} 关键字（用于定位 FROM 子句边界）。 */
    private static final Pattern FROM_KEYWORD_PATTERN = Pattern.compile("(?i)(?<!\\S)from(?!\\S)");

    /** FROM 子句终止符（出现即视为 FROM 子句结束）。 */
    private static final String[] CLAUSE_TERMINATORS = {
            " where ", " group by ", " order by ", " limit ", " having ", " union ", " window ", " for "};

    /** 出现在表名之后但属于 SQL 关键字者，不得被当作别名。 */
    private static final Set<String> NON_ALIAS_WORDS = new HashSet<>(Arrays.asList(
            "where", "join", "left", "right", "inner", "outer", "cross", "full", "natural",
            "straight_join", "on", "using", "group", "order", "limit", "having", "union",
            "and", "or", "not", "as", "select", "from", "set", "values", "into", "for",
            "update", "lock", "share", "mode", "by", "asc", "desc", "end", "then", "else"));

    /** 子查询 / CTE 探测（保守档位下拒绝：正则无法安全定位其内部租户列）。 */
    private static final Pattern SUBQUERY_PATTERN = Pattern.compile("(?is)\\(\\s*select\\b");

    private MySqlAiSqlGuard() {
    }

    /** 表引用：真实表名 + 用于限定列的限定符（有别名用别名，否则用表名）。 */
    public static final class TableRef {
        private final String table;
        private final String qualifier;

        TableRef(String table, String qualifier) {
            this.table = table;
            this.qualifier = qualifier;
        }

        public String getTable() {
            return table;
        }

        public String getQualifier() {
            return qualifier;
        }

        @Override
        public String toString() {
            return table + (qualifier.equals(table) ? "" : " AS " + qualifier);
        }
    }

    /** 受控 NL2SQL 安全异常。 */
    public static class Nl2SqlSecurityException extends RuntimeException {
        public Nl2SqlSecurityException(String message) {
            super(message);
        }
    }

    /**
     * 校验 SQL 是否安全可执行。
     *
     * @return true 表示通过
     */
    public static boolean validate(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }
        String s = sql.trim();
        // 1) 多语句 / 末尾分号（防语句堆叠）
        if (s.indexOf(';') >= 0) {
            return false;
        }
        // 2) 注释（防注释绕过关键字检测）
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.contains("--") || lower.contains("/*") || lower.contains("*/")) {
            return false;
        }
        // 3) 必须是单条只读查询
        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            return false;
        }
        // 4) 词法扫描：危险关键字 / 系统库 / 黑名单表
        Matcher m = WORD_PATTERN.matcher(lower);
        while (m.find()) {
            String w = m.group();
            if (FORBIDDEN_KEYWORDS.contains(w)) {
                return false;
            }
            if (SYSTEM_SCHEMAS.contains(w)) {
                return false;
            }
            if (TABLE_BLACKLIST.contains(w)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验失败即抛异常。
     *
     * @throws Nl2SqlSecurityException 校验不通过
     */
    public static void assertSafe(String sql) {
        if (!validate(sql)) {
            throw new Nl2SqlSecurityException("SQL 未通过受控 NL2SQL 安全校验（词法）："
                    + (sql == null ? "null" : sql.trim()));
        }
    }

    /**
     * 提取主表（第一个 FROM 后的表名），用于判定租户字段是否存在。
     *
     * @return 表名，提取不到返回 null
     */
    public static String extractPrimaryTable(String sql) {
        if (sql == null) {
            return null;
        }
        Matcher m = FROM_PATTERN.matcher(sql);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 提取 SQL 中所有 {@code FROM / JOIN} 引用的表（含别名限定符）。
     *
     * <p>用于租户隔离的<b>逐表覆盖校验</b>：只按「第一个 FROM 的表」判定租户列会漏掉
     * JOIN 进来的租户表，导致租户过滤被整体跳过（跨租户泄漏）。</p>
     *
     * @return 按出现顺序去重的表引用；解析不到返回空列表
     */
    public static List<TableRef> extractTableRefs(String sql) {
        List<TableRef> refs = new ArrayList<>();
        if (sql == null || sql.isEmpty()) {
            return refs;
        }
        Set<String> seen = new HashSet<>();
        Matcher m = TABLE_REF_PATTERN.matcher(sql);
        while (m.find()) {
            String table = m.group(1).toLowerCase(Locale.ROOT);
            String alias = m.group(2) == null ? null : m.group(2).toLowerCase(Locale.ROOT);
            if (alias != null && NON_ALIAS_WORDS.contains(alias)) {
                alias = null;
            }
            String qualifier = alias == null ? table : alias;
            if (seen.add(table + "|" + qualifier)) {
                refs.add(new TableRef(table, qualifier));
            }
        }
        return refs;
    }

    /**
     * 是否为子查询 / CTE 形态。
     *
     * <p>词法级重写器无法定位子查询内部的租户列（追加条件会落到顶层 WHERE 上），
     * 因此在保守档位下此类 SQL 必须 <b>fail-closed</b> 而不是放行。</p>
     */
    public static boolean hasSubqueryOrCte(String sql) {
        return sql != null && SUBQUERY_PATTERN.matcher(sql).find();
    }

    /**
     * 是否为「隐式逗号连接」形态（{@code FROM a, b}）。
     *
     * <p><b>为什么必须 fail-closed</b>：{@link #TABLE_REF_PATTERN} 只在 {@code FROM/JOIN}
     * 关键字之后识别表名，逗号连接的第二张表<b>不会被识别</b> → 若该表带租户列也不会被限定，
     * 且「是否多表」判定也会错（导致租户列不带限定符而报 ambiguous）。词法级无法安全处理，
     * 故保守档位下直接拒绝，要求模型改写为显式 {@code JOIN ... ON}。</p>
     *
     * <p>扫描范围严格限定在顶层 FROM 子句内，并按括号深度过滤 —— {@code IN (1,2)} 这类
     * 函数/子表达式里的逗号不会被误判。</p>
     */
    public static boolean hasImplicitCommaJoin(String sql) {
        if (sql == null) {
            return false;
        }
        String lower = sql.toLowerCase(Locale.ROOT);
        Matcher fromM = FROM_KEYWORD_PATTERN.matcher(lower);
        if (!fromM.find()) {
            return false;
        }
        int start = fromM.end();
        int end = lower.length();
        for (String term : CLAUSE_TERMINATORS) {
            int idx = lower.indexOf(term, start);
            if (idx >= 0 && idx < end) {
                end = idx;
            }
        }
        int depth = 0;
        for (int i = start; i < end; i++) {
            char c = lower.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (depth > 0) {
                    depth--;
                }
            } else if (c == ',' && depth == 0) {
                return true;
            }
        }
        return false;
    }
}
