package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.crm.agent.tool.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolRegistry} 按 Agent 白名单过滤下发工具集的单测。
 *
 * <p><b>这里唯一真正难的地方是 null 与空集的区别</b>，而它恰好是最容易写错、
 * 写错后后果最奇怪的地方：</p>
 * <ul>
 *   <li>{@code null} = 这个 Agent 在 {@code agent_tool_binding} 里<b>一行都没有</b>。
 *       这是所有存量 Agent 的状态，必须下发<b>全量</b>工具。</li>
 *   <li>空集 = 绑过行但全被管理员停用了。这是「我明确要求它什么都不许用」，
 *       必须下发<b>零个</b>工具。</li>
 * </ul>
 *
 * <p>两者在表结构上都是「零个启用行」，只能靠查询方式区分。一旦有人图省事
 * 把 null 也当空集处理，升级上线的瞬间就是「所有 Agent 集体失去工具能力」——
 * 而因为模型仍然会回答（只是不再调工具），表现为「AI 好像变笨了」，
 * 不会有一条报错把矛头指向过滤逻辑。</p>
 */
@DisplayName("ToolRegistry 白名单过滤")
class ToolRegistryWhitelistTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        ReflectionTestUtils.setField(registry, "toolList", fakeTools("search_drug", "web_search", "execute_sql"));
        registry.init();
    }

    /** 三个够用的假工具：过滤逻辑只关心名字，不关心实现。 */
    private static List<Tool> fakeTools(String... names) {
        List<Tool> list = new ArrayList<>();
        for (String name : names) {
            list.add(new Tool() {
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public String getDescription() {
                    return name + " 的描述";
                }

                @Override
                public Map<String, Object> getParametersSchema() {
                    Map<String, Object> schema = new LinkedHashMap<>();
                    schema.put("type", "object");
                    schema.put("properties", new LinkedHashMap<String, Object>());
                    return schema;
                }

                @Override
                public ToolResult execute(Map<String, Object> arguments) {
                    return ToolResult.ok("ok");
                }
            });
        }
        return list;
    }

    private static List<String> namesOf(List<Map<String, Object>> toolsArray) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> def : toolsArray) {
            @SuppressWarnings("unchecked")
            Map<String, Object> fn = (Map<String, Object>) def.get("function");
            names.add((String) fn.get("name"));
        }
        return names;
    }

    // ==================== 三态 ====================

    @Test
    @DisplayName("T1 null（无绑定行）= 全量下发，这是存量 Agent 的默认状态")
    void nullMeansFullSet() {
        List<Map<String, Object>> tools = registry.buildToolsArray((Set<String>) null, false);
        assertEquals(List.of("search_drug", "web_search", "execute_sql"), namesOf(tools),
                "把 null 当空集处理 = 升级后所有 Agent 集体失去工具，且没有任何报错");
    }

    @Test
    @DisplayName("T2 空集（绑了但全停用）= 一个都不给，与 T1 语义相反")
    void emptySetMeansNothing() {
        List<Map<String, Object>> tools = registry.buildToolsArray(Set.of(), false);
        assertTrue(tools.isEmpty(),
                "空集必须是「真的一个都不给」。若与 null 合并，管理员把工具全禁了反而变成全开");
    }

    @Test
    @DisplayName("T3 子集 = 只给列出的那几个")
    void subsetIsHonored() {
        List<Map<String, Object>> tools = registry.buildToolsArray(Set.of("web_search"), false);
        assertEquals(List.of("web_search"), namesOf(tools));
    }

    @Test
    @DisplayName("T4 白名单里的陌生名字被忽略，但不影响其余合法项（不静默清空）")
    void unknownNamesAreIgnoredNotFatal() {
        List<Map<String, Object>> tools =
                registry.buildToolsArray(Set.of("search_drug", "tool_that_was_deleted", "typo_here"), false);
        assertEquals(List.of("search_drug"), namesOf(tools),
                "改过名的工具条目应该只少这一个，不能把整个白名单判废");
    }

    @Test
    @DisplayName("T5 白名单全是陌生名字 → 空数组（而不是回退成全量）")
    void allUnknownMeansEmptyNotNull() {
        List<Map<String, Object>> tools = registry.buildToolsArray(Set.of("no_such_tool"), false);
        assertTrue(tools.isEmpty(),
                "配置写错了就诚实地下发零个工具，退回全量会掩盖这个配置错误");
    }

    // ==================== 顺序与结构 ====================

    @Test
    @DisplayName("T6 下发顺序恒为注册顺序，与白名单的迭代顺序无关")
    void orderFollowsRegistration() {
        // 白名单故意用与注册顺序相反的书写顺序
        List<Map<String, Object>> tools =
                registry.buildToolsArray(Set.of("execute_sql", "search_drug"), false);
        assertEquals(List.of("search_drug", "execute_sql"), namesOf(tools),
                "工具顺序影响 prompt 前缀稳定性（缓存命中），不能被一个 Set 的迭代顺序带偏");
    }

    @Test
    @DisplayName("T7 schema 补 additionalProperties:false，且是幂等的")
    void schemaIsHardenedIdempotently() {
        List<Map<String, Object>> first = registry.buildToolsArray((Set<String>) null, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>)
                ((Map<String, Object>) first.get(0).get("function")).get("parameters");
        assertEquals(false, schema.get("additionalProperties"),
                "不补这条，模型会发明 schema 里没声明的入参");

        // 再跑一次：不应因为重复调用而出问题，也不应把已有值改掉
        List<Map<String, Object>> second = registry.buildToolsArray((Set<String>) null, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> again = (Map<String, Object>)
                ((Map<String, Object>) second.get(0).get("function")).get("parameters");
        assertEquals(false, again.get("additionalProperties"));
    }

    @Test
    @DisplayName("T8 toolStrict=true 时才下发 strict，默认不带（国产网关对它有兼容问题）")
    void strictOnlyWhenAsked() {
        List<Map<String, Object>> plain = registry.buildToolsArray((Set<String>) null, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> plainFn = (Map<String, Object>) plain.get(0).get("function");
        assertFalse(plainFn.containsKey("strict"),
                "默认关闭：strict 字段在部分 OpenAI 兼容实现上直接 400");

        List<Map<String, Object>> strict = registry.buildToolsArray((Set<String>) null, true);
        @SuppressWarnings("unchecked")
        Map<String, Object> strictFn = (Map<String, Object>) strict.get(0).get("function");
        assertEquals(true, strictFn.get("strict"));
    }

    @Test
    @DisplayName("T9 三层结构齐全：type:function → {name, description, parameters}")
    void shapeMatchesOpenAiSpec() {
        List<Map<String, Object>> tools = registry.buildToolsArray((Set<String>) null, false);
        Map<String, Object> def = tools.get(0);
        assertEquals("function", def.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fn = (Map<String, Object>) def.get("function");
        assertEquals("search_drug", fn.get("name"));
        assertNotNull(fn.get("description"));
        assertNotNull(fn.get("parameters"));
    }

    @Test
    @DisplayName("T10 get/getAll 反映注册表本身，且与白名单无关（白名单只影响下发）")
    void registryItselfIsUnfiltered() {
        assertNotNull(registry.get("search_drug"));
        assertNull(registry.get("no_such_tool"));
        assertEquals(3, registry.getAll().size());
        // 空白名单只是「本轮不给」，不是「把它从注册表删了」
        registry.buildToolsArray(Set.of(), false);
        assertNotNull(registry.get("search_drug"));
    }
}
