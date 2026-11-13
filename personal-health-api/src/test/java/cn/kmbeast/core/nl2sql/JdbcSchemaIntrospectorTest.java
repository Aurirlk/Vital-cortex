package cn.kmbeast.core.nl2sql;

import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.ColumnSchema;
import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.ForeignKey;
import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.TableSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外键关系推断测试（纯逻辑，不连库）。
 *
 * <p>背景：本库【实库外键数 = 0】，{@code information_schema.KEY_COLUMN_USAGE} 恒返回空，
 * 若不做推断，{@code buildSchemaText} 的【Foreign keys】段永远为空、模型只能单表查询。
 *
 * <p>这里锁死三条最重要的边界：
 * <ol>
 *   <li>命名约定 {@code x_id → x} 能推断出来（本库最高价值的一条：
 *       {@code user_health.health_model_config_id → health_model_config.id}）；</li>
 *   <li>【黑名单表绝不能被推断为目标】—— JOIN 到 {@code user} 会被
 *       {@code MySqlAiSqlGuard} 整条拦死，等于功能不可用；</li>
 *   <li>真实外键优先，不被推断覆盖。</li>
 * </ol>
 */
class JdbcSchemaIntrospectorTest {

    private static TableSchema table(String name, ColumnSchema... cols) {
        return new TableSchema(name, "", List.of(cols));
    }

    private static ColumnSchema col(String name) {
        return new ColumnSchema(name, "", "int", false, false);
    }

    /** 每张表都放一个 id 列，模拟真实主键。 */
    private static TableSchema tableWithId(String name, ColumnSchema... extra) {
        ColumnSchema[] all = new ColumnSchema[extra.length + 1];
        all[0] = new ColumnSchema("id", "", "int", true, false);
        System.arraycopy(extra, 0, all, 1, extra.length);
        return table(name, all);
    }

    @Test
    @DisplayName("命名约定推断：user_health.health_model_config_id → health_model_config.id")
    void infersForeignKeyByNamingConvention() {
        TableSchema target = tableWithId("health_model_config");
        TableSchema src = tableWithId("user_health", col("user_id"), col("health_model_config_id"));

        Map<String, TableSchema> byName = new LinkedHashMap<>();
        byName.put("health_model_config", target);
        byName.put("user_health", src);

        JdbcSchemaIntrospector.inferRelations(byName);

        assertEquals(1, src.getForeignKeys().size(), "只应推断出到 health_model_config 的这一条");
        assertEquals("user_health.health_model_config_id -> health_model_config.id",
                src.getForeignKeys().get(0).toString());
    }

    @Test
    @DisplayName("黑名单表绝不作为推断目标（否则 JOIN 会被词法守卫整条拦死）")
    void neverInfersToBlacklistedTable() {
        // user 表真实存在且有 id 列 —— 若不做黑名单过滤，就会被推断出来
        TableSchema user = tableWithId("user");
        TableSchema src = tableWithId("user_health", col("user_id"));

        Map<String, TableSchema> byName = new LinkedHashMap<>();
        byName.put("user", user);
        byName.put("user_health", src);

        JdbcSchemaIntrospector.inferRelations(byName);

        assertTrue(src.getForeignKeys().isEmpty(),
                "user 属于 TABLE_BLACKLIST，绝不能被推断为 JOIN 目标");
        assertTrue(Nl2SqlSchemaService.isBlacklisted("user"));
    }

    @Test
    @DisplayName("真实外键优先，不被命名约定推断覆盖")
    void realForeignKeyIsNotOverwritten() {
        TableSchema target = tableWithId("health_model_config");
        TableSchema src = tableWithId("user_health", col("health_model_config_id"));
        // 预置一条"真外键"（指向别的表），推断不得覆盖它
        src.getForeignKeys().add(new ForeignKey("user_health", "health_model_config_id",
                "other_table", "id"));

        Map<String, TableSchema> byName = new LinkedHashMap<>();
        byName.put("health_model_config", target);
        byName.put("user_health", src);

        JdbcSchemaIntrospector.inferRelations(byName);

        assertEquals(1, src.getForeignKeys().size(), "同一列不应产生第二条外键");
        assertEquals("other_table", src.getForeignKeys().get(0).getReferencedTable());
    }

    @Test
    @DisplayName("人工提示：visit_record.doctor_id → hospital_doctor（命名约定推断不出）")
    void infersFromRelationHint() {
        TableSchema doctor = tableWithId("hospital_doctor");
        // 注意：库里没有名为 doctor 的表，纯命名约定只能推出 doctor / doctors / doctor_info
        TableSchema src = tableWithId("visit_record", col("doctor_id"));

        Map<String, TableSchema> byName = new LinkedHashMap<>();
        byName.put("hospital_doctor", doctor);
        byName.put("visit_record", src);

        JdbcSchemaIntrospector.inferRelations(byName);

        assertEquals(1, src.getForeignKeys().size());
        assertEquals("hospital_doctor", src.getForeignKeys().get(0).getReferencedTable());
    }

    @Test
    @DisplayName("候选表不存在时不推断（避免产出指向空气的关系）")
    void noInferenceWhenTargetMissing() {
        TableSchema src = tableWithId("mall_order", col("shipping_address_id"));

        Map<String, TableSchema> byName = new LinkedHashMap<>();
        byName.put("mall_order", src); // shipping_address 不在本批 schema 中

        JdbcSchemaIntrospector.inferRelations(byName);

        assertTrue(src.getForeignKeys().isEmpty());
    }

    @Test
    @DisplayName("自引用不产生（表的 x_id 指向自己）")
    void noSelfReference() {
        TableSchema src = tableWithId("order_item", col("order_item_id"));

        Map<String, TableSchema> byName = new LinkedHashMap<>();
        byName.put("order_item", src);

        JdbcSchemaIntrospector.inferRelations(byName);

        assertTrue(src.getForeignKeys().isEmpty(), "不应推断出自己 JOIN 自己");
    }

    @Test
    @DisplayName("空输入 / null 安全")
    void handlesNullOrEmpty() {
        JdbcSchemaIntrospector.inferRelations(null);
        JdbcSchemaIntrospector.inferRelations(new LinkedHashMap<>());
        // 不抛异常即通过
        assertNotNull(JdbcSchemaIntrospector.class);
    }
}
