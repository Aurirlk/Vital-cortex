package cn.kmbeast.core.nl2sql;

import cn.kmbeast.config.Nl2SqlReadOnlyDataSourceProvider;
import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.ForeignKey;
import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.TableMeta;
import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.TableSchema;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 数据智能体流水线（DataAgent 迁移核心）。
 *
 * <p>对齐 DataAgent 的节点编排（意图识别 → 查询增强 → 表召回 → 表关系 → SQL 生成 →
 * 语义一致性 → 执行 → 报告），并<b>每步回调</b> {@link StepSink}，
 * 支撑前端「工作流 / 思考过程 / 工具调用」三视图。</p>
 *
 * <p><b>安全边界（不可绕过）</b>：</p>
 * <ol>
 *   <li>执行主体 {@code dataOwnerId} 由 {@code DataAgentQaService} 依据身份 + 授权得出，
 *       本类只负责「把它变成 SQL 上的租户条件」；</li>
 *   <li>SQL 必经 {@link MySqlAiSqlGuard#assertSafe} 词法校验；</li>
 *   <li>SQL 必经 {@link TenantSqlRewriter} 中和模型写的租户谓词并无条件追加主体条件；</li>
 *   <li>SQL 所涉<b>任一</b>表带租户列而拿不到执行主体 → <b>fail-closed</b>，直接失败而不是放行；
 *       子查询 / CTE、隐式逗号连接 {@code FROM a, b}、引用白名单外表的 SQL → 同样 fail-closed
 *       （这三类都无法用词法安全定位租户列，放行等于跨租户泄漏）；</li>
 *   <li>行数 / 超时 / 重试次数均为硬上限。</li>
 * </ol>
 */
@Slf4j
@Service
public class DataAgentPipeline {

    /** thinking 文本最大保留长度（避免 SSE 帧过大）。 */
    private static final int MAX_THINKING_CHARS = 6000;
    /** 结果预览行数。 */
    private static final int ROW_PREVIEW = 20;
    /**
     * 修复重试上限的历史默认值。
     *
     * @deprecated 2026-10-07 起实际使用 {@code config.getMaxFixRetry()}（可在线配置，钳制 0~5），
     *             本常量仅保留作历史参照，不再被引用。
     */
    @Deprecated
    private static final int MAX_FIX_RETRY = 2;
    private static final String[] TENANT_CANDIDATE_COLUMNS = {"user_id", "patient_id", "doctor_id"};
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[[^\\]]*]");

    @Resource
    private DataAgentConfigHolder config;

    @Resource
    private JdbcSchemaIntrospector introspector;

    @Resource
    private Nl2SqlSchemaService schemaService;

    @Resource
    private Nl2SqlPromptBuilder promptBuilder;

    @Resource
    private Nl2SqlLlmClient llm;

    /**
     * NL2SQL 专用只读数据源提供者。bean 恒存在，但未配置
     * {@code crm.ai.nl2sql.read-only.url} 时其 {@link Nl2SqlReadOnlyDataSourceProvider#get()}
     * 返回 {@code null}，此时 {@link #execDataSource()} <b>拒绝执行</b>。
     */
    @Autowired(required = false)
    @Qualifier(Nl2SqlReadOnlyDataSourceProvider.BEAN_NAME)
    private Nl2SqlReadOnlyDataSourceProvider readOnlyDataSourceProvider;

    /**
     * 取「执行 SQL 用」的数据源：<b>只接受只读源，缺失即失败</b>。
     *
     * <p>2026-10-12：只读池改由 {@link Nl2SqlReadOnlyDataSourceProvider} 持有。
     * 此前本类注入的是一个 {@code DataSource} bean，而「注册任何 DataSource bean」
     * 会让 Spring Boot 的 {@code DataSourceAutoConfiguration} 整体退避，导致主
     * {@code dataSource} 永不创建、应用在任何 profile 下都启动失败。详见该提供者类注释。</p>
     *
     * <p><b>2026-10-20（阶段 6）：取消「未配置只读源 → 回落主数据源」的兜底。</b>
     * 原兜底把模型生成的 SQL 打到主库账号上执行 —— 而主库账号拥有全部写权限，
     * 只读保证只剩 {@code MySqlAiSqlGuard} 的正则词法守卫这一层。守卫是正则级的，
     * 不能作为唯一防线（本项目自己的类注释就是这么写的）。缺只读源时静默回落，
     * 等于让「配置遗漏」变成「安全等级无提示地降级」，且从日志上完全看不出来。
     * 现在改为 fail-closed：明确报错并给出补配置的路径。</p>
     */
    private DataSource execDataSource() {
        Nl2SqlReadOnlyDataSourceProvider provider = readOnlyDataSourceProvider;
        DataSource ro = provider == null ? null : provider.get();
        if (ro == null) {
            throw new ReadOnlyDataSourceUnavailableException(
                    "未配置 NL2SQL 只读数据源（crm.ai.nl2sql.read-only.url），"
                            + "为避免用主库账号执行模型生成的 SQL，已拒绝执行。"
                            + "请先执行 Data/02_迁移脚本/09_create_ai_ro_user.sql 创建只读账号，"
                            + "再配置 crm.ai.nl2sql.read-only.url / username / password。");
        }
        return ro;
    }

    /**
     * 只读数据源缺失。
     *
     * <p>刻意做成独立类型：这是<b>配置错误</b>，不是「SQL 写得不对」。
     * {@link #executeWithRetry} 捕获所有异常后会让模型重写 SQL 再试，
     * 而缺配置重写一万次也不会好 —— 那样只会刷掉重试次数、
     * 并把真正的原因埋在一串「已修复 SQL 并重试」日志后面。</p>
     */
    static final class ReadOnlyDataSourceUnavailableException extends IllegalStateException {
        ReadOnlyDataSourceUnavailableException(String message) {
            super(message);
        }
    }

    /**
     * 业务术语 Evidence（指标字典 + 口语别名 → db_name 映射）。
     * 见 {@link Nl2SqlEvidenceService}。可能为 {@code null}（单测桩未注入），
     * 故取值统一走 {@link #safeEvidence()}。
     */
    @Resource
    private Nl2SqlEvidenceService evidenceService;

    /**
     * 安全取术语 Evidence：任何异常都退化为空串，绝不让术语功能拖垮整条流水线。
     */
    private String safeEvidence() {
        try {
            Nl2SqlEvidenceService svc = this.evidenceService;
            if (svc == null) {
                return "";
            }
            String text = svc.evidence();
            return text == null ? "" : text;
        } catch (Exception e) {
            log.warn("[DataAgent] 术语 Evidence 获取失败，按无 Evidence 处理: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 2026-10-07 新增：长期记忆（用户偏好）。
     *
     * <p><b>为什么要接</b>：{@code AgentMemoryService} 的用户偏好此前只被 REST 层读写，
     * 任何 Agent 链路都不读 → 记忆对用户不可见。</p>
     *
     * <p><b>为什么拼进 Evidence 而不是新开一个占位符</b>：Evidence 段的语义就是
     * "补充模型不知道的业务语义"（术语字典 + 口语别名→列名映射），用户偏好同属此类；
     * 而 {@code previous_step_results} 的语义是"前序步骤的真实数据"，
     * 把偏好塞进去会让模型误以为那是查询结果。<b>不动 prompt 模板，零模板改动风险。</b></p>
     *
     * <p>注入侧约束与 ReAct 链路一致：取不到身份就不注入（绝不跨用户读偏好），
     * 总量有上限，读失败降级为空。</p>
     */
    @Autowired(required = false)
    private cn.kmbeast.core.agent.AgentMemoryService memoryService;

    /** 注入 prompt 的偏好段最大字符数（与 ReAct 链路同量级）。 */
    private static final int MEMORY_SECTION_MAX_CHARS = 400;

    /** 单条偏好最大字符数。 */
    private static final int MEMORY_ITEM_MAX_CHARS = 60;

    /**
     * 安全取用户偏好文本，异常一律降级为空串。
     *
     * @param dataOwnerId 数据归属主体；为 {@code null} 时不注入
     * @return 偏好文本（可能为空串），永不返回 {@code null}
     */
    private String safeMemory(Long dataOwnerId) {
        try {
            if (dataOwnerId == null) {
                return "";
            }
            cn.kmbeast.core.agent.AgentMemoryService svc = this.memoryService;
            if (svc == null) {
                return "";
            }
            // 注意：这里用 dataOwnerId 而非 LocalThreadHolder.getUserId()。
            // 医生端查询的是「接诊患者」的数据，记忆应归属患者本人 —— 也就是 dataOwnerId。
            // 用户端两者相同（DataAgentQaService 已把 patientId 强制改写为 callerId）。
            Map<String, String> prefs = svc.getUserPreferences(dataOwnerId.intValue());
            if (prefs == null || prefs.isEmpty()) {
                return "";
            }

            List<String> keys = new java.util.ArrayList<>(prefs.keySet());
            List<String> lines = new java.util.ArrayList<>();
            // 倒序：LinkedHashMap 保持插入序，倒序即「最新优先」
            for (int i = keys.size() - 1; i >= 0; i--) {
                String key = keys.get(i);
                String value = prefs.get(key);
                if (key == null || key.isBlank() || value == null || value.isBlank()) {
                    continue;
                }
                lines.add("- " + key + "：" + (value.length() > MEMORY_ITEM_MAX_CHARS
                        ? value.substring(0, MEMORY_ITEM_MAX_CHARS) + "…" : value));
            }
            if (lines.isEmpty()) {
                return "";
            }

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
                body.append(lines.get(0));
                omitted = lines.size() - 1;
            }
            return "\n\n【用户偏好（长期记忆，可据此理解口语化表达与统计口径）】\n"
                    + (omitted > 0 ? "（另有 " + omitted + " 条较早偏好未列出）\n" : "")
                    + body;
        } catch (Exception e) {
            log.warn("[DataAgent] 用户偏好读取失败，按无偏好处理: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 术语 Evidence + 用户偏好（长期记忆）拼成的注入文本。
     *
     * <p>二者同属「模型不知道的业务语义」，故合并成一个字符串传入 {@code {evidence}}，
     * 避免新增模板占位符带来的兼容风险。</p>
     *
     * @param dataOwnerId 数据归属主体
     * @return 注入文本，可能为空串
     */
    private String buildEvidenceWithMemory(Long dataOwnerId) {
        String evidence = safeEvidence();
        String memory = safeMemory(dataOwnerId);
        if (memory.isEmpty()) {
            return evidence;
        }
        return evidence + memory;
    }

    /**
     * 运行流水线。
     *
     * @param req         请求
     * @param dataOwnerId 数据归属主体 id（用户端=本人 userId；医生端=已授权的 patientId）
     * @param sink        步骤回调（同步链路传收集器，SSE 链路传 PrintWriter 写入器）
     */
    public DataAgentQaResult run(DataAgentQaRequest req, Long dataOwnerId, StepSink sink) {
        return run(req, dataOwnerId, sink, null);
    }

    /**
     * 2026-10-07 新增：带「使用者类型」的运行入口。
     *
     * <p>用途：让管理员能<b>按用户端 / 医生端分别</b>配置数据智能体的开关、报告、行数上限、
     * SQL 超时（见 {@code DataAgentConfigHolder} 的 {@code *For(audience)} 系列方法）。
     * 未传 audience 时行为与之前完全一致（读全局配置）。</p>
     *
     * @param req         请求
     * @param dataOwnerId 数据归属主体 id（用户端=本人 userId；医生端=已授权的 patientId）
     * @param sink        步骤回调
     * @param audience    使用者类型（{@code "user"} / {@code "doctor"}），null 表示不分层
     * @return 执行结果
     */
    public DataAgentQaResult run(DataAgentQaRequest req, Long dataOwnerId, StepSink sink,
                                 String audience) {
        // 2026-10-07 P1-1：audience 改为【形参透传】，不再存单例 @Service 的实例字段。
        // 原实现把它存为 volatile 字段、在 run() 内 set 并在 finally 清空，在 ReAct 多轮 +
        // toolExecutor 线程池并发时必然串端（A 轮的 doctor 配置被 B 轮的 user 读到，清空后
        // 又被其他轮读到 null），导致分层开关 / 行数 / 超时全部错乱。现在 normalize 后置入
        // doRun 形参，整条流水线同一次调用内一致、跨调用互不干扰；不加锁（加锁会串行化十步）。
        String normalized = (DataAgentConfigHolder.AUDIENCE_DOCTOR.equals(audience)
                || DataAgentConfigHolder.AUDIENCE_USER.equals(audience)) ? audience : null;
        return doRun(req, dataOwnerId, sink, normalized);
    }

    // 2026-10-07 P1-1：audience 不再作为实例字段，改为 run -> doRun -> executeWithRetry ->
    // execute 形参透传（见上方 run 方法说明）。彻底消除单例 Bean 的并发串端风险。

    private DataAgentQaResult doRun(DataAgentQaRequest req, Long dataOwnerId, StepSink sink, String audience) {
        long started = System.currentTimeMillis();
        DataAgentQaResult result = new DataAgentQaResult();
        result.setSessionId(req.getSessionId());

        StepSink out = sink == null ? StepSink.NOOP : sink;
        if (out == StepSink.NOOP) {
            // 同步链路（StepSink.NOOP）没有事件消费者，步骤会被直接丢弃，导致
            // POST /user/data-agent/query 返回的 result.steps 恒为空数组。
            // 这里就地收集并回放进 result —— 每 emit 一次就更新一次引用，最终拿到完整十步。
            // SSE 链路【不】收集：步骤已逐条推给前端，再塞进 result 会让 result 帧体积翻倍。
            final List<AgentStepEvent> steps = new ArrayList<>();
            out = ev -> {
                steps.add(ev);
                result.setSteps(steps);
            };
        }

        if (!config.isEnabledFor(audience)) {
            out.emit(AgentStepEvent.failed(AgentStep.INTENT, 0L, "数据智能体未开启"));
            return fill(result, DataAgentQaResult.error(
                    "数据智能体未开启（管理员可在「系统配置 → 数据智能体」中开启）", elapsed(started)));
        }
        String question = req.normalizedQuestion();
        if (question.isEmpty()) {
            return fill(result, DataAgentQaResult.error("问题不能为空", elapsed(started)));
        }

        // 术语 Evidence（指标字典 + 口语别名）+ 用户偏好（长期记忆）。
        // 整条流水线只取一次，供 SQL 生成与错误修复复用。
        // 刻意【不注入】mix-selector（选表）与 semantic-consistency（语义校验）——那两处不需要
        // 术语与偏好，注入会让 token 翻倍。取失败时返回空串，退化为无注入，不影响主流程。
        String evidence = buildEvidenceWithMemory(dataOwnerId);

        try {
            // ---- 1. 意图识别 ----
            long t = started;
            out.emit(AgentStepEvent.running(AgentStep.INTENT));
            String intentRaw = llm.chat(promptBuilder.buildIntentRecognitionPrompt(
                    nullToEmpty(req.getMultiTurn()), question), false, "");
            JSONObject intentJson = parseObject(intentRaw);
            String classification = intentJson == null ? "" : intentJson.getString("classification");
            boolean chitchat = classification != null && classification.contains("闲聊");
            AgentStepEvent intentEv = AgentStepEvent.success(AgentStep.INTENT, elapsed(t),
                    chitchat ? "判定为闲聊/无关指令" : "判定为数据分析请求");
            intentEv.withThinking(trim(intentRaw)).withPayload("classification", classification);
            out.emit(intentEv);
            if (chitchat) {
                String reply = intentJson == null ? "" : intentJson.getString("response");
                result.setIntent("CHITCHAT");
                result.setAnswer(reply == null || reply.trim().isEmpty()
                        ? "这个问题更适合日常咨询，换个和数据相关的问题试试？" : reply.trim());
                result.setSessionId(req.getSessionId());
                return fill(result, success(result, started));
            }
            result.setIntent("DATA_QUERY");

            // ---- 2. 查询增强 ----
            String canonical = question;
            t = System.currentTimeMillis();
            out.emit(AgentStepEvent.running(AgentStep.QUERY_ENHANCE));
            JSONObject enhanceJson = parseObject(llm.chat(
                    promptBuilder.buildQueryEnhancePrompt(nullToEmpty(req.getMultiTurn()), question, ""), false, ""));
            if (enhanceJson != null && enhanceJson.getString("canonical_query") != null
                    && !enhanceJson.getString("canonical_query").trim().isEmpty()) {
                canonical = enhanceJson.getString("canonical_query").trim();
            }
            AgentStepEvent enhanceEv = AgentStepEvent.success(AgentStep.QUERY_ENHANCE, elapsed(t), "已规范化查询");
            enhanceEv.withThinking(enhanceJson == null ? null : enhanceJson.toJSONString())
                    .withPayload("canonicalQuery", canonical);
            out.emit(enhanceEv);

            // ---- 3. 数据表召回 ----
            t = System.currentTimeMillis();
            out.emit(AgentStepEvent.running(AgentStep.SCHEMA_RECALL));
            List<TableMeta> allowed = schemaService.listAllowedTables();
            List<String> allowedNames = allowed.stream().map(TableMeta::getName).collect(Collectors.toList());
            List<TableSchema> fullSchema = schemaService.loadSchema(allowedNames);
            String selectionPrompt = promptBuilder.buildSchemaSelectorPrompt(
                    promptBuilder.buildSchemaText(dbId(), fullSchema), canonical, "");
            List<String> selected = sanitize(selectTables(llm.chat(selectionPrompt, false, ""), allowedNames));
            AgentStepEvent recallEv = AgentStepEvent.success(AgentStep.SCHEMA_RECALL, elapsed(t),
                    "召回 " + selected.size() + " 张候选表");
            recallEv.withPayload("candidateTables", selected).withPayload("totalTables", allowedNames.size());
            out.emit(recallEv);

            List<TableSchema> schemas = schemaService.loadSchema(selected);
            if (schemas.isEmpty()) {
                out.emit(AgentStepEvent.failed(AgentStep.SCHEMA_RECALL, 0L, "未匹配到可用数据表"));
                return fill(result, DataAgentQaResult.error("未能为该问题匹配到可用的数据表", elapsed(started)));
            }

            // ---- 4. 表关系分析 ----
            t = System.currentTimeMillis();
            out.emit(AgentStepEvent.running(AgentStep.TABLE_RELATION));
            List<String> relations = collectRelations(schemas);
            String schemaText = promptBuilder.buildSchemaText(dbId(), schemas);
            AgentStepEvent relationEv = AgentStepEvent.success(AgentStep.TABLE_RELATION, elapsed(t),
                    relations.isEmpty() ? "候选表间无外键，单表查询" : "发现 " + relations.size() + " 条表关系");
            relationEv.withPayload("relations", relations);
            out.emit(relationEv);

            // ---- 5. SQL 生成 ----
            t = System.currentTimeMillis();
            out.emit(AgentStepEvent.running(AgentStep.SQL_GENERATE));
            String sql = Nl2SqlLlmClient.stripFence(llm.chat(promptBuilder.buildSqlGeneratePrompt(
                    config.getDialect(), canonical, schemaText, evidence, canonical, "无"), true, canonical)).trim();
            AgentStepEvent genEv = AgentStepEvent.success(AgentStep.SQL_GENERATE, elapsed(t), "已生成 SQL");
            genEv.withThinking(sql).withPayload("sql", sql);
            out.emit(genEv);

            // ---- 6. 语义一致性校验（不通过则重生成一轮）----
            t = System.currentTimeMillis();
            out.emit(AgentStepEvent.running(AgentStep.SEMANTIC_CHECK));
            JSONObject checkJson = parseObject(llm.chat(promptBuilder.buildSemanticConsistencyPrompt(
                    config.getDialect(), canonical, question, "", schemaText, sql), false, ""));
            boolean passed = checkJson == null || !Boolean.FALSE.equals(checkJson.getBoolean("passed"));
            AgentStepEvent checkEv = AgentStepEvent.success(AgentStep.SEMANTIC_CHECK, elapsed(t),
                    passed ? "SQL 与问题语义一致" : "校验未通过，重新生成");
            checkEv.withThinking(checkJson == null ? null : checkJson.toJSONString()).withPayload("passed", passed);
            out.emit(checkEv);
            if (!passed && checkJson != null) {
                String reason = nullToEmpty(checkJson.getString("reason"));
                t = System.currentTimeMillis();
                out.emit(AgentStepEvent.running(AgentStep.SQL_GENERATE));
                sql = Nl2SqlLlmClient.stripFence(llm.chat(promptBuilder.buildSqlGeneratePrompt(
                        config.getDialect(), canonical, schemaText, reason, canonical, "上一版未通过语义校验：" + reason),
                        true, canonical)).trim();
                AgentStepEvent regenEv = AgentStepEvent.success(AgentStep.SQL_GENERATE, elapsed(t), "已按校验意见重生成 SQL");
                regenEv.withThinking(sql).withPayload("sql", sql).withPayload("regenerated", true);
                out.emit(regenEv);
            }

            // ---- 7. 安全守卫 ----
            t = System.currentTimeMillis();
            out.emit(AgentStepEvent.running(AgentStep.SECURITY_GUARD));
            try {
                MySqlAiSqlGuard.assertSafe(sql);
            } catch (MySqlAiSqlGuard.Nl2SqlSecurityException e) {
                out.emit(AgentStepEvent.failed(AgentStep.SECURITY_GUARD, elapsed(t), "安全校验未通过: " + e.getMessage()));
                return fill(result, DataAgentQaResult.error("生成的 SQL 未通过安全校验：" + e.getMessage(), elapsed(started)));
            }
            AgentStepEvent guardEv = AgentStepEvent.success(AgentStep.SECURITY_GUARD, elapsed(t), "词法校验通过（只读）");
            guardEv.withPayload("primaryTable", MySqlAiSqlGuard.extractPrimaryTable(sql));
            out.emit(guardEv);

            // ---- 8. 租户隔离重写（逐表覆盖 + 表白名单，2026-10-07 二次加固）----
            t = System.currentTimeMillis();
            out.emit(AgentStepEvent.running(AgentStep.TENANT_REWRITE));
            // 8.1 SQL 只允许触及「已召回白名单」内的表
            Set<String> allowedLower = new HashSet<>();
            for (String n : allowedNames) {
                allowedLower.add(n.toLowerCase(Locale.ROOT));
            }
            // 8.2 校验 + 逐表租户重写：任何「无法安全定位租户列」的形态一律 fail-closed
            StringBuilder why = new StringBuilder();
            SafeRewrite safe = safeRewrite(sql, fullSchema, allowedLower, dataOwnerId, why);
            if (safe == null) {
                out.emit(AgentStepEvent.failed(AgentStep.TENANT_REWRITE, elapsed(t), "已拒绝执行：" + why));
                return fill(result, DataAgentQaResult.error("已拒绝执行以保护数据：" + why, elapsed(started)));
            }
            List<String> tenantColumns = safe.tenantColumns;
            String tenantColumn = tenantColumns.isEmpty() ? null : tenantColumns.get(0);
            TenantSqlRewriter.Rewritten rewritten = safe.rewritten;
            AgentStepEvent tenantEv = AgentStepEvent.success(AgentStep.TENANT_REWRITE, elapsed(t),
                    tenantColumns.isEmpty() ? "所涉表均无租户列，跳过" : "已强制限定 " + tenantColumns + " = 当前主体");
            tenantEv.withPayload("tenantApplied", rewritten.isTenantApplied())
                    .withPayload("tenantColumns", tenantColumns)
                    .withPayload("tenantColumn", tenantColumn)
                    .withPayload("finalSql", rewritten.getSql());
            out.emit(tenantEv);

            result.setSql(rewritten.getSql());
            result.setTenantApplied(rewritten.isTenantApplied());
            result.setTenantColumn(tenantColumn);

            // ---- 9. 执行（含故障修复重试）----
            t = System.currentTimeMillis();
            out.emit(AgentStepEvent.running(AgentStep.EXECUTE));
            List<Map<String, Object>> rows;
            try {
            rows = executeWithRetry(result, rewritten, tenantColumns, fullSchema, allowedLower, dataOwnerId,
                    audience, canonical, schemaText, question, evidence, out);
            } catch (Exception e) {
                out.emit(AgentStepEvent.failed(AgentStep.EXECUTE, elapsed(t), "执行失败: " + e.getMessage()));
                return fill(result, DataAgentQaResult.error("SQL 执行失败：" + e.getMessage(), elapsed(started)));
            }
            result.setRows(rows);
            result.setRowCount(rows.size());
            AgentStepEvent execEv = AgentStepEvent.success(AgentStep.EXECUTE, elapsed(t), "返回 " + rows.size() + " 行");
            execEv.withPayload("rowCount", rows.size())
                    .withPayload("preview", rows.size() > ROW_PREVIEW ? rows.subList(0, ROW_PREVIEW) : rows)
                    .withPayload("columns", rows.isEmpty() ? new ArrayList<>() : new ArrayList<>(rows.get(0).keySet()));
            out.emit(execEv);

            // ---- 10. 生成报告 ----
            boolean wantReport = req.getEnableReport() != null ? req.getEnableReport()
                : config.isReportEnabledFor(audience);
            if (wantReport) {
                t = System.currentTimeMillis();
                out.emit(AgentStepEvent.running(AgentStep.REPORT));
                try {
                    String report = Nl2SqlLlmClient.stripFence(llm.chat(promptBuilder.buildReportPrompt(
                            question, canonical, result.getSql(), tenantColumn, rows), true, question));
                    result.setReport(report);
                    AgentStepEvent reportEv = AgentStepEvent.success(AgentStep.REPORT, elapsed(t), "已生成 Markdown 报告");
                    reportEv.withThinking(report).withPayload("report", report);
                    out.emit(reportEv);
                } catch (Exception e) {
                    out.emit(AgentStepEvent.failed(AgentStep.REPORT, elapsed(t), "报告生成失败: " + e.getMessage()));
                }
            } else {
                out.emit(AgentStepEvent.skipped(AgentStep.REPORT, "未开启报告生成"));
            }

            return fill(result, success(result, started));
        } catch (Exception e) {
            log.error("[DataAgent] 流水线异常", e);
            return fill(result, DataAgentQaResult.error("数据智能体处理失败：" + e.getMessage(), elapsed(started)));
        }
    }

    // ---------------- 内部实现 ----------------

    /**
     * 校验 + 逐表租户重写（<b>唯一的安全出口</b>）。
     *
     * <p>任何一条不满足，返回 {@code null} 并由调用方 fail-closed（绝不执行）：</p>
     * <ol>
     *   <li>词法守卫不通过（写操作 / DDL / 系统库 / 黑名单表）；</li>
     *   <li>子查询 / CTE —— 正则无法定位其内部租户列；</li>
     *   <li>隐式逗号连接 {@code FROM a, b} —— 第二张表不被识别，可能漏掉租户列；</li>
     *   <li>引用了<b>白名单之外</b>的表 —— 无法判定其是否有租户列（这是 2026-10-07 复核
     *       发现的关键缺口：原实现只对「已召回表」判定，引用未召回表时会静默跳过租户过滤）；</li>
     *   <li>涉及租户列却拿不到执行主体。</li>
     * </ol>
     *
     * @param why 失败原因（出参），透传到前端提示
     * @return 可安全执行的重写结果；不可安全执行时返回 {@code null}
     */
    private SafeRewrite safeRewrite(String sql, List<TableSchema> fullSchema, Set<String> allowedLower,
                                    Long dataOwnerId, StringBuilder why) {
        try {
            MySqlAiSqlGuard.assertSafe(sql);
        } catch (Exception e) {
            why.append("SQL 未通过安全校验");
            return null;
        }
        if (MySqlAiSqlGuard.hasSubqueryOrCte(sql)) {
            why.append("包含子查询 / CTE 写法（受控模式暂不支持，请改用单表或显式 JOIN）");
            return null;
        }
        if (MySqlAiSqlGuard.hasImplicitCommaJoin(sql)) {
            why.append("使用隐式逗号连接（请改用显式 JOIN ... ON）");
            return null;
        }
        List<MySqlAiSqlGuard.TableRef> refs = MySqlAiSqlGuard.extractTableRefs(sql);
        List<String> unknown = new ArrayList<>();
        for (MySqlAiSqlGuard.TableRef ref : refs) {
            if (!allowedLower.contains(ref.getTable()) && !unknown.contains(ref.getTable())) {
                unknown.add(ref.getTable());
            }
        }
        if (!unknown.isEmpty()) {
            why.append("SQL 引用了未授权的数据表 ").append(unknown);
            return null;
        }
        List<String> tenantColumns = resolveTenantColumns(fullSchema, refs);
        if (!tenantColumns.isEmpty() && dataOwnerId == null) {
            why.append("检测到租户列 ").append(tenantColumns).append(" 但无执行主体");
            return null;
        }
        TenantSqlRewriter.Rewritten rewritten = TenantSqlRewriter.rewriteAll(sql, tenantColumns);
        if (!MySqlAiSqlGuard.validate(rewritten.getSql())) {
            why.append("重写后的 SQL 未通过安全校验");
            return null;
        }
        return new SafeRewrite(rewritten, tenantColumns);
    }

    /** 安全重写结果（重写后的 SQL + 实际被限定的租户列）。 */
    private static final class SafeRewrite {
        private final TenantSqlRewriter.Rewritten rewritten;
        private final List<String> tenantColumns;

        SafeRewrite(TenantSqlRewriter.Rewritten rewritten, List<String> tenantColumns) {
            this.rewritten = rewritten;
            this.tenantColumns = tenantColumns;
        }
    }

    private List<Map<String, Object>> executeWithRetry(DataAgentQaResult result, TenantSqlRewriter.Rewritten rewritten,
                                                       List<String> tenantColumns, List<TableSchema> fullSchema,
                                                       Set<String> allowedLower, Long dataOwnerId, String audience,
                                                       String canonical, String schemaText, String question,
                                                       String evidence, StepSink out) {
        String currentSql = rewritten.getSql();
        List<String> currentTenantColumns = tenantColumns;
        String lastError = null;
        // 2026-10-07：由硬编码 MAX_FIX_RETRY 改为可配置（钳制 0~5）
        int maxRetry = config.getMaxFixRetry();
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                return execute(currentSql, dataOwnerId, currentTenantColumns, audience);
            } catch (ReadOnlyDataSourceUnavailableException e) {
                // 配置问题不重试：让模型重写 SQL 解决不了「没有只读账号」
                throw e;
            } catch (Exception e) {
                lastError = e.getMessage();
                if (attempt >= maxRetry) {
                    break;
                }
                log.warn("[DataAgent] 执行失败，第 {} 次修复: {}", attempt + 1, lastError);
                long t = System.currentTimeMillis();
                out.emit(AgentStepEvent.running(AgentStep.SQL_GENERATE));
                String fixed = Nl2SqlLlmClient.stripFence(llm.chat(promptBuilder.buildSqlErrorFixerPrompt(
                        config.getDialect(), question, schemaText, evidence, currentSql, nullToEmpty(lastError), canonical, "无"),
                        true, question)).trim();
                // 修复后的 SQL 必须重新走完整安全闸门（模型可能引入子查询/逗号连接/越权表）
                StringBuilder fixWhy = new StringBuilder();
                SafeRewrite fixSafe = safeRewrite(fixed, fullSchema, allowedLower, dataOwnerId, fixWhy);
                if (fixSafe == null) {
                    lastError = "修复后的 SQL 仍不可安全执行：" + fixWhy;
                    log.warn("[DataAgent] 放弃重试：{}", lastError);
                    break;
                }
                currentSql = fixSafe.rewritten.getSql();
                currentTenantColumns = fixSafe.tenantColumns;
                AgentStepEvent fixEv = AgentStepEvent.success(AgentStep.SQL_GENERATE, elapsed(t), "已修复 SQL 并重试");
                fixEv.withThinking(currentSql).withPayload("sql", currentSql).withPayload("fixAttempt", attempt + 1);
                out.emit(fixEv);
                result.setSql(currentSql);
                result.setTenantApplied(fixSafe.rewritten.isTenantApplied());
                result.setTenantColumn(currentTenantColumns.isEmpty() ? null : currentTenantColumns.get(0));
            }
        }
        throw new IllegalStateException(lastError == null ? "执行失败" : lastError);
    }

    private List<Map<String, Object>> execute(String sql, Long dataOwnerId, List<String> tenantColumns, String audience) {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(execDataSource());
        jdbc.getJdbcTemplate().setMaxRows(config.getMaxRowsFor(audience));
        jdbc.getJdbcTemplate().setQueryTimeout(config.getQueryTimeoutFor(audience));
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (tenantColumns != null && !tenantColumns.isEmpty() && dataOwnerId != null) {
            params.addValue("__tenantUserId", dataOwnerId);
        }
        return enforceByteBudget(jdbc.queryForList(sql, params));
    }

    /**
     * 沙箱：结果字节预算（2026-10-07 新增）。
     *
     * <p><b>为什么 {@code maxRows} 不够</b>：行数上限只约束「几行」，一个 50 列的宽表
     * 200 行轻松就是几 MB 的 JSON —— 全部塞进 prompt 会挤爆 token 预算，
     * 而 {@code TokenBudgetManager} 只在<b>请求前</b>校验历史，不校验 LLM 返回结果。</p>
     *
     * <p>策略：累计每行的 UTF-8 字节数，超预算即截断并在末尾追加一行提示，
     * <b>不抛异常</b> —— 数据智能体应当「尽力返回部分结果」而不是整体失败。</p>
     *
     * @param rows 查询结果
     * @return 受字节预算约束的结果
     */
    private List<Map<String, Object>> enforceByteBudget(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        int maxBytes = config != null ? config.getSandboxMaxBytes() : 512 * 1024;
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        long total = 0L;
        int truncated = 0;
        for (Map<String, Object> row : rows) {
            int rowBytes = estimateRowBytes(row);
            if (total + rowBytes > maxBytes) {
                truncated++;
                continue;
            }
            total += rowBytes;
            out.add(row);
        }
        if (truncated > 0) {
            Map<String, Object> notice = new LinkedHashMap<>();
            notice.put("_truncated", true);
            notice.put("_message", "结果超过沙箱字节上限（" + maxBytes + " 字节），已截断："
                    + "返回 " + out.size() + " 行，另有 " + truncated + " 行未返回。"
                    + "请缩小查询范围或增加 WHERE 条件后重试。");
            out.add(notice);
            log.warn("[DataAgent] 沙箱字节上限生效: maxBytes={}, 保留 {} 行, 丢弃 {} 行", maxBytes, out.size(), truncated);
        }
        return out;
    }

    /** 粗估单行的 UTF-8 字节数。 */
    private int estimateRowBytes(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return 16;
        }
        int bytes = 32;   // key 名 + 分隔符的固定开销
        for (Map.Entry<String, Object> e : row.entrySet()) {
            bytes += e.getKey() == null ? 4 : e.getKey().length() * 3;
            Object v = e.getValue();
            if (v == null) {
                bytes += 4;
            } else if (v instanceof byte[]) {
                bytes += ((byte[]) v).length;
            } else {
                bytes += String.valueOf(v).length() * 3;
            }
        }
        return bytes;
    }

    /**
     * 解析 SQL 中所有需要限定的「限定符.租户列」。
     *
     * <p><b>2026-10-07 关键修复</b>：原实现只解析「第一个 FROM 的表」的租户列。当主表没有
     * 租户列、而 JOIN 进来的表有（或 SQL 用 CTE 包裹）时，{@code tenantColumn} 为 null，
     * 租户过滤被整体跳过 → 跨租户数据泄漏。</p>
     *
     * <p><b>2026-10-07 二次加固</b>：判定所用的 schema 由「本次召回的表」改为
     * <b>全量白名单 schema</b>。原实现下，只要模型写出了没被召回的表，{@code tenantColumnOf}
     * 就查不到该表而静默跳过 —— 同样是泄漏路径（已由 {@link #safeRewrite} 的白名单校验兜住）。</p>
     *
     * <p>单表时返回不带限定符的列名（保持既有输出形态）；多表时统一带表名/别名前缀，
     * 避免 JOIN 两侧同名列导致 {@code Column 'user_id' in where clause is ambiguous}。</p>
     */
    private List<String> resolveTenantColumns(List<TableSchema> schemas,
                                             List<MySqlAiSqlGuard.TableRef> refs) {
        List<String> out = new ArrayList<>();
        boolean multiTable = refs.size() > 1;
        for (MySqlAiSqlGuard.TableRef ref : refs) {
            String col = tenantColumnOf(schemas, ref.getTable());
            if (col == null) {
                continue;
            }
            out.add(multiTable ? ref.getQualifier() + "." + col : col);
        }
        return out;
    }

    /** 按表名查租户列（user_id 优先，其次 patient_id / doctor_id）。 */
    private String tenantColumnOf(List<TableSchema> schemas, String table) {
        if (table == null) {
            return null;
        }
        for (TableSchema ts : schemas) {
            if (ts.getName().equalsIgnoreCase(table)) {
                for (String col : TENANT_CANDIDATE_COLUMNS) {
                    if (ts.hasColumn(col)) {
                        return col;
                    }
                }
                return null;
            }
        }
        return null;
    }

    private List<String> collectRelations(List<TableSchema> schemas) {
        Set<String> relations = new LinkedHashSet<>();
        for (TableSchema ts : schemas) {
            for (ForeignKey fk : ts.getForeignKeys()) {
                relations.add(fk.toString());
            }
        }
        return new ArrayList<>(relations);
    }

    private List<String> selectTables(String raw, List<String> allowedNames) {
        String text = Nl2SqlLlmClient.stripFence(raw).trim();
        List<String> parsed = new ArrayList<>();
        try {
            JSONArray arr = JSON.parseArray(text);
            if (arr != null) {
                parsed.addAll(arr.toList(String.class));
            }
        } catch (Exception ignored) {
            Matcher m = JSON_ARRAY_PATTERN.matcher(text);
            if (m.find()) {
                try {
                    JSONArray arr = JSON.parseArray(m.group());
                    if (arr != null) {
                        parsed.addAll(arr.toList(String.class));
                    }
                } catch (Exception ignored2) {
                    log.warn("[DataAgent] 表选择解析失败，回退为全库");
                }
            }
        }
        if (parsed.isEmpty()) {
            parsed = allowedNames;
        }
        return parsed;
    }

    private List<String> sanitize(List<String> selected) {
        return schemaService.sanitizeSelectedTables(selected);
    }

    private JSONObject parseObject(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String text = Nl2SqlLlmClient.stripFence(raw).trim();
        try {
            return JSON.parseObject(text);
        } catch (Exception ignored) {
            Matcher m = JSON_OBJECT_PATTERN.matcher(text);
            if (m.find()) {
                try {
                    return JSON.parseObject(m.group());
                } catch (Exception ignored2) {
                    return null;
                }
            }
            return null;
        }
    }

    private DataAgentQaResult fill(DataAgentQaResult result, DataAgentQaResult src) {
        result.setSuccess(src.isSuccess());
        result.setMessage(src.getMessage());
        result.setElapsedMs(src.getElapsedMs());
        return result;
    }

    private DataAgentQaResult success(DataAgentQaResult result, long started) {
        result.setSuccess(true);
        result.setElapsedMs(elapsed(started));
        if (result.getAnswer() == null && result.getReport() == null) {
            result.setAnswer("已完成查询，共 " + result.getRowCount() + " 行结果。");
        }
        return result;
    }

    private String dbId() {
        try (Connection c = execDataSource().getConnection()) {
            String catalog = c.getCatalog();
            return catalog == null || catalog.isEmpty() ? "personal_health" : catalog;
        } catch (Exception e) {
            return "personal_health";
        }
    }

    private static long elapsed(long started) {
        return System.currentTimeMillis() - started;
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_THINKING_CHARS ? s : s.substring(0, MAX_THINKING_CHARS) + "\n…（已截断）";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
