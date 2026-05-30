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

                for (ToolCall tc : response.getToolCalls()) {
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

                    addToolResultMessage(messages, tc, result);
                }
            } else {
                streamFinalAnswer(messages, callback);
                return;
            }
        }

        callback.onEvent("error",
                JSON.toJSONString(buildMap("message", "推理轮次超过上限")));
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
