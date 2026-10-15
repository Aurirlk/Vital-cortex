package cn.kmbeast.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mapper XML 与实际数据表一致性校验（阶段 0-A / A4）
 *
 * <p><b>为什么必须有这个测试</b>：
 *
 * <p>2026-10-06 全项目普查发现 <b>7 张表被 Mapper 引用但数据库中根本不存在</b>，
 * 涉及 33 条 SQL、一调用就抛 {@code Table 'personal_health.xxx' doesn't exist}：
 * <ul>
 *   <li><b>A 类（删表未同步改代码）</b>：{@code drug} / {@code drug_subscription} ——
 *       2026-08-28 用户决定"药品合并商城"删掉了表，{@code DrugMapper} 却原封不动留着。</li>
 *   <li><b>B 类（从未落地的纸面能力）</b>：{@code role} / {@code permission} /
 *       {@code role_permission} / {@code user_role} / {@code sensitive_word} ——
 *       在 17 份历史备份中<b>一次都没出现过</b>，实际权限靠 {@code RoleEnum} 硬编码枚举。</li>
 * </ul>
 *
 * <p>而它们能"潜伏"至今的原因是：<b>既有 229 个用例全部是 Mockito 单测、零 {@code @SpringBootTest}</b>，
 * 从不连数据库。所以"删了表"在当时的测试里<b>根本不可能被发现</b>。
 *
 * <p>本测试是本问题的<b>唯一自动化防线</b>：脚本层面的静态校验，无需连库即可在 CI 拦截。
 * 以后任何人删表，这里会立刻红。
 *
 * <p><b>权威表清单来源</b>：{@code Data/04_结构基线/数据表结构基线-*.md}，
 * 由 {@code Data/tools/gen_schema_doc.py} 从 {@code information_schema} 生成。
 * ⚠️ 若改表后没重新生成基线文档，本测试会误报 —— 届时应先跑生成脚本。
 */
@DisplayName("Mapper 与实际数据表一致性")
class MapperTableConsistencyTest {

    /**
     * 允许出现的非业务"表"：
     * <ul>
     *   <li>{@code information_schema} / {@code dual} —— 元数据或哑表查询</li>
     *   <li>{@code json_table} —— MySQL 内置的<b>表函数</b>（{@code JSON_TABLE(json, path COLUMNS ...)}），
     *       出现在 FROM 后被本测试的正则捕获，但它不是真实表，需豁免。</li>
     * </ul>
     */
    private static final Set<String> WHITELIST = new HashSet<>(Arrays.asList(
            "information_schema", "dual", "json_table"
    ));

    private static final Pattern TABLE_HEADING = Pattern.compile("^###\\s+`([a-z_][a-z0-9_]*)`", Pattern.MULTILINE);

    /** 提取 FROM / JOIN / UPDATE / INTO 之后的表名 */
    private static final Pattern TABLE_REF = Pattern.compile(
            "(?i)\\b(?:FROM|JOIN|UPDATE|INTO)\\s+`?([a-z_][a-z0-9_]*)`?");

    @Test
    @DisplayName("🔴 所有 Mapper XML 引用的表都必须真实存在")
    void everyReferencedTableMustExist() throws IOException {
        Set<String> existing = loadExistingTables();
        assertFalse(existing.isEmpty(),
                "未能从结构基线文档读到任何表名，请检查 Data/04_结构基线/ 是否存在");

        Set<String> pending = loadPendingMigrationTables(existing);
        if (!pending.isEmpty()) {
            // 明确打出来而不是静默跳过：这些表在本机确实还不存在，
            // 相关 Mapper 一旦被调用仍会抛 Table doesn't exist。
            System.out.println("[MapperTableConsistency] ⏳ 已声明但本机尚未执行迁移的表（按「待落地」放行）: "
                    + pending);
        }

        Map<String, List<String>> dangling = new LinkedHashMap<>();

        for (Path xml : mapperXmlFiles()) {
            String content = Files.readString(xml, StandardCharsets.UTF_8);
            for (String table : extractReferencedTables(content)) {
                if (WHITELIST.contains(table) || existing.contains(table) || pending.contains(table)) {
                    continue;
                }
                dangling.computeIfAbsent(table, k -> new ArrayList<>())
                        .add(xml.getFileName().toString());
            }
        }

        if (!dangling.isEmpty()) {
            StringBuilder msg = new StringBuilder("\n\n");
            msg.append("══════ Mapper 引用了不存在的数据表 ══════\n");
            msg.append("这些 SQL 一旦被调用就会抛 Table doesn't exist。\n");
            msg.append("常见成因：① 删表后没同步改 Mapper  ② 抄来的样板代码从未落地\n");
            msg.append("注意：迁移脚本（Data/02_迁移脚本/*.sql）里声明过的表不在此列 ——\n");
            msg.append("      那种情况属于「待落地」，已在上面单独提示。\n\n");
            for (Map.Entry<String, List<String>> e : dangling.entrySet()) {
                msg.append(String.format("  ✗ 表 `%s`%n", e.getKey()));
                for (String f : e.getValue()) {
                    msg.append(String.format("      └─ %s%n", f));
                }
            }
            msg.append("\n处置：① 改指向真实存在的表（遵循强耦合原则，优先加列不建表）\n");
            msg.append("      ② 若为从未落地的死代码，删除对应 Mapper + XML + Service\n");
            msg.append("      ③ 若此表确实新建了，请把建表语句放进 Data/02_迁移脚本/ 并重跑\n");
            msg.append("         Data/tools/gen_schema_doc.py 更新基线\n");
            msg.append("══════════════════════════════════════════\n");
            fail(msg.toString());
        }
    }

    @Test
    @DisplayName("「待落地表」识别：CREATE 计入、DROP 抵消、注释里的语句不计入")
    void pendingMigrationScannerIsHonest() {
        // CREATE 计入
        assertTrue(declaredCreatedTables("CREATE TABLE IF NOT EXISTS agent_config (id INT);")
                .contains("agent_config"));
        // DROP 抵消：脚本里先建后删的表不算「待落地」——否则一个已废弃的表，
        // 只要它的建表语句还留在某个迁移脚本里，就会被永久放行
        String created = "CREATE TABLE IF NOT EXISTS tmp_a (id INT);";
        String dropped = "DROP TABLE IF EXISTS tmp_a;";
        assertEquals(Set.of(), minusDropped(declaredCreatedTables(created), declaredDropped(dropped)));
        // 注释掉的 DROP 不生效（05_drop_unused_tables.sql 整份都是注释掉的候选清单，
        // 按字符匹配会把 shopping_cart / audit_log 等真实存在的表误判成已删除）
        assertEquals(Set.of(), declaredDropped("-- DROP TABLE IF EXISTS shopping_cart;"));
        // 反引号包裹的也认
        assertTrue(declaredCreatedTables("create table `kb_chunk` (...)").contains("kb_chunk"));
    }

    @Test
    @DisplayName("「待落地表」与结构基线必须互斥（已落地的表不该再被当作待落地）")
    void pendingTablesNeverOverlapBaseline() throws IOException {
        Set<String> existing = loadExistingTables();
        Set<String> pending = loadPendingMigrationTables(existing);
        Set<String> overlap = new TreeSet<>(pending);
        overlap.retainAll(existing);
        assertTrue(overlap.isEmpty(),
                "以下表既在结构基线里、又被判为「待落地」—— 说明识别逻辑失效: " + overlap);
    }

    @Test
    @DisplayName("已修复：drug / drug_subscription 不再被任何 Mapper 引用")
    void drugTablesNoLongerReferenced() throws IOException {
        Set<String> refs = new TreeSet<>();
        for (Path xml : mapperXmlFiles()) {
            refs.addAll(extractReferencedTables(Files.readString(xml, StandardCharsets.UTF_8)));
        }
        assertFalse(refs.contains("drug"),
                "drug 表已于 2026-08-28 删除（商品并入 mall_product），不应再出现在 SQL 的表位");
        assertFalse(refs.contains("drug_subscription"),
                "drug_subscription 表已删除，订阅能力应复用 shopping_cart");
    }

    @Test
    @DisplayName("提取逻辑自检：能识别表位，且不把字符串字面量误判为表")
    void extractorIgnoresStringLiterals() {
        // product_type='drug' 里的 drug 是字符串常量，不是表名
        String sql = "SELECT p.id FROM mall_product p WHERE p.product_type = 'drug' AND p.status = 1";
        Set<String> tables = extractReferencedTables(sql);
        assertTrue(tables.contains("mall_product"), "应识别出真实表: " + tables);
        assertFalse(tables.contains("drug"), "不应把字符串字面量 'drug' 当成表名");
    }

    @Test
    @DisplayName("提取逻辑自检：能识别 JOIN / UPDATE / INTO")
    void extractorCoversKeywords() {
        Set<String> t = extractReferencedTables(
                "UPDATE mall_product SET name=? WHERE id=?; "
                        + "INSERT INTO shopping_cart (user_id) VALUES (?); "
                        + "SELECT * FROM user_health uh JOIN health_model_config hmc ON hmc.id=uh.health_model_config_id");
        assertTrue(t.contains("mall_product"), t.toString());
        assertTrue(t.contains("shopping_cart"), t.toString());
        assertTrue(t.contains("user_health"), t.toString());
        assertTrue(t.contains("health_model_config"), t.toString());
    }

    // ==================== 列名校验（2026-10-12 新增） ====================

    /**
     * <b>为什么表名校验不够</b>
     *
     * <p>上面那条"表必须存在"的测试只覆盖了问题域的一半。2026-10-12 排查发现
     * {@code NotificationMapper} 写入 {@code related_id}、{@code NewsMapper} 写入
     * {@code update_time}，两列在库中<b>都不存在</b>：
     * <ul>
     *   <li>{@code related_id} —— 建库与迁移脚本里都没有，{@code INSERT} 必抛
     *       {@code Unknown column}，导致通知写入链路整条失效（自 2026-08-26 起零写入）。</li>
     *   <li>{@code update_time} —— {@code news} 表的真实列名是 {@code updated_at}，
     *       帖子发布/编辑/下架全部不可用。</li>
     * </ul>
     *
     * <p>而"表存在"测试对这两处<b>完全无感</b>——它压根不看列。本测试补上这半边：
     * 把 Mapper 里出现的列名与结构基线文档逐一比对。
     *
     * <p><b>为什么只能校验"可归属的列"</b>：{@code SELECT} 里的裸列名无法确定属于哪张表
     * （多表 JOIN 时尤其如此），强行归表会制造大量误报。故本测试只检查三类
     * <b>归属明确</b>的列引用：
     * <ol>
     *   <li>{@code 别名.列} —— 别名能唯一（或多候选）定位到表</li>
     *   <li>{@code INSERT INTO t (列, 列...)} 的列清单</li>
     *   <li>{@code UPDATE t SET 列 = ...} 的赋值左值</li>
     * </ol>
     * 这三类恰好是<b>写入路径</b>——历史缺陷也全落在这三类上。
     *
     * <p>别名被复用时（如 {@code AppointmentMapper} 里 {@code d} 既指 {@code department}
     * 又指 {@code hospital_doctor}），采取保守策略：<b>所有候选表都缺该列</b>才报，
     * 宁可漏报也不误报。
     */
    @Test
    @DisplayName("🔴 所有 Mapper XML 引用的列都必须真实存在（写入路径）")
    void everyReferencedColumnMustExist() throws IOException {
        Map<String, Set<String>> schema = loadExistingColumns();
        assertFalse(schema.isEmpty(),
                "未能从结构基线文档读到任何列，请检查 Data/04_结构基线/ 是否存在");
        // 自检：解析器若退化（如文档格式变了），这里会先红，而不是静默放过所有列
        int totalCols = schema.values().stream().mapToInt(Set::size).sum();
        assertTrue(schema.size() >= 30 && totalCols >= 300,
                "结构基线解析异常：只解析出 " + schema.size() + " 张表 / " + totalCols
                        + " 个字段，疑似文档格式变更导致解析器失效");

        Map<String, List<String>> dangling = new TreeMap<>();
        for (Path xml : mapperXmlFiles()) {
            String content = Files.readString(xml, StandardCharsets.UTF_8);
            for (Map.Entry<String, String> e : findDanglingColumns(content, schema).entrySet()) {
                dangling.computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                        .add(xml.getFileName() + " ⇢ " + e.getValue());
            }
        }

        if (!dangling.isEmpty()) {
            StringBuilder msg = new StringBuilder("\n\n");
            msg.append("══════ Mapper 引用了不存在的列 ══════\n");
            msg.append("这些 SQL 一旦被调用就会抛 Unknown column。\n");
            msg.append("常见成因：① 列名拼错/记错（updated_at 写成 update_time）\n");
            msg.append("          ② 表合并/迁移后没同步改 Mapper（related_id 早已不存在）\n");
            msg.append("          ③ 抄来的样板代码从未落地\n\n");
            for (Map.Entry<String, List<String>> e : dangling.entrySet()) {
                msg.append(String.format("  ✗ %s%n", e.getKey()));
                for (String f : e.getValue()) {
                    msg.append(String.format("      └─ %s%n", f));
                }
            }
            msg.append("\n处置：① 改指向真实存在的列（先查结构基线文档确认列名）\n");
            msg.append("      ② 若该列确实新增，请重跑 Data/tools/gen_schema_doc.py 更新基线\n");
            msg.append("══════════════════════════════════════════\n");
            fail(msg.toString());
        }
    }

    @Test
    @DisplayName("列提取自检：能捕获历史上真实发生过的 related_id / update_time 缺陷")
    void columnCheckerCatchesPlantedBugs() throws IOException {
        Map<String, Set<String>> schema = loadExistingColumns();
        assertFalse(schema.isEmpty(), "结构基线为空，自检无法进行");

        // 缺陷 1：NotificationMapper 的 related_id（INSERT 列清单）
        String insertion = "<insert id=\"save\">INSERT INTO notification "
                + "(user_id, related_id) VALUES (#{userId}, #{relatedId})</insert>";
        assertTrue(findDanglingColumns(insertion, schema).containsKey("notification.related_id"),
                "未捕获 INSERT 列清单中的 related_id");

        // 缺陷 2：NewsMapper 的 update_time（INSERT 列清单）
        String newsInsert = "<insert id=\"insertPost\">INSERT INTO news "
                + "(title, update_time) VALUES (#{a}, #{b})</insert>";
        assertTrue(findDanglingColumns(newsInsert, schema).containsKey("news.update_time"),
                "未捕获 INSERT 列清单中的 update_time");

        // 缺陷 3：NewsMapper 的 update_time（UPDATE 赋值左值，无前导逗号）
        String newsUpdate = "<update id=\"deletePost\">UPDATE news SET status = 2, "
                + "update_time = NOW() WHERE id = 1</update>";
        assertTrue(findDanglingColumns(newsUpdate, schema).containsKey("news.update_time"),
                "未捕获 UPDATE 赋值左值中的 update_time");

        // 缺陷 4：别名限定列
        String aliased = "<select id=\"q\">SELECT u.user_account, u.role FROM user u</select>";
        assertTrue(findDanglingColumns(aliased, schema).containsKey("user.role"),
                "未捕获别名限定列 user.role（真实列名是 user_role）");

        // 正例：合法 SQL 不得误报
        String good = "<select id=\"ok\">SELECT n.id, n.title, n.updated_at FROM news n "
                + "WHERE n.status = 1</select>";
        Map<String, String> goodHits = findDanglingColumns(good, schema);
        assertTrue(goodHits.isEmpty(), "合法 SQL 被误报: " + goodHits);

        // 正例：子查询右值里的列属于别的表，不得当成外层表的列
        String sub = "<update id=\"u\">UPDATE news SET hot_score = "
                + "(SELECT MAX(view_count) FROM news) WHERE id = 1</update>";
        Map<String, String> subHits = findDanglingColumns(sub, schema);
        assertTrue(subHits.isEmpty(), "子查询右值被误报: " + subHits);
    }

    // ==================== 列提取实现 ====================

    /** 结构基线文档里的列行：{@code | `col` 或 | `col`**新增** } */
    private static final Pattern COLUMN_ROW = Pattern.compile("^\\|\\s*`([a-z_][a-z0-9_]*)`");

    /** {@code 别名.列} */
    private static final Pattern QUALIFIED_COL = Pattern.compile(
            "\\b([a-z_][a-z0-9_]*)\\.([a-z_][a-z0-9_]*)\\b", Pattern.CASE_INSENSITIVE);

    /** {@code INSERT INTO t (列清单)} */
    private static final Pattern INSERT_COLS = Pattern.compile(
            "(?is)\\bINSERT\\s+INTO\\s+`?([a-z_][a-z0-9_]*)`?\\s*\\(([^)]*)\\)");

    /** {@code UPDATE t ...} 到 {@code WHERE} 之间的赋值段 */
    private static final Pattern UPDATE_SEG = Pattern.compile(
            "(?is)\\bUPDATE\\s+`?([a-z_][a-z0-9_]*)`?(.{0,8000}?)(?:\\bWHERE\\b|$)");

    /** 赋值左值候选 */
    private static final Pattern ASSIGN = Pattern.compile(
            "([a-z_][a-z0-9_]*)\\s*=", Pattern.CASE_INSENSITIVE);

    /** 表名后紧跟的别名 */
    private static final Pattern ALIAS_AFTER = Pattern.compile(
            "(?i)\\s+(?:AS\\s+)?`?([a-z_][a-z0-9_]*)`?");

    private static final Pattern IDENT = Pattern.compile("[a-z_][a-z0-9_]*");

    /** SQL 关键字/函数名——出现在列位置时不应被当成列名 */
    private static final Set<String> SQL_KEYWORDS = new HashSet<>(Arrays.asList(
            "select", "from", "where", "and", "or", "not", "null", "is", "as", "on",
            "insert", "into", "values", "update", "set", "delete", "join", "left",
            "right", "inner", "outer", "group", "by", "order", "limit", "having",
            "case", "when", "then", "else", "end", "asc", "desc", "distinct", "union",
            "all", "in", "exists", "between", "like", "count", "sum", "max", "min",
            "avg", "if", "ifnull", "coalesce", "cast", "convert", "now", "curdate",
            "date", "date_format", "concat", "greatest", "least", "trim", "lower",
            "upper", "substring", "replace", "length", "key", "dual", "true", "false",
            "using", "duplicate", "interval", "str_to_date", "from_unixtime"
    ));

    /**
     * 从结构基线文档解析 表名 → 列集合。
     *
     * <p>基线文档由 {@code Data/tools/gen_schema_doc.py} 从 {@code information_schema} 生成，
     * 是项目声明的唯一权威结构来源，与"表存在性"测试用的是同一份。
     */
    private static Map<String, Set<String>> loadExistingColumns() throws IOException {
        Path base = locateRepoRoot().resolve(Paths.get("Data", "04_结构基线"));
        if (!Files.isDirectory(base)) {
            return Collections.emptyMap();
        }
        List<Path> docs;
        try (Stream<Path> s = Files.list(base)) {
            docs = s.filter(p -> p.getFileName().toString().startsWith("数据表结构基线")
                            && p.toString().endsWith(".md"))
                    .sorted(Comparator.comparing(Path::toString).reversed())
                    .toList();
        }
        if (docs.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Set<String>> schema = new LinkedHashMap<>();
        Set<String> current = null;
        for (String line : Files.readAllLines(docs.get(0), StandardCharsets.UTF_8)) {
            Matcher h = TABLE_HEADING.matcher(line);
            if (h.find()) {
                current = schema.computeIfAbsent(h.group(1), k -> new LinkedHashSet<>());
                continue;
            }
            if (current == null) {
                continue;
            }
            Matcher c = COLUMN_ROW.matcher(line);
            if (c.find()) {
                current.add(c.group(1));
            }
        }
        // 表标题下没解析出任何列的表剔掉，避免它作为"别名候选"把误报放大
        schema.values().removeIf(Set::isEmpty);
        return schema;
    }

    /**
     * 找出 Mapper 里引用了不存在列的地方。
     *
     * @return {@code "表.列"} → 出错上下文片段
     */
    static Map<String, String> findDanglingColumns(String rawXml, Map<String, Set<String>> schema) {
        String sql = cleanSql(rawXml);
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, Set<String>> aliases = buildAliasMap(sql, schema.keySet());

        // A) 别名限定列：所有候选表都没有该列才报（保守，避免别名复用误报）
        Matcher m = QUALIFIED_COL.matcher(sql);
        while (m.find()) {
            String alias = m.group(1).toLowerCase(Locale.ROOT);
            String col = m.group(2).toLowerCase(Locale.ROOT);
            Set<String> cands = aliases.get(alias);
            if (cands == null) {
                continue;   // 不是已知表的别名（information_schema / dto / t 等）
            }
            boolean found = false;
            for (String t : cands) {
                if (schema.get(t).contains(col)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                out.putIfAbsent(aliasLabel(cands) + "." + col, snippet(sql, m.start(), m.end()));
            }
        }

        // B) INSERT INTO t (列清单)
        Matcher ins = INSERT_COLS.matcher(sql);
        while (ins.find()) {
            String tbl = ins.group(1).toLowerCase(Locale.ROOT);
            Set<String> cols = schema.get(tbl);
            if (cols == null) {
                continue;
            }
            for (String tok : ins.group(2).split("[,\\s]+")) {
                String c = tok.replace("`", "").trim().toLowerCase(Locale.ROOT);
                if (c.isEmpty() || !IDENT.matcher(c).matches() || SQL_KEYWORDS.contains(c)) {
                    continue;
                }
                if (!cols.contains(c)) {
                    out.putIfAbsent(tbl + "." + c, snippet(sql, ins.start(), ins.end()));
                }
            }
        }

        // C) UPDATE t SET 列 = ...，只取括号深度 0 处的赋值左值
        Matcher up = UPDATE_SEG.matcher(sql);
        while (up.find()) {
            String tbl = up.group(1).toLowerCase(Locale.ROOT);
            Set<String> cols = schema.get(tbl);
            if (cols == null) {
                continue;
            }
            String seg = up.group(2);
            Matcher eq = ASSIGN.matcher(seg);
            while (eq.find()) {
                String c = eq.group(1).toLowerCase(Locale.ROOT);
                if (SQL_KEYWORDS.contains(c) || cols.contains(c)) {
                    continue;
                }
                if (parenDepthAt(seg, eq.start(1)) > 0) {
                    continue;   // 子查询/函数内部的列属于别的表
                }
                String prev = seg.substring(Math.max(0, eq.start(1) - 3), eq.start(1)).trim();
                if (prev.endsWith("=") || prev.endsWith("<") || prev.endsWith(">")
                        || prev.endsWith("!") || prev.endsWith("+") || prev.endsWith("-")
                        || prev.endsWith("/") || prev.endsWith("*") || prev.endsWith("%")) {
                    continue;   // 等号右侧或比较式，不是赋值左值
                }
                out.putIfAbsent(tbl + "." + c, snippet(seg, eq.start(), eq.end()));
            }
        }
        return out;
    }

    /**
     * 扫描 SQL 里 {@code FROM/JOIN/UPDATE/INTO} 后出现的表及其别名。
     *
     * <p>同一别名可能指向多张表（不同语句各用一次，或历史的别名滥用），
     * 故值是<b>候选表集合</b>而非单张表。
     */
    private static Map<String, Set<String>> buildAliasMap(String sql, Set<String> knownTables) {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        Matcher m = TABLE_REF.matcher(sql);
        while (m.find()) {
            String tbl = m.group(1).toLowerCase(Locale.ROOT);
            if (WHITELIST.contains(tbl) || !knownTables.contains(tbl)) {
                continue;
            }
            int start = m.start(1);
            if (start > 0 && sql.charAt(start - 1) == '.') {
                continue;   // information_schema.tables 之类
            }
            map.computeIfAbsent(tbl, k -> new LinkedHashSet<>()).add(tbl);
            Matcher a = ALIAS_AFTER.matcher(sql);
            a.region(m.end(), sql.length());
            if (a.lookingAt()) {
                String alias = a.group(1).toLowerCase(Locale.ROOT);
                if (!SQL_KEYWORDS.contains(alias) && !WHITELIST.contains(alias)) {
                    map.computeIfAbsent(alias, k -> new LinkedHashSet<>()).add(tbl);
                }
            }
        }
        return map;
    }

    private static String aliasLabel(Set<String> cands) {
        return cands.size() == 1 ? cands.iterator().next() : String.join("/", cands);
    }

    private static int parenDepthAt(String s, int idx) {
        int depth = 0;
        for (int i = 0; i < idx; i++) {
            char ch = s.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth = Math.max(0, depth - 1);
            }
        }
        return depth;
    }

    private static String snippet(String s, int from, int to) {
        int a = Math.max(0, from - 60);
        int b = Math.min(s.length(), to + 40);
        return s.substring(a, b).replaceAll("\\s+", " ").trim();
    }

    // ==================== 辅助 ====================

    private static List<Path> mapperXmlFiles() throws IOException {
        Path dir = Paths.get("src", "main", "resources", "mapper");
        assertTrue(Files.isDirectory(dir), "找不到 mapper 目录: " + dir.toAbsolutePath());
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".xml")).sorted().toList();
        }
    }

    // ==================== 待落地表识别 ====================

    /** {@code CREATE TABLE [IF NOT EXISTS] t} */
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?i)\\bCREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([a-z_][a-z0-9_]*)`?");

    /** {@code DROP TABLE [IF EXISTS] t} */
    private static final Pattern DROP_TABLE = Pattern.compile(
            "(?i)\\bDROP\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?`?([a-z_][a-z0-9_]*)`?");

    /**
     * 「已写好迁移脚本、但这份结构基线里还没有」的表。
     *
     * <p><b>为什么需要这个概念</b>：结构基线文档由 {@code gen_schema_doc.py} 从
     * {@code information_schema} 生成，反映的是<b>本机数据库的实际结构</b>。
     * 而新表的落地顺序天然是「先写迁移脚本 → 再执行 → 再重新生成基线」。
     * 处在中间状态时，Mapper 引用了一张<b>已经声明</b>但还没执行建表的表 ——
     * 这不是「悬空引用」（本测试要抓的缺陷），而是「待落地」。若不加区分，
     * 每加一张新表都会先把测试搞红，逼着人把基线文档手改成与真实库不符的样子，
     * 而这恰好就是本测试最初要防的「纸面能力」。</p>
     *
     * <p><b>判定条件（两个都满足才算）</b>：
     * <ol>
     *   <li>某份 {@code Data/02_迁移脚本/*.sql} 里有一条 {@code CREATE TABLE} 声明它；</li>
     *   <li>结构基线文档里<b>没有</b>它。</li>
     * </ol>
     * 脚本里若同时有该表的 {@code DROP TABLE}，则相互抵消（先建后删的废弃表
     * 不该被永久放行）。</p>
     *
     * <p><b>为什么只认 {@code 02_迁移脚本} 这一个目录</b>：这个目录的语义是
     * 「待应用到目标库的结构变更」。历史归档（{@code 90_归档/}）刻意不扫描 ——
     * 那里的 {@code sensitive_word_未落地_20261012.sql} 正是「从未落地的纸面能力」，
     * 把它算作待落地就等于把本测试最初抓到的缺陷重新放行。</p>
     *
     * @param existing 结构基线里的表；函数保证返回值与它无交集
     */
    static Set<String> loadPendingMigrationTables(Set<String> existing) throws IOException {
        Path dir = locateRepoRoot().resolve(Paths.get("Data", "02_迁移脚本"));
        if (!Files.isDirectory(dir)) {
            return Collections.emptySet();
        }
        Set<String> created = new TreeSet<>();
        Set<String> dropped = new TreeSet<>();
        try (Stream<Path> s = Files.list(dir)) {
            for (Path sql : s.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                String text = Files.readString(sql, StandardCharsets.UTF_8);
                created.addAll(declaredCreatedTables(text));
                dropped.addAll(declaredDropped(text));
            }
        }
        Set<String> pending = minusDropped(created, dropped);
        pending.removeAll(existing);
        return pending;
    }

    /**
     * 提取 {@code CREATE TABLE} 声明的表名。
     *
     * <p>内部先剥注释 —— 剥注释是<b>提取正确性的一部分</b>，不是调用方的义务：
     * 让调用方记得先剥，等于给这个函数埋一个「传原文就得到错答案」的坑，
     * 而错答案的表现恰好是「默默把该放行的表放行/该拦的拦不住」，没人会注意。</p>
     */
    static Set<String> declaredCreatedTables(String sql) {
        return collect(CREATE_TABLE, stripSqlComments(sql == null ? "" : sql));
    }

    /** 提取 {@code DROP TABLE} 声明的表名（同样内部剥注释）。 */
    static Set<String> declaredDropped(String sql) {
        return collect(DROP_TABLE, stripSqlComments(sql == null ? "" : sql));
    }

    static Set<String> minusDropped(Set<String> created, Set<String> dropped) {
        Set<String> out = new TreeSet<>(created);
        out.removeAll(dropped);
        return out;
    }

    private static Set<String> collect(Pattern p, String text) {
        Set<String> out = new TreeSet<>();
        if (text == null) {
            return out;
        }
        Matcher m = p.matcher(text);
        while (m.find()) {
            out.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /**
     * 剥离 SQL 注释。
     *
     * <p>必须做：{@code 05_drop_unused_tables.sql} 整份文件都是<b>注释掉的</b>
     * 候选删除清单（{@code -- DROP TABLE IF EXISTS shopping_cart; ...}）。
     * 不剥注释就会把 {@code shopping_cart} / {@code audit_log} 等<b>真实存在</b>的表
     * 当成已删除，从而把它们从「待落地」里错误地剔除或计入。
     * 行注释（双连字符）与块注释都要处理。</p>
     */
    private static String stripSqlComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ")
                  .replaceAll("--[^\\n]*", " ");
    }

    /**
     * 从结构基线文档提取全部表名。取最新的一份文件。
     */
    private static Set<String> loadExistingTables() throws IOException {
        Path base = locateRepoRoot().resolve(Paths.get("Data", "04_结构基线"));
        if (!Files.isDirectory(base)) {
            return Collections.emptySet();
        }
        List<Path> docs;
        try (Stream<Path> s = Files.list(base)) {
            docs = s.filter(p -> p.getFileName().toString().startsWith("数据表结构基线")
                            && p.toString().endsWith(".md"))
                    .sorted(Comparator.comparing(Path::toString).reversed())
                    .toList();
        }
        if (docs.isEmpty()) {
            return Collections.emptySet();
        }
        String text = Files.readString(docs.get(0), StandardCharsets.UTF_8);
        Set<String> tables = new LinkedHashSet<>();
        Matcher m = TABLE_HEADING.matcher(text);
        while (m.find()) {
            tables.add(m.group(1));
        }
        return tables;
    }

    /**
     * SQL 清洗：剥离 XML 注释 / XML 标签 / SQL 行注释 / 字符串字面量，
     * 并剔除 {@code ON DUPLICATE KEY UPDATE}（其后跟的是列名，不是表名）。
     *
     * <p>表名校验与列名校验共用本方法，保证两者看到的 SQL 完全一致。
     *
     * <p>🔴 必须先剥掉 XML 标签，否则 MyBatis 的 {@code <update id="xxx">} 在清空属性值后
     * 会变成 {@code "<update id="">"}，被误当成 SQL 的 {@code "UPDATE id"} → {@code id}
     * 被误报成不存在的表。MyBatis 的动态标签属性本就会被下面清空，SQL 里的 {@code <}
     * 一律转义写成 {@code &lt;}，故此处整段删除标签是安全的。
     *
     * <p>🔴 {@code ON DUPLICATE KEY UPDATE gender=#{gender}} —— UPDATE 后面跟的是"列名"
     * 不是"表名"，不剔除会把 {@code reason} / {@code quantity} / {@code gender}
     * 等字段误报成不存在的表。
     */
    private static String cleanSql(String rawXml) {
        String s = rawXml;
        // 去掉 XML 注释
        s = s.replaceAll("(?s)<!--.*?-->", " ");
        // 去掉 XML 标签（连带属性，故 test="..." 里的 EL 表达式一并消失）
        s = s.replaceAll("(?s)<[^>]+>", " ");
        // 去掉 SQL 行注释
        s = s.replaceAll("--[^\\n]*", " ");
        // 字符串字面量置空，防止 'drug' 被当成表名
        s = s.replaceAll("'[^']*'", "''");
        s = s.replaceAll("\"[^\"]*\"", "\"\"");
        s = s.replaceAll("(?i)ON\\s+DUPLICATE\\s+KEY\\s+UPDATE", " ");
        return s;
    }

    /** 从 SQL 文本提取表位上的表名（先剥离字符串字面量与注释，避免误判） */
    private static Set<String> extractReferencedTables(String rawXml) {
        String s = cleanSql(rawXml);

        Set<String> out = new LinkedHashSet<>();
        Matcher m = TABLE_REF.matcher(s);
        while (m.find()) {
            String name = m.group(1).toLowerCase(Locale.ROOT);
            // 排除紧跟在 . 之后的（如 information_schema.tables 的列名场景）
            int start = m.start(1);
            if (start > 0 && s.charAt(start - 1) == '.') {
                continue;
            }
            out.add(name);
        }
        return out;
    }

    /** 从当前工作目录逐级上溯定位仓库根目录（含 Data/04_结构基线 的那一级） */
    private static Path locateRepoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5 && p != null; i++) {
            if (Files.isDirectory(p.resolve(Paths.get("Data", "04_结构基线")))) {
                return p;
            }
            p = p.getParent();
        }
        return Paths.get("").toAbsolutePath();
    }
}
