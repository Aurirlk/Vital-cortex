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

    /**
     * 本 agent 只支持流式输出，同步主循环无意义——调用即报错，
     * 避免有人「顺手用错接口」得到一条没走完流式链路的半截回答。
     */
    @Override
    protected ReActResponse doRun(List<Map<String, String>> userMessages) {
        throw new UnsupportedOperationException(
                "StreamingReActAgent 只支持流式调用，请使用 runWithContextStreaming()");
    }

    protected void doRunStreaming(List<Map<String, String>> userMessages, StreamCallback callback) {
        // 运行记录此前只挂在同步入口 BaseReActAgent.runWithContext 上，流式整条链路一个都没调，
        // 表现为「智能体运行记录」页面只有医生端那几条，用户端对话全程无痕。
        // 回答正文从 SSE 事件里回捞：下面的执行循环有 6 个出口，逐个塞不现实。
        StringBuilder answerCollector = new StringBuilder();
        StreamCallback recordingCallback = (eventName, jsonData) -> {
            if ("answer_chunk".equals(eventName)) {
                try {
                    String content = JSON.parseObject(jsonData).getString("content");
                    if (content != null) {
                        answerCollector.append(content);
                    }
                } catch (Exception e) {
                    log.debug("[StreamingReAct] answer_chunk 事件体解析失败，仅影响本轮运行记录");
                }
            }
            callback.onEvent(eventName, jsonData);
        };

        beginRunRecording(userMessages);
        String failure = null;
        try {
            runRounds(userMessages, recordingCallback);
        } catch (RuntimeException e) {
            failure = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw e;
        } finally {
            finishRunRecording(answerCollector.length() == 0
                    ? null : ReActResponse.text(answerCollector.toString()), failure);
        }
    }

    private void runRounds(List<Map<String, String>> userMessages, StreamCallback callback) {
        List<Map<String, Object>> messages = buildInitialMessages(userMessages);
        int maxRounds = crmConfig.getMaxReactRounds();

        // 2026-10-07：跨轮幂等台账（AG-12 扩展）。
        // 此前只做【同轮】去重，跨轮重复调用会重复写 audit_log。
        // ⚠️ 必须是方法局部变量：本类由 Spring 管理，用实例字段会跨请求串号。
        Set<String> executedKeys = new LinkedHashSet<>();

        for (int round = 1; round <= maxRounds; round++) {
            callback.onEvent("reasoning_start",
                    JSON.toJSONString(buildMap("round", round, "max_rounds", maxRounds)));

            ReActResponse response;
            try {
                response = callLLMWithTools(messages);
            } catch (CrmException e) {
                log.error("[StreamingReAct] LLM调用失败: {}", e.getMessage());
                // 阶段 3 四层 fail-safe 第 4 层：LLM 完全故障时，高置信度数据意图离线兜底
                ReActResponse offline = tryOfflineFallback(peekIntentHint());
                if (offline != null) {
                    callback.onEvent("answer_chunk", JSON.toJSONString(buildMap(
                            "content", offline.getContent(), "done", false)));
                    callback.onEvent("answer_done", JSON.toJSONString(buildMap(
                            "done", true, "total_length", offline.getContent().length())));
                    return;
                }
                callback.onEvent("error",
                        JSON.toJSONString(buildMap("message", "AI服务暂时不可用: " + e.getMessage())));
                return;
            }

            if (response.hasToolCalls()) {
                addAssistantToolCallMessage(messages, response.getToolCalls(),
                        response.getAssistantText());

                List<ToolCall> calls = response.getToolCalls();

                // 2026-10-07：并行执行改调基类 executeToolsParallel（唯一实现）。
                // 此前这里是**串行 for 循环**，注释却写「并行执行」（注释与实现矛盾），
                // 且与 ReActAgent 各维护一份几乎相同的 40 行代码。
                // now：真正并行 + 与非流式链路共用实现；tool_call 事件通过
                // beforeExecute 回调发出（保持原有 SSE 事件时序）。
                // ⚠️ round 是 for 循环变量（非 effectively final），必须先复制成 final 副本才能进 lambda
                final int roundNo = round;
                ToolResult[] results = executeToolsParallel(calls, executedKeys, tc -> {
                    callback.onEvent("tool_call", JSON.toJSONString(buildMap(
                            "tool", tc.getName(),
                            "arguments", tc.getArguments(),
                            "round", roundNo
                    )));
                });

                // 工具执行完成后统一发 tool_result 事件（含耗时）
                for (int i = 0; i < calls.size(); i++) {
                    ToolCall tc = calls.get(i);
                    ToolResult r = results[i];
                    callback.onEvent("tool_result", JSON.toJSONString(buildMap(
                            "tool", tc.getName(),
                            "success", r.isSuccess(),
                            "result_size", r.getContentLength(),
                            "round", roundNo
                    )));
                }

                for (int i = 0; i < calls.size(); i++) {
                    addToolResultMessage(messages, calls.get(i), results[i]);
                }
            } else {
                // 阶段 3 四层 fail-safe 第 2 层：首轮未调用数据工具但意图高置信度，
                // 追加强化指令再给一轮机会（避免模型凭记忆编造健康数据）。
                if (shouldRetryForRouting(round, maxRounds)) {
                    Map<String, Object> note = buildRoutingReinforceNote();
                    if (note != null) {
                        log.info("[StreamingReAct] 第 1 轮未调用数据工具但意图高置信度，追加强化指令重跑一轮");
                        messages.add(note);
                        continue;
                    }
                }
                ReActResponse finalResponse = streamAnswerOrTools(messages, callback);
                if (finalResponse == null) {
                    // 已由 streamAnswerOrTools 通过 error 事件告知前端，直接结束
                    return;
                }
                // 2026-10-07 P0-4：末轮流式可能带回工具调用（finish_reason=tool_calls）。
                // 此前 streamFinalAnswer 是 void，工具调用无处安放只能丢弃 ——
                // 现把结果喂回 messages 再跑一轮，让模型基于工具结果给出最终答案。
                if (finalResponse.hasToolCalls()) {
                    if (round >= maxRounds) {
                        log.warn("[StreamingReAct] 末轮才返回工具调用但轮次已耗尽，放弃执行");
                        callback.onEvent("answer_done", JSON.toJSONString(buildMap(
                                "done", true, "total_length", 0)));
                        return;
                    }
                    List<ToolCall> pendingCalls = finalResponse.getToolCalls();
                    addAssistantToolCallMessage(messages, pendingCalls,
                            finalResponse.getAssistantText());

                    final int execRound = round;
                    ToolResult[] pendingResults = executeToolsParallel(
                            pendingCalls, executedKeys, tc ->
                                    callback.onEvent("tool_call", JSON.toJSONString(buildMap(
                                            "tool", tc.getName(),
                                            "arguments", tc.getArguments(),
                                            "round", execRound
                                    ))));
                    for (int i = 0; i < pendingCalls.size(); i++) {
                        ToolCall tc = pendingCalls.get(i);
                        ToolResult r = pendingResults[i];
                        callback.onEvent("tool_result", JSON.toJSONString(buildMap(
                                "tool", tc.getName(),
                                "success", r.isSuccess(),
                                "result_size", r.getContentLength(),
                                "round", execRound
                        )));
                        addToolResultMessage(messages, tc, r);
                    }
                    // 继续下一轮：模型会基于工具结果给出最终答案
                    continue;
                }
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
     * 2026-10-07 P0-4：流式 {@code delta.tool_calls} 分片累积器。
     *
     * <p><b>为什么必须有这个类</b>：OpenAI 流式协议下，工具调用<b>不是一次下全的</b>，
     * 而是被拆成多个 chunk 按 {@code delta.tool_calls[].index} 顺序推送：
     * <pre>
     * chunk1: tool_calls=[{index:0, id:"call_abc", function:{name:"search_drug", arguments:""}}]
     * chunk2: tool_calls=[{index:0,             function:{arguments:"{\"key"}}]
     * chunk3: tool_calls=[{index:0,             function:{arguments:"word\":\"布洛芬\"}"}}]
     * chunk4: tool_calls=[{index:1, id:"call_def", function:{name:"get_health_data", arguments:""}}]
     * ...
     * </pre>
     * 即：<b>{@code id} 与 {@code name} 只在首片出现</b>，{@code arguments} 被逐字符切片追加。
     * 此前本类根本不解析 {@code delta.tool_calls}，这些分片会被<b>静默丢弃</b> ——
     * 一旦给流式链路加上 tools，模型会返回 tool_calls 而代码当成纯文本处理，
     * 结果是「模型要调工具」但前端收到空回答。</p>
     *
     * <p><b>为什么用 index 而不是出现顺序</b>：index 是协议里唯一可靠的分片标识。
     * 多个工具并行时 chunk 未必严格按序到达（虽有 index 保证可重排），
     * 用「list.size() 当 id」会在乱序时把 arguments 拼到错误的工具上。</p>
     */
    static final class ToolCallAccumulator {
        /** key = OpenAI 的 delta.tool_calls[].index（int） */
        private final Map<Integer, StringBuilder> argsByIndex = new LinkedHashMap<>();
        private final Map<Integer, String> idByIndex = new LinkedHashMap<>();
        private final Map<Integer, String> nameByIndex = new LinkedHashMap<>();

        /**
         * 吃进一帧的 {@code delta.tool_calls} 数组。
         *
         * @param fragments fastjson2 解析出的数组；可为 null / 空
         */
        void accept(JSONArray fragments) {
            if (fragments == null || fragments.isEmpty()) {
                return;
            }
            for (int i = 0; i < fragments.size(); i++) {
                JSONObject frag = fragments.getJSONObject(i);
                if (frag == null) {
                    continue;
                }
                // index 缺失时退化为「出现顺序」，保证不丢片（宁可顺序猜错也不整条丢弃）
                Integer index = frag.getInteger("index");
                int key = (index != null) ? index : argsByIndex.size();

                JSONObject function = frag.getJSONObject("function");
                // 首片才带 id / name，后续片只有 arguments
                String id = frag.getString("id");
                if (id != null && !id.isBlank()) {
                    idByIndex.put(key, id);
                }
                if (function != null) {
                    String name = function.getString("name");
                    if (name != null && !name.isBlank()) {
                        nameByIndex.put(key, name);
                    }
                    String argsPart = function.getString("arguments");
                    if (argsPart != null && !argsPart.isEmpty()) {
                        argsByIndex.computeIfAbsent(key, k -> new StringBuilder())
                                .append(argsPart);
                    }
                }
            }
        }

        boolean isEmpty() {
            return argsByIndex.isEmpty() && idByIndex.isEmpty() && nameByIndex.isEmpty();
        }

        int size() {
            return Math.max(argsByIndex.size(),
                    Math.max(idByIndex.size(), nameByIndex.size()));
        }

        /**
         * 产出完整的 {@link ToolCall} 列表。
         *
         * <p><b>容错设计</b>：缺 id 时合成一个稳定 id（协议要求 tool_call_id 必须能配对，
         * 缺失会被服务端判 400）；缺 name 的分片直接跳过（无法确定调哪个工具，
         * 硬凑会让工具注册表返回「未知工具」污染对话）；<b>arguments 不是合法 JSON 时
         * 返回空列表</b> —— 半截 JSON 传给工具必然抛解析异常，
         * 不如让上层当作「本轮没有工具调用」走正常回答路径。</p>
         */
        List<ToolCall> toToolCalls() {
            List<ToolCall> list = new ArrayList<>();
            if (isEmpty()) {
                return list;
            }
            // 按 index 升序输出，与模型给出的调用顺序一致
            List<Integer> indices = new ArrayList<>();
            for (Integer k : nameByIndex.keySet()) {
                if (!indices.contains(k)) {
                    indices.add(k);
                }
            }
            for (Integer k : argsByIndex.keySet()) {
                if (!indices.contains(k)) {
                    indices.add(k);
                }
            }
            for (Integer k : idByIndex.keySet()) {
                if (!indices.contains(k)) {
                    indices.add(k);
                }
            }
            java.util.Collections.sort(indices);

            for (Integer k : indices) {
                String name = nameByIndex.get(k);
                if (name == null || name.isBlank()) {
                    log.warn("[StreamingReAct] 丢弃无法确定工具名的 tool_call 分片, index={}", k);
                    continue;
                }
                String argsRaw = argsByIndex.containsKey(k)
                        ? argsByIndex.get(k).toString()
                        : "{}";
                Map<String, Object> parsed;
                try {
                    parsed = JSON.parseObject(argsRaw, Map.class);
                    if (parsed == null) {
                        parsed = new LinkedHashMap<>();
                    }
                } catch (Exception e) {
                    log.warn("[StreamingReAct] tool_call({}) 的 arguments 不是合法 JSON，丢弃本组工具调用: {}",
                            name, argsRaw);
                    return new ArrayList<>();
                }
                String id = idByIndex.get(k);
                if (id == null || id.isBlank()) {
                    id = "call_stream_" + k + "_" + name;
                    log.warn("[StreamingReAct] tool_call({}) 缺少 id，合成: {}", name, id);
                }
                list.add(ToolCall.builder().id(id).name(name).arguments(parsed).build());
            }
            return list;
        }
    }

    /**
     * 末轮流式输出：既可以只推文本，也可以带 {@code tools} 以接收流式工具调用。
     *
     * <p><b>2026-10-07 重构要点</b>（原 {@code streamFinalAnswer} 的 4 个缺陷）：</p>
     * <ol>
     *   <li>🔴 <b>不解析 {@code delta.tool_calls}</b> → 开启 tools 后分片被丢弃。
     *       现由 {@link ToolCallAccumulator} 按 index 累积。</li>
     *   <li>⚠️ <b>{@code startsWith("data: ")} 强依赖恰好一个空格</b> ——
     *       SSE 规范允许 {@code data:{...}}（无空格），此写法会漏掉整帧。
     *       现改为 {@code startsWith("data:")} + 跳过可选空白。</li>
     *   <li>🔴 <b>{@code JSON.parseObject} 无 try</b> —— 一帧畸形 JSON（如网关插入
     *       心跳注释、中途截断）会抛异常，被外层 catch 吞掉后<b>丢弃全部已生成内容</b>，
     *       用户看到半句话突然中断。现改为跳过该帧并计数，不影响已推内容。</li>
     *   <li>🔴 <b>只认 {@code finish_reason=="stop"}</b> —— {@code length}（长度截断）
     *       会被当完整答案输出，{@code content_filter} 会被忽略。
     *       现按四值分别处理。</li>
     * </ol>
     *
     * @param withTools 是否下发 {@code tools}（灰度开关控制，默认 false）
     * @return 有工具调用时返回含 toolCalls 的响应；纯文本时 content 为完整答案
     */
    private ReActResponse streamAnswerOrTools(List<Map<String, Object>> messages,
                                              StreamCallback callback) {
        JSONObject body = new JSONObject();
        body.put("model", aiConfig.getModel());
        body.put("messages", messages);
        body.put("temperature", crmConfig.getReactStreamTemperature());
        body.put("stream", true);
        // 与非流式链路对齐，避免服务端默认值静默截断末轮答案
        body.put("max_tokens", crmConfig.getReactMaxTokens());

        boolean withTools = crmConfig.isReactStreamToolsEnabled();
        if (withTools && toolRegistry != null) {
            // 必须按当前 Agent 的白名单下发，与非流式链路同一口径（BaseReActAgent:1190）。
            // 此前用的是无参全局数组，管理端精心配的白名单在用户实际聊天时根本不生效。
            body.put("tools", toolRegistry.buildToolsArray(
                    resolveAllowedTools(), crmConfig.isReactToolStrict()));
            body.put("tool_choice", "auto");
            body.put("parallel_tool_calls", true);
        }

        StringBuilder fullAnswer = new StringBuilder();
        ToolCallAccumulator accumulator = new ToolCallAccumulator();
        // 2026-10-07：finish_reason 四态
        String finishReason = null;
        int malformedFrames = 0;

        try {
            Request request = new Request.Builder()
                    .url(aiConfig.getApiUrl())
                    .addHeader("Authorization", "Bearer " + aiConfig.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .post(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    callback.onEvent("error",
                            JSON.toJSONString(buildMap("message", "流式响应异常: HTTP " + response.code())));
                    return null;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line == null || line.isEmpty()) {
                            continue;
                        }
                        // SSE 规范允许 `data:{...}`（无空格）与 `data: {...}`，两种都要认
                        if (!line.startsWith("data:")) {
                            continue;
                        }
                        String json = line.substring(5).trim();
                        if (json.isEmpty()) {
                            continue;
                        }
                        if ("[DONE]".equals(json)) {
                            break;
                        }

                        JSONObject chunk;
                        try {
                            chunk = JSON.parseObject(json);
                        } catch (Exception parseError) {
                            // 畸形帧只跳过，绝不让它把已生成的内容全部带走
                            malformedFrames++;
                            if (malformedFrames <= 3) {
                                log.warn("[StreamingReAct] 跳过 {} 个畸形 SSE 帧: {}",
                                        malformedFrames, parseError.getMessage());
                            }
                            continue;
                        }
                        if (chunk == null) {
                            continue;
                        }
                        JSONArray choices = chunk.getJSONArray("choices");
                        if (choices == null || choices.isEmpty()) {
                            continue;
                        }
                        JSONObject choice0 = choices.getJSONObject(0);
                        if (choice0 == null) {
                            continue;
                        }
                        JSONObject delta = choice0.getJSONObject("delta");
                        if (delta != null) {
                            String content = delta.getString("content");
                            if (content != null && !content.isEmpty()) {
                                fullAnswer.append(content);
                                callback.onEvent("answer_chunk", JSON.toJSONString(buildMap(
                                        "content", content,
                                        "done", false
                                )));
                            }
                            if (withTools) {
                                accumulator.accept(delta.getJSONArray("tool_calls"));
                            }
                        }
                        String fr = choice0.getString("finish_reason");
                        if (fr != null && !fr.isEmpty()) {
                            finishReason = fr;
                            // stop / length / tool_calls 都表示本轮流已正常结束
                            if (!"tool_calls".equals(fr)) {
                                break;
                            }
                        }
                    }
                }
            }

            if (malformedFrames > 3) {
                log.warn("[StreamingReAct] 本次流式共跳过 {} 个畸形帧", malformedFrames);
            }

            // ---- ① finish_reason == tool_calls：把累积的分片交回主循环执行工具 ----
            if ("tool_calls".equals(finishReason) && withTools) {
                List<ToolCall> calls = accumulator.toToolCalls();
                if (!calls.isEmpty()) {
                    log.info("[StreamingReAct] 流式收到 {} 个工具调用，转入执行", calls.size());
                    // 模型的过渡文本不能白丢：写进 assistantText，由主循环拼回 messages
                    String modelText = fullAnswer.isEmpty() ? null : fullAnswer.toString();
                    return ReActResponse.toolCalls(calls, modelText);
                }
                log.warn("[StreamingReAct] finish_reason=tool_calls 但分片不完整，降级为纯文本回答");
            }

            // ---- ② finish_reason == length：截断。答案不完整必须告知用户 ----
            if ("length".equals(finishReason)) {
                log.warn("[StreamingReAct] 末轮答案被 max_tokens 截断（已生成 {} 字符）",
                        fullAnswer.length());
                callback.onEvent("error", JSON.toJSONString(buildMap(
                        "message", "回答超过长度上限已被截断，内容不完整，请把问题拆小后重试")));
                return null;
            }

            // ---- ③ finish_reason == content_filter：内容被安全策略拦截 ----
            if ("content_filter".equals(finishReason)) {
                log.warn("[StreamingReAct] 末轮响应被内容安全策略拦截");
                callback.onEvent("error", JSON.toJSONString(buildMap(
                        "message", "该问题触发了内容安全策略，无法回答。请换个说法")));
                return null;
            }

            // ---- ④ stop 或未收到 finish_reason（部分实现在此直接 [DONE]）→ 正常完成 ----
            callback.onEvent("answer_done", JSON.toJSONString(buildMap(
                    "done", true,
                    "total_length", fullAnswer.length()
            )));
            return ReActResponse.text(fullAnswer.toString());
        } catch (Exception e) {
            log.error("[StreamingReAct] 流式输出异常", e);
            callback.onEvent("error", JSON.toJSONString(buildMap(
                    "message", "流式输出中断: " + e.getMessage()
            )));
            return null;
        }
    }

    /**
     * 流式链路的统一入口：设置角色与数据引导 → 跑 ReAct → <b>finally 必定清理</b>。
     *
     * <p>与 {@code runWithContext} 同理，调用方不必再自己 set/clear。
     *
     * @param roleType  角色编码，null 走默认角色
     * @param guidance  Harness 生成的追问指令，null 表示无需追问
     */
    public void runWithContextStreaming(List<Map<String, String>> userMessages,
                                        StreamCallback callback,
                                        String roleType,
                                        String guidance) {
        setRoleHint(roleType);
        setGuidancePrompt(guidance);
        try {
            doRunStreaming(userMessages, callback);
        } finally {
            clearConversationContext();
        }
    }

    /**
     * 裸执行（兼容旧调用方）。
     *
     * <p>旧实现直接跑循环、从不清理 ThreadLocal，{@code CrmChatController} 就是这么调的。
     * 这里同样转发到 {@link #runWithContextStreaming} 传 null，把 finally 清理的保证补上——
     * 宁可多一次 set/clear，也不能让 Tomcat 线程复用时串号。
     */
    public void runStreaming(List<Map<String, String>> userMessages, StreamCallback callback) {
        runWithContextStreaming(userMessages, callback, null, null);
    }

    /**
     * 带意图倾向的流式入口（阶段 3）。
     *
     * @param intentHint 由 {@code IntentRouter} 算出的意图倾向；null 走中性引导
     */
    public void runWithContextStreaming(List<Map<String, String>> userMessages,
                                        StreamCallback callback,
                                        String roleType,
                                        String guidance,
                                        IntentRouter.IntentHint intentHint) {
        setRoleHint(roleType);
        setGuidancePrompt(guidance);
        setIntentHint(intentHint);
        try {
            doRunStreaming(userMessages, callback);
        } finally {
            clearConversationContext();
        }
    }
}
