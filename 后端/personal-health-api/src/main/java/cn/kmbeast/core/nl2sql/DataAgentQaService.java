package cn.kmbeast.core.nl2sql;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.crm.agent.tool.ToolContext;
import cn.kmbeast.mapper.UserHealthMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据问答门面：身份解析 → 授权校验 → 跑流水线 → 审计 → 会话持久化。
 *
 * <p><b>双轨访问控制铁律</b>（与阶段 1/2 一致）：</p>
 * <ul>
 *   <li>用户端（roleId≠3）：<b>强制查本人</b>，请求体里的 {@code patientId} 一律忽略，防越权；</li>
 *   <li>医生端（roleId==3）：必须显式传 {@code patientId}，且先校验
 *       {@code appointment ∪ visit_record} 接诊关系；未授权 <b>fail-closed</b>
 *       （不返回任何数据 + 记 {@code audit_log.status=0}）。</li>
 * </ul>
 *
 * <p><b>强耦合原则</b>：不新建表 —— 审计写既有 {@code audit_log}，
 * 会话与消息写既有 {@code ai_conversation} / {@code ai_chat_record}
 * （用 {@code agent_type} 区分 USER / DOCTOR 两个主体空间）。</p>
 */
@Slf4j
@Service
public class DataAgentQaService {

    /** 用户端会话/消息归属标记。 */
    public static final String AGENT_TYPE_USER = "DATA_AGENT_USER";
    /** 医生端会话/消息归属标记。 */
    public static final String AGENT_TYPE_DOCTOR = "DATA_AGENT_DOCTOR";
    /** 审计资源名。 */
    private static final String AUDIT_RESOURCE = "data_agent";
    private static final String AUDIT_ACTION = "DATA_QUERY";

    /** 读取会话消息给前端回看的默认上限。 */
    private static final int SESSION_MSG_LIMIT = 200;

    /**
     * 多轮上下文最多回看的消息条数。
     *
     * <p>取偶数是为了让 {@code user}/{@code assistant} 成对出现 —— 只回看奇数条会出现
     * 「有问无答」或「有答无问」的悬空上下文，反而误导意图识别。</p>
     */
    private static final int MULTI_TURN_MSG_LIMIT = 6;

    /**
     * 多轮上下文的字符上限（约 600~800 token），防长报告把 prompt 撑爆。
     *
     * <p>{@code persist} 存进 {@code ai_chat_record} 的 assistant 消息可能是报告全文
     * （上千字），不做截断会导致 token 成本与延迟失控。</p>
     */
    private static final int MULTI_TURN_MAX_CHARS = 1500;

    @Resource
    private DataAgentPipeline pipeline;

    @Resource
    private DataAgentConfigHolder configHolder;

    @Resource
    private UserHealthMapper userHealthMapper;

    @Resource
    private DataSource dataSource;

    /**
     * JDBC 模板工厂。
     *
     * <p>抽成方法而非在 8 处内联 {@code new JdbcTemplate(dataSource)}，是为了让单元测试
     * 能用子类把它替换成桩实现 —— 本类的会话落库、审计写入、配置读取全都直连数据库，
     * 不做这层抽取就只能靠 Mockito 逐个 mock DataSource/Connection/PreparedStatement，
     * 且 {@code persist()} / {@code writeAudit()} 都是 private 无法覆写。</p>
     */
    protected JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 执行一次数据问答。
     *
     * @param req  请求
     * @param sink 步骤回调（SSE 链路传入；同步链路传 {@link StepSink#NOOP}）
     * @param http 用于审计的请求上下文，可空
     */
    public DataAgentQaResult query(DataAgentQaRequest req, StepSink sink, HttpServletRequest http) {
        long started = System.currentTimeMillis();
        Integer callerId = LocalThreadHolder.getUserId();
        Integer roleId = LocalThreadHolder.getRoleId();
        boolean doctor = Integer.valueOf(3).equals(roleId);
        String agentType = doctor ? AGENT_TYPE_DOCTOR : AGENT_TYPE_USER;

        if (callerId == null) {
            return DataAgentQaResult.error("未登录，无法使用数据问答", elapsed(started));
        }

        Long dataOwnerId;
        if (doctor) {
            Integer patientId = req.getPatientId();
            if (patientId == null) {
                writeAudit(callerId, http, 0, "医生端未指定 patientId，拒绝执行");
                return DataAgentQaResult.error("医生端必须指定患者（patientId）", elapsed(started));
            }
            Integer access = userHealthMapper.countDoctorPatientAccess(callerId, patientId);
            if (access == null || access <= 0) {
                writeAudit(callerId, http, 0, "医生 " + callerId + " 无患者 " + patientId + " 的接诊授权，已拒绝");
                return DataAgentQaResult.error("无权访问该患者数据（无接诊记录）", elapsed(started));
            }
            dataOwnerId = patientId.longValue();
        } else {
            // 用户端强制查本人：忽略请求里的 patientId，杜绝越权
            dataOwnerId = callerId.longValue();
        }

        // ---- 多轮上下文回填 ----
        // 前端若自己组装了 multiTurn 则尊重其取值；否则后端从当前会话最近若干条消息自取。
        // 目的是让「那血糖呢」「再详细一点」这类追问能承接上一轮的指标与时间范围。
        if (req.getMultiTurn() == null || req.getMultiTurn().trim().isEmpty()) {
            req.setMultiTurn(buildMultiTurnContext(req));
        }

        DataAgentQaResult result;
        try {
            // 2026-10-07：传入使用者类型，使管理员能按用户端/医生端分别配置
            // 开关、报告、行数上限、SQL 超时与沙箱字节上限。
            result = pipeline.run(req, dataOwnerId, sink == null ? StepSink.NOOP : sink,
                    doctor ? DataAgentConfigHolder.AUDIENCE_DOCTOR : DataAgentConfigHolder.AUDIENCE_USER);
        } catch (Exception e) {
            log.error("[DataAgent] 执行异常", e);
            writeAudit(callerId, http, 0, "执行异常: " + e.getMessage());
            return DataAgentQaResult.error("数据问答执行异常：" + e.getMessage(), elapsed(started));
        }

        if (result.isSuccess()) {
            writeAudit(callerId, http, 1,
                    "数据问答成功: intent=" + result.getIntent() + ", rows=" + result.getRowCount());
            persist(req, callerId, agentType, result);
        } else {
            writeAudit(callerId, http, 0, "数据问答失败: " + result.getMessage());
        }
        return result;
    }

    /**
     * 工具入口（P1-2 / P1-4，2026-10-07）。
     *
     * <p><b>与 {@link #query} 的区别</b>：身份与授权已由调用方
     * （{@code DataQueryTool}）依据 {@link ToolContext} 完成，本方法<b>只</b>负责：</p>
     * <ol>
     *   <li>跑 {@code DataAgentPipeline}（使用传入的 {@code dataOwnerId} 与 {@code audience}）；</li>
     *   <li>写审计（{@code http==null} 时从 {@link ToolContext} 读客户端 IP / UA 等）；</li>
     *   <li><b>不</b>落 {@code ai_conversation} / {@code ai_chat_record} —— 对话主链路已落库，
     *       工具层再落会重复且可能改写会话归属，导致历史错乱。</li>
     * </ol>
     *
     * <p>🔴 安全边界：{@code dataOwnerId} 必须由工具侧鉴权后传入，本方法<b>不再</b>二次判定；
     * 若直接被外部调用且传入错误主体，风险由调用方承担。故本方法仅 package/同上下文可见，
     * 真正的鉴权在 {@code DataQueryTool} 内。</p>
     *
     * @param req         请求
     * @param dataOwnerId 已鉴权后的数据归属主体（用户端=本人 userId；医生端=已授权 patientId）
     * @param audience    使用者类型（{@code "user"} / {@code "doctor"}）
     * @param callerId    调用方用户/医生 ID（仅用于审计落库）
     * @param sink        步骤回调（工具内通常传 {@link StepSink#NOOP}）
     */
    public DataAgentQaResult queryForTool(DataAgentQaRequest req, Long dataOwnerId, String audience,
                                          Integer callerId, StepSink sink) {
        long started = System.currentTimeMillis();
        if (callerId == null) {
            return DataAgentQaResult.error("未登录，无法使用数据问答", elapsed(started));
        }
        if (dataOwnerId == null) {
            return DataAgentQaResult.error("无法确定数据查询主体", elapsed(started));
        }
        DataAgentQaResult result;
        try {
            result = pipeline.run(req, dataOwnerId, sink == null ? StepSink.NOOP : sink, audience);
        } catch (Exception e) {
            log.error("[DataAgent] 工具入口执行异常", e);
            writeAudit(callerId, null, 0, "执行异常: " + e.getMessage());
            return DataAgentQaResult.error("数据问答执行异常：" + e.getMessage(), elapsed(started));
        }
        if (result.isSuccess()) {
            writeAudit(callerId, null, 1, "数据问答成功: intent=" + result.getIntent()
                    + ", rows=" + result.getRowCount());
        } else {
            writeAudit(callerId, null, 0, "数据问答失败: " + result.getMessage());
        }
        return result;
    }

    /** 会话历史（按 agent_type + 主体过滤，避免医生/用户 id 空间串号）。 */
    public List<Map<String, Object>> history() {
        Integer callerId = LocalThreadHolder.getUserId();
        Integer roleId = LocalThreadHolder.getRoleId();
        if (callerId == null) {
            return new ArrayList<>();
        }
        String agentType = Integer.valueOf(3).equals(roleId) ? AGENT_TYPE_DOCTOR : AGENT_TYPE_USER;
        JdbcTemplate jdbc = jdbc();
        // 双端隔离：会话侧（c）与消息侧（r）都过滤 user_id + agent_type。
        // 此前只过滤了 c 侧；虽然 JOIN 约束下不会真的泄露，但与 sessions() / clearSessions() /
        // sessionMessages() 的双条件写法不一致，属于口径漂移 —— 一旦将来 SQL 改成 LEFT JOIN
        // 或去掉 JOIN，就会变成越权读。现补齐。
        // 已实测（2026-10-07）：库内 c 侧与 r 侧归属不一致的脏数据为 0 行，加过滤不会让历史消失。
        String sql = "SELECT r.id, r.conversation_id, r.role, r.content, r.agent_type, r.create_time "
                + "FROM ai_chat_record r JOIN ai_conversation c ON c.id = r.conversation_id "
                + "WHERE c.user_id = ? AND c.agent_type = ? "
                + "AND r.user_id = ? AND r.agent_type = ? "
                + "ORDER BY r.id DESC LIMIT 100";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, callerId, agentType, callerId, agentType);
            // 返回正序，便于前端直接渲染
            List<Map<String, Object>> ordered = new ArrayList<>(rows);
            java.util.Collections.reverse(ordered);
            return ordered;
        } catch (Exception e) {
            log.warn("[DataAgent] 读取历史失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ---------------- 会话历史（对齐 DataAgent ChatSidebar 的会话管理能力） ----------------

    /**
     * 我的会话列表（按最近活跃倒序）。
     *
     * <p>与 {@link #history()} 的差别：history() 是<b>扁平的最近消息流</b>（用于快速回看问答），
     * sessions() 提供<b>会话维度</b>的列表，供前端历史侧边栏展示与选择。</p>
     */
    public List<Map<String, Object>> sessions() {
        Integer callerId = LocalThreadHolder.getUserId();
        if (callerId == null) {
            return new ArrayList<>();
        }
        String agentType = currentAgentType();
        JdbcTemplate jdbc = jdbc();
        String sql = "SELECT c.id, c.title, c.message_count, c.create_time, c.last_message_time "
                + "FROM ai_conversation c WHERE c.user_id = ? AND c.agent_type = ? "
                + "ORDER BY c.last_message_time DESC, c.id DESC LIMIT 100";
        try {
            return jdbc.queryForList(sql, callerId, agentType);
        } catch (Exception e) {
            log.warn("[DataAgent] 读取会话列表失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 指定会话的消息（仅限本人本端）。会话不存在或不属于当前主体 → 返回空列表（fail-closed）。
     */
    public List<Map<String, Object>> sessionMessages(String sessionId) {
        return sessionMessages(sessionId, SESSION_MSG_LIMIT);
    }

    /**
     * 指定会话的消息（仅限本人本端），最多返回 {@code limit} 条（取最近若干条，仍按时间正序）。
     *
     * <p>用子查询先 {@code ORDER BY id DESC LIMIT ?} 再外层 {@code ORDER BY id ASC}，
     * 这样「取最近 N 条」与「按时间正序返回」两个需求能同时满足。</p>
     *
     * <p>⚠️ 旧实现没有 LIMIT，长会话会把全部历史一次性拉出来 —— 作为多轮上下文注入时
     * 会直接撑爆 prompt，故新增本重载并在调用处强制截断。</p>
     */
    public List<Map<String, Object>> sessionMessages(String sessionId, int limit) {
        Integer callerId = LocalThreadHolder.getUserId();
        Long cid = parseId(sessionId);
        if (callerId == null || cid == null) {
            return new ArrayList<>();
        }
        int cap = limit <= 0 ? SESSION_MSG_LIMIT : limit;
        String agentType = currentAgentType();
        JdbcTemplate jdbc = jdbc();
        try {
            Integer owned = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ai_conversation WHERE id = ? AND user_id = ? AND agent_type = ?",
                    Integer.class, cid, callerId, agentType);
            if (owned == null || owned == 0) {
                return new ArrayList<>();
            }
            return jdbc.queryForList(
                    "SELECT * FROM (SELECT id, role, content, create_time FROM ai_chat_record "
                            + "WHERE conversation_id = ? AND user_id = ? AND agent_type = ? "
                            + "ORDER BY id DESC LIMIT ?) t ORDER BY id ASC",
                    cid, callerId, agentType, cap);
        } catch (Exception e) {
            log.warn("[DataAgent] 读取会话消息失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 构建多轮上下文文本：读当前会话最近若干条消息，拼成 {@code user:/assistant:} 交替文本。
     *
     * <p><b>为什么后端自取而不是让前端传</b>：{@code DataAgentQaRequest.multiTurn} 字段
     * 与 prompt 模板里的 {@code {multi_turn}} 占位符早已存在，但前端从未回传，
     * 导致该占位符恒渲染为空串。这里直接从库里取，前端零改动也能生效；
     * 前端若将来自己组装，也可以传进来覆盖（此处仅在为空时回填）。</p>
     *
     * <p>权限自洽：{@link #sessionMessages(String, int)} 内部用
     * {@code LocalThreadHolder} 当前身份 + {@code agent_type} 双重过滤，
     * 非本人会话返回空 → 不会把别人的历史注入进来。</p>
     *
     * @return 多轮文本；无会话 / 无历史 / 读取失败时返回空串（退化为单轮，不报错）
     */
    private String buildMultiTurnContext(DataAgentQaRequest req) {
        try {
            String sid = req == null ? null : req.getSessionId();
            if (sid == null || sid.trim().isEmpty()) {
                return "";
            }
            return toMultiTurnText(sessionMessages(sid, MULTI_TURN_MSG_LIMIT));
        } catch (Exception e) {
            log.warn("[DataAgent] 多轮上下文构建失败，按单轮处理: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 消息列表 → 多轮上下文文本（纯函数，便于单测）。
     *
     * <p>规则：只取末尾 {@link #MULTI_TURN_MSG_LIMIT} 条（调用方已限流，这里再兜一层），
     * 形如 {@code user: …} / {@code assistant: …} 逐行拼接，最终按
     * {@link #MULTI_TURN_MAX_CHARS} 从【尾部】截断（保留最近的轮次，旧的丢掉）。</p>
     */
    static String toMultiTurnText(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        List<Map<String, Object>> tail = messages.size() > MULTI_TURN_MSG_LIMIT
                ? messages.subList(messages.size() - MULTI_TURN_MSG_LIMIT, messages.size())
                : messages;
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> m : tail) {
            if (m == null) {
                continue;
            }
            Object role = m.get("role");
            Object content = m.get("content");
            if (content == null || String.valueOf(content).trim().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("assistant".equalsIgnoreCase(String.valueOf(role)) ? "assistant: " : "user: ")
                    .append(String.valueOf(content).trim());
        }
        String text = sb.toString();
        if (text.length() > MULTI_TURN_MAX_CHARS) {
            // 从尾部保留：最近的轮次才是当前追问真正依赖的上下文
            text = "…（更早的对话已省略）\n" + text.substring(text.length() - MULTI_TURN_MAX_CHARS);
        }
        return text;
    }

    /**
     * 删除一个会话（连同其消息）。
     *
     * <p>删除条件始终包含 {@code user_id} + {@code agent_type} —— 即使传入别人的会话 id
     * 也删不掉（返回 false），不存在横向越权面。</p>
     *
     * @return true 表示确实删掉了一个会话
     */
    public boolean deleteSession(String sessionId) {
        Integer callerId = LocalThreadHolder.getUserId();
        Long cid = parseId(sessionId);
        if (callerId == null || cid == null) {
            return false;
        }
        String agentType = currentAgentType();
        JdbcTemplate jdbc = jdbc();
        try {
            int removed = jdbc.update(
                    "DELETE FROM ai_conversation WHERE id = ? AND user_id = ? AND agent_type = ?",
                    cid, callerId, agentType);
            if (removed > 0) {
                jdbc.update("DELETE FROM ai_chat_record WHERE conversation_id = ? AND user_id = ? AND agent_type = ?",
                        cid, callerId, agentType);
            }
            return removed > 0;
        } catch (Exception e) {
            log.warn("[DataAgent] 删除会话失败: {}", e.getMessage());
            return false;
        }
    }

    /** 清空当前主体的全部会话（医生端与用户端互不影响，靠 agent_type 隔离）。 */
    public int clearSessions() {
        Integer callerId = LocalThreadHolder.getUserId();
        if (callerId == null) {
            return 0;
        }
        String agentType = currentAgentType();
        JdbcTemplate jdbc = jdbc();
        try {
            jdbc.update("DELETE FROM ai_chat_record WHERE user_id = ? AND agent_type = ?", callerId, agentType);
            return jdbc.update("DELETE FROM ai_conversation WHERE user_id = ? AND agent_type = ?", callerId, agentType);
        } catch (Exception e) {
            log.warn("[DataAgent] 清空会话失败: {}", e.getMessage());
            return 0;
        }
    }

    /** 重命名会话（仅限本人本端）。 */
    public boolean renameSession(String sessionId, String title) {
        Integer callerId = LocalThreadHolder.getUserId();
        Long cid = parseId(sessionId);
        if (callerId == null || cid == null || title == null || title.trim().isEmpty()) {
            return false;
        }
        String agentType = currentAgentType();
        String safeTitle = title.trim();
        if (safeTitle.length() > 200) {
            safeTitle = safeTitle.substring(0, 200);
        }
        JdbcTemplate jdbc = jdbc();
        try {
            return jdbc.update("UPDATE ai_conversation SET title = ?, update_time = NOW() "
                            + "WHERE id = ? AND user_id = ? AND agent_type = ?",
                    safeTitle, cid, callerId, agentType) > 0;
        } catch (Exception e) {
            log.warn("[DataAgent] 重命名会话失败: {}", e.getMessage());
            return false;
        }
    }

    /** 当前请求所属的主体空间（医生 / 用户）。 */
    private String currentAgentType() {
        return Integer.valueOf(3).equals(LocalThreadHolder.getRoleId()) ? AGENT_TYPE_DOCTOR : AGENT_TYPE_USER;
    }

    /**
     * 当前端的预设问题（管理员可配，未配置时用内置默认）。
     *
     * <p>医生端与用户端各一份，管理员在「数据智能体 → 预设问题」中维护。</p>
     */
    public List<String> presetQuestions(boolean doctor) {
        try {
            return configHolder.presetQuestions(doctor);
        } catch (Exception e) {
            log.warn("[DataAgent] 读取预设问题失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private static Long parseId(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------------- 内部实现 ----------------

    private void persist(DataAgentQaRequest req, Integer callerId, String agentType, DataAgentQaResult result) {
        try {
            JdbcTemplate jdbc = jdbc();
            String sessionId = req.getSessionId();
            Long conversationId = null;
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                try {
                    conversationId = Long.parseLong(sessionId.trim());
                    Integer exists = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM ai_conversation WHERE id = ? AND user_id = ? AND agent_type = ?",
                            Integer.class, conversationId, callerId, agentType);
                    if (exists == null || exists == 0) {
                        conversationId = null;
                    }
                } catch (NumberFormatException ignored) {
                    conversationId = null;
                }
            }
            if (conversationId == null) {
                conversationId = createConversation(jdbc, callerId, agentType, req.normalizedQuestion());
            }
            insertMessage(jdbc, conversationId, callerId, "user", req.normalizedQuestion(), agentType);
            String assistant = result.getReport() != null && !result.getReport().isEmpty()
                    ? result.getReport() : result.getAnswer();
            if (assistant != null && !assistant.isEmpty()) {
                insertMessage(jdbc, conversationId, callerId, "assistant", assistant, agentType);
            }
            jdbc.update("UPDATE ai_conversation SET message_count = message_count + 2, last_message_time = NOW(), "
                    + "update_time = NOW() WHERE id = ?", conversationId);
            result.setSessionId(String.valueOf(conversationId));
        } catch (Exception e) {
            log.warn("[DataAgent] 会话持久化失败（不影响查询结果）: {}", e.getMessage());
        }
    }

    private Long createConversation(JdbcTemplate jdbc, Integer callerId, String agentType, String title) {
        PreparedStatementCreator psc = (Connection con) -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO ai_conversation (user_id, title, agent_type, message_count, last_message_time, "
                            + "create_time, update_time) VALUES (?, ?, ?, 0, NOW(), NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, callerId);
            ps.setString(2, title.length() > 200 ? title.substring(0, 200) : title);
            ps.setString(3, agentType);
            return ps;
        };
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(psc, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    private void insertMessage(JdbcTemplate jdbc, Long conversationId, Integer callerId,
                               String role, String content, String agentType) {
        if (conversationId == null) {
            return;
        }
        jdbc.update("INSERT INTO ai_chat_record (conversation_id, user_id, role, content, agent_type, create_time) "
                        + "VALUES (?, ?, ?, ?, ?, NOW())",
                conversationId, callerId, role, content.length() > 16000 ? content.substring(0, 16000) : content, agentType);
    }

    private void writeAudit(Integer callerId, HttpServletRequest http, int status, String description) {
        try {
            // P1-3：工具层没有 HttpServletRequest（运行在线程池），从 ToolContext 回落读 HTTP 元数据。
            String ip = http != null ? clientIp(http) : ToolContext.getClientIp();
            String ua = http != null ? trim(http.getHeader("User-Agent"), 480) : ToolContext.getUserAgent();
            String method = http != null ? http.getMethod() : ToolContext.getRequestMethod();
            String url = http != null ? trim(http.getRequestURI(), 480) : ToolContext.getRequestUrl();
            JdbcTemplate jdbc = jdbc();
            jdbc.update("INSERT INTO audit_log (user_id, user_name, action, resource, resource_id, description, "
                            + "ip_address, user_agent, request_method, request_url, status, error_msg, create_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    callerId, null, AUDIT_ACTION, AUDIT_RESOURCE, null,
                    description == null ? "" : (description.length() > 480 ? description.substring(0, 480) : description),
                    ip, ua, method, url,
                    status, status == 1 ? null : trim(description, 480), Timestamp.valueOf(LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("[DataAgent] 审计写入失败: {}", e.getMessage());
        }
    }

    private static String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return http.getRemoteAddr();
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static long elapsed(long started) {
        return System.currentTimeMillis() - started;
    }
}
