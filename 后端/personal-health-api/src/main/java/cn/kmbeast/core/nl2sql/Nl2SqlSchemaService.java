package cn.kmbeast.core.nl2sql;

import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.TableMeta;
import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.TableSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 阶段 4（DataAgent 资产摘取）· Schema 组装服务。
 *
 * <p>移植自 DataAgent {@code SchemaServiceImpl}(521) + {@code TableMetadataService}(310)，
 * <b>丢弃</b> 其 {@code AgentVectorStoreService}/{@code DynamicFilterService}/{@code Document}/
 * {@code FilterExpressionBuilder}（Spring AI 依赖主要来源），只保留「从库内读取 schema 并组装」。</p>
 *
 * <p>安全边界：敏感表 {@link #TABLE_BLACKLIST} 永不被召回，从源头避免 LLM 触达鉴权/配置/审计数据。</p>
 */
@Slf4j
@Component
public class Nl2SqlSchemaService {

    /**
     * 永不允许 NL2SQL 触及的表（对应迁移方案 §六 表范围黑名单）。
     *
     * <p><b>唯一真源</b>：此前本表与 {@code MySqlAiSqlGuard.TABLE_BLACKLIST} 各写一份，
     * 改一边忘另一边就会出现「schema 允许召回、守卫却拦死」的诡异不一致。现统一由本处定义，
     * {@code MySqlAiSqlGuard} 直接引用。改动必须两处同步，现已通过引用消除该风险。</p>
     */
    public static final Set<String> TABLE_BLACKLIST = new HashSet<>(Arrays.asList(
            "user", "role", "permission", "system_config", "audit_log"));

    @Resource
    private JdbcSchemaIntrospector introspector;

    @Resource
    private Nl2SqlProperties properties;

    /** 当前库所有「白名单通过」的表（去除黑名单），用于表选择上下文。 */
    public List<TableMeta> listAllowedTables() {
        return introspector.listTables().stream()
                .filter(t -> !TABLE_BLACKLIST.contains(t.getName().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    /**
     * 将 LLM 选出的表名收敛为可内省的表（去重 + 小写 + 去除黑名单 + 去空）。
     */
    public List<String> sanitizeSelectedTables(List<String> selected) {
        if (selected == null) {
            return new ArrayList<>();
        }
        Set<String> cleaned = new HashSet<>();
        for (String t : selected) {
            if (t == null) {
                continue;
            }
            String lower = t.trim().toLowerCase(Locale.ROOT);
            if (lower.isEmpty() || TABLE_BLACKLIST.contains(lower)) {
                continue;
            }
            cleaned.add(lower);
        }
        return new ArrayList<>(cleaned);
    }

    /** 内省指定表（已过滤），返回 schema 列表。 */
    public List<TableSchema> loadSchema(List<String> tableNames) {
        List<String> allowed = sanitizeSelectedTables(tableNames);
        return introspector.introspect(allowed, properties.getMaxColumnsPerTable());
    }

    public static boolean isBlacklisted(String tableName) {
        return tableName != null && TABLE_BLACKLIST.contains(tableName.toLowerCase(Locale.ROOT));
    }
}
