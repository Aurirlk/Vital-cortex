package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.core.agent.registry.AgentDefinition;
import cn.kmbeast.core.agent.registry.AgentRegistry;
import cn.kmbeast.crm.agent.BaseReActAgent;
import cn.kmbeast.crm.agent.ReActAgent;
import cn.kmbeast.crm.agent.model.ReActResponse;
import cn.kmbeast.crm.agent.model.ToolResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 间委派工具：让当前 Agent 把子问题交给另一个 Agent，并把对方的结论收回。
 *
 * <p>这是「多 Agent」从「一个 Agent 会演多个角色」变成<b>真的多个 Agent 协作</b>的
 * 分界点：改造前 6 个角色只是同一个 ReAct 循环换一段提示词，
 * 谁也不认识谁；有了本工具，A 的结论里可以包含 B 的实际产出。</p>
 *
 * <p><b>三道闸门，缺一不可</b>：</p>
 * <ol>
 *   <li><b>关系白名单</b>：只有 {@code agent_handoff} 里配了
 *       {@code 发起方 → 目标} 才放行。这是「谁能指挥谁」的授权，
 *       不能被模型自行决定 —— 模型只能决定「要不要委派」，不能决定「有没有资格委派」。</li>
 *   <li><b>深度上限</b>：见 {@link #MAX_DEPTH}。</li>
 *   <li><b>目标可用性</b>：目标 Agent 必须存在且启用。</li>
 * </ol>
 *
 * <p><b>为什么用 {@link ObjectProvider} 注入执行器</b>：
 * {@code ToolRegistry} 收集 {@code List<Tool>} → 本类 → {@code ReActAgent} →
 * {@code BaseReActAgent} → {@code ToolRegistry}，这是一个<b>真实的循环依赖</b>。
 * {@code ObjectProvider} 在注入阶段不做解析、调用时才取实例，是 Spring 里打断
 * 这类环的标准手段，比 {@code @Lazy} 代理更直接（后者会在调用链上多一层
 * 语义不明的间接）。注意只能在 {@link #execute} 里取实例，不能在字段初始化或
 * 构造阶段取 —— 那时容器还没就绪。</p>
 */
@Slf4j
@Component
public class CallAgentTool implements Tool {

    public static final String TOOL_NAME = "call_agent";
    public static final String ARG_AGENT_KEY = "agent_key";
    public static final String ARG_QUESTION = "question";

    /**
     * 委派链最大长度（含根 Agent）。
     *
     * <p>取 2 表示<b>只允许一跳</b>：根 Agent 可以委派给一个专科 Agent，
     * 被委派者不能再往下委派。理由不是保守，而是本实现没有做环检测 ——
     * 一跳意味着 A→B→A 这种环在结构上不可能出现。若日后要放开到多跳，
     * 必须同时补上「已访问 Agent 集合」的环检测，否则两个互相配置了
     * 委派关系的 Agent 会无限互相调用直到栈溢出。</p>
     */
    private static final int MAX_DEPTH = 2;

    /** 子问题长度上限：委派内容会进对方的 prompt，不设限会被单次调用撑爆预算。 */
    private static final int QUESTION_MAX = 2000;

    @Resource
    private AgentRegistry agentRegistry;

    @Resource
    private ObjectProvider<ReActAgent> reActAgentProvider;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "把问题委派给另一个专业智能体，并取回它的回答。"
                + "当你判断该问题属于更专业的领域（症状诊断、营养、心理、报告解读）"
                + "且自己无法给出同等质量的分析时使用。"
                + "传入目标智能体标识与一个自包含的问题描述 —— "
                + "对方看不到你们的对话历史，问题必须写完整。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> agentKeyProp = new LinkedHashMap<>();
        agentKeyProp.put("type", "string");
        agentKeyProp.put("description", "目标智能体标识，取值必须是枚举中的一项");
        // 枚举来自注册表：只列出全库被委派过的目标，避免模型去猜一个不存在的 key。
        // 每次调用都新建 map —— ToolRegistry 会就地补 additionalProperties，
        // 返回共享实例会被反复改写（见 ToolRegistry.withSchemaHardening 的约定）。
        if (agentRegistry != null) {
            Set<String> targets = agentRegistry.allHandoffTargets();
            if (!targets.isEmpty()) {
                agentKeyProp.put("enum", new ArrayList<>(new LinkedHashSet<>(targets)));
            }
        }
        properties.put(ARG_AGENT_KEY, agentKeyProp);

        Map<String, Object> questionProp = new LinkedHashMap<>();
        questionProp.put("type", "string");
        questionProp.put("description", "要委派的子问题，需自包含（不要用「上面那个」「他说的」这类指代）");
        properties.put(ARG_QUESTION, questionProp);

        schema.put("properties", properties);
        schema.put("required", List.of(ARG_AGENT_KEY, ARG_QUESTION));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (agentRegistry == null) {
            return ToolResult.error("委派能力未装配");
        }
        String targetKey = asText(arguments, ARG_AGENT_KEY);
        String question = asText(arguments, ARG_QUESTION);
        if (targetKey == null) {
            return ToolResult.error("缺少参数: " + ARG_AGENT_KEY);
        }
        if (question == null) {
            return ToolResult.error("缺少参数: " + ARG_QUESTION);
        }
        if (question.length() > QUESTION_MAX) {
            question = question.substring(0, QUESTION_MAX);
        }

        // 发起方身份只能从 ToolContext 取：本工具跑在工具线程池上，
        // BaseReActAgent 的 ThreadLocal 在这个线程上是空的（见 ToolContext 类注释）。
        String callerKey = ToolContext.getAgentKey();
        if (callerKey == null) {
            return ToolResult.error("无法确定当前智能体，禁止委派");
        }

        // 闸门 ①：关系白名单
        if (!agentRegistry.isHandoffAllowed(callerKey, targetKey)) {
            log.warn("[CallAgent] 拦截未授权的委派: {} → {}", callerKey, targetKey);
            return ToolResult.error("当前智能体未被授权委派给 " + targetKey);
        }

        // 闸门 ③：目标可用性
        AgentDefinition target = agentRegistry.get(targetKey);
        if (target == null || !target.isEnabled()) {
            return ToolResult.error("目标智能体不存在或已停用: " + targetKey);
        }

        // 闸门 ②：深度
        int depth = ToolContext.getHandoffDepth();
        if (depth + 1 >= MAX_DEPTH) {
            log.warn("[CallAgent] 委派深度超限(depth={}, max={})，拒绝 {} → {}",
                    depth, MAX_DEPTH, callerKey, targetKey);
            return ToolResult.error("委派层级已达上限，请直接基于已掌握的信息作答");
        }

        ReActAgent executor = reActAgentProvider != null ? reActAgentProvider.getIfAvailable() : null;
        if (executor == null) {
            return ToolResult.error("委派执行器不可用");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        // 只给子问题，不带父对话历史：父对话可能很长，而且子 Agent 的目标
        // 是回答一个明确的问题，上下文越多越容易跑偏。
        userMsg.put("content", question);
        messages.add(userMsg);

        // 深度写回 ToolContext，让子 Agent 自己的工具调用也能看到它 ——
        // 子 Agent 跑在池线程上，靠的还是这份透传值。
        // 这是一次「借出去」的写入：下面的 finally 必须还回 depth。
        ToolContext.set(ToolContext.KEY_HANDOFF_DEPTH, depth + 1);

        // 防御性保存/恢复本线程的对话上下文。
        // 当前拓扑下 executeTool 总是把工具 submit 到线程池，子 Agent 跑在
        // 池线程上，而父 Agent 的上下文在请求线程 —— 天然隔离，这段是 no-op。
        // 保留它是因为这个隔离依赖「工具永远不在调用线程上执行」这一实现细节；
        // 一旦有人为了「线程池满了就降级为同步执行」而改掉 executeTool，
        // 子 Agent 的 finally clearConversationContext() 会静默清掉父 Agent
        // 的角色与工具白名单，而父 Agent 回到循环后毫无察觉。这种失败模式
        // 排查代价极高，用三行代码换掉是划算的。
        BaseReActAgent.ConversationContext saved = executor.snapshotContext();
        long startMs = System.currentTimeMillis();
        try {
            ReActResponse response = executor.runWithContext(messages, targetKey, null);
            String content = response != null ? response.getContent() : null;
            if (content == null || content.isBlank()) {
                return ToolResult.error(target.getName() + " 未给出有效回答");
            }
            log.info("[CallAgent] 委派完成: {} → {}，耗时 {}ms",
                    callerKey, targetKey, System.currentTimeMillis() - startMs);
            return ToolResult.ok(content);
        } catch (Exception e) {
            log.warn("[CallAgent] 委派失败: {} → {}，{}", callerKey, targetKey, e.getMessage());
            return ToolResult.error("委派失败: " + e.getMessage());
        } finally {
            // 归还上面借出去的深度。
            //
            // 子 Agent 已经在 runWithContext 内部读走了那个值（它的工具在提交线程池时
            // 快照了整份 ToolContext），此刻还原不影响子 Agent 的自查。但「不还原」会让一次
            // **失败**的委派永久吃掉本线程的深度预算：同一个 Agent 后续再想委派，会被
            // 闸门② 拒成「委派层级已达上限」—— 而它一次都没成功委派过。模型收到的是一个
            // 假理由，于是它不再重试，这一轮回答就少了一整块能力。
            // 委派失败本身很常见（模型超时、目标 Agent 报错），所以这条路径一定会被走到。
            ToolContext.set(ToolContext.KEY_HANDOFF_DEPTH, depth);
            executor.restoreContext(saved);
        }
    }

    private static String asText(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return null;
        }
        Object value = arguments.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
