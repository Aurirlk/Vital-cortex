package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.CrmException;
import cn.kmbeast.crm.agent.model.ReActResponse;
import cn.kmbeast.crm.agent.model.ToolCall;
import cn.kmbeast.crm.agent.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class ReActAgent extends BaseReActAgent {

    public ReActResponse run(List<Map<String, String>> userMessages) {
        List<Map<String, Object>> messages = buildInitialMessages(userMessages);

        List<String> allToolsUsed = new ArrayList<>();
        int maxRounds = crmConfig.getMaxReactRounds();

        for (int round = 1; round <= maxRounds; round++) {
            log.info("[ReAct] 第 {} 轮推理, messages count={}", round, messages.size());

            ReActResponse response;
            try {
                response = callLLMWithTools(messages);
            } catch (CrmException e) {
                log.error("[ReAct] LLM调用失败: {}", e.getMessage());
                return ReActResponse.text("AI服务暂时不可用: " + e.getMessage(), allToolsUsed);
            }

            if (response.hasToolCalls()) {
                addAssistantToolCallMessage(messages, response.getToolCalls());

                // AG-07 整改：同一轮多个 tool_calls 并行执行（模型已按
                // OpenAI parallel tool calls 返回数组），结果按原顺序回填，
                // 保证 tool_call_id 与消息顺序一致。
                List<ToolCall> calls = response.getToolCalls();
                ToolResult[] results = new ToolResult[calls.size()];
                CountDownLatch latch = new CountDownLatch(calls.size());

                for (int i = 0; i < calls.size(); i++) {
                    ToolCall tc = calls.get(i);
                    // AG-12 整改：循环检测——同一轮里对相同工具+相同参数去重，
                    // 重复调用直接标记失败，不重复执行，防止模型原地打转浪费 token。
                    if (hasDuplicateCall(calls, i, tc)) {
                        log.warn("[ReAct] 检测到同轮重复工具调用，跳过: {} args={}", tc.getName(), tc.getArguments());
                        results[i] = ToolResult.error("该工具调用与本轮已执行的调用重复，请勿重复调用");
                        latch.countDown();
                        continue;
                    }
                    final int idx = i;
                    toolExecutor.execute(() -> {
                        try {
                            log.info("[ReAct] 执行工具: {}, args={}", tc.getName(), tc.getArguments());
                            results[idx] = executeTool(tc);
                        } catch (Exception e) {
                            log.error("[ReAct] 工具执行异常: {}", tc.getName(), e);
                            results[idx] = ToolResult.error("工具执行异常: " + e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                // 等待本轮全部工具执行完成（并行场景下统一收口）
                try {
                    long timeout = crmConfig.getToolTimeoutSeconds() + 5L;
                    if (!latch.await(timeout, TimeUnit.SECONDS)) {
                        log.warn("[ReAct] 第 {} 轮工具执行超时（部分未完成），继续下一轮", round);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                for (int i = 0; i < calls.size(); i++) {
                    allToolsUsed.add(calls.get(i).getName());
                    addToolResultMessage(messages, calls.get(i), results[i]);
                }
            } else {
                return ReActResponse.text(response.getContent(), allToolsUsed);
            }
        }

        // AG-04 整改：轮次耗尽不再直接丢弃已获取的工具结果。
        // 末轮调用一次无工具的 LLM，基于已收集的全部上下文做总结回答。
        log.info("[ReAct] 达到轮次上限({})，进行末轮强制总结", maxRounds);
        return summarizeFinalAnswer(messages, allToolsUsed, maxRounds);
    }

    /**
     * AG-12：检测本轮已执行过的相同工具+相同参数调用。
     */
    private boolean hasDuplicateCall(List<ToolCall> calls, int currentIndex, ToolCall tc) {
        String currentKey = tc.getName() + "::" + (tc.getArguments() != null ? tc.getArguments().toString() : "");
        for (int j = 0; j < currentIndex; j++) {
            ToolCall prev = calls.get(j);
            String prevKey = prev.getName() + "::" + (prev.getArguments() != null ? prev.getArguments().toString() : "");
            if (currentKey.equals(prevKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * AG-04：无工具的末轮总结调用。
     */
    private ReActResponse summarizeFinalAnswer(List<Map<String, Object>> messages,
                                               List<String> allToolsUsed, int maxRounds) {
        Map<String, Object> systemNote = new LinkedHashMap<>();
        systemNote.put("role", "system");
        systemNote.put("content",
                "你已完成多轮工具调用收集信息。请基于对话历史中已经获取到的全部信息，"
                        + "给用户一个完整、有条理的最终回答。不要请求更多工具。");
        List<Map<String, Object>> finalMessages = new ArrayList<>(messages);
        finalMessages.add(systemNote);

        try {
            String summary = callLLMPlain(finalMessages);
            return ReActResponse.text(summary, allToolsUsed);
        } catch (Exception e) {
            log.error("[ReAct] 末轮总结调用失败", e);
            return ReActResponse.text("已收集到相关信息但总结失败，请重新提问。", allToolsUsed);
        }
    }
}
