package cn.kmbeast.crm.agent;

import cn.kmbeast.config.AiConfig;
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

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
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

    protected OkHttpClient httpClient;

    private final ExecutorService toolExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "crm-tool");
        t.setDaemon(true);
        return t;
    });

    protected static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是一个健康管理 CRM 系统的 AI 助手，名叫\"小健\"。\n\n" +
            "## 核心功能\n" +
            "你有三大核心功能：\n\n" +
            "### 1. 药品推荐与价格查询\n" +
            "当用户咨询药品相关问题时，使用 search_drug 工具搜索药品信息。\n" +
            "你可以为用户推荐合适的药品，展示药品名称、价格、规格、说明等信息。\n" +
            "如果用户需要购买，引导用户前往药品订阅页面。\n\n" +
            "### 2. 推荐AI医生\n" +
            "当用户有健康问题需要专业咨询时，推荐用户使用AI医生功能。\n" +
            "我们有以下AI医生角色：\n" +
            "- **全科医生**：症状分析、分诊建议、用药指导\n" +
            "- **营养师**：饮食规划、营养搭配、体重管理\n" +
            "- **心理咨询师**：情绪疏导、压力管理、心理支持\n" +
            "- **报告分析师**：体检报告解读、异常指标分析\n" +
            "- **全能助手**：综合健康咨询\n" +
            "告知用户可以在\"AI健康分析\"页面选择对应角色进行咨询。\n\n" +
            "### 3. 推荐健康帖子\n" +
            "当用户询问健康知识、养生方法等问题时，使用 search_knowledge 工具检索相关文章。\n" +
            "为用户推荐系统中的健康资讯文章，并简要介绍文章内容。\n\n" +
            "## 工作流程\n" +
            "1. 分析用户问题，判断属于哪个功能类别\n" +
            "2. 使用对应工具获取信息（search_drug、search_knowledge、get_chat_history、get_health_data）\n" +
            "3. 综合信息给出专业、有帮助的回答\n\n" +
            "## 规则\n" +
            "- 涉及药品推荐时，必须使用 search_drug 获取真实药品数据\n" +
            "- 涉及健康知识时，使用 search_knowledge 检索文章\n" +
            "- 涉及用户历史对话时，优先使用 get_chat_history\n" +
            "- 涉及用户健康数据（血压、血糖、体重等），必须使用 get_health_data\n" +
            "- 对模糊问题主动追问\n" +
            "- 医疗建议必须附免责声明：\"以上建议仅供参考，具体用药请遵医嘱\"\n" +
            "- 用中文回答，语气亲切专业";

    protected String getSystemPrompt() {
        String configured = crmConfig.getReactPrompt();
        return (configured != null && !configured.isEmpty()) ? configured : DEFAULT_SYSTEM_PROMPT;
    }

    @PostConstruct
    public void initHttpClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(aiConfig.getConnectTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(aiConfig.getReadTimeout(), TimeUnit.MILLISECONDS)
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                .build();
    }

    protected List<Map<String, Object>> buildInitialMessages(List<Map<String, String>> userMessages) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(buildMap("role", "system", "content", getSystemPrompt()));
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
        toolMsg.put("content", result.toJson());
        messages.add(toolMsg);
    }

    protected ToolResult executeTool(ToolCall tc) {
        Tool tool = toolRegistry.get(tc.getName());
        if (tool == null) {
            return ToolResult.error("未知工具: " + tc.getName());
        }
        try {
            // 捕获当前线程的ToolContext，在线程池中恢复
            final String phone = ToolContext.getString("phoneNumber");
            final Integer userId = ToolContext.getInt("userId");

            Future<ToolResult> future = toolExecutor.submit(() -> {
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
            log.warn("[ReAct] 工具执行超时: {}, timeout={}s", tc.getName(), crmConfig.getToolTimeoutSeconds());
            return ToolResult.error("工具执行超时(" + crmConfig.getToolTimeoutSeconds() + "s): " + tc.getName());
        } catch (ExecutionException e) {
            log.error("[ReAct] 工具执行异常: {}", tc.getName(), e.getCause());
            return ToolResult.error("工具执行异常: " +
                    (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("工具执行被中断: " + tc.getName());
        }
    }

    protected ReActResponse callLLMWithTools(List<Map<String, Object>> messages) {
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
            throw e;
        } catch (Exception e) {
            throw CrmException.aiUnavailable(e.getMessage());
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
