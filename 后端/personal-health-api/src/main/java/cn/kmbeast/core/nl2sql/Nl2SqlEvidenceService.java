package cn.kmbeast.core.nl2sql;

import cn.kmbeast.config.Nl2SqlReadOnlyDataSourceConfig;
import cn.kmbeast.crm.agent.tool.HealthMetric;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * 业务术语 Evidence：为 NL2SQL 的 SQL 生成 / 错误修复提供「指标字典 + 口语别名」。
 *
 * <p><b>要解决什么</b>：用户说「血压」「血脂」「BMI」，而 {@code health_model_config.name}
 * 里存的是「收缩压」「舒张压」「高密度脂蛋白」。模型若不知道这层映射，就只能猜列名或退回空结果
 * —— 而 {@code new-sql-generate.txt} 又明令「不得猜测相似列名、状态值或表关系」。
 * 本服务把映射关系直接喂给模型。</p>
 *
 * <p><b>为什么走 Evidence 而不是向量库</b>：{@code crm.rag} 的
 * {@code HybridRetriever} 以 {@code articleId} 为融合 key，且
 * {@code parseArticleId()} 会丢弃 id ≤ 0 的记录 —— 术语文档没有合法 articleId，
 * 灌进 {@code health_knowledge} 会被整条丢弃或互相覆盖。术语是<b>结构化小数据</b>，
 * 直接查库拼文本更可靠。</p>
 *
 * <p><b>注入位置（刻意只接 2 处）</b>：{@code new-sql-generate} 与 {@code sql-error-fixer}。
 * {@code mix-selector}（选表）与 {@code semantic-consistency}（语义校验）<b>不注入</b> ——
 * 选表阶段不需要术语，语义校验更不需要，两处都注入会让 token 翻倍。</p>
 *
 * <p>数据源走只读账号（本服务只做 SELECT），见
 * {@link Nl2SqlReadOnlyDataSourceConfig}；未配置 read-only.url 时回落主数据源。</p>
 */
@Slf4j
@Service
public class Nl2SqlEvidenceService {

    /** 指标字典极少变动，缓存 5 分钟足够，避免每次提问都查库。 */
    private static final long TTL_MS = 300_000L;

    /** Evidence 硬上限（字符）。超长会挤占 prompt 预算并推高成本。 */
    private static final int MAX_CHARS = 1200;

    private static final int MAX_ROWS = 20;

    @Resource(name = Nl2SqlReadOnlyDataSourceConfig.BEAN_NAME)
    private DataSource dataSource;

    private volatile String cache = "";
    private volatile long loadedAt = 0L;

    /**
     * 取术语 Evidence 文本（带缓存）。
     *
     * @return 术语说明文本；取不到时返回空串（退化为无 Evidence，不影响主流程）
     */
    public String evidence() {
        String snapshot = cache;
        if (snapshot != null && !snapshot.isEmpty()
                && System.currentTimeMillis() - loadedAt < TTL_MS) {
            return snapshot;
        }
        String loaded = load();
        cache = loaded;
        loadedAt = System.currentTimeMillis();
        return loaded;
    }

    private String load() {
        StringBuilder sb = new StringBuilder(512);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT name, unit, symbol, value_range FROM health_model_config "
                            + "WHERE is_global = 1 ORDER BY id LIMIT " + MAX_ROWS);
            if (!rows.isEmpty()) {
                sb.append("# 指标术语字典（来自 health_model_config，is_global=1）\n");
                sb.append("格式：指标名(符号, 单位, 正常范围) ｜ 用户口语别名\n");
                for (Map<String, Object> r : rows) {
                    String name = str(r.get("name"));
                    if (name.isEmpty()) {
                        continue;
                    }
                    String symbol = str(r.get("symbol"));
                    String unit = str(r.get("unit"));
                    String range = formatRange(str(r.get("value_range")));
                    StringBuilder line = new StringBuilder(name);
                    if (!symbol.isEmpty() || !unit.isEmpty() || !range.isEmpty()) {
                        line.append('(');
                        if (!symbol.isEmpty()) {
                            line.append(symbol);
                        }
                        if (!unit.isEmpty()) {
                            if (line.charAt(line.length() - 1) != '(') {
                                line.append(", ");
                            }
                            line.append(unit);
                        }
                        if (!range.isEmpty()) {
                            if (line.charAt(line.length() - 1) != '(') {
                                line.append(", ");
                            }
                            line.append("正常范围 ").append(range);
                        }
                        line.append(')');
                    }
                    sb.append("- ").append(line);
                    String alias = aliasHint(name);
                    if (!alias.isEmpty()) {
                        sb.append(" ｜ ").append(alias);
                    }
                    sb.append('\n');
                }
            }
        } catch (Exception e) {
            log.warn("[NL2SQL] 读取指标术语字典失败，Evidence 将不含术语: {}", e.getMessage());
        }

        // 口语别名 → 规范 db_name。与指标字典互补：即使 health_model_config 读不到，
        // 这部分也不依赖数据库。
        try {
            sb.append("# 口语 → health_model_config.name 对照（提问中出现左列时应映射到右列）\n");
            for (HealthMetric m : HealthMetric.values()) {
                sb.append("- ").append(String.join("/", m.aliases()))
                        .append(" → ").append(m.dbName()).append('\n');
            }
        } catch (Exception e) {
            log.warn("[NL2SQL] 追加指标别名失败: {}", e.getMessage());
        }

        String text = sb.toString().trim();
        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS) + "\n（术语已截断）";
        }
        return text;
    }

    /** 该指标的口语别名（去掉与 dbName 重复的那一个）。 */
    private static String aliasHint(String dbName) {
        for (HealthMetric m : HealthMetric.values()) {
            if (m.dbName().equalsIgnoreCase(dbName)) {
                StringBuilder sb = new StringBuilder();
                for (String a : m.aliases()) {
                    if (a.equalsIgnoreCase(dbName)) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append('/');
                    }
                    sb.append(a);
                }
                return sb.toString();
            }
        }
        return "";
    }

    /**
     * 归一化正常范围字符串 —— <b>必须兼容两种历史格式</b>。
     *
     * <p>{@code health_model_config.value_range} 存在两套互不兼容的写法：</p>
     * <ul>
     *   <li>老 5 条为逗号式：{@code "90,140"}</li>
     *   <li>新 7 条为 JSON 式：{@code {"min":0,"max":11.1}}</li>
     * </ul>
     *
     * <p>两者都要能读，否则模型会拿到一半空白的正常范围。统一输出为 {@code min~max}。</p>
     *
     * @param raw 原始字符串，可空
     * @return {@code "90~140"} 形式；无法解析时原样返回（宁可给原文也不给空）
     */
    static String formatRange(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        if (s.startsWith("{")) {
            try {
                JSONObject o = JSON.parseObject(s);
                Object mn = o.get("min");
                Object mx = o.get("max");
                if (mn != null && mx != null) {
                    return trimNum(mn) + "~" + trimNum(mx);
                }
            } catch (Exception ignored) {
                // 落到原样返回
            }
            return s;
        }
        int comma = s.indexOf(',');
        if (comma > 0) {
            return s.substring(0, comma).trim() + "~" + s.substring(comma + 1).trim();
        }
        return s;
    }

    /** 去掉 fastjson2 可能返回的整型尾巴（11.0 → 11）。 */
    private static String trimNum(Object num) {
        String s = String.valueOf(num);
        if (s.endsWith(".0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
