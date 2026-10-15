package cn.kmbeast.core.agent.orchestrator;

import cn.kmbeast.core.agent.AgentCoordinator;
import cn.kmbeast.core.agent.Audience;
import cn.kmbeast.core.agent.Resolution;
import cn.kmbeast.core.agent.registry.AgentDefinition;
import cn.kmbeast.core.agent.registry.AgentRegistry;
import cn.kmbeast.crm.agent.IntentRouter;
import jakarta.annotation.Resource;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 编排器：决定「这一轮该由哪个 Agent 回答」。
 *
 * <p>它取代了原先 {@code AgentCoordinator.identifyAgent} 作为路由决策者的角色。
 * 两者的区别不在算法，而在<b>角色定义的来源</b>：改造前是与 DB 无关的硬编码 Map，
 * 改造后是 {@link AgentRegistry}（DB 驱动 + 内置兜底）。算法本身刻意保持等价——
 * 同一份关键词表、同样的「命中计数取最大、平票按声明序」规则，
 * 这样在 DB 尚未建表的环境里，路由结果与改造前<b>逐个输入都相同</b>。</p>
 *
 * <p><b>为什么保留 AgentCoordinator 而不是删掉它</b>：它仍然承担两件事——
 * ① 注册表在 DB 不可用时的兜底角色定义源；
 * ② {@code /agent/roles} 与 {@code agentName} 回执所需的中文名
 * （见 {@link #agentName(String)}，注册表优先、它作兜底）。
 * 删了它就得把这两件事复制到本类，反而多一处真值源。</p>
 */
@Slf4j
@Component
public class AgentOrchestrator {

    @Resource
    private AgentRegistry agentRegistry;

    /**
     * 仅用于把 key 换成中文名的兜底来源；路由决策不依赖它。
     */
    @Resource
    private AgentCoordinator agentCoordinator;

    /**
     * 决定路由目标。
     *
     * @param resolution 意图识别结论，可为 null
     * @param query      用户原始输入
     * @param audience   调用端（{@link Audience#USER} / {@link Audience#DOCTOR} / {@link Audience#ADMIN}）；
     *                   null = 不按端过滤。传 null 时所有 Agent 都可见，与改造前一致。
     * @return 决策详情，{@link RouteDecision#getAgentKey()} 永不为 null
     */
    public RouteDecision decide(Resolution resolution, String query, String audience) {
        // ① 意图明确（非 CHAT）时意图优先，不让关键词打分把结论带偏
        if (resolution != null && resolution.intent() != null
                && resolution.intent() != IntentRouter.Intent.CHAT) {
            String mapped = mapIntentToAgent(resolution.intent());
            if (isUsable(mapped, audience)) {
                return RouteDecision.builder()
                        .agentKey(mapped)
                        .agentName(nameOf(mapped))
                        .reason("intent:" + resolution.intent().name())
                        .build();
            }
            // 意图指向的 Agent 已被管理员停用/删除 —— 不能返回一个不存在的 key，
            // 退回关键词打分，而不是让调用方拿到一个查不到的 Agent。
            log.info("[Orchestrator] 意图 {} 指向的 Agent[{}] 当前不可用，退回关键词路由",
                    resolution.intent().name(), mapped);
        }

        // ② 关键词打分
        RouteDecision scored = scoreByKeywords(query, audience);
        if (scored != null) {
            return scored;
        }

        // ③ 该端兜底 Agent
        AgentDefinition def = agentRegistry.getDefault(audience);
        return RouteDecision.builder()
                .agentKey(def.getAgentKey())
                .agentName(def.getName())
                .reason("default")
                .build();
    }

    /** 只要 key 的便捷重载。 */
    public String route(Resolution resolution, String query, String audience) {
        return decide(resolution, query, audience).getAgentKey();
    }

    /** 供 {@code AgentController} 的「按文本猜角色」入口沿用。 */
    public String identifyAgent(String userMessage) {
        return route(null, userMessage, null);
    }

    /**
     * 把 Agent key 换成展示用中文名，供接口回执与前端展示。
     *
     * <p>取值顺序：<b>注册表 → AgentCoordinator 内置 → key 本身</b>。</p>
     *
     * <p><b>为什么不能继续用 {@code AgentCoordinator.getAgentRole(key).getName()}</b>：
     * 它的实现是 {@code getOrDefault(key, general_assistant)} —— <b>查不到就返回「全能助手」</b>。
     * 改造前只有 6 个写死的角色，这个兜底不会出错；改造后管理员可以在后台
     * <b>新建任意多个 Agent</b>，于是一个叫「睡眠顾问」的智能体在回执里会显示成
     * 「全能助手」。不报错、不告警，只是名字错了 —— 排查时看到的是「明明配了睡眠顾问，
     * 前端却显示全能助手」，而没有一处日志提示名字来自兜底。</p>
     *
     * <p>所以这里改成：注册表认识就用注册表的名字；注册表不认识、但内置角色表确实
     * <b>包含</b>这个 key 才用内置名；两者都不认识时<b>原样返回 key</b> ——
     * 一个陌生的英文 key 至少指向了真实的那个 Agent，比一个张冠李戴的中文名有用。</p>
     */
    public String agentName(String agentKey) {
        if (agentKey == null || agentKey.isBlank()) {
            return agentKey;
        }
        AgentDefinition def = agentRegistry.get(agentKey);
        if (def != null && def.getName() != null && !def.getName().isBlank()) {
            return def.getName();
        }
        if (agentCoordinator != null) {
            AgentCoordinator.AgentRole role = agentCoordinator.getAllAgentRoles().get(agentKey);
            // 这里刻意不用 getAgentRole()：它查不到时会返回 general_assistant，
            // 等于用一个错误的答案冒充「查到了」。要的是 containsKey 这个事实。
            if (role != null && role.getName() != null && !role.getName().isBlank()) {
                return role.getName();
            }
        }
        return agentKey;
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 关键词打分。
     *
     * <p>与改造前的实现逐条对齐：遍历顺序即 agent 的声明序（现在是 sort_order 升序），
     * 每个命中关键词记 1 分，取最高分；<b>平票时先遍历到的胜出</b>
     * （用 {@code >} 而非 {@code >=} 比较，天然实现「首个最大者获胜」）。</p>
     *
     * @return null 表示无人命中，由调用方走兜底
     */
    private RouteDecision scoreByKeywords(String query, String audience) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        String lowerQuery = query.toLowerCase();
        RouteDecision best = null;
        int bestScore = 0;
        for (AgentDefinition def : agentRegistry.listAll()) {
            if (!visibleTo(def, audience)) {
                continue;
            }
            int score = 0;
            for (String keyword : def.keywordsOrEmpty()) {
                if (keyword != null && !keyword.isEmpty()
                        && lowerQuery.contains(keyword.toLowerCase())) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = RouteDecision.builder()
                        .agentKey(def.getAgentKey())
                        .agentName(def.getName())
                        .reason("keyword")
                        .score(score)
                        .build();
            }
        }
        return best;
    }

    /** 意图 → Agent，与改造前 {@code AgentCoordinator.mapIntentToAgent} 完全一致。 */
    private String mapIntentToAgent(IntentRouter.Intent intent) {
        switch (intent) {
            case HEALTH_RECORDS:
            case DATA_QUERY:
                return "analyst";
            case GRAPH:
            case KNOWLEDGE:
                return "consultant";
            case DRUG:
                return "doctor";
            case CHAT:
            default:
                return AgentRegistry.FALLBACK_KEY;
        }
    }

    private boolean isUsable(String agentKey, String audience) {
        AgentDefinition def = agentRegistry.get(agentKey);
        return def != null && def.isEnabled() && visibleTo(def, audience);
    }

    /**
     * 该 Agent 对某端是否可见。
     * audience 为 null（未指定端）或 Agent 自身为 all 时一律可见。
     *
     * <p><b>这里的 null 分支是唯一的「不按端过滤」入口</b>，也正因如此它很危险：
     * 改造前 {@code SeaChatWorkflow} 恒定传 null，于是「管理员为医生端新建一个 Agent」
     * 在用户端对话里照样会被路由到 —— 而每一处调用点看起来都完全正常。
     * 现在生产链路一律显式传端（见 {@link Audience}），null 只剩单测与
     * 「改造前行为」的兼容语义。</p>
     */
    private boolean visibleTo(AgentDefinition def, String audience) {
        if (audience == null || audience.isEmpty()) {
            return true;
        }
        String own = def.getAudience();
        return own == null || own.isEmpty() || Audience.ALL.equalsIgnoreCase(own)
                || audience.equalsIgnoreCase(own);
    }

    private String nameOf(String agentKey) {
        AgentDefinition def = agentRegistry.get(agentKey);
        return def == null ? agentKey : def.getName();
    }

    /**
     * 一次路由决策的完整结果。
     *
     * <p>保留 reason 与 score 是为了让管理端的「试跑」能回答
     * 「为什么这句话被路由到了它」—— 只回一个 key 的话，管理员
     * 只能靠猜，改关键词就变成了盲调。</p>
     */
    @Data
    @Builder
    public static class RouteDecision {
        private String agentKey;
        private String agentName;
        /** intent:XXX / keyword / default。 */
        private String reason;
        /** 命中关键词数；非关键词路由时为 0。 */
        private int score;
    }
}
