package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
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
     * 构造 DeepSeek API 所需的 tools 参数 JSON 数组
     */
    public List<Map<String, Object>> buildToolsArray() {
        List<Map<String, Object>> toolsArray = new ArrayList<>();
        for (Tool tool : tools.values()) {
            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("type", "function");

            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            function.put("parameters", tool.getParametersSchema());

            toolDef.put("function", function);
            toolsArray.add(toolDef);
        }
        return toolsArray;
    }
}
