package cn.kmbeast.crm.workflow;

import cn.kmbeast.crm.agent.ReActAgent;
import cn.kmbeast.crm.agent.model.ReActResponse;
import cn.kmbeast.crm.agent.tool.ToolContext;
import cn.kmbeast.crm.dto.CrmChatRequest;
import cn.kmbeast.crm.dto.CrmChatResponse;
import cn.kmbeast.crm.sqlite.SqliteChatHistoryService;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.core.agent.Audience;
import cn.kmbeast.core.agent.ChatRouting;
import cn.kmbeast.core.harness.HarnessEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class SeaChatWorkflow {

    /**
     * 同步链路真正执行对话的 agent。
     *
     * <p><b>这条字段不能换成 {@code StreamingReActAgent}</b>：早期版本 Controller
     * 把角色提示设在 {@code streamingReActAgent} 上、却让 workflow 跑 {@code ReActAgent}，
     * 结果角色指令从没进过 prompt，AgentCoordinator 的 6 个角色形同虚设。
     * 角色必须由「真正执行对话的这个 bean」来设置，见 {@link #processChat}。
     */
    @Resource
    private ReActAgent reActAgent;

    @Resource
    private SqliteChatHistoryService chatHistoryService;

    @Resource
    private UserMapper userMapper;

    /** 2026-10-05：意图路由（6 个专业角色） */
    /**
     * 阶段 1：路由决策改由编排器负责（角色定义可来自 DB）。
     * 原先这里注入的是 {@code AgentCoordinator}，它现在只作为注册表的兜底角色源。
     */
    @Resource
    private cn.kmbeast.core.agent.orchestrator.AgentOrchestrator agentOrchestrator;

    /** 2026-10-05：健康档案数据引导（槽位抽取与追问话术生成） */
    @Resource
    private HarnessEngine harnessEngine;

    /** 阶段 3（2026-10-09）：意图识别统一入口（规则先行 + LLM 兜底） */
    @Resource
    private cn.kmbeast.core.agent.IntentResolver intentResolver;

    private static final String WELCOME_MESSAGE =
            "您好！我是健康管理员助手，可以帮您解答健康问题、分析体检报告、提供营养饮食建议。" +
            "请告诉我您想咨询什么？";

    /**
     * 处理一轮同步对话。
     *
     * @param authenticatedUserId 调用方已认证的 userId（JWT 里的那个）。
     *        <p><b>为什么必须支持显式传入</b>：早期版本只按 {@code phone} 反查 userId，
     *        但 {@code user_account} 存的是登录名（admin/lily/wang）而 {@code phone}
     *        字段在库里大量为 NULL，{@code WHERE phone='lily'} 永远查不到 →
     *        userId 恒为 null → 槽位不落库、{@code get_health_data} 工具直接罢工。
     *        <p>用户端本来就有 JWT，身份应当以 token 为准，而不是靠手机号反查。
     *        传 null 时才退回手机号反查（供 {@code /crm/**} 这类无 JWT 的通道使用）。
     * @param audience 调用端，见 {@link #route(Integer, String, String)}。
     *        <p><b>为什么这里是必填参数</b>：改造前本方法只有
     *        {@code processChat(request)} / {@code processChat(request, userId)} 两个重载，
     *        端信息在内部被写死成 null（= 不过滤），而调用点看上去毫无异样。
     *        两个「短一点、看起来更方便」的重载正是这个缺陷赖以存活的地方，
     *        所以它们不是被改成默认值，而是被删掉 —— 由编译器保证每个调用点都想过这件事。
     */
    public CrmChatResponse processChat(CrmChatRequest request, Integer authenticatedUserId, String audience) {
        String phoneNumber = request.getPhoneNumber();
        String query = request.getQuery();

        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return CrmChatResponse.builder()
                    .reply("手机号不能为空")
                    .rounds(0)
                    .isNewUser(false)
                    .build();
        }

        if (query == null || query.trim().isEmpty()) {
            return CrmChatResponse.builder()
                    .reply("请输入您的问题")
                    .rounds(0)
                    .isNewUser(false)
                    .build();
        }

        boolean isNewUser = chatHistoryService.isNewUser(phoneNumber);

        String sessionId;
        if (request.getSessionId() != null && !request.getSessionId().trim().isEmpty()) {
            sessionId = request.getSessionId();
        } else {
            sessionId = UUID.randomUUID().toString();
        }

        // 历史必须在落库之前读：先存再读会让当前这句既从历史里回来、又被显式加一遍，
        // 提示词里同一句话出现两次（token 翻倍，模型还容易重复回应）。
        // 读取点也留在 route() 之前 —— 万一路由抛错，用户这一句仍然已经留痕。
        List<Map<String, Object>> priorHistory = isNewUser
                ? List.of()
                : chatHistoryService.getHistory(phoneNumber, 10);

        // AG-10：保存失败不再静默，记录告警（不阻塞主流程）
        if (!chatHistoryService.saveMessage(phoneNumber, sessionId, "user", query, null, null)) {
            log.warn("[CRM] 用户消息保存失败: phone={}", phoneNumber);
        }
        // 有 JWT 身份优先用它；只有 /crm/** 这类无 token 的通道才靠手机号反查
        Integer userId = authenticatedUserId != null ? authenticatedUserId : resolveUserId(phoneNumber);

        // 2026-10-05：同步链路此前完全没有意图路由，所有问题走同一套 prompt。
        // 这里与流式链路共用同一个 route()，保证两条链路角色一致、行为可预期。
        ChatRouting routing = route(userId, query, audience);

        try {
            ToolContext.setPhoneAndUserId(phoneNumber, userId);
            // 阶段 2：标注调用方为用户（roleId=2）
            ToolContext.setIdentity(2, null);

            List<Map<String, String>> userMessages = new ArrayList<>();

            if (isNewUser) {
                userMessages.add(userMsg("user", WELCOME_MESSAGE + "\n\n现在用户说：" + query));
            } else {
                for (Map<String, Object> record : priorHistory) {
                    String role = (String) record.get("role");
                    String content = (String) record.get("content");
                    if (role != null && content != null) {
                        userMessages.add(userMsg(role, content));
                    }
                }
                userMessages.add(userMsg("user", query));
            }

            // runWithContext：由真正执行对话的 ReActAgent 自己开入口，
            // set 角色与引导 → 跑 ReAct → finally 清理 ThreadLocal。
            ReActResponse reActResponse = reActAgent.runWithContext(
                    userMessages, routing.getAgentType(), routing.getGuidance(),
                    routing.getIntentHint());
            String aiReply = reActResponse != null ? reActResponse.getContent() : "AI服务暂时不可用";
            List<String> toolsUsed = reActResponse != null ? reActResponse.getToolsUsed() : new ArrayList<>();

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("tools_used", toolsUsed);
            // AG-05：工具调用轨迹（含参数与结果状态）落库，供审计与恢复
            if (reActResponse != null && reActResponse.getToolDetails() != null
                    && !reActResponse.getToolDetails().isEmpty()) {
                metadata.put("tool_details", reActResponse.getToolDetails());
            }
            metadata.put("session_id", sessionId);
            if (!chatHistoryService.saveMessage(phoneNumber, sessionId, "assistant", aiReply, null, metadata)) {
                log.warn("[CRM] AI回答保存失败: phone={}", phoneNumber);
            }

            return CrmChatResponse.builder()
                    .reply(aiReply)
                    // AG-17 整改：原实现恒写 1，前端无法得知真实推理轮次；
                    // 改为工具调用次数（无工具调用时为 0，直接回答）。
                    .rounds(toolsUsed.size())
                    .toolsUsed(toolsUsed)
                    .sessionId(sessionId)
                    .isNewUser(isNewUser)
                    .agentType(routing.getAgentType())
                    .guidance(routing.getGuidance())
                    .build();
        } finally {
            ToolContext.clear();
        }
    }

    /**
     * 执行一轮「意图识别 + 健康槽位抽取 + 追问话术生成」，返回本轮路由结果。
     *
     * <p>同步链路由 {@link #processChat} 内部调用；流式链路
     * （{@code /user/chat/stream}、{@code /crm/chat/stream}）自己拼好初始消息后
     * 也调它，再用返回的 agentType/guidance 去跑流式 agent。
     * 这样四条链路共用同一套路由逻辑，不会出现「同步走医生、流式却走默认」的割裂。
     *
     * <p><b>副作用说明</b>：抽到的槽位会落库（只写 null 槽位，不覆盖已有值），
     * 因此一轮内重复调用不会产生脏数据。
     *
     * @param userId 当前用户 id；<b>为 null 时既不落库也不生成追问</b>
     *        （认不出身份就别做数据引导，否则会出现「反复追问却存不下来」）
     * @param query  本轮用户输入原文
     * @param audience 调用端（{@link Audience#USER} / {@link Audience#DOCTOR} /
     *        {@link Audience#ADMIN}），决定本轮哪些 Agent 可被路由到：
     *        {@code audience='all'} 的 Agent 对所有端可见，只在医生端开放的 Agent
     *        在用户端不会被选中。
     *
     *        <p><b>为什么它是必填参数，而不是给它一个默认值</b>：改造前这里硬编码传
     *        {@code null}，而 {@code null} 在编排器里等于「不过滤」—— 于是管理员
     *        在后台为医生端新建的 Agent，会从用户端的普通问诊里被路由到。
     *        给个「默认 user」看似能兜住，但把它接到医生端调用点上时，
     *        就会静默地把医生端专属 Agent 全部藏起来。
     *        两种错都只表现为「路由结果不太对」，没有一处日志能归因到这一步。
     *        做成必填，编译器会替我们检查每个调用点都真的想过「我是哪一端」。</p>
     */
    public ChatRouting route(Integer userId, String query, String audience) {
        // 阶段 3 统一入口：IntentResolver（规则先行 + LLM 兜底）产出 Resolution。
        // 独立 try，失败绝不拖垮主对话——降级为中性 CHAT 意图。
        cn.kmbeast.core.agent.Resolution resolution = null;
        try {
            if (intentResolver != null) {
                resolution = intentResolver.resolve(query);
            }
        } catch (Exception e) {
            log.warn("[CRM] 意图识别失败，本轮退回中性意图: {}", e.getMessage());
        }

        // 角色识别：优先消费 Resolution（非 CHAT 直接定角色），CHAT 才走关键词打分。
        // 阶段 1 起改由 AgentOrchestrator 决策 —— 角色定义与关键词表来自 AgentRegistry
        // （DB 驱动，表未建时自动回退到 AgentCoordinator 的内置 6 角色）。
        // 阶段 3（多端解耦，2026-10-13）：audience 由调用方显式传入并原样透传给编排器，
        // 不再写死 null。写死 null 时「只在医生端开放的 Agent」在用户端照样会被选中，
        // 因为 null 对编排器而言是「不过滤」而不是「用户端」。
        String agentType = "general_assistant";
        try {
            String identified = agentOrchestrator.route(resolution, query, audience);
            if (identified != null && !identified.isBlank()) {
                agentType = identified;
            }
        } catch (Exception e) {
            log.warn("[CRM] 意图识别失败，本轮退回默认角色: {}", e.getMessage());
        }

        Set<String> extracted = new LinkedHashSet<>();
        try {
            Map<String, String> slots = harnessEngine.extractSlots(query);
            if (slots != null && !slots.isEmpty()) {
                extracted.addAll(slots.keySet());
                if (userId != null) {
                    harnessEngine.persistSlots(userId, slots);
                }
            }
        } catch (Exception e) {
            // 数据引导属于增强能力，失败不能拖垮主对话——降级为无追问继续回答。
            log.warn("[CRM] 健康槽位抽取失败，本轮跳过数据引导: {}", e.getMessage());
        }

        String guidance = null;
        if (userId != null) {
            try {
                // 必须按 userId 显式取：CRM 通道走手机号鉴权，LocalThreadHolder 里没有 userId，
                // 用 buildGuidancePromptForCurrentUser() 会读空档案、追问策略失效。
                guidance = harnessEngine.buildGuidancePromptForUser(userId);
            } catch (Exception e) {
                log.warn("[CRM] 追问话术生成失败，本轮不追问: {}", e.getMessage());
            }
        }
        // ⚠️ userId 解析不出来（手机号未绑定、CRM 通道传入陌生号码）时**一律不追问**：
        // loadUserSlots(null) 会返回「全部槽位缺失」，于是每轮都追着问身高体重年龄，
        // 而采集到的值又因为没有 userId 无法落库——用户被反复盘问却什么也没存下来。
        // 认不出身份就不做数据引导，这是比「假装在引导」更诚实的降级。

        // 意图倾向 hint 取自 Resolution（含 LLM 兜底命中后的引导文本），供 prompt 引导与 fail-safe
        cn.kmbeast.crm.agent.IntentRouter.IntentHint intentHint =
                resolution != null ? resolution.hint() : null;

        return new ChatRouting(agentType, extracted, guidance, intentHint);
    }

    public List<Map<String, String>> buildInitialMessages(CrmChatRequest request) {
        String phoneNumber = request.getPhoneNumber();
        String query = request.getQuery();

        boolean isNewUser = chatHistoryService.isNewUser(phoneNumber);
        List<Map<String, String>> messages = new ArrayList<>();

        if (isNewUser) {
            messages.add(userMsg("user", WELCOME_MESSAGE + "\n\n现在用户说：" + query));
        } else {
            List<Map<String, Object>> history = chatHistoryService.getHistory(phoneNumber, 10);
            for (Map<String, Object> record : history) {
                String role = (String) record.get("role");
                String content = (String) record.get("content");
                if (role != null && content != null) {
                    messages.add(userMsg(role, content));
                }
            }
            messages.add(userMsg("user", query));
        }

        return messages;
    }

    public List<Map<String, Object>> getHistory(String phoneNumber) {
        return chatHistoryService.getHistory(phoneNumber, 50);
    }

    public Map<String, Object> getSqliteStats() {
        return chatHistoryService.getStats();
    }

    public List<Map<String, Object>> executeSqlQuery(String sql) {
        // 管理员运维入口：只做只读校验，不做租户隔离（由 API Key 拦截器保证调用方身份）
        return chatHistoryService.executeQuery(sql, null);
    }

    private Integer resolveUserId(String phoneNumber) {
        try {
            User user = userMapper.findByPhone(phoneNumber);
            return user != null ? user.getId() : null;
        } catch (Exception e) {
            log.warn("[CRM] phone→userId映射失败: phone={}", phoneNumber);
            return null;
        }
    }

    private Map<String, String> userMsg(String role, String content) {
        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }
}
