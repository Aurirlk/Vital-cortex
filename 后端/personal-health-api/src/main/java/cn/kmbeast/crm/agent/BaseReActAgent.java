package cn.kmbeast.crm.agent;

import cn.kmbeast.config.AiConfig;
import cn.kmbeast.core.http.HttpClientFactory;
import cn.kmbeast.crm.CrmException;
import cn.kmbeast.crm.agent.model.ReActResponse;
import cn.kmbeast.crm.agent.model.ToolCall;
import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.crm.agent.tool.Tool;
import cn.kmbeast.crm.agent.tool.ToolContext;
import cn.kmbeast.crm.config.CrmConfig;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public abstract class BaseReActAgent {

    @Resource
    protected AiConfig aiConfig;

    @Resource
    protected CrmConfig crmConfig;

    @Resource
    protected ToolRegistry toolRegistry;

    /** 2026-10-04：共享 HTTP 客户端工厂（统一超时/连接池/线程池上限） */
    @Resource
    protected HttpClientFactory httpClientFactory;

    protected OkHttpClient httpClient;

    /**
     * AG-06 整改：原实现用 {@code newCachedThreadPool()}——线程数无上限，
     * 慢工具可无限堆积线程。改为有界池（核心 2 / 最大 8 / 队列 16），
     * 超出即触发饱和策略：直接丢弃并返回错误，避免打满线程。
     */
    protected final ExecutorService toolExecutor = new ThreadPoolExecutor(
            2, 8, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16),
            r -> {
                Thread t = new Thread(r, "crm-tool");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy());
    protected static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    /** AG-08：LLM 调用重试（429 / 5xx 指数退避） */
    private static final int LLM_MAX_RETRIES = 3;
    private static final long LLM_BASE_BACKOFF_MS = 400;
    private static final long LLM_MAX_BACKOFF_MS = 5000;

    private static final String DEFAULT_SYSTEM_PROMPT =
            """
            你是一个健康管理员 CRM 系统的 AI 助手，名叫"小健"。
            
            ## 核心功能
            你有三大核心功能：
            
            ### 1. 药品推荐与价格查询
            当用户咨询药品相关问题时，使用 search_drug 工具搜索药品信息。
            你可以为用户推荐合适的药品，展示药品名称、价格、规格、说明等信息。
            如果用户需要购买，引导用户前往药品订阅页面。
            
            ### 2. 推荐AI医生
            当用户有健康问题需要专业咨询时，推荐用户使用AI医生功能。
            我们有以下AI医生角色：
            - **全科医生**：症状分析、分诊建议、用药指导
            - **营养师**：饮食规划、营养搭配、体重管理
            - **心理咨询师**：情绪疏导、压力管理、心理支持
            - **报告分析师**：体检报告解读、异常指标分析
            - **全能助手**：综合健康咨询
            告知用户可以在"AI健康分析"页面选择对应角色进行咨询。
            
            ### 3. 推荐健康帖子
            当用户询问健康知识、养生方法等问题时，使用 search_knowledge 工具检索相关文章。
            为用户推荐系统中的健康资讯文章，并简要介绍文章内容。
            
            ## 工作流程
            1. 分析用户问题，判断属于哪个功能类别
            2. 使用对应工具获取信息（search_drug、search_knowledge、get_chat_history、get_health_data）
            3. 综合信息给出专业、有帮助的回答
            
            ## 规则
            - 涉及药品推荐时，必须使用 search_drug 获取真实药品数据
            - 涉及健康知识时，使用 search_knowledge 检索文章
            - 涉及用户历史对话时，优先使用 get_chat_history
            - 涉及用户健康数据（血压、血糖、体重等），必须使用 get_health_data
            - 对模糊问题主动追问
            - 医疗建议必须附免责声明："以上建议仅供参考，具体用药请遵医嘱"
            - 用中文回答，语气亲切专业""";

    protected String getSystemPrompt() {
        String configured = crmConfig.getReactPrompt();
        return (configured != null && !configured.isEmpty()) ? configured : DEFAULT_SYSTEM_PROMPT;
    }

    /**
     * 本轮的角色类型（2026-10-04 新增）。
     *
     * <p><b>背景</b>：{@code AgentCoordinator} 定义了 6 个专业角色（全科医生/营养师/
     * 心理咨询师/报告分析师/健康助手/全能助手），并实现了意图识别，
     * 但<b>对话主流程从未调用它</b> —— 角色定义形同虚设，所有问题都走同一套 prompt。
     *
     * <p><b>⚠️ 必须用 ThreadLocal 而非普通字段</b>：{@code BaseReActAgent} 是
     * {@code @Service} 单例，被所有用户请求共享。若用实例字段，
     * 并发场景下 A 用户的角色提示会串到 B 用户身上 —— 属于严重的上下文泄露。
     */
    private final ThreadLocal<String> roleHint = new ThreadLocal<>();

    /** Harness 生成的追问指令（无缺失槽位时为 null） */
    private final ThreadLocal<String> guidancePrompt = new ThreadLocal<>();

    /**
     * 设置角色提示（由 Controller 层在调用前根据意图识别结果设置）
     *
     * @param roleType 角色编码，如 doctor / nutritionist；null 视为默认角色
     */
    public void setRoleHint(String roleType) {
        roleHint.set((roleType == null || roleType.isBlank()) ? "general_assistant" : roleType);
    }

    public String getRoleHint() {
        String v = roleHint.get();
        return v != null ? v : "general_assistant";
    }

    /**
     * 设置数据引导指令（由 {@code HarnessEngine} 生成）
     *
     * @param prompt 追问话术；null 或空串表示档案已完整
     */
    public void setGuidancePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            guidancePrompt.remove();
        } else {
            guidancePrompt.set(prompt);
        }
    }

    /**
     * 角色专属指令表。
     *
     * <p>比动态改 system prompt 更稳的做法是<b>追加段落</b>：
     * 基础能力（工具用法、免责声明）来自配置里的 prompt，不动；
     * 这里只追加「本轮以什么身份、侧重什么」，模型既保留通用能力又获得角色聚焦。
     */
    private static final Map<String, String> ROLE_INSTRUCTIONS = Map.of(
            "doctor", """
                    ## 本轮角色：全科医生
                    侧重症状分析、分诊建议与用药指导。
                    遇到可能危及生命的症状（胸痛、呼吸困难、意识障碍、剧烈头痛伴呕吐等），
                    必须首先明确建议立即就医或拨打急救电话，不要继续追问细节。
                    """,
            "nutritionist", """
                    ## 本轮角色：营养师
                    侧重饮食规划、营养搭配与体重管理。
                    给建议时尽量给出可执行的具体食物与份量，而不是笼统的「少吃多餐」。
                    如涉及热量摄入，需要先了解用户的身高体重（若未提供应主动询问）。
                    """,
            "psychologist", """
                    ## 本轮角色：心理咨询师
                    侧重情绪疏导、压力管理与心理支持。
                    遇到自伤/自杀意念等高危信号时，优先进行安全评估并建议寻求专业帮助，
                    不要停留在一般性的情绪建议。
                    """,
            "analyst", """
                    ## 本轮角色：报告分析师
                    侧重体检报告解读与异常指标分析。
                    解读时先说明该指标正常范围，再判断异常程度，最后给出建议。
                    涉及具体数值时优先调用 get_health_data 工具取真实数据，不要凭空推测。
                    """,
            "consultant", """
                    ## 本轮角色：健康助手
                    侧重健康生活方式建议：运动、 作息、季节性养生、预防保健。
                    """,
            "general_assistant", """
                    ## 本轮角色：全能助手
                    根据问题类型自行判断侧重方向，必要时提示用户可以切换到更专业的角色。
                    """
    );

    /**
     * 组装最终 system prompt = 基础能力 + 角色指令 + 数据追问指令。
     *
     * <p>相比直接替换 prompt，追加方式的好处：
     * 管理员在 {@code crm.react.prompt} 里配的通用能力不会被角色覆盖丢失。
     *
     * <p><b>2026-10-04 关键改动：读取即清除（一次性消费）</b>。
     * 角色与引导只对<b>本轮对话</b>有效，实现方式是读出来的同时 {@code remove()}。
     * 原因：本类是 @Service 单例，上下文存在 ThreadLocal 里，而 Tomcat 会复用线程。
     * 若依赖「每个调用方都在 finally 里记得调 {@code clearConversationContext()}」，
     * 只要有一个调用方漏掉（项目里就有 {@code CrmChatController} 两条路径从未清理），
     * 下一个复用该线程的用户就会读到上一位的角色指令——这是健康咨询场景下
     * 可能误导用户（比如把营养问题当心理问题回应）的上下文泄露。
     * 改成读取即消费后，即使调用方完全忘记清理，最坏情况也只是本轮没有角色增强，
     * 而不是把别人的角色带进来。显式清理仍保留，作为可读性更好的意图表达。
     */
    protected String enhanceSystemPrompt() {
        StringBuilder sb = new StringBuilder(getSystemPrompt());
        // get + remove：一次性取出，取完即失效
        String role = roleHint.get();
        roleHint.remove();
        String instruction = ROLE_INSTRUCTIONS.get(role);
        if (instruction != null) {
            sb.append("\n\n").append(instruction);
        }
        String guidance = guidancePrompt.get();
        guidancePrompt.remove();
        if (guidance != null) {
            sb.append(guidance);
        }
        return sb.toString();
    }

    /**
     * 清理本线程的对话上下文。
     *
     * <p><b>必须调用</b>：Tomcat 线程池会复用线程，若不清理，
     * 下一个请求（可能是另一个用户）会读到上一个请求遗留的角色提示与追问指令。
     * 建议在 Controller 的 finally 里调用。
     */
    public void clearConversationContext() {
        roleHint.remove();
        guidancePrompt.remove();
    }

    /**
     * 2026-10-04：改为从 {@link HttpClientFactory} 取共享实例。
     * 原来这里自建 client，与另外 4 处 LLM 调用方各占一套线程池与连接池。
     */
    @PostConstruct
    public void initHttpClient() {
        this.httpClient = httpClientFactory.llmClient();
    }

    protected List<Map<String, Object>> buildInitialMessages(List<Map<String, String>> userMessages) {
        List<Map<String, Object>> messages = new ArrayList<>();
        // 2026-10-04：用 enhanceSystemPrompt() 追加角色指令与数据追问指令，
        // 而不是裸的 getSystemPrompt()，让 AgentCoordinator 的角色路由
        // 与 HarnessEngine 的槽位引导真正进入对话流程。
        messages.add(buildMap("role", "system", "content", enhanceSystemPrompt()));
        if (userMessages != null) {
            for (Map<String, String> msg : userMessages) {
                messages.add(buildMap("role", msg.get("role"), "content", msg.get("content")));
            }
        }
        return messages;
    }

    protected List<Map<String, Object>> buildToolCallsForMessage(List<ToolCall> toolCalls) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ToolCall tc : toolCalls) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", tc.getId());
            item.put("type", "function");
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("name", tc.getName());
            func.put("arguments", JSON.toJSONString(tc.getArguments()));
            item.put("function", func);
            list.add(item);
        }
        return list;
    }

    protected void addAssistantToolCallMessage(List<Map<String, Object>> messages, List<ToolCall> toolCalls) {
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("tool_calls", buildToolCallsForMessage(toolCalls));
        assistantMsg.put("content", "");
        messages.add(assistantMsg);
    }

    protected void addToolResultMessage(List<Map<String, Object>> messages, ToolCall tc, ToolResult result) {
        Map<String, Object> toolMsg = new LinkedHashMap<>();
        toolMsg.put("role", "tool");
        toolMsg.put("tool_call_id", tc.getId());
        // AG-14 整改：工具返回的 content 本身常是 JSON 字符串，
        // 直接整体 toJson 会把内层 JSON 转义成 \"...\" 浪费 token 且降低模型解析质量。
        // 若 content 是合法 JSON，先解析成结构化对象再放入消息。
        toolMsg.put("content", tryParseJson(result.getContent()));
        messages.add(toolMsg);
    }

    /**
     * 若字符串是合法 JSON（对象或数组）则解析为结构化值，否则原样返回。
     */
    private Object tryParseJson(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return JSON.parse(trimmed);
            } catch (Exception ignored) {
                // 不是 JSON，按原文返回
            }
        }
        return content;
    }

    protected ToolResult executeTool(ToolCall tc) {
        Tool tool = toolRegistry.get(tc.getName());
        if (tool == null) {
            return ToolResult.error("未知工具: " + tc.getName());
        }
        // AG-16：执行前按 schema 校验参数类型与必填项，避免 ClassCastException
        String argError = ToolArgsValidator.validate(tool, tc.getArguments());
        if (argError != null) {
            log.warn("[ReAct] 工具参数校验失败: {} - {}", tc.getName(), argError);
            return ToolResult.error("参数校验失败: " + argError);
        }
        Future<ToolResult> future = null;
        try {
            // 捕获当前线程的ToolContext，在线程池中恢复
            final String phone = ToolContext.getString("phoneNumber");
            final Integer userId = ToolContext.getInt("userId");

            future = toolExecutor.submit(() -> {
                // 在线程池线程中设置ToolContext
                ToolContext.setPhoneAndUserId(phone, userId);
                try {
                    return tool.execute(tc.getArguments());
                } finally {
                    ToolContext.clear();
                }
            });
            long timeout = crmConfig.getToolTimeoutSeconds();
            return future.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // AG-06 整改：超时必须 cancel(true) 中断工具线程，
            // 否则慢工具在后台继续执行（可能反复调用外部 API、继续消耗资源）。
            if (future != null) {
                future.cancel(true);
            }
            log.warn("[ReAct] 工具执行超时并已取消: {}, timeout={}s", tc.getName(), crmConfig.getToolTimeoutSeconds());
            return ToolResult.error("工具执行超时(" + crmConfig.getToolTimeoutSeconds() + "s): " + tc.getName());
        } catch (ExecutionException e) {
            log.error("[ReAct] 工具执行异常: {}", tc.getName(), e.getCause());
            return ToolResult.error("工具执行异常: " +
                    (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
        } catch (RejectedExecutionException e) {
            log.warn("[ReAct] 工具线程池已满，拒绝执行: {}", tc.getName());
            return ToolResult.error("工具执行队列已满，请稍后重试: " + tc.getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("工具执行被中断: " + tc.getName());
        }
    }

    protected ReActResponse callLLMWithTools(List<Map<String, Object>> messages) {
        CrmException lastError = null;
        for (int attempt = 0; attempt < LLM_MAX_RETRIES; attempt++) {
            try {
                JSONObject body = new JSONObject();
                body.put("model", aiConfig.getModel());
                body.put("messages", messages);
                body.put("temperature", crmConfig.getReactTemperature());
                body.put("tools", toolRegistry.buildToolsArray());
                body.put("tool_choice", "auto");

                Request request = new Request.Builder()
                        .url(aiConfig.getApiUrl())
                        .addHeader("Authorization", "Bearer " + aiConfig.getApiKey())
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = "";
                        try {
                            if (response.body() != null) errorBody = response.body().string();
                        } catch (Exception ignored) {}
                        // AG-08 整改：429（限流）与 5xx（服务端故障）指数退避重试，
                        // 4xx（参数/鉴权错误）重试无意义，直接抛出。
                        if ((response.code() == 429 || response.code() >= 500)
                                && attempt < LLM_MAX_RETRIES - 1) {
                            long waitMs = Math.min(
                                    LLM_BASE_BACKOFF_MS * (1L << attempt), LLM_MAX_BACKOFF_MS);
                            log.warn("[ReAct] LLM 返回 {}，{}ms 后重试 ({}/{})",
                                    response.code(), waitMs, attempt + 1, LLM_MAX_RETRIES);
                            sleep(waitMs);
                            continue;
                        }
                        throw CrmException.aiUnavailable("HTTP " + response.code() + " " + errorBody);
                    }

                    if (response.body() == null) {
                        throw CrmException.aiUnavailable("AI服务返回为空");
                    }

                    String respBody = response.body().string();
                    JSONObject json = JSON.parseObject(respBody);

                    if (json.containsKey("error")) {
                        throw CrmException.aiUnavailable(
                                json.getJSONObject("error").getString("message"));
                    }

                    JSONArray choices = json.getJSONArray("choices");
                    if (choices == null || choices.isEmpty()) {
                        throw CrmException.aiUnavailable("返回choices为空");
                    }

                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject message = choice.getJSONObject("message");

                    if (message.containsKey("tool_calls") && message.get("tool_calls") != null) {
                        JSONArray callArray = message.getJSONArray("tool_calls");
                        List<ToolCall> toolCalls = new ArrayList<>();
                        for (int i = 0; i < callArray.size(); i++) {
                            JSONObject tc = callArray.getJSONObject(i);
                            JSONObject func = tc.getJSONObject("function");
                            ToolCall toolCall = ToolCall.builder()
                                    .id(tc.getString("id"))
                                    .name(func.getString("name"))
                                    .arguments(JSON.parseObject(func.getString("arguments"), Map.class))
                                    .build();
                            toolCalls.add(toolCall);
                        }
                        return ReActResponse.toolCalls(toolCalls);
                    }

                    String content = message.getString("content");
                    if (content != null && !content.isEmpty()) {
                        return ReActResponse.text(content);
                    }

                    throw CrmException.aiUnavailable("LLM返回空内容");
                }
            } catch (CrmException e) {
                // 仅对可重试状态码做退避（上面 continue 分支），其余直接抛出
                lastError = e;
                if (!isRetryableStatus(e)) {
                    throw e;
                }
            } catch (IOException e) {
                lastError = CrmException.aiUnavailable("网络异常: " + e.getMessage());
                if (attempt < LLM_MAX_RETRIES - 1) {
                    long waitMs = Math.min(
                            LLM_BASE_BACKOFF_MS * (1L << attempt), LLM_MAX_BACKOFF_MS);
                    log.warn("[ReAct] LLM 网络异常，{}ms 后重试 ({}/{}): {}",
                            waitMs, attempt + 1, LLM_MAX_RETRIES, e.getMessage());
                    sleep(waitMs);
                }
            } catch (Exception e) {
                // JSON 解析/参数错误：重试无意义，直接抛
                throw CrmException.aiUnavailable(e.getMessage());
            }
        }
        throw lastError != null ? lastError : CrmException.aiUnavailable("LLM 调用失败");
    }

    /**
     * 判定 CrmException 是否属于可重试状态码（429/5xx）。
     */
    private boolean isRetryableStatus(CrmException e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("HTTP 429") || msg.contains("HTTP 5")
                || msg.contains("HTTP 50") || msg.contains("HTTP 51")
                || msg.contains("HTTP 52") || msg.contains("HTTP 53"));
    }

    /**
     * AG-04：不带 tools 的普通 LLM 调用，用于轮次耗尽后的强制总结。
     * 复用 callLLMWithTools 的重试与解析逻辑，仅去掉 tools 字段。
     */
    protected String callLLMPlain(List<Map<String, Object>> messages) {
        CrmException lastError = null;
        for (int attempt = 0; attempt < LLM_MAX_RETRIES; attempt++) {
            try {
                JSONObject body = new JSONObject();
                body.put("model", aiConfig.getModel());
                body.put("messages", messages);
                body.put("temperature", crmConfig.getReactTemperature());

                Request request = new Request.Builder()
                        .url(aiConfig.getApiUrl())
                        .addHeader("Authorization", "Bearer " + aiConfig.getApiKey())
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = "";
                        try {
                            if (response.body() != null) errorBody = response.body().string();
                        } catch (Exception ignored) {}
                        if ((response.code() == 429 || response.code() >= 500)
                                && attempt < LLM_MAX_RETRIES - 1) {
                            long waitMs = Math.min(
                                    LLM_BASE_BACKOFF_MS * (1L << attempt), LLM_MAX_BACKOFF_MS);
                            sleep(waitMs);
                            continue;
                        }
                        throw CrmException.aiUnavailable("HTTP " + response.code() + " " + errorBody);
                    }
                    if (response.body() == null) {
                        throw CrmException.aiUnavailable("AI服务返回为空");
                    }
                    String respBody = response.body().string();
                    JSONObject json = JSON.parseObject(respBody);
                    JSONArray choices = json.getJSONArray("choices");
                    if (choices == null || choices.isEmpty()) {
                        throw CrmException.aiUnavailable("返回choices为空");
                    }
                    JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                    String content = message.getString("content");
                    if (content != null && !content.isEmpty()) {
                        return content;
                    }
                    throw CrmException.aiUnavailable("LLM返回空内容");
                }
            } catch (CrmException e) {
                lastError = e;
                if (!isRetryableStatus(e)) {
                    throw e;
                }
            } catch (IOException e) {
                lastError = CrmException.aiUnavailable("网络异常: " + e.getMessage());
                if (attempt < LLM_MAX_RETRIES - 1) {
                    long waitMs = Math.min(
                            LLM_BASE_BACKOFF_MS * (1L << attempt), LLM_MAX_BACKOFF_MS);
                    sleep(waitMs);
                }
            } catch (Exception e) {
                throw CrmException.aiUnavailable(e.getMessage());
            }
        }
        throw lastError != null ? lastError : CrmException.aiUnavailable("LLM 调用失败");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> buildMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
