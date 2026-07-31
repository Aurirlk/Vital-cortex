package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.tool.Tool;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolArgsValidator 工具入参 schema 校验单测（AG-16）。
 */
class ToolArgsValidatorTest {

    /** 构造一个带 schema 的假工具 */
    private Tool buildTool(String requiredField) {
        return new Tool() {
            @Override
            public String getName() { return "test_tool"; }

            @Override
            public String getDescription() { return "test"; }

            @Override
            public Map<String, Object> getParametersSchema() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                Map<String, Object> properties = new LinkedHashMap<>();

                Map<String, Object> nameProp = new LinkedHashMap<>();
                nameProp.put("type", "string");
                properties.put("name", nameProp);

                Map<String, Object> limitProp = new LinkedHashMap<>();
                limitProp.put("type", "integer");
                properties.put("limit", limitProp);

                schema.put("properties", properties);
                schema.put("required", Collections.singletonList(requiredField));
                return schema;
            }

            @Override
            public cn.kmbeast.crm.agent.model.ToolResult execute(Map<String, Object> arguments) {
                return cn.kmbeast.crm.agent.model.ToolResult.ok("ok");
            }
        };
    }

    @Test
    void validArgsPass() {
        Tool tool = buildTool("name");
        Map<String, Object> args = new HashMap<>();
        args.put("name", "张三");
        args.put("limit", 5);
        assertNull(ToolArgsValidator.validate(tool, args));
    }

    @Test
    void missingRequiredRejected() {
        Tool tool = buildTool("name");
        Map<String, Object> args = new HashMap<>();
        args.put("limit", 5);
        String err = ToolArgsValidator.validate(tool, args);
        assertNotNull(err);
        assertTrue(err.contains("name"));
    }

    @Test
    void wrongTypeRejected() {
        Tool tool = buildTool("name");
        Map<String, Object> args = new HashMap<>();
        args.put("name", "张三");
        args.put("limit", "不是数字");
        String err = ToolArgsValidator.validate(tool, args);
        assertNotNull(err);
        assertTrue(err.contains("limit"));
    }

    @Test
    void nullArgsRejected() {
        Tool tool = buildTool("name");
        assertNotNull(ToolArgsValidator.validate(tool, null));
    }
}
