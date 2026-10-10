package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.*;

@Slf4j
@Component
public class ToolRegistry {

    @Resource
    private List<Tool> toolList;

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        for (Tool tool : toolList) {
            tools.put(tool.getName(), tool);
            log.info("[ToolRegistry] 注册工具: {}", tool.getName());
        }
        log.info("[ToolRegistry] 共注册 {} 个工具", tools.size());
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public Collection<Tool> getAll() {
        return tools.values();
    }

    /**
     * 构造 OpenAI function calling 的 {@code tools} 参数（标准三层结构：
     * {@code type:"function" → {name, description, parameters}}）。
     *
     * @param toolStrict 是否下发 {@code function.strict=true}（OpenAI 结构化输出强约束）。
     *                   默认传 false：DeepSeek / vLLM / 国产网关对该字段<b>支持不一致，
     *                   部分实现遇到直接 400。无论开关如何，都会补
     *                   {@code additionalProperties:false} —— 这是纯 JSON Schema 标准字段，
     *                   任何实现都认，且能约束模型不要发明未声明的入参。
     */
    public List<Map<String, Object>> buildToolsArray(boolean toolStrict) {
        List<Map<String, Object>> toolsArray = new ArrayList<>();
        for (Tool tool : tools.values()) {
            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("type", "function");

            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            function.put("parameters", withSchemaHardening(tool.getParametersSchema()));

            toolDef.put("function", function);
            toolsArray.add(toolDef);

            if (toolStrict) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fn = (Map<String, Object>) toolDef.get("function");
                fn.put("strict", true);
            }
        }
        return toolsArray;
    }

    /**
     * 给工具的 parameters schema 补上 {@code additionalProperties:false}。
     *
     * <p><b>为什么加</b>：没有这条约束时，模型可能传回 schema 里没声明的字段
     * （幻觉参数）。工具侧若未校验就直接取用，可能读到 null 引发 NPE 或产生错误逻辑。
     * 加上后严格实现会拒绝非法参数。</p>
     *
     * <p><b>不变性</b>：<b>就地修改传入的 schema 对象</b>。工具的 schema 是
     * {@code @Component} 单例每次都新建 map（见各工具 {@code getParametersSchema()} 实现），
     * 重复调用会得到幂等结果；此处刻意不改结构顺序，用 {@code put} 覆盖以保持
     * 「已存在则值不变」，避免破坏 P0-2 刚统一的 LinkedHashMap 键序（影响 prompt 缓存命中）。</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> withSchemaHardening(Map<String, Object> schema) {
        if (schema == null) {
            return schema;
        }
        Object existing = schema.get("additionalProperties");
        if (existing != null) {
            return schema;
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    /** 不带 strict 的等价实现（保持既有调用方行为不变）。 */
    public List<Map<String, Object>> buildToolsArray() {
        return buildToolsArray(false);
    }
}
