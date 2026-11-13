package cn.kmbeast.crm.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolCall {

    private String id;

    private String name;

    private Map<String, Object> arguments;
}
