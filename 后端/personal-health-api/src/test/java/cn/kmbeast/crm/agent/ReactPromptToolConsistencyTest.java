package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.tool.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T7：ReAct 提示词外置与「工具名一致性校验」。
 *
 * <p><b>这条测试对应一个已经发生的真实缺陷</b>：8 个工具里的
 * {@code execute_sql} 与 {@code web_search} 在硬编码 prompt 里<b>一个字都没提</b>，
 * 模型永远不会调用它们。这类缺陷不会报错、不会告警，
 * 表现为「功能好像上线了但从来没人用过」，<b>是最难自查的一类 bug</b>。
 * 所以需要一条在启动时就能报出「prompt 提到但没注册」的校验。</p>
 */
class ReactPromptToolConsistencyTest {

    private static ReactPromptLoader newLoader() {
        ReactPromptLoader loader = new ReactPromptLoader();
        loader.load();
        return loader;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Tool> toolsOf(ToolRegistry registry) {
        try {
            Field f = ToolRegistry.class.getDeclaredField("tools");
            f.setAccessible(true);
            return (Map<String, Tool>) f.get(registry);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 注册真实工具实例（T7.4 用：验证的是「类路径上真实存在的工具」而非手写名单）。 */
    private static void register(ToolRegistry registry, Tool tool) {
        toolsOf(registry).put(tool.getName(), tool);
    }

    /** 注册一个同名桩（T7.5 用：只关心名字集合，不需要工具行为）。 */
    private static void register(ToolRegistry registry, String name) {
        register(registry, new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "d"; }
            @Override public Map<String, Object> getParametersSchema() {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("type", "object");
                s.put("properties", new LinkedHashMap<>());
                return s;
            }
            @Override public cn.kmbeast.crm.agent.model.ToolResult execute(Map<String, Object> a) {
                return null;
            }
        });
    }

    @Test
    @DisplayName("T7.1 classpath 能读到 system.txt 与 6 个角色 prompt")
    void classpathPromptsLoadable() {
        ReactPromptLoader loader = newLoader();
        assertNotNull(loader.classpathSystemPrompt(),
                "🔴 读不到 classpath:ai-prompts/react/system.txt —— 提示词外置失效，会静默回落到 Java 常量");
        assertTrue(loader.classpathSystemPrompt().length() > 100);

        Map<String, String> roles = loader.classpathRolePrompts();
        assertEquals(6, roles.size(), "应加载 6 个角色 prompt，实际 " + roles.keySet());
        for (String r : ReactPromptLoader.knownRoles()) {
            assertNotNull(roles.get(r), "缺角色 prompt: " + r);
        }
    }

    @Test
    @DisplayName("T7.2 角色 prompt 与 Java 常量语义一致（外置没改坏内容）")
    void rolePromptsKeepSemantics() {
        ReactPromptLoader loader = newLoader();
        assertTrue(loader.classpathRolePrompts().get("doctor").contains("全科医生"));
        assertTrue(loader.classpathRolePrompts().get("analyst").contains("报告分析师"));
        assertTrue(loader.classpathRolePrompts().get("psychologist").contains("心理咨询师"));
        assertTrue(loader.classpathRolePrompts().get("nutritionist").contains("营养师"));
        assertTrue(loader.classpathRolePrompts().get("consultant").contains("健康助手"));
        assertTrue(loader.classpathRolePrompts().get("general_assistant").contains("全能助手"));
    }

    @Test
    @DisplayName("T7.3 工具全集全部在 system prompt 中被提及（含此前被漏掉的 execute_sql / web_search）")
    void allToolsMentionedInPrompt() {
        ReactPromptLoader loader = newLoader();
        String prompt = loader.classpathSystemPrompt();
        for (String t : ToolCatalog.ALL) {
            assertTrue(prompt.contains(t),
                    "🔴 system prompt 未提及工具 " + t + " —— 模型永远不会调用它（这是已发生过的真实缺陷）");
        }
    }

    @Test
    @DisplayName("T7.4 一致性校验：类路径扫描出的真实工具全部注册时 missing 为空")
    void consistencyPassesWhenAllRegistered() {
        ReactPromptLoader loader = newLoader();
        ToolRegistry registry = new ToolRegistry();
        // 注册真实 Tool 实现（而不是按名字造桩）：若某个工具类的 getName()
        // 与类路径清单不一致，这里就会以真实的注册名参与校验。
        for (Tool tool : ToolCatalog.instances()) {
            register(registry, tool);
        }
        loader.setToolRegistry(registry);

        ReactPromptLoader.ConsistencyReport report = loader.verifyToolConsistency();
        assertTrue(report.missing.isEmpty(),
                "🔴 提示词提到但未注册的工具: " + report.missing);
        assertEquals(ToolCatalog.size(), report.registryTools.size());
        assertTrue(report.declaredTools.contains("execute_sql"));
        assertTrue(report.declaredTools.contains("web_search"));
    }

    @Test
    @DisplayName("T7.5 一致性校验：缺注册时报出 missing（防「工具新增后忘改 prompt」）")
    void consistencyDetectsMissing() {
        ReactPromptLoader loader = newLoader();
        ToolRegistry registry = new ToolRegistry();
        // 只注册 3 个，故意漏掉 execute_sql / web_search 等
        register(registry, "search_drug");
        register(registry, "search_knowledge");
        register(registry, "get_health_data");
        loader.setToolRegistry(registry);

        ReactPromptLoader.ConsistencyReport report = loader.verifyToolConsistency();
        assertFalse(report.missing.isEmpty(), "缺注册时必须报出 missing");
        assertTrue(report.missing.contains("execute_sql"),
                "应识别出 execute_sql 缺失，实际: " + report.missing);
        assertTrue(report.missing.contains("query_health_records"));
    }

    @Test
    @DisplayName("T7.6 工具名抽取不误伤普通英文/非工具标识符")
    void toolNameExtractionHasLowFalsePositive() {
        ReactPromptLoader loader = newLoader();
        var names = loader.extractToolNames();
        // 不应把常见非工具词当工具名抽出来（否则告警满天飞，没人看）
        assertFalse(names.contains("ai"), "不应抽出 'ai' 这种普通词");
        assertFalse(names.contains("sql"), "不应抽出裸 'sql'（要求带下划线）");
        assertTrue(names.contains("execute_sql"));
        assertTrue(names.contains("search_drug"));
    }

    @Test
    @DisplayName("T7.7 registry 为 null 时校验不 NPE（启动顺序未保证时安全）")
    void consistencyWithoutRegistry() {
        ReactPromptLoader loader = newLoader();
        loader.setToolRegistry(null);
        ReactPromptLoader.ConsistencyReport report = loader.verifyToolConsistency();
        assertNotNull(report);
        assertTrue(report.registryTools.isEmpty());
    }

    @Test
    @DisplayName("T7.8 外部热改文件缺失时静默回落（不抛异常，应用照常启动）")
    void externalFileAbsentIsSilent() {
        ReactPromptLoader loader = newLoader();
        assertNull(loader.externalSystemPrompt(),
                "仓库里不该有 config/react-prompts.json；没有时应返回 null 走 classpath");
        for (String role : ReactPromptLoader.knownRoles()) {
            assertNull(loader.externalRolePrompt(role));
        }
    }
}