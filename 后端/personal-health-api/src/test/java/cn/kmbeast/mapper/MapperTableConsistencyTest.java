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

        Map<String, List<String>> dangling = new LinkedHashMap<>();

        for (Path xml : mapperXmlFiles()) {
            String content = Files.readString(xml, StandardCharsets.UTF_8);
            for (String table : extractReferencedTables(content)) {
                if (WHITELIST.contains(table) || existing.contains(table)) {
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
            msg.append("常见成因：① 删表后没同步改 Mapper  ② 抄来的样板代码从未落地\n\n");
            for (Map.Entry<String, List<String>> e : dangling.entrySet()) {
                msg.append(String.format("  ✗ 表 `%s`%n", e.getKey()));
                for (String f : e.getValue()) {
                    msg.append(String.format("      └─ %s%n", f));
                }
            }
            msg.append("\n处置：① 改指向真实存在的表（遵循强耦合原则，优先加列不建表）\n");
            msg.append("      ② 若为从未落地的死代码，删除对应 Mapper + XML + Service\n");
            msg.append("      ③ 若此表确实新建了，请重跑 Data/tools/gen_schema_doc.py 更新基线\n");
            msg.append("══════════════════════════════════════════\n");
            fail(msg.toString());
        }
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

    // ==================== 辅助 ====================

    private static List<Path> mapperXmlFiles() throws IOException {
        Path dir = Paths.get("src", "main", "resources", "mapper");
        assertTrue(Files.isDirectory(dir), "找不到 mapper 目录: " + dir.toAbsolutePath());
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".xml")).sorted().toList();
        }
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

    /** 从 SQL 文本提取表位上的表名（先剥离字符串字面量与注释，避免误判） */
    private static Set<String> extractReferencedTables(String rawXml) {
        String s = rawXml;
        // 去掉 XML 注释
        s = s.replaceAll("(?s)<!--.*?-->", " ");
        // 🔴 必须先剥掉 XML 标签，否则 MyBatis 的 <update id="xxx"> 在下面清空属性值后
        //    会变成 "<update id="">"，被误当成 SQL 的 "UPDATE id" → id 被误报成不存在的表。
        //    MyBatis 的动态标签属性本就会被下面清空，SQL 里的 < 一律转义写成 &lt;，
    //    故此处整段删除标签是安全的。
        s = s.replaceAll("(?s)<[^>]+>", " ");
        // 去掉 SQL 行注释
        s = s.replaceAll("--[^\\n]*", " ");
        // 字符串字面量置空，防止 'drug' 被当成表名
        s = s.replaceAll("'[^']*'", "''");
        s = s.replaceAll("\"[^\"]*\"", "\"\"");
        // 🔴 ON DUPLICATE KEY UPDATE gender=#{gender} —— UPDATE 后面跟的是"列名"不是"表名"，
        //    不剔除会把 reason / quantity / gender 等字段误报成不存在的表。
        s = s.replaceAll("(?i)ON\\s+DUPLICATE\\s+KEY\\s+UPDATE", " ");

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
