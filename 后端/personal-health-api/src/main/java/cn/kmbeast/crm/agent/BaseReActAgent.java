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
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
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

    /**
     * 2026-10-07：长期记忆（用户偏好）。
     *
     * <p><b>为什么此前没接</b>：{@code AgentMemoryService} 治理做得不错（有界 LRU + 定时清理
     * + 返回不可变副本），但全仓只有 {@code AgentController} 的 3 个 REST 端点调用它，
     * ReAct 链路从不读取 —— 意味着「用户偏好记忆」虽然可通过 API 读写，
     * <b>永远不会进入 prompt，对用户完全不可见</b>。</p>
     *
     * <p><b>注入方式</b>：{@code required=false}。单测里 {@code BaseReActAgent} 的子类
     * 不会被 Spring 装配，硬注入会导致 NPE。</p>
     */
    @Autowired(required = false)
    protected cn.kmbeast.core.agent.AgentMemoryService memoryService;

    /**
     * 注入到 prompt 的偏好段最大字符数。
     *
     * <p>偏好是长期累积的，用户若不断追加会无限增长 → 必须设上限，否则单次对话的
     * system prompt 会膨胀到挤占 token 预算（{@code TokenBudgetManager} 只校验不压缩）。
     * 超限时<b>按插入顺序保留最新的若干条</b>（更旧的偏好参考价值低）。</p>
     */
    protected static final int MEMORY_SECTION_MAX_CHARS = 800;

    /** 单条偏好的最大字符数，超长截断，防止单条就把整段撑爆。 */
    protected static final int MEMORY_ITEM_MAX_CHARS = 80;

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

    /**
     * 2026-10-10：容器关闭时优雅停掉工具线程池。
     *
     * <p>池内线程是 daemon，不会阻止 JVM 退出，所以此前「没关池」不至于挂住进程；
     * 但 Spring 销毁 Bean 时正在执行的工具调用会被硬中断，
     * 且线程池对象不释放。补一个 @PreDestroy，让在途任务有机会收尾。
     */
    @jakarta.annotation.PreDestroy
    public void shutdownToolExecutor() {
        toolExecutor.shutdown();
        try {
            if (!toolExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                toolExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            toolExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

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
            2. 使用对应工具获取信息（search_drug、search_knowledge、get_chat_history、get_health_data、query_health_records、query_knowledge_graph）
            3. 综合信息给出专业、有帮助的回答
            
            ## 规则
            - 涉及药品推荐时，必须使用 search_drug 获取真实药品数据
            - 涉及健康知识时，使用 search_knowledge 检索文章
            - 涉及用户历史对话时，优先使用 get_chat_history
            - 涉及用户健康数据（血压、血糖、体重等），必须使用 get_health_data
            - 涉及「某指标最近测量值/统计/是否达标」（如「我最近的血压正常吗」「查一下空腹血糖」），使用 query_health_records
            - 涉及「A 与 B 的医学关系」（如「高血压吃什么药」「布洛芬治什么」「糖尿病有哪些症状」），使用 query_knowledge_graph
            - 对模糊问题主动追问
            - 医疗建议必须附免责声明："以上建议仅供参考，具体用药请遵医嘱"
            - 用中文回答，语气亲切专业""";

    /**
     * 2026-10-07 P0-6：提示词外置加载器。
     *
     * <p>{@code required=false}：单测里 {@code BaseReActAgent} 的子类不会被 Spring 装配，
     * 硬注入会 NPE。null 时 {@link #getSystemPrompt()} 与 {@link #roleInstruction(String)}
     * 全部回落 Java 常量，<b>行为与改造前完全一致</b>。</p>
     */
    @Autowired(required = false)
    protected ReactPromptLoader reactPromptLoader;

    protected String getSystemPrompt() {
        // 2026-10-05：crmConfig 在单测实例与早期初始化场景下可能为 null，
        // 此前直接调用必然 NPE，会让整个 ReAct 主链路（含 enhanceSystemPrompt）
        // 在点一次调试用例时就炸掉。配置缺失应降级为默认 prompt，而不是崩溃。
        // 2026-10-07 P0-6：读取优先级补了两级外部来源 ——
        //   ① crm.react.prompt（yml，行为不变）
        //   ② ./config/react-prompts.json（可热改）
        //   ③ classpath:ai-prompts/react/system.txt（随 jar 打包）
        //   ④ Java 常量（最后兜底）
        String configured = crmConfig != null ? crmConfig.getReactPrompt() : null;
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (reactPromptLoader != null) {
            String external = reactPromptLoader.externalSystemPrompt();
            if (external != null && !external.isBlank()) {
                return external;
            }
            String cp = reactPromptLoader.classpathSystemPrompt();
            if (cp != null && !cp.isBlank()) {
                return cp;
            }
        }
        return DEFAULT_SYSTEM_PROMPT;
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
     * 阶段 3（2026-10-08）：意图软路由结果。
     *
     * <p>由 {@code IntentRouter} 在请求入口处计算出，随 {@link #runWithContext} 注入。
     * 与 roleHint/guidancePrompt 不同：本字段<b>不采用「读取即清除」</b>——
     * 它需要在 {@code enhanceSystemPrompt()}（拼 prompt）与 doRun 的四层 fail-safe
     * （Layer 2 重路由判定 / Layer 4 离线兜底）两处<b>都读到同一个值</b>。
     * 释放统一交给 {@link #clearConversationContext()} 的 finally 保证，
     * 而 {@code runWithContext}/{@code runWithContextStreaming} 都必经 finally，不会泄漏。
     */
    private final ThreadLocal<IntentRouter.IntentHint> intentHint = new ThreadLocal<>();

    /** 注入本轮意图倾向（null/空视为无倾向，等同于普通咨询） */
    public void setIntentHint(IntentRouter.IntentHint hint) {
        if (hint == null) {
            intentHint.remove();
        } else {
            intentHint.set(hint);
        }
    }

    /** 读取但不清除（供 enhanceSystemPrompt 与 fail-safe 多次使用） */
    public IntentRouter.IntentHint peekIntentHint() {
        return intentHint.get();
    }

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
     * 取角色专属指令（2026-10-07 P0-6：支持外置）。
     *
     * <p>优先级：外部热改文件 → classpath 资源 → Java 常量兜底。
     * <b>注意</b>：{@code roleHint} 被「读一次即消费」清空后 role 可能是 null，
     * 而 {@code Map.of()} 返回的不可变 Map 用 {@code get(null)} 会抛 NPE
     *（不是返回 null）—— 调用方必须先判空。</p>
     */
    protected String roleInstruction(String role) {
        if (role == null) {
            return null;
        }
        if (reactPromptLoader != null) {
            String external = reactPromptLoader.externalRolePrompt(role);
            if (external != null && !external.isBlank()) {
                return external;
            }
            String cp = reactPromptLoader.classpathRolePrompts().get(role);
            if (cp != null && !cp.isBlank()) {
                return cp;
            }
        }
        return ROLE_INSTRUCTIONS.get(role);
    }

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
        // 注意这里必须先判空：roleHint 被「读一次即消费」清空后 role 就是 null，
        // 而 Map.of() 返回的不可变 Map 用 get(null) 查键会抛 NullPointerException
        //（不是返回 null），直接 ROLE_INSTRUCTIONS.get(role) 会炸在这一行。
        String instruction = role != null ? roleInstruction(role) : null;
        if (instruction != null) {
            sb.append("\n\n").append(instruction);
        }
        String guidance = guidancePrompt.get();
        guidancePrompt.remove();
        if (guidance != null) {
            sb.append(guidance);
        }
        // 阶段 3：意图软路由结果（读取不清除——fail-safe 还要再用）
        IntentRouter.IntentHint hint = intentHint.get();
        if (hint != null && hint.getHintText() != null && !hint.getHintText().isBlank()) {
            sb.append("\n\n## 本轮意图倾向\n").append(hint.getHintText());
        }
        // 阶段 4（2026-10-07 新增）：长期记忆（用户偏好）
        //  注意与 roleHint 不同：偏好是【跨会话长期记忆】，不是本轮一次性上下文，
        //  因此这里不消费、不清除，每轮都应重新读取。
        String memory = buildMemorySection();
        if (memory != null) {
            sb.append(memory);
        }
        return sb.toString();
    }

    /**
     * 把用户偏好拼成 system prompt 的一段。
     *
     * <p><b>2026-10-07 新增</b>。此前 {@code AgentMemoryService} 只被 REST 层调用，
     * 偏好永远进不了 prompt —— 这段代码把它接进来。</p>
     *
     * <p>安全约束（任一不满足即返回 {@code null}，不注入任何内容）：</p>
     * <ol>
     *   <li>{@code memoryService} 未注入（单测场景）→ 跳过</li>
     *   <li>当前用户 id 取不到（未登录 / 跨线程身份丢失）→ 跳过，<b>绝不跨用户读偏好</b></li>
     *   <li>偏好为空 → 跳过</li>
     *   <li>组装后超过 {@link #MEMORY_SECTION_MAX_CHARS} → <b>保留最新若干条</b>并标注已省略</li>
     * </ol>
     *
     * @return 可直接追加到 system prompt 的文本（含前后换行），或 {@code null} 表示无需注入
     */
    protected String buildMemorySection() {
        try {
            if (memoryService == null) {
                return null;
            }
            Integer userId = cn.kmbeast.context.LocalThreadHolder.getUserId();
            if (userId == null) {
                // 没取到身份就不注入 —— 否则可能把上一位用户的偏好带进来
                return null;
            }
            Map<String, String> prefs = memoryService.getUserPreferences(userId);
            if (prefs == null || prefs.isEmpty()) {
                return null;
            }

            // LinkedHashMap 保持插入序：先写入的是旧偏好，故倒序遍历以便优先保留最新
            List<String> lines = new ArrayList<>();
            List<String> keys = new ArrayList<>(prefs.keySet());
            for (int i = keys.size() - 1; i >= 0; i--) {
                String key = keys.get(i);
                String value = prefs.get(key);
                if (key == null || key.isBlank() || value == null || value.isBlank()) {
                    continue;
                }
                String item = "- " + key + "：" + (value.length() > MEMORY_ITEM_MAX_CHARS
                        ? value.substring(0, MEMORY_ITEM_MAX_CHARS) + "…" : value);
                lines.add(item);
            }
            if (lines.isEmpty()) {
                return null;
            }

            // 总量超限：从最新一条开始收，够预算即停
            StringBuilder body = new StringBuilder();
            int omitted = 0;
            for (String item : lines) {
                if (body.length() + item.length() > MEMORY_SECTION_MAX_CHARS) {
                    omitted++;
                    continue;
                }
                if (body.length() > 0) {
                    body.append('\n');
                }
                body.append(item);
            }
            if (body.length() == 0) {
                // 连一条都放不下：只保留最新一条并截断
                String first = lines.get(0);
                body.append(first.length() > MEMORY_SECTION_MAX_CHARS
                        ? first.substring(0, MEMORY_SECTION_MAX_CHARS) + "…"
                        : first);
                omitted = lines.size() - 1;
            }

            StringBuilder sb = new StringBuilder("\n\n## 用户偏好（长期记忆）\n");
            if (omitted > 0) {
                sb.append("（另有 ").append(omitted).append(" 条较早的偏好未列出）\n");
            }
            sb.append(body)
              .append("\n请在回答时自然遵循以上偏好；与用户当次明确要求冲突时，以当次要求为准。");
            return sb.toString();
        } catch (Exception e) {
            // 记忆读取失败绝不能影响对话
            log.debug("[AgentMemory] 注入偏好失败，已跳过: {}", e.getMessage());
            return null;
        }
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
        intentHint.remove();
    }

    /**
     * 子类实现的 ReAct 主循环（同步）：负责把 userMessages 组装成完整对话并跑完整轮。
     *
     * <p>存在意义：让 {@link #runWithContext} 能在 finally 里保证清理，
     * 而不必要求每个调用方都记得先 set、再 run、再手动 clear。
     */
    protected abstract ReActResponse doRun(List<Map<String, String>> userMessages);

    /**
     * 不带角色与引导的裸执行（兼容旧调用方与测试），等同于 {@link #runWithContext}
     * 传 {@code null}。
     */
    public ReActResponse run(List<Map<String, String>> userMessages) {
        return runWithContext(userMessages, null, null);
    }

    /**
     * 带角色 + 数据引导 + 意图倾向跑完一整轮（阶段 3 入口）。
     *
     * @param intentHint 由 {@code IntentRouter} 算出的意图倾向；null 走中性引导
     * @see #runWithContext(List, String, String)
     */
    public final ReActResponse runWithContext(List<Map<String, String>> userMessages,
                                              String roleType,
                                              String guidance,
                                              IntentRouter.IntentHint intentHint) {
        setIntentHint(intentHint);
        return runWithContext(userMessages, roleType, guidance);
    }

    /**
     * 带角色与数据引导跑完一整轮，<b>调用方无需再关心 ThreadLocal 清理</b>。
     *
     * <p><b>为什么必须提供这个入口</b>：同步链路曾出过一次真实的空转 bug ——
     * Controller 把 roleHint 设在了 {@code streamingReActAgent} 上，
     * 而工作流内部实际执行的是另一个 {@code ReActAgent} bean，
     * 角色指令根本没进 prompt，{@code AgentCoordinator} 的 6 个角色形同虚设。
     * 根因是「上下文要调用方手动传递」这种写法，漏一处就失效。
     * 改成由真正执行对话的 agent 自己开一个入口（set → run → finally clear），
     * 调用方只有一种正确写法，这类空转不再可能复发。
     *
     * @param roleType  角色编码，null 走默认角色
     * @param guidance  Harness 生成的追问指令，null 表示档案已完整
     */
    public final ReActResponse runWithContext(List<Map<String, String>> userMessages,
                                              String roleType,
                                              String guidance) {
        setRoleHint(roleType);
        setGuidancePrompt(guidance);
        try {
            return doRun(userMessages);
        } finally {
            clearConversationContext();
        }
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
        if (toolCalls == null) {
            return list;
        }
        for (ToolCall tc : toolCalls) {
            if (tc == null) {
                // 并行执行超时回填 error 时不会产生 null 元素，但流式分片累积
                // 理论上可能出现空槽位。跳过比塞 null 更安全。
                continue;
            }
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

    /**
     * 追加「assistant 发起工具调用」这一条消息。
     *
     * <p>2026-10-07 P0-6 整改：{@code content} 此前<b>硬编码为空串</b>，
     * 等于把模型同轮生成的过渡文本（若有）全部丢弃 —— 模型下一轮看不到自己刚说过的话，
     * 可能重复表述或语气不一致。现改为把 {@code assistantText} 拼回；
     * 为空时置 {@code null}（OpenAI 规范中 content 可为 null）。</p>
     *
     * @param modelText 模型在本轮输出的伴随文本，可为 null
     */
    protected void addAssistantToolCallMessage(List<Map<String, Object>> messages,
                                               List<ToolCall> toolCalls,
                                               String modelText) {
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("tool_calls", buildToolCallsForMessage(toolCalls));
        String text = (modelText == null || modelText.isBlank()) ? null : modelText;
        assistantMsg.put("content", text);
        messages.add(assistantMsg);
    }

    /** 兼容旧调用方（不保留伴随文本）。 */
    protected void addAssistantToolCallMessage(List<Map<String, Object>> messages, List<ToolCall> toolCalls) {
        addAssistantToolCallMessage(messages, toolCalls, null);
    }

    protected void addToolResultMessage(List<Map<String, Object>> messages, ToolCall tc, ToolResult result) {
        if (tc == null) {
            return;
        }
        Map<String, Object> toolMsg = new LinkedHashMap<>();
        toolMsg.put("role", "tool");
        toolMsg.put("tool_call_id", tc.getId());
        // 2026-10-07 OpenAI schema 合规整改：
        //   此前把工具返回的 JSON 字符串解析成 JSONObject/JSONArray 直接放进 content。
        //   OpenAI 规范要求 role:"tool" 的 content 必须是 String（或 content parts 数组），
        //   传对象会被严格实现（vLLM / 新版网关）判 400 —— 当前 DeepSeek 容忍，换厂商即炸。
        //   现在 content 恒为 String（原样透传，不做二次包装）；结构化数据走
        //   ToolResult.getStructuredContent()，不进 messages。
        String content = result == null ? null : result.getContent();
        // OpenAI 不接受 tool 消息的 content 为 null，归一为 ""
        toolMsg.put("content", content == null ? "" : content);
        messages.add(toolMsg);
    }

    /**
     * 把文本里的 JSON 解析成结构化值，供<b>日志 / 审计</b>使用。
     *
     * @deprecated 2026-10-07 起<b>禁止</b>用于构造 OpenAI 的 {@code tool.content}。
     *             协议要求 {@code content} 必须是 String；结构化数据请用
     *             {@link ToolResult#getStructuredContent()}，它不会进入请求体。
     *             保留此方法仅为迁移期兼容既有调用方。
     */
    @Deprecated
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

    /**
     * 并行执行同一轮的全部工具调用（AG-07 整改）。
     *
     * <p><b>2026-10-07 抽取到基类</b>：此前 {@code ReActAgent} 用 {@link CountDownLatch}
     * 真并行、{@code StreamingReActAgent} 却是串行 for 循环（注释写「并行」实为串行），
     * 两份重复代码各自演化。此处统一为<b>唯一实现</b>，两个子类共用。</p>
     *
     * <p><b>修掉的三个缺陷</b>：</p>
     * <ol>
     *   <li>🔴 {@code latch.await} 超时后 {@code results[i]} 仍为 {@code null}，
     *       此前直接回填会在 {@code addToolResultMessage} 里抛 NPE 且<b>无 try 包裹</b>，
     *       会冒泡到 Controller 变成 500。现统一填 error 结果。</li>
     *   <li>🔴 跨轮<b>无幂等台账</b>：模型第 1 轮调 {@code query_health_records}、
     *       第 3 轮又调一次会<b>执行两次</b>（该工具会写 audit_log → 重复审计行）。
     *       现由 {@code executedKeys} 记录，<b>有副作用的工具</b>跨轮直接拒绝。</li>
     *   <li>线程池拒绝提交（队列满）时 {@code toolExecutor.execute} 抛
     *       {@link java.util.concurrent.RejectedExecutionException}，此前整个循环中断，
     *       后续工具全部不执行。现逐个捕获。</li>
     * </ol>
     *
     * @param calls        本轮的 tool_calls（保持模型给出的顺序）
     * @param executedKeys <b>跨轮</b>已成功执行的「工具名::参数」台账；成功执行后自动写入。
     *                     <b>必须是方法局部变量</b> —— 本类是单例 Bean，用实例字段会跨请求串号。
     * @param beforeExecute 每个工具执行前的回调（流式链路用它发 {@code tool_call} 事件），
     *                      可为 null
     * @return 与 {@code calls} 等长的结果数组，<b>元素永不为 null</b>
     */
    protected ToolResult[] executeToolsParallel(List<ToolCall> calls,
                                                Set<String> executedKeys,
                                                java.util.function.Consumer<ToolCall> beforeExecute) {
        ToolResult[] results = new ToolResult[calls.size()];
        CountDownLatch latch = new CountDownLatch(calls.size());

        for (int i = 0; i < calls.size(); i++) {
            final int idx = i;
            ToolCall tc = calls.get(i);

            // ① 同轮去重：相同工具 + 相同参数不重复执行
            if (hasDuplicateCall(calls, i, tc)) {
                log.warn("[ReAct] 检测到同轮重复工具调用，跳过: {} args={}", tc.getName(), tc.getArguments());
                results[i] = ToolResult.error("该工具调用与本轮已执行的调用重复，请勿重复调用");
                latch.countDown();
                continue;
            }

            // ② 跨轮幂等：有副作用的工具不允许重复执行
            String callKey = callKey(tc);
            if (executedKeys != null && executedKeys.contains(callKey)) {
                log.warn("[ReAct] 跨轮重复工具调用，跳过（有副作用工具不重复执行）: {}", tc.getName());
                results[i] = ToolResult.error("该工具调用与之前的调用重复执行过，请勿重复调用相同参数");
                latch.countDown();
                continue;
            }

            if (beforeExecute != null) {
                try {
                    beforeExecute.accept(tc);
                } catch (Exception e) {
                    log.warn("[ReAct] 工具执行前回调异常，忽略: {}", tc.getName(), e);
                }
            }

            try {
                toolExecutor.execute(() -> {
                    try {
                        results[idx] = executeTool(tc);
                        // 只有成功执行才记台账（失败允许重试）
                        if (results[idx] != null && results[idx].isSuccess()
                                && executedKeys != null && hasSideEffects(tc.getName())) {
                            executedKeys.add(callKey);
                        }
                    } catch (Exception e) {
                        log.error("[ReAct] 工具执行异常: {}", tc.getName(), e);
                        results[idx] = ToolResult.error("工具执行异常: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                // ③ 线程池队列满：不能中断整个循环，后续工具仍要执行
                log.error("[ReAct] 线程池拒绝提交（队列满）: {}", tc.getName(), e);
                results[idx] = ToolResult.error("工具执行失败：系统繁忙（线程池队列已满），请稍后重试");
                latch.countDown();
            }
        }

        // 等待本轮全部完成（超时按未完成处理，不抛异常）
        try {
            long timeout = resolveToolTimeoutSeconds() + 5L;
            if (!latch.await(timeout, TimeUnit.SECONDS)) {
                log.warn("[ReAct] 工具执行超时（部分未完成），继续下一轮");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[ReAct] 等待工具执行被中断");
        }

        // 🔴 统一兜底：超时/中断会让部分槽位仍为 null，这里全部填 error，
        //    避免调用方拿到 null 后在 addToolResultMessage 里 NPE。
        for (int i = 0; i < results.length; i++) {
            if (results[i] == null) {
                results[i] = ToolResult.error("工具执行超时或未完成（" + calls.get(i).getName() + "），本轮结果不可用");
            }
        }
        return results;
    }

    /**
     * 并行执行的整体等待超时（秒）。
     *
     * <p>抽成方法是为了让单测能覆写成 1 秒 —— 否则测试要么真等 35 秒，
     * 要么只能去反射改 {@code CrmConfig} 的私有字段。</p>
     */
    protected long resolveToolTimeoutSeconds() {
        return crmConfig != null ? crmConfig.getToolTimeoutSeconds() : 30;
    }

    /** 工具调用的幂等键（工具名 + 参数）。 */
    protected static String callKey(ToolCall tc) {
        return tc.getName() + "::" + (tc.getArguments() != null ? tc.getArguments().toString() : "");
    }

    /**
     * 工具是否有副作用（有副作用者不允许跨轮重复执行）。
     *
     * <p>2026-10-07 新增。当前 {@code query_health_records} 会写 {@code audit_log}，
     * 重复执行产生重复审计行；未来若加入写入类工具（下单、发消息）必须一并登记。</p>
     */
    protected static boolean hasSideEffects(String toolName) {
        // query_health_records / query_health_data 都会写 audit_log，重复执行会产生重复审计行
        return "query_health_records".equals(toolName) || "query_health_data".equals(toolName);
    }

    /**
     * 同轮去重：检查 {@code calls[0..currentIndex-1]} 里是否已有相同工具 + 相同参数。
     */
    protected boolean hasDuplicateCall(List<ToolCall> calls, int currentIndex, ToolCall tc) {
        String currentKey = callKey(tc);
        for (int j = 0; j < currentIndex; j++) {
            ToolCall prev = calls.get(j);
            if (currentKey.equals(callKey(prev))) {
                return true;
            }
        }
        return false;
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
            // 2026-10-07（阶段 2）：角色身份也必须跨线程透传。
            // 否则医生端在工具内取不到 roleId/doctorId，双轨访问控制彻底失效。
            final Integer roleId = ToolContext.getRoleId();
            final Integer doctorId = ToolContext.getDoctorId();
            // 2026-10-07 P1-3：HTTP 审计上下文同样要跨线程透传，否则工具内审计拿不到
            // 客户端 IP / UA / 方法 / URL（工具运行在线程池，无 HttpServletRequest）。
            final String clientIp = ToolContext.getClientIp();
            final String userAgent = ToolContext.getUserAgent();
            final String method = ToolContext.getRequestMethod();
            final String url = ToolContext.getRequestUrl();

            future = toolExecutor.submit(() -> {
                // 在线程池线程中设置ToolContext
                ToolContext.setPhoneAndUserId(phone, userId);
                ToolContext.setIdentity(roleId, doctorId);
                ToolContext.setHttpMeta(clientIp, userAgent, method, url);
                try {
                    return tool.execute(tc.getArguments());
                } finally {
                    ToolContext.clear();
                }
            });
            long timeout = resolveToolTimeoutSeconds();
            return future.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // AG-06 整改：超时必须 cancel(true) 中断工具线程，
            // 否则慢工具在后台继续执行（可能反复调用外部 API、继续消耗资源）。
            if (future != null) {
                future.cancel(true);
            }
            long t = resolveToolTimeoutSeconds();
            log.warn("[ReAct] 工具执行超时并已取消: {}, timeout={}s", tc.getName(), t);
            return ToolResult.error("工具执行超时(" + t + "s): " + tc.getName());
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
        } catch (RuntimeException e) {
            // 2026-10-07 P0-3：补 RuntimeException 兜底。
            // 此前 catch 链只覆盖 Future 相关异常，工具侧的未受检异常
            // （如 ToolArgsValidator 读到畸形 schema 抛的 IllegalStateException）
            // 会直接冒泡到 Controller 变成 500。
            // 这里必须降级为「工具失败」让模型自己重试或改用其他工具。
            log.error("[ReAct] 工具执行未预期异常: {}", tc.getName(), e);
            return ToolResult.error("工具执行异常(" + tc.getName() + "): " + e.getMessage());
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
                body.put("tools", toolRegistry.buildToolsArray(crmConfig.isReactToolStrict()));
                body.put("tool_choice", "auto");
                // 2026-10-07 OpenAI 合规：此前缺这两个标准字段。
                // max_tokens 不传时由服务端决定默认值，部分自托管推理服务默认仅 1024，
                // 长篇医学解释会被静默截断而用户毫不知情。
                body.put("max_tokens", crmConfig.getReactMaxTokens());
                // 允许模型在同一轮返回多个 tool_calls（配合下方并行执行）。
                // 显式声明可避免部分实现默认只返回一个、行为不可预期。
                body.put("parallel_tool_calls", true);

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
                        throw CrmException.aiUnavailable("HTTP " + response.code() + " " + errorBody,
                                response.code());
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
                        throw CrmException.badResponse("返回choices为空");
                    }

                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject message = choice.getJSONObject("message");

                    // 2026-10-07：读 finish_reason。长度截断必须显式失败，
                    // 否则用户会把半截医学结论当成完整答案（健康场景比报错更危险）。
                    String finishReason = choice.getString("finish_reason");
                    if ("length".equals(finishReason)) {
                        throw CrmException.truncatedByLength(crmConfig.getReactMaxTokens());
                    }

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
                        // 2026-10-07 P0-6：保留模型同轮输出的文本。
                        // 部分模型在发起工具调用的同时会先说一段过渡语
                        // （如「我来查一下你的血压记录」），此前被直接丢弃，
                        // 既浪费 token，也让下一轮看不到自己的发言上下文。
                        return ReActResponse.toolCalls(toolCalls, message.getString("content"));
                    }

                    String content = message.getString("content");
                    if (content != null && !content.isEmpty()) {
                        return ReActResponse.text(content);
                    }

                    throw CrmException.badResponse("LLM返回空内容");
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
     * 判定 CrmException 是否属于可重试失败（429 限流 / 5xx 服务端故障 / 无状态码的网络异常）。
     *
     * <p><b>2026-10-07 重写</b>：此前是 {@code msg.contains("HTTP 429")} 字符串嗅探。
     * 三个具体问题：① 换厂商或文案一改（如「HTTP状态码 429」）重试逻辑<b>静默失效</b>；
     * ② 无法区分「网络异常」（值得重试）与「参数错误 400」（重试无意义）；
     * ③ 单测要为重试行为构造带特定中文字符串的异常，极不合理。
     * 现改为读 {@link CrmException#getHttpStatus()} 做纯数值比较。</p>
     *
     * <p>语义变化：<b>无状态码的异常（网络超时、连接中断）现在可重试</b> ——
     * 这才是正确行为，此前它们因不含「HTTP」字样而被当作不可重试，一次抖动就整个失败。</p>
     */
    private boolean isRetryableStatus(CrmException e) {
        // 协议层问题（响应结构不对 / 被截断）重试无意义，立即失败
        if (e.isProtocolFailure()) {
            return false;
        }
        Integer status = e.getHttpStatus();
        if (status == null) {
            // 无状态码 = 网络层失败（超时、连接中断），瞬时性较强，值得退避重试
            return true;
        }
        return status == 429 || status >= 500;
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
                // 与 callLLMWithTools 保持一致：显式下发 max_tokens，
                // 否则末轮总结可能被服务端默认值静默截断。
                body.put("max_tokens", crmConfig.getReactMaxTokens());

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
                        throw CrmException.aiUnavailable("HTTP " + response.code() + " " + errorBody,
                                response.code());
                    }
                    if (response.body() == null) {
                        throw CrmException.aiUnavailable("AI服务返回为空");
                    }
                    String respBody = response.body().string();
                    JSONObject json = JSON.parseObject(respBody);
                    JSONArray choices = json.getJSONArray("choices");
                    if (choices == null || choices.isEmpty()) {
                        throw CrmException.badResponse("返回choices为空");
                    }
                    // 🔴 末轮总结被截断 = 半个医学结论，必须显式失败
                    String finishReason = choices.getJSONObject(0).getString("finish_reason");
                    if ("length".equals(finishReason)) {
                        throw CrmException.truncatedByLength(crmConfig.getReactMaxTokens());
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

    /**
     * 从用户消息列表中提取「最新一句用户提问」，供离线兜底与日志使用。
     *
     * <p>取最后一个 role=user 的内容；找不到返回 null。
     */
    protected String extractLatestUserQuery(List<Map<String, String>> userMessages) {
        if (userMessages == null) {
            return null;
        }
        for (int i = userMessages.size() - 1; i >= 0; i--) {
            Map<String, String> msg = userMessages.get(i);
            if (msg != null && "user".equals(msg.get("role"))) {
                return msg.get("content");
            }
        }
        return null;
    }

    /**
     * 四层 fail-safe 第 4 层：LLM 完全故障（API 不可用 / 超时）时的离线兜底。
     *
     * <p><b>仅对「高置信度 + 有确定工具落点」的意图生效</b>：{@code HEALTH_RECORDS} 与
     * {@code GRAPH}。这两个工具的执行只依赖 {@link ToolContext} 里的身份（JWT 透传），
     * 不依赖 LLM，因此即使大模型全挂，也能返回真实数据而不是一句「服务不可用」。
     *
     * <p>调用方需保证本方法执行时 {@link ToolContext} 已设置（控制器在请求线程上已 set）。
     * 直接在当前线程执行工具（不丢线程池），避免 ToolContext 再次跨线程丢失。
     *
     * @return 兜底成功返回带「[离线模式]」前缀的回答；任何不满足条件/工具执行失败返回 null
     */
    protected ReActResponse tryOfflineFallback(IntentRouter.IntentHint hint) {
        if (hint == null || hint.getTargetTool() == null || hint.getSuggestedArgs() == null) {
            return null;
        }
        Integer userId = ToolContext.getUserId();
        if (userId == null) {
            // 认不出身份就不查数据（双轨访问控制铁律），退回上层「服务不可用」文案
            return null;
        }
        if (toolRegistry == null) {
            return null;
        }
        Tool tool = toolRegistry.get(hint.getTargetTool());
        if (tool == null) {
            return null;
        }
        try {
            ToolResult result = tool.execute(hint.getSuggestedArgs());
            if (result != null && result.isSuccess()
                    && result.getContent() != null && !result.getContent().isBlank()) {
                log.warn("[ReAct] 触发离线兜底：LLM 不可用时直接调用 {} 返回真实数据",
                        hint.getTargetTool());
                return ReActResponse.text("[离线模式] " + result.getContent(),
                        List.of(hint.getTargetTool()));
            }
        } catch (Exception e) {
            log.warn("[ReAct] 离线兜底工具执行失败: tool={}, msg={}",
                    hint.getTargetTool(), e.getMessage());
        }
        return null;
    }

    /**
     * 四层 fail-safe 第 2 层判定：首轮 LLM 返回纯文本（未调用数据工具），但意图软路由给出
     * 「高置信度 + 有确定工具落点」时，是否值得再给一轮机会追加强化指令重跑。
     *
     * <p><b>触发条件</b>：① 当前是第 1 轮；② 还有富余轮次（round < maxRounds，避免抢占
     * 正常的多轮工具协作）；③ 意图为 HEALTH_RECORDS / GRAPH 且高置信度。
     */
    protected boolean shouldRetryForRouting(int round, int maxRounds) {
        if (round != 1 || round >= maxRounds) {
            return false;
        }
        IntentRouter.IntentHint hint = peekIntentHint();
        return hint != null && hint.isHighConfidenceDataIntent();
    }

    /**
     * 第 2 层用的强化指令（system 消息）。要求模型立即调用指定数据工具而非凭记忆作答。
     * 无高置信度数据意图时返回 null（不强行干预，避免破坏普通咨询）。
     */
    protected Map<String, Object> buildRoutingReinforceNote() {
        IntentRouter.IntentHint hint = peekIntentHint();
        if (hint == null || hint.getTargetTool() == null) {
            return null;
        }
        Map<String, Object> note = new LinkedHashMap<>();
        note.put("role", "system");
        note.put("content", "重要：你本轮的问题需要真实数据支撑。请立即调用工具 "
                + hint.getTargetTool() + " 获取真实数据，不要仅凭记忆回答，"
                + "也不要在未取数前直接给出最终结论。");
        return note;
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
