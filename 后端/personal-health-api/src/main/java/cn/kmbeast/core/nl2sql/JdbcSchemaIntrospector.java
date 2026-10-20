package cn.kmbeast.core.nl2sql;

import cn.kmbeast.config.Nl2SqlReadOnlyDataSourceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阶段 4（DataAgent 资产摘取）· Schema 内省器。
 *
 * <p>移植自 DataAgent {@code MysqlJdbcDdl}（249 行），但 <b>丢弃</b> 其 {@code Ddl}/{@code AbstractJdbcDdl}/
 * {@code DdlFactory}/{@code AccessorFactory}/自建连接池，<b>复用本项目既有 {@link DataSource}</b>。
 * 保留：information_schema 表/列/外键查询、列注释采样、库名正则提取。</p>
 *
 * <p>本类只产出 {@link TableSchema} 结构，不依赖 Spring AI。</p>
 */
@Slf4j
@Component
public class JdbcSchemaIntrospector {

    /** 从 JDBC URL 提取库名：{@code jdbc:mysql://host:3306/dbname?...} → dbname */
    private static final Pattern DB_NAME_PATTERN = Pattern.compile(":\\d+/([^/?&]+)");

    /**
     * 主数据源（Spring Boot 自动配置，bean 名固定为 {@code dataSource}）。
     * 未配置只读数据源时，NL2SQL 查询走它。
     */
    @Resource(name = "dataSource")
    private DataSource primaryDataSource;

    /**
     * NL2SQL 专用只读数据源提供者。bean 恒存在，但未配置
     * {@code crm.ai.nl2sql.read-only.url} 时其 {@link Nl2SqlReadOnlyDataSourceProvider#get()}
     * 返回 {@code null}，由 {@link #execDataSource()} 回落主数据源。
     */
    @Autowired(required = false)
    @Qualifier(Nl2SqlReadOnlyDataSourceProvider.BEAN_NAME)
    private Nl2SqlReadOnlyDataSourceProvider readOnlyDataSourceProvider;

    /** 是否已就「内省回落到主数据源」告警过一次（避免每次刷新都刷屏）。 */
    private volatile boolean fallbackWarned = false;

    /**
     * 取「内省用」的数据源：优先只读源，缺失时回落主数据源。
     *
     * <p>2026-10-12：只读池改由 {@link Nl2SqlReadOnlyDataSourceProvider} 持有，
     * 原因见该类注释（注册 DataSource bean 会令主数据源自动配置退避，应用无法启动）。</p>
     *
     * <p><b>为什么这里仍允许回落</b>（而 {@code DataAgentPipeline#execDataSource()}
     * 在阶段 6 已改为 fail-closed）：内省只读 {@code information_schema} 元数据，
     * 不执行任何模型生成的 SQL，用主库账号不会带来写入风险；反过来若也 fail-closed，
     * 未配置只读账号的环境连「数据智能体 → 表清单」都打不开。执行路径才是必须
     * 拒绝的那一层。回落时打一条 warn，避免「以为走了只读账号」的误解。</p>
     */
    private DataSource execDataSource() {
        Nl2SqlReadOnlyDataSourceProvider provider = readOnlyDataSourceProvider;
        DataSource ro = provider == null ? null : provider.get();
        if (ro != null) {
            return ro;
        }
        if (!fallbackWarned) {
            fallbackWarned = true;
            log.warn("[JdbcSchemaIntrospector] 未配置 crm.ai.nl2sql.read-only.url，"
                    + "表结构内省回落主数据源（仅读 information_schema；SQL 执行侧仍会拒绝）。"
                    + "建议执行 Data/02_迁移脚本/09_create_ai_ro_user.sql 并配置只读账号。");
        }
        return primaryDataSource;
    }

    /** 从连接 URL 解析库名（兜底用，优先用 {@link Connection#getCatalog()}）。 */
    public static String extractSchemaFromUrl(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = DB_NAME_PATTERN.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /** 列出当前库所有基表（名称 + 注释），用于表选择上下文。 */
    public List<TableMeta> listTables() {
        List<TableMeta> result = new ArrayList<>();
        try (Connection conn = execDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT TABLE_NAME, TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES "
                             + "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME")) {
            ps.setString(1, conn.getCatalog());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new TableMeta(rs.getString(1), rs.getString(2)));
                }
            }
        } catch (SQLException e) {
            log.error("[JdbcSchemaIntrospector] listTables failed", e);
            throw new RuntimeException("读取表清单失败", e);
        }
        return result;
    }

    /**
     * 内省指定表的 schema（列 + 外键）。
     *
     * @param tableNames         目标表名（会被过滤为真实存在的表）
     * @param maxColumnsPerTable 单表最大列数（超出截断）
     */
    public List<TableSchema> introspect(Collection<String> tableNames, int maxColumnsPerTable) {
        if (tableNames == null || tableNames.isEmpty()) {
            return new ArrayList<>();
        }
        List<TableSchema> schemas = new ArrayList<>();
        try (Connection conn = execDataSource().getConnection()) {
            String catalog = conn.getCatalog();
            Map<String, TableSchema> byName = new LinkedHashMap<>();
            for (String tableName : tableNames) {
                TableSchema ts = introspectTable(conn, catalog, tableName, maxColumnsPerTable);
                if (ts != null) {
                    byName.put(ts.getName(), ts);
                }
            }
            Set<String> names = new LinkedHashSet<>(byName.keySet());
            fillForeignKeys(conn, catalog, names, byName);
            // 实库外键数 = 0，上一步恒为空；按列名约定 + 人工核实过的提示补充关系，
            // 否则 buildSchemaText 的【Foreign keys】段永远为空、模型只能退化为单表查询。
            inferRelations(byName);
            schemas = new ArrayList<>(byName.values());
        } catch (SQLException e) {
            log.error("[JdbcSchemaIntrospector] introspect failed", e);
            throw new RuntimeException("Schema 内省失败", e);
        }
        return schemas;
    }

    private TableSchema introspectTable(Connection conn, String catalog, String tableName, int maxColumnsPerTable)
            throws SQLException {
        TableSchema ts = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT column_name, column_comment, data_type, "
                        + "IF(column_key='PRI','true','false') AS is_pk, "
                        + "IF(IS_NULLABLE='NO','true','false') AS is_notnull "
                        + "FROM information_schema.COLUMNS WHERE table_schema=? AND table_name=? "
                        + "ORDER BY ordinal_position LIMIT ?")) {
            ps.setString(1, catalog);
            ps.setString(2, tableName);
            ps.setInt(3, maxColumnsPerTable <= 0 ? 50 : maxColumnsPerTable);
            try (ResultSet rs = ps.executeQuery()) {
                boolean has = false;
                List<ColumnSchema> columns = new ArrayList<>();
                String tableComment = "";
                while (rs.next()) {
                    has = true;
                    String comment = rs.getString(2);
                    columns.add(new ColumnSchema(
                            rs.getString(1),
                            comment == null ? "" : comment,
                            rs.getString(3),
                            "true".equalsIgnoreCase(rs.getString(4)),
                            "true".equalsIgnoreCase(rs.getString(5))));
                }
                if (!has) {
                    return null; // 表不存在或无权访问
                }
                ts = new TableSchema(tableName, tableComment, columns);
            }
        }
        // 单独补表注释（避免上面的 LIMIT 影响）
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) {
            ps.setString(1, catalog);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && ts != null) {
                    String c = rs.getString(1);
                    ts.setComment(c == null ? "" : c);
                }
            }
        }
        return ts;
    }

    private void fillForeignKeys(Connection conn, String catalog, Set<String> tableNames,
                                Map<String, TableSchema> byName) throws SQLException {
        if (tableNames.isEmpty()) {
            return;
        }
        String in = String.join(",", tableNames.stream().map(n -> "'" + n + "'").toList());
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME "
                        + "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE "
                        + "WHERE CONSTRAINT_SCHEMA=? AND CONSTRAINT_NAME<>'PRIMARY' "
                        + "AND TABLE_NAME IN (" + in + ") AND REFERENCED_TABLE_NAME IN (" + in + ")")) {
            ps.setString(1, catalog);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ForeignKey fk = new ForeignKey(rs.getString(1), rs.getString(2),
                            rs.getString(3), rs.getString(4));
                    TableSchema ts = byName.get(fk.getTable());
                    if (ts != null) {
                        ts.getForeignKeys().add(fk);
                    }
                }
            }
        }
    }

    /**
     * 业务上确定、但列名约定推断不出的关系（逐条人工核实过 JOIN 能关联出数据）。
     * key = 小写 {@code 表名.列名}，value = 目标表名。
     */
    private static final Map<String, String> RELATION_HINTS = Map.of(
            "appointment.doctor_id", "hospital_doctor",
            "doctor_schedule.doctor_id", "hospital_doctor",
            "followup_task.doctor_id", "hospital_doctor",
            "visit_record.doctor_id", "hospital_doctor");

    /**
     * 按列名约定 + 人工提示补充外键关系（本库实库外键数 = 0，必须靠推断）。
     *
     * <p><b>为什么需要</b>：{@link #fillForeignKeys} 查 {@code KEY_COLUMN_USAGE} 恒返回空，
     * 于是 {@code Nl2SqlPromptBuilder#buildSchemaText} 的【Foreign keys】段永远不输出，
     * 而 SQL 生成模板又明令「不得猜测关联关系」→ 模型只能退化为单表查询。
     * 补上关系后，多表 JOIN 才有依据。
     *
     * <p><b>推断规则</b>：列名 {@code x_id} → 候选表 {@code x} → {@code xs} → {@code x_info}；
     * 候选表必须 ① 在本批 schema 中真实存在 ② 含 {@code id} 列 ③ 不在黑名单内。
     * 已存在的真外键优先，不覆盖。
     *
     * <p>⛔ <b>能力边界（不得虚标）</b>：{@code user_id} / {@code patient_id} 指向的 {@code user}
     * 表属于 {@link Nl2SqlSchemaService#TABLE_BLACKLIST}，任何引用它的 SQL 都会被
     * {@link MySqlAiSqlGuard} 整条拦死，因此这里【不推断】到 user ——
     * 「就诊记录 + 患者姓名」这类 JOIN 本轮不支持，这是既有安全边界而非本次退化。
     *
     * <p>⚠️ <b>风险</b>：推断错会让模型写出错误 JOIN，且<b>结果错但不报错</b>
     *（语义一致性校验在 JSON 解析失败时默认判通过）。因此只接受「候选表真实存在」的推断，
     * 并在 prompt 的关系段标注「由列名约定推断」。
     *
     * @param byName 表名 → 表 schema（调用方已过滤黑名单）
     */
    public static void inferRelations(Map<String, TableSchema> byName) {
        if (byName == null || byName.isEmpty()) {
            return;
        }
        for (TableSchema ts : byName.values()) {
            if (ts == null || ts.getColumns() == null) {
                continue;
            }
            for (ColumnSchema c : ts.getColumns()) {
                String col = c.getName();
                if (col == null || col.length() <= 3) {
                    continue;
                }
                String lowerCol = col.toLowerCase(Locale.ROOT);
                if (!lowerCol.endsWith("_id")) {
                    continue;
                }
                if (hasForeignKeyOn(ts, col)) {
                    continue; // 真外键优先，不覆盖
                }
                String hintKey = ts.getName().toLowerCase(Locale.ROOT) + "." + lowerCol;
                String hinted = RELATION_HINTS.get(hintKey);
                TableSchema target = resolveTarget(byName, hinted);
                if (target == null) {
                    // 退回命名约定：x_id → x / xs / x_info
                    String base = lowerCol.substring(0, lowerCol.length() - 3);
                    for (String cand : List.of(base, base + "s", base + "_info")) {
                        target = resolveTarget(byName, cand);
                        if (target != null) {
                            break;
                        }
                    }
                }
                if (target != null && !target.getName().equalsIgnoreCase(ts.getName())) {
                    ts.getForeignKeys().add(
                            new ForeignKey(ts.getName(), col, target.getName(), "id"));
                }
            }
        }
    }

    /** 候选目标表是否可用：存在 + 有 id 列 + 不在黑名单。 */
    private static TableSchema resolveTarget(Map<String, TableSchema> byName, String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return null;
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        // 黑名单表永不可触及：JOIN 到它会被词法守卫整条拦死
        if (Nl2SqlSchemaService.isBlacklisted(lower)) {
            return null;
        }
        TableSchema target = byName.get(lower);
        if (target == null || !target.hasColumn("id")) {
            return null;
        }
        return target;
    }

    /** 该列是否已有（真实）外键，避免推断覆盖真外键。 */
    private static boolean hasForeignKeyOn(TableSchema ts, String column) {
        for (ForeignKey fk : ts.getForeignKeys()) {
            if (fk.getColumn() != null && fk.getColumn().equalsIgnoreCase(column)) {
                return true;
            }
        }
        return false;
    }

    // ---- DTO（移植 DataAgent bo.schema.*，精简为 NL2SQL 所需字段）----

    /** 列信息。 */
    public static class ColumnSchema {
        private final String name;
        private final String comment;
        private final String type;
        private final boolean primary;
        private final boolean notNull;

        public ColumnSchema(String name, String comment, String type, boolean primary, boolean notNull) {
            this.name = name;
            this.comment = comment;
            this.type = type;
            this.primary = primary;
            this.notNull = notNull;
        }

        public String getName() { return name; }
        public String getComment() { return comment; }
        public String getType() { return type; }
        public boolean isPrimary() { return primary; }
        public boolean isNotNull() { return notNull; }
    }

    /** 外键信息。 */
    public static class ForeignKey {
        private final String table;
        private final String column;
        private final String referencedTable;
        private final String referencedColumn;

        public ForeignKey(String table, String column, String referencedTable, String referencedColumn) {
            this.table = table;
            this.column = column;
            this.referencedTable = referencedTable;
            this.referencedColumn = referencedColumn;
        }

        public String getTable() { return table; }
        public String getColumn() { return column; }
        public String getReferencedTable() { return referencedTable; }
        public String getReferencedColumn() { return referencedColumn; }

        @Override
        public String toString() {
            return table + "." + column + " -> " + referencedTable + "." + referencedColumn;
        }
    }

    /** 表 schema。 */
    public static class TableSchema {
        private final String name;
        private String comment;
        private final List<ColumnSchema> columns;
        private final List<ForeignKey> foreignKeys = new ArrayList<>();

        public TableSchema(String name, String comment, List<ColumnSchema> columns) {
            this.name = name;
            this.comment = comment;
            this.columns = columns;
        }

        public String getName() { return name; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public List<ColumnSchema> getColumns() { return columns; }
        public List<ForeignKey> getForeignKeys() { return foreignKeys; }

        /** 该表是否含某个列名（用于判定租户字段）。 */
        public boolean hasColumn(String columnName) {
            for (ColumnSchema c : columns) {
                if (c.getName().equalsIgnoreCase(columnName)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 轻量表元信息（名称 + 注释）。 */
    public static class TableMeta {
        private final String name;
        private final String comment;

        public TableMeta(String name, String comment) {
            this.name = name;
            this.comment = comment;
        }

        public String getName() { return name; }
        public String getComment() { return comment; }
    }
}
