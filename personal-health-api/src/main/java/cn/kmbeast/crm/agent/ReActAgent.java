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

    @Override
    protected ReActResponse doRun(List<Map<String, String>> userMessages) {
        List<Map<String, Object>> messages = buildInitialMessages(userMessages);

        List<String> allToolsUsed = new ArrayList<>();
        List<Map<String, Object>> toolDetails = new ArrayList<>();
        int maxRounds = crmConfig.getMaxReactRounds();

        // 2026-10-07：跨轮幂等台账（AG-12 扩展）。
        // 此前只做【同轮】去重，模型第 1 轮调 query_health_records、第 3 轮又调一次
        // 会执行两次 —— 而该工具会写 audit_log，产生重复审计行。
        // ⚠️ 必须是 doRun 的局部变量：本类是 @Service 单例，用实例字段会跨请求串号。
        Set<String> executedKeys = new LinkedHashSet<>();

        for (int round = 1; round <= maxRounds; round++) {
            log.info("[ReAct] 第 {} 轮推理, messages count={}", round, messages.size());

            ReActResponse response;
            try {
                response = callLLMWithTools(messages);
            } catch (CrmException e) {
                log.error("[ReAct] LLM调用失败: {}", e.getMessage());
                // 阶段 3 四层 fail-safe 第 4 层：LLM 完全故障时，高置信度数据意图直接离线兜底
                ReActResponse offline = tryOfflineFallback(peekIntentHint());
                if (offline != null) {
                    return offline;
                }
                return ReActResponse.text("AI服务暂时不可用: " + e.getMessage(), allToolsUsed);
            }

            if (response.hasToolCalls()) {
                // 2026-10-07 P0-6：把模型同轮的过渡文本拼回 assistant 消息，
                // 否则模型下一轮看不到自己刚说过的话（内容已被生成并计费，丢弃是浪费）。
                addAssistantToolCallMessage(messages, response.getToolCalls(),
                        response.getAssistantText());

                // AG-07 整改：同一轮多个 tool_calls 并行执行（模型已按
                // OpenAI parallel tool calls 返回数组），结果按原顺序回填，
                // 保证 tool_call_id 与消息顺序一致。
                List<ToolCall> calls = response.getToolCalls();
                // 2026-10-07：并行执行抽到基类 executeToolsParallel（唯一实现）。
                // 该方法已统一处理：同轮去重 / 跨轮幂等台账 / RejectedExecutionException
                // / 超时后 results[i] 为 null 导致的 NPE（此前会冒泡成 500）。
                ToolResult[] results = executeToolsParallel(calls, executedKeys, null);

                for (int i = 0; i < calls.size(); i++) {
                    ToolCall call = calls.get(i);
                    allToolsUsed.add(call.getName());
                    // AG-05：记录工具调用轨迹（参数 + 结果状态），随响应落库审计
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("tool", call.getName());
                    detail.put("args", call.getArguments() != null ? call.getArguments().toString() : "");
                    detail.put("success", results[i] != null && results[i].isSuccess());
                    toolDetails.add(detail);
                    addToolResultMessage(messages, call, results[i]);
                }
            } else {
                // 阶段 3 四层 fail-safe 第 2 层：首轮未调用数据工具但意图高置信度，
                // 追加强化指令再给一轮机会（避免模型凭记忆编造健康数据）。
                if (shouldRetryForRouting(round, maxRounds)) {
                    Map<String, Object> note = buildRoutingReinforceNote();
                    if (note != null) {
                        log.info("[ReAct] 第 1 轮未调用数据工具但意图高置信度，追加强化指令重跑一轮");
                        messages.add(note);
                        continue;
                    }
                }
                return ReActResponse.builder().content(response.getContent())
                        .toolsUsed(allToolsUsed).toolDetails(toolDetails).build();
            }
        }

        // AG-04 整改：轮次耗尽不再直接丢弃已获取的工具结果。
        // 末轮调用一次无工具的 LLM，基于已收集的全部上下文做总结回答。
        log.info("[ReAct] 达到轮次上限({})，进行末轮强制总结", maxRounds);
        return summarizeFinalAnswer(messages, allToolsUsed, maxRounds, toolDetails);
    }

    /**
     * AG-04：无工具的末轮总结调用。
     */
    private ReActResponse summarizeFinalAnswer(List<Map<String, Object>> messages,
                                               List<String> allToolsUsed, int maxRounds,
                                               List<Map<String, Object>> toolDetails) {
        Map<String, Object> systemNote = new LinkedHashMap<>();
        systemNote.put("role", "system");
        systemNote.put("content",
                "你已完成多轮工具调用收集信息。请基于对话历史中已经获取到的全部信息，"
                        + "给用户一个完整、有条理的最终回答。不要请求更多工具。");
        List<Map<String, Object>> finalMessages = new ArrayList<>(messages);
        finalMessages.add(systemNote);

        try {
            String summary = callLLMPlain(finalMessages);
            return ReActResponse.builder().content(summary)
                    .toolsUsed(allToolsUsed).toolDetails(toolDetails).build();
        } catch (Exception e) {
            log.error("[ReAct] 末轮总结调用失败", e);
            return ReActResponse.builder().content("已收集到相关信息但总结失败，请重新提问。")
                    .toolsUsed(allToolsUsed).toolDetails(toolDetails).build();
        }
    }
}
