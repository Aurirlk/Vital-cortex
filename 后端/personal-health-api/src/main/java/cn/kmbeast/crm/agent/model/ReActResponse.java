package cn.kmbeast.crm.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReActResponse {

    private String content;

    private List<ToolCall> toolCalls;

    @Builder.Default
    private List<String> toolsUsed = new ArrayList<>();

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public static ReActResponse text(String content) {
        return ReActResponse.builder()
                .content(content)
                .toolCalls(Collections.emptyList())
                .toolsUsed(new ArrayList<>())
                .build();
    }

    public static ReActResponse text(String content, List<String> toolsUsed) {
        return ReActResponse.builder()
                .content(content)
                .toolCalls(Collections.emptyList())
                .toolsUsed(toolsUsed != null ? toolsUsed : new ArrayList<>())
                .build();
    }

    public static ReActResponse toolCalls(List<ToolCall> toolCalls) {
        return ReActResponse.builder()
                .content(null)
                .toolCalls(toolCalls)
                .toolsUsed(new ArrayList<>())
                .build();
    }
}
