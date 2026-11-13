package cn.kmbeast.crm.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReActResponse {

    private String content;

    private List<ToolCall> toolCalls;

    @Builder.Default
    private List<String> toolsUsed = new ArrayList<>();

    /**
     * AG-05: tool invocation trail (name + args + success) for audit/checkpoint.
     */
    @Builder.Default
    private List<Map<String, Object>> toolDetails = new ArrayList<>();

    /**
     * 2026-10-07 P0-6：模型在<b>发起工具调用的同一轮</b>里输出的文本。
     *
     * <p><b>为什么必须保留</b>：部分模型（尤其 DeepSeek）在返回 {@code tool_calls} 的同时
     * 会先说一段过渡语，例如「好的，我来查一下你最近的血压记录」。此前这段文本被丢弃，
     * 造成两个后果：</p>
     * <ol>
     *   <li><b>上下文断裂</b>——下一轮 messages 里看不到自己刚说过的话，
     *       模型可能重复表述、或前后语气不一致；</li>
     *   <li><b>token 白白浪费</b>——这些内容已经生成并计费了。</li>
     * </ol>
     *
     * <p>它<b>不会</b>直接作为答案返回用户：主循环把 tool 结果喂回后，模型还会再生成一轮，
     * 那才是最终答案。本字段的用途是拼进下一轮的 assistant 消息（见
     * {@code addAssistantToolCallMessage}）。</p>
     */
    private String assistantText;

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

    /**
     * 带「同轮过渡文本」的工具调用响应（2026-10-07）。
     *
     * @param modelText 模型同轮输出的文本，可为 null（多数实现不输出）
     */
    public static ReActResponse toolCalls(List<ToolCall> toolCalls, String modelText) {
        return ReActResponse.builder()
                .content(null)
                .toolCalls(toolCalls)
                .toolsUsed(new ArrayList<>())
                .assistantText(modelText)
                .build();
    }
}
