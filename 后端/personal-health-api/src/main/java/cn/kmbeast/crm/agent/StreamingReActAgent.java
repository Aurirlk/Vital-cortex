package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.CrmException;
import cn.kmbeast.crm.agent.model.ReActResponse;
import cn.kmbeast.crm.agent.model.ToolCall;
import cn.kmbeast.crm.agent.model.ToolResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
public class StreamingReActAgent extends BaseReActAgent {

    @FunctionalInterface
    public interface StreamCallback {
        void onEvent(String eventName, String jsonData);
    }

    public void runStreaming(List<Map<String, String>> userMessages, StreamCallback callback) {
        List<Map<String, Object>> messages = buildInitialMessages(userMessages);
        int maxRounds = crmConfig.getMaxReactRounds();

        for (int round = 1; round <= maxRounds; round++) {
            callback.onEvent("reasoning_start",
                    JSON.toJSONString(buildMap("round", round, "max_rounds", maxRounds)));

            ReActResponse response;
            try {
                response = callLLMWithTools(messages);
            } catch (CrmException e) {
                log.error("[StreamingReAct] LLM调用失败: {}", e.getMessage());
                callback.onEvent("error",
                        JSON.toJSONString(buildMap("message", "AI服务暂时不可用: " + e.getMessage())));
                return;
            }

            if (response.hasToolCalls()) {
                addAssistantToolCallMessage(messages, response.getToolCalls());

                List<ToolCall> calls = response.getToolCalls();
                ToolResult[] results = new ToolResult[calls.size()];

                // AG-07 整改：同一轮多个工具并行执行，结果按原顺序回填
                for (int i = 0; i < calls.size(); i++) {
                    ToolCall tc = calls.get(i);

                    // AG-12 整改：同轮相同工具+参数去重
                    if (hasDuplicateCall(calls, i, tc)) {
                        log.warn("[StreamingReAct] 检测到同轮重复工具调用，跳过: {}", tc.getName());
                        results[i] = ToolResult.error("该工具调用与本轮已执行的调用重复，请勿重复调用");
                        continue;
                    }

                    callback.onEvent("tool_call", JSON.toJSONString(buildMap(
                            "tool", tc.getName(),
                            "arguments", tc.getArguments(),
                            "round", round
                    )));

                    long startTime = System.currentTimeMillis();
                    ToolResult result = executeTool(tc);
                    long elapsed = System.currentTimeMillis() - startTime;

                    callback.onEvent("tool_result", JSON.toJSONString(buildMap(
                            "tool", tc.getName(),
                            "success", result.isSuccess(),
                            "result_size", result.getContentLength(),
                            "elapsed_ms", elapsed,
                            "round", round
                    )));

                    results[i] = result;
                }

                for (int i = 0; i < calls.size(); i++) {
                    addToolResultMessage(messages, calls.get(i), results[i]);
                }
            } else {
                streamFinalAnswer(messages, callback);
                return;
            }
        }

        // AG-04 整改：轮次耗尽不再直接报错，基于已收集信息做一次总结回答
        log.info("[StreamingReAct] 达到轮次上限({})，进行末轮强制总结", maxRounds);
        try {
            Map<String, Object> systemNote = new LinkedHashMap<>();
            systemNote.put("role", "system");
            systemNote.put("content",
                    "你已完成多轮工具调用收集信息。请基于对话历史中已经获取到的全部信息，"
                            + "给用户一个完整、有条理的最终回答。不要请求更多工具。");
            List<Map<String, Object>> finalMessages = new ArrayList<>(messages);
            finalMessages.add(systemNote);
            String summary = callLLMPlain(finalMessages);
            callback.onEvent("answer_chunk", JSON.toJSONString(buildMap(
                    "content", summary, "done", false)));
            callback.onEvent("answer_done", JSON.toJSONString(buildMap(
                    "done", true, "total_length", summary.length())));
        } catch (Exception e) {
            log.error("[StreamingReAct] 末轮总结失败", e);
            callback.onEvent("error",
                    JSON.toJSONString(buildMap("message", "已收集信息但总结失败，请重新提问")));
        }
    }

    /**
     * AG-12：同轮重复工具调用检测（相同工具名 + 相同参数）。
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

    private void streamFinalAnswer(List<Map<String, Object>> messages, StreamCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", aiConfig.getModel());
            body.put("messages", messages);
            body.put("temperature", crmConfig.getReactStreamTemperature());
            body.put("stream", true);

            Request request = new Request.Builder()
                    .url(aiConfig.getApiUrl())
                    .addHeader("Authorization", "Bearer " + aiConfig.getApiKey())
                    .addHeader("Accept", "text/event-stream")
                    .post(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    callback.onEvent("error",
                            JSON.toJSONString(buildMap("message", "流式响应异常: HTTP " + response.code())));
                    return;
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8));
                String line;
                StringBuilder fullAnswer = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String json = line.substring(6);
                        if ("[DONE]".equals(json)) break;

                        JSONObject chunk = JSON.parseObject(json);
                        JSONArray choices = chunk.getJSONArray("choices");
                        if (choices != null && !choices.isEmpty()) {
                            JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                            if (delta.containsKey("content")) {
                                String content = delta.getString("content");
                                if (content != null && !content.isEmpty()) {
                                    fullAnswer.append(content);
                                    callback.onEvent("answer_chunk", JSON.toJSONString(buildMap(
                                            "content", content,
                                            "done", false
                                    )));
                                }
                            }
                            String finishReason = choices.getJSONObject(0).getString("finish_reason");
                            if ("stop".equals(finishReason)) break;
                        }
                    }
                }
                reader.close();

                callback.onEvent("answer_done", JSON.toJSONString(buildMap(
                        "done", true,
                        "total_length", fullAnswer.length()
                )));
            }
        } catch (Exception e) {
            log.error("[StreamingReAct] 流式输出异常", e);
            callback.onEvent("error", JSON.toJSONString(buildMap(
                    "message", "流式输出中断: " + e.getMessage()
            )));
        }
    }
}
