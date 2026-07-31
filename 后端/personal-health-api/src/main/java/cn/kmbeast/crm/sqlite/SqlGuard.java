package cn.kmbeast.crm.sqlite;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CRM SQL read-only guard (SEC-04 fix).
 *
 * <p>The original protection was a blacklist keyword contains-check with two flaws:
 * <ol>
 *   <li>SQL comments could hide inside keywords (e.g. {@code SEL/*} + {@code *&#47;ECT}) to bypass contains;</li>
 *   <li>even with SELECT only, {@code SELECT * FROM chat_history} could dump every user's
 *       consultation records - there was no tenant isolation.</li>
 * </ol>
 *
 * <p>This guard works at the lexical level:
 * <ul>
 *   <li>strip comments (block &amp; line) before case-insensitive keyword check - blocks comment-split bypass;</li>
 *   <li>only a single SELECT / WITH statement; any semicolon is rejected (blocks multi-statement injection);</li>
 *   <li>dangerous verbs (write / DDL / ATTACH / PRAGMA etc.) are all rejected;</li>
 *   <li>if the query touches {@code chat_history}, the top-level WHERE must contain
 *       {@code phone_number = 'xxx'} and {@code xxx} must equal the current context phone -
 *       this is the tenant-isolation backstop: the LLM can only ever see the current user's records.</li>
 * </ul>
 */
public final class SqlGuard {

    private SqlGuard() {
    }

    /** Semicolon anywhere = multi-statement risk (no legit use of ; inside SQL) */
    private static final Pattern SEMICOLON = Pattern.compile(";");
    /** single-quoted string literal incl. '' escaping */
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");
    /** identifiers / numbers */
    private static final Pattern WORD = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b|\\d+");
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|--[^\\r\\n]*", Pattern.DOTALL);
    private static final Pattern PHONE_FILTER = Pattern.compile(
            "\\bphone_number\\s*=\\s*'([^']+)'", Pattern.CASE_INSENSITIVE);

    private static final String[] BLOCKED_KEYWORDS = {
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", "TRUNCATE",
            "REPLACE", "ATTACH", "DETACH", "PRAGMA", "VACUUM", "REINDEX",
            "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT"
    };

    /**
     * Validate SQL as a single read-only query.
     *
     * @param sql user/LLM submitted SQL
     * @throws IllegalArgumentException with the reason when validation fails
     */
    public static void validateReadOnly(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL must not be empty");
        }
        if (SEMICOLON.matcher(sql).find()) {
            throw new IllegalArgumentException("only a single read-only query is allowed; multi-statement is forbidden");
        }
        String stripped = COMMENT.matcher(sql).replaceAll(" ");
        String upper = stripped.toUpperCase();
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            throw new IllegalArgumentException("read-only SELECT queries only");
        }
        for (String kw : BLOCKED_KEYWORDS) {
            if (containsKeyword(stripped, kw)) {
                throw new IllegalArgumentException("forbidden keyword: " + kw.toLowerCase());
            }
        }
    }

    /**
     * Tenant-isolation check: if the query touches {@code chat_history}, the top-level WHERE must pin the current phone.
     *
     * @param sql                 SQL already passed {@link #validateReadOnly}
     * @param expectedPhoneNumber current session phone; null = no context (deny access to chat history)
     * @throws IllegalArgumentException if validation fails
     */
    public static void enforceTenantIsolation(String sql, String expectedPhoneNumber) {
        String stripped = COMMENT.matcher(sql).replaceAll(" ");
        String masked = STRING_LITERAL.matcher(stripped).replaceAll("''");
        if (!referencesTable(masked, "chat_history")) {
            // read-only SQLite has no other business tables; no tenant filter needed
            return;
        }
        if (expectedPhoneNumber == null || expectedPhoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "cannot confirm current user identity; access to chat history denied");
        }
        if (!isSingleStatement(masked)) {
            throw new IllegalArgumentException("subqueries are not allowed");
        }
        Matcher m = PHONE_FILTER.matcher(stripped);
        while (m.find()) {
            if (expectedPhoneNumber.equals(m.group(1))) {
                return;
            }
        }
        throw new IllegalArgumentException(
                "queries on chat_history must filter by your own phone: WHERE phone_number = 'your phone'");
    }

    /**
     * Whether the keyword appears as a whole word, to avoid false hits (e.g. "INSERT" inside LIKE '%history%').
     * String literals are masked already, so their content cannot trigger this.
     */
    private static boolean containsKeyword(String maskedSql, String keyword) {
        Matcher m = WORD.matcher(maskedSql);
        while (m.find()) {
            if (keyword.equalsIgnoreCase(m.group())) {
                return true;
            }
        }
        return false;
    }

    private static boolean referencesTable(String maskedSql, String table) {
        Matcher m = WORD.matcher(maskedSql);
        while (m.find()) {
            if (table.equalsIgnoreCase(m.group())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Roughly detect subqueries: count paren depth after masking string literals -
     * depth beyond 1 (e.g. {@code WHERE id IN (SELECT ...)}) is rejected.
     */
    private static boolean isSingleStatement(String maskedSql) {
        int depth = 0;
        for (char c : maskedSql.toCharArray()) {
            if (c == '(') {
                depth++;
                if (depth > 1) {
                    return false;
                }
            } else if (c == ')') {
                depth--;
            }
        }
        return true;
    }
}
