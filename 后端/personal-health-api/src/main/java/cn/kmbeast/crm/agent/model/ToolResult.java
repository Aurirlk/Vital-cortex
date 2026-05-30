package cn.kmbeast.crm.agent.model;

import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ToolResult {

    private boolean success;

    private String content;

    private int contentLength;

    public static ToolResult ok(String content) {
        return new ToolResult(true, content, content != null ? content.length() : 0);
    }

    public static ToolResult error(String message) {
        return new ToolResult(false, message, message != null ? message.length() : 0);
    }

    public String toJson() {
        return JSON.toJSONString(this);
    }
}
