package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.crm.agent.model.ToolResult;

import java.util.Map;

public interface Tool {

    String getName();

    String getDescription();

    Map<String, Object> getParametersSchema();

    ToolResult execute(Map<String, Object> arguments);
}
