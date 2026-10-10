package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.tool.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T2：8 个工具的 schema 结构合规性。
 *
 * <p><b>为什么用「扫源码目录」而不是「实例化 8 个工具」</b>：工具都是
 * {@code @Component}，构造它们需要注入 mapper/service，塞桩代价高且脆弱。
 * 这里改为静态扫描源码：一行 {@code grep} 就能验，改起来也直观。</p>
 *
 * <p>但静态扫描有个致命弱点 —— <b>它证明不了运行时行为</b>。所以 T2 只管
 * 「源码里有没有写」，真正的运行时验证放在 {@code ToolRegistryAdditionalPropertiesTest}
 * 与 {@link ToolArgsValidatorRequiredArrayTest}。</p>
 */
class ToolSchemaComplianceTest {

    /** 期望的 8 个工具（与 ToolRegistry 日志一致）。 */
    private static final List<String> EXPECTED_TOOLS = List.of(
            "search_drug", "search_knowledge", "query_knowledge_graph",
            "query_health_records", "get_health_data", "get_chat_history",
            "web_search", "execute_sql");

    private static final String TOOL_DIR = "src/main/java/cn/kmbeast/crm/agent/tool";

    @Test
    @DisplayName("T2.1 required 必须是 List 形态（String[] 会让本地校验静默失效）")
    void requiredIsListNotArray() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String tool : EXPECTED_TOOLS) {
            String schemaBody = extractSchemaBody(readToolSource(tool));
            // 去掉注释行后再找 new String[]{ —— 否则改造说明注释里写的
            // 「required: new String[]{"keyword"} → List.of("keyword")」会被误判为违规
            String code = stripComments(schemaBody);
            if (code.contains("new String[]")) {
                violations.add(tool);
            }
        }
        assertTrue(violations.isEmpty(),
                "🔴 以下工具的 required 仍是 String[] 形态，本地必填校验会静默失效：" + violations
                        + "（ToolArgsValidator 已支持三种形态，但统一成 List 更安全）");
    }

    @Test
    @DisplayName("T2.2 schema map 统一为 LinkedHashMap（键序稳定利于 prompt cache 命中）")
    void schemaUsesLinkedHashMap() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String tool : EXPECTED_TOOLS) {
            String code = stripComments(extractSchemaBody(readToolSource(tool)));
            // 只在 schema 构建处检查，不能扫全文件：
            // 工具的 execute() 里用 new HashMap 构造**结果**是完全正常的
            if (code.contains("new HashMap") && code.contains("\"properties\"")) {
                violations.add(tool);
            }
        }
        assertTrue(violations.isEmpty(),
                "🔴 以下工具的 schema 仍用 HashMap，键序不稳定：" + violations);
    }

    @Test
    @DisplayName("T2.3 有数值上下文的参数必须声明 minimum/maximum（模型无法从中文描述感知边界）")
    void numericParamsDeclareBounds() throws Exception {
        // days / limit / days 等有明确范围语义的参数
        assertTrue(readToolSource("get_health_data").contains("minimum"),
                "get_health_data 的 days 应声明 minimum");
        assertTrue(readToolSource("get_health_data").contains("maximum"),
                "get_health_data 的 days 应声明 maximum");
        assertTrue(readToolSource("query_health_records").contains("minimum"),
                "query_health_records 的 days/limit 应声明 minimum");
        assertTrue(readToolSource("query_health_records").contains("maximum"),
                "query_health_records 的 days/limit 应声明 maximum");
    }

    @Test
    @DisplayName("T2.4 8 个工具齐备，且工具名与 prompt 中声明的一致")
    void toolSetIsStable() throws Exception {
        for (String tool : EXPECTED_TOOLS) {
            assertTrue(tool.contains("_"), "工具名应遵循 snake_case: " + tool);
            readToolSource(tool); // 文件存在性
        }
        // 反向：prompt 必须覆盖全部 8 个工具（否则模型永远不调它）
        String prompt = readReactPrompt("system.txt");
        for (String tool : EXPECTED_TOOLS) {
            assertTrue(prompt.contains(tool),
                    "🔴 system prompt 未提及工具 " + tool + " —— 模型永远不会调用它");
        }
    }

    // ------------------------------------------------------------------
    // 工具函数
    // ------------------------------------------------------------------

    /** 读取工具源码；文件不存在直接失败（说明工具被删了）。 */
    private static String readToolSource(String toolName) throws Exception {
        java.io.File f = locateToolFile(toolName);
        assertTrue(f != null, "找不到工具源码（工具名 " + toolName + "）");
        return java.nio.file.Files.readString(f.toPath());
    }

    /**
     * 按工具名定位源码文件。
     *
     * <p><b>不能靠「驼峰化 + Tool 后缀」推类名</b>：实际命名并不规则，
     * 例如 {@code query_knowledge_graph} 的类是 {@code QueryGraphTool}（不是
     * {@code QueryKnowledgeGraphTool}）。硬编码映射表会在下次重命名时再次失效，
     * 所以改为<b>扫目录 + 读文件内容里的 getName 返回值</b>，以代码为准。</p>
     */
    private static java.io.File locateToolFile(String toolName) throws Exception {
        java.io.File dir = new java.io.File(TOOL_DIR);
        java.io.File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        String needle = "\"" + toolName + "\"";
        for (java.io.File f : files) {
            if (!f.getName().endsWith("Tool.java")) {
                continue;
            }
            String src = java.nio.file.Files.readString(f.toPath());
            // getName() 方法体里出现工具名才算数
            if (src.contains("return " + needle)) {
                return f;
            }
        }
        return null;
    }

    private static String readReactPrompt(String fileName) throws Exception {
        java.io.File f = new java.io.File("src/main/resources/ai-prompts/react/" + fileName);
        assertTrue(f.exists(), "找不到 prompt 资源: " + f.getPath());
        return java.nio.file.Files.readString(f.toPath());
    }

    private static String toPascalCase(String snake) {
        StringBuilder sb = new StringBuilder();
        for (String part : snake.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /** 截出 {@code getParametersSchema()} 的方法体（含签名行）。 */
    private static String extractSchemaBody(String src) {
        int start = src.indexOf("getParametersSchema()");
        if (start < 0) {
            return "";
        }
        // 从方法签名处找到第一个 {，再按花括号配平取到对应 }
        int open = src.indexOf('{', start);
        if (open < 0) {
            return "";
        }
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char ch = src.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return src.substring(start, i + 1);
                }
            }
        }
        return src.substring(start);
    }

    /** 去掉行注释与块注释，避免改造说明里的代码片段被误判为违规。 */
    private static String stripComments(String src) {
        String noBlock = src.replaceAll("(?s)/\\*.*?\\*/", "");
        StringBuilder sb = new StringBuilder();
        for (String line : noBlock.split("\n")) {
            int idx = line.indexOf("//");
            sb.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return sb.toString();
    }

    @Test
    @DisplayName("T2.5 ToolRegistry.buildToolsArray 补 additionalProperties:false（防幻觉参数）")
    void buildToolsArrayAddsAdditionalProperties() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        Field field = ToolRegistry.class.getDeclaredField("tools");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Tool> tools = (Map<String, Tool>) field.get(registry);
        tools.put("demo_tool", new Tool() {
            @Override public String getName() { return "demo_tool"; }
            @Override public String getDescription() { return "d"; }
            @Override public Map<String, Object> getParametersSchema() {
                Map<String, Object> props = new LinkedHashMapX();
                props.put("q", Map.of("type", "string"));
                Map<String, Object> schema = new LinkedHashMapX();
                schema.put("type", "object");
                schema.put("properties", props);
                return schema;
            }
            @Override public cn.kmbeast.crm.agent.model.ToolResult execute(Map<String, Object> a) {
                return null;
            }
        });

        List<Map<String, Object>> arr = registry.buildToolsArray(false);
        assertEquals(1, arr.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> fn = (Map<String, Object>) ((Map<?, ?>) arr.get(0).get("function"));
        assertEquals("function", arr.get(0).get("type"));
        assertEquals("demo_tool", fn.get("name"));

        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) fn.get("parameters");
        assertEquals(Boolean.FALSE, schema.get("additionalProperties"),
                "🔴 缺 additionalProperties:false，模型可能传入 schema 未声明的幻觉参数");

        // strict 默认关闭：function 下不应出现 strict 键
        assertFalse(fn.containsKey("strict"),
                "strict 默认关闭时不应下发该键（DeepSeek/vLLM 对它支持不一致，遇则 400）");
    }

    @Test
    @DisplayName("T2.6 strict=true 时才下发 strict，且不破坏 additionalProperties")
    void strictOnlyWhenEnabled() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        Field field = ToolRegistry.class.getDeclaredField("tools");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Tool> tools = (Map<String, Tool>) field.get(registry);
        tools.put("t2", new Tool() {
            @Override public String getName() { return "t2"; }
            @Override public String getDescription() { return "d"; }
            @Override public Map<String, Object> getParametersSchema() {
                Map<String, Object> schema = new LinkedHashMapX();
                schema.put("type", "object");
                schema.put("properties", new LinkedHashMapX());
                return schema;
            }
            @Override public cn.kmbeast.crm.agent.model.ToolResult execute(Map<String, Object> a) {
                return null;
            }
        });

        List<Map<String, Object>> arr = registry.buildToolsArray(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> fn = (Map<String, Object>) ((Map<?, ?>) arr.get(0).get("function"));
        assertEquals(Boolean.TRUE, fn.get("strict"));
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) fn.get("parameters");
        assertEquals(Boolean.FALSE, schema.get("additionalProperties"),
                "strict 开启时 additionalProperties 仍必须保留");
    }

    /** 测试用的 LinkedHashMap 别名（避免 import 冲突的写法噪音）。 */
    static final class LinkedHashMapX extends java.util.LinkedHashMap<String, Object> {
    }

    @Test
    @DisplayName("T2.7 schema 为 null 时 withSchemaHardening 不 NPE")
    void nullSchemaSafe() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        Field field = ToolRegistry.class.getDeclaredField("tools");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Tool> tools = (Map<String, Tool>) field.get(registry);
        tools.put("no_schema", new Tool() {
            @Override public String getName() { return "no_schema"; }
            @Override public String getDescription() { return "d"; }
            @Override public Map<String, Object> getParametersSchema() { return null; }
            @Override public cn.kmbeast.crm.agent.model.ToolResult execute(Map<String, Object> a) {
                return null;
            }
        });
        List<Map<String, Object>> arr = registry.buildToolsArray(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> fn = (Map<String, Object>) ((Map<?, ?>) arr.get(0).get("function"));
        assertNotNull(fn);
        assertEquals(null, fn.get("parameters"));
    }
}