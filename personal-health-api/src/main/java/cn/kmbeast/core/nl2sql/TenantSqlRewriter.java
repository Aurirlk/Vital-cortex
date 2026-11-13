package cn.kmbeast.core.nl2sql;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阶段 4 · 租户隔离重写器（对应迁移方案 §六「重写而非校验」核心）。
 *
 * <p><b>核心思想：删除而非校验。</b>不去「检查 SQL 里有没有正确的 user_id」——
 * LLM 可以写 {@code WHERE user_id=3 OR 1=1}、{@code UNION SELECT}，检查永远追不上花样。
 * 正确做法是<b>无条件覆写</b>：把 LLM 写的租户谓词全部中和掉，再无条件追加自己的绑定。</p>
 *
 * <p>保守档位实现（正则，无需 AST）：单表 SELECT、无子查询/UNION，
 * 因此只需处理顶层 WHERE —— 把整个 WHERE 括起来再 AND 租户条件，
 * 从而规避 {@code OR} 优先级导致 {@code WHERE X OR Y AND 租户条件} 被绕过。</p>
 *
 * <p>租户真值<b>从不来自 SQL 文本</b>，由调用方从 {@code LocalThreadHolder} 取并以
 * {@code :__tenantUserId} 命名参数绑定。</p>
 */
public final class TenantSqlRewriter {

    /** 命名参数占位符，执行侧用 NamedParameterJdbcTemplate 绑定真值。 */
    public static final String TENANT_PARAM = ":__tenantUserId";

    /** 租户相关列：LLM 写的这些谓词一律中和。 */
    private static final String[] TENANT_COLUMNS = {"user_id", "patient_id", "doctor_id"};

    /** 中和后的占位谓词（保持 SQL 语法合法，语义恒真，安全由后续 AND 租户条件保证）。 */
    private static final String NEUTRAL_PREDICATE = "1=1";

    /**
     * 谓词匹配：{@code [限定符.][`]col[`] 比较符 值}。
     *
     * <p>限定符（{@code a.user_id}）必须整体吃掉——否则中和后残留 {@code a.1=1} 造成语法错误；
     * JOIN 场景下模型必然带表前缀，这是必现路径。</p>
     */
    private static final Pattern PREDICATE_PATTERN = Pattern.compile(
            "(?i)(?:`?\\w+`?\\.)?`?(user_id|patient_id|doctor_id)`?\\s*(=|!=|<>|>=|<=|>|<|like)\\s*"
                    + "('[^']*'|\\d+(?:\\.\\d+)?|\\?)");

    /** WHERE 子句终止符（保守档位：无子查询，取第一次出现即可）。 */
    private static final String[] WHERE_TERMINATORS = {" group by ", " order by ", " limit ", " having ", " union "};

    private static final Pattern WHERE_PATTERN = Pattern.compile("(?i)(?<!\\S)(where)(?!\\S)");

    private TenantSqlRewriter() {
    }

    /** 重写结果。 */
    public static class Rewritten {
        private final String sql;
        private final boolean tenantApplied;

        Rewritten(String sql, boolean tenantApplied) {
            this.sql = sql;
            this.tenantApplied = tenantApplied;
        }

        public String getSql() { return sql; }
        public boolean isTenantApplied() { return tenantApplied; }
    }

    /**
     * 重写 SQL。
     *
     * @param sql          LLM 生成的、已过 {@link MySqlAiSqlGuard} 校验的 SQL
     * @param tenantColumn 主表的租户列名（如 {@code user_id}）；为 null 表示该表无租户列，不做租户过滤
     * @return 重写结果；若 {@code tenantColumn} 为 null 则原样返回（仅中和掉租户谓词）
     */
    public static Rewritten rewrite(String sql, String tenantColumn) {
        if (sql == null || sql.trim().isEmpty()) {
            return new Rewritten(sql, false);
        }
        // 1) 中和 LLM 写的租户谓词（删除而非校验：物理上无法表达 OR 1=1 绕过）
        String neutralized = neutralizeTenantPredicates(sql);
        if (tenantColumn == null || tenantColumn.trim().isEmpty()) {
            return new Rewritten(neutralized, false);
        }
        // 2) 无条件追加租户条件
        String withTenant = appendTenantCondition(neutralized, tenantColumn.trim());
        return new Rewritten(withTenant, true);
    }

    /**
     * 多表逐列限定重写（JOIN 场景加固，2026-10-07）。
     *
     * <p>阶段 4 的 {@link #rewrite} 只按「第一个 FROM 的表」判定租户列；一旦<b>主表无租户列
     * 而 JOIN 进来的表有</b>（或 SQL 用 CTE 包裹），租户过滤会被整体跳过 → 跨租户泄漏。
     * 本方法由调用方传入<b>所有</b>需限定的「限定符.列名」，逐个追加；
     * 调用方必须在传入非空清单前完成「无主体即 fail-closed」的判定。</p>
     *
     * @param qualifiedTenantColumns 形如 {@code ["a.user_id", "b.patient_id"]}；空列表表示无需租户过滤
     */
    public static Rewritten rewriteAll(String sql, List<String> qualifiedTenantColumns) {
        if (sql == null || sql.trim().isEmpty()) {
            return new Rewritten(sql, false);
        }
        String neutralized = neutralizeTenantPredicates(sql);
        if (qualifiedTenantColumns == null || qualifiedTenantColumns.isEmpty()) {
            return new Rewritten(neutralized, false);
        }
        StringBuilder extra = new StringBuilder();
        for (String col : qualifiedTenantColumns) {
            if (col == null || col.trim().isEmpty()) {
                continue;
            }
            extra.append(" AND ").append(col.trim()).append(" = ").append(TENANT_PARAM);
        }
        if (extra.length() == 0) {
            return new Rewritten(neutralized, false);
        }
        return new Rewritten(appendRawCondition(neutralized, extra.toString()), true);
    }

    /** 在顶层 WHERE 之后（或新建 WHERE）追加一段原始 AND 条件。 */
    private static String appendRawCondition(String sql, String andClause) {
        Matcher where = WHERE_PATTERN.matcher(sql);
        if (!where.find()) {
            int cut = findTerminatorIndex(sql, 0);
            String head = withSingleTrailingSpace(sql.substring(0, cut));
            return head + "WHERE " + stripLeadingAnd(andClause) + sql.substring(cut);
        }
        int whereStart = where.start();
        int whereEnd = findTerminatorIndex(sql, whereStart);
        String before = withSingleTrailingSpace(sql.substring(0, whereStart));
        String originalWhere = sql.substring(whereStart, whereEnd).trim();
        String condition = originalWhere.length() > "where".length()
                ? originalWhere.substring("where".length()).trim()
                : "";
        String newWhere = condition.isEmpty()
                ? "WHERE " + stripLeadingAnd(andClause)
                : "WHERE (" + condition + ")" + andClause;
        return before + newWhere + sql.substring(whereEnd);
    }

    private static String stripLeadingAnd(String s) {
        return s.trim().replaceFirst("(?i)^and\\s+", "");
    }

    private static String neutralizeTenantPredicates(String sql) {
        Matcher m = PREDICATE_PATTERN.matcher(sql);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(NEUTRAL_PREDICATE));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String appendTenantCondition(String sql, String tenantColumn) {
        Matcher where = WHERE_PATTERN.matcher(sql);
        if (!where.find()) {
            // 无 WHERE：在终止符前插入完整 WHERE 子句
            int cut = findTerminatorIndex(sql, 0);
            String head = withSingleTrailingSpace(sql.substring(0, cut));
            String tail = sql.substring(cut);
            return head + "WHERE " + tenantColumn + " = " + TENANT_PARAM + tail;
        }
        int whereStart = where.start();
        int whereEnd = findTerminatorIndex(sql, whereStart);
        String before = withSingleTrailingSpace(sql.substring(0, whereStart));
        String originalWhere = sql.substring(whereStart, whereEnd).trim();
        // 去掉 WHERE 关键字本身，保留条件表达式并括起来，规避 OR 优先级
        String condition = originalWhere.length() > "where".length()
                ? originalWhere.substring("where".length()).trim()
                : "";
        String newWhere;
        if (condition.isEmpty()) {
            newWhere = "WHERE " + tenantColumn + " = " + TENANT_PARAM;
        } else {
            // 若已被中和得只剩恒真谓词，仍保留括号 + AND，语义等价且安全
            newWhere = "WHERE (" + condition + ") AND " + tenantColumn + " = " + TENANT_PARAM;
        }
        String tail = sql.substring(whereEnd);
        return before + newWhere + tail;
    }

    /** 保证片段以且仅以一个空格结尾，避免拼接出现双空格。 */
    private static String withSingleTrailingSpace(String s) {
        return s.replaceAll("\\s+$", "") + " ";
    }

    /** 从 fromIndex 起找第一个 WHERE 终止符位置；找不到返回字符串长度。 */
    private static int findTerminatorIndex(String sql, int fromIndex) {
        String lower = sql.toLowerCase(Locale.ROOT);
        int best = sql.length();
        for (String term : WHERE_TERMINATORS) {
            int idx = lower.indexOf(term, fromIndex);
            if (idx >= 0 && idx < best) {
                best = idx;
            }
        }
        return best;
    }
}
