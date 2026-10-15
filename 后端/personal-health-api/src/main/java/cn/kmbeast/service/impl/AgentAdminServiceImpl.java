package cn.kmbeast.service.impl;

import cn.kmbeast.core.agent.Resolution;
import cn.kmbeast.core.agent.orchestrator.AgentOrchestrator;
import cn.kmbeast.core.agent.registry.AgentDefinition;
import cn.kmbeast.core.agent.registry.AgentRegistry;
import cn.kmbeast.core.agent.IntentResolver;
import cn.kmbeast.crm.agent.ToolRegistry;
import cn.kmbeast.crm.agent.tool.CallAgentTool;
import cn.kmbeast.crm.agent.tool.Tool;
import cn.kmbeast.mapper.AgentConfigMapper;
import cn.kmbeast.mapper.AgentHandoffMapper;
import cn.kmbeast.mapper.AgentRunMapper;
import cn.kmbeast.mapper.AgentRunStepMapper;
import cn.kmbeast.mapper.AgentToolBindingMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.AgentConfigQueryDto;
import cn.kmbeast.pojo.dto.query.extend.AgentRunQueryDto;
import cn.kmbeast.pojo.entity.AgentConfig;
import cn.kmbeast.pojo.entity.AgentHandoff;
import cn.kmbeast.pojo.entity.AgentRun;
import cn.kmbeast.pojo.entity.AgentRunStep;
import cn.kmbeast.pojo.entity.AgentToolBinding;
import cn.kmbeast.service.AgentAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 智能体管理（管理员）实现。
 *
 * <h3>三条贯穿全类的设计约束</h3>
 * <ol>
 *   <li><b>没有外键，级联必须手写。</b>本库所有表都不建 FOREIGN KEY（见结构基线），
 *       完整性靠代码保证。所以删一个 Agent 必须自己删它的工具绑定和两侧转交关系，
 *       否则会留下永远没人清理的脏行。</li>
 *   <li><b>写操作一律让注册表缓存失效。</b>注册表有 60s TTL，管理员点完保存再刷新
 *       页面仍是旧值，唯一可能的判断就是「没保存上」。</li>
 *   <li><b>数据库里的「零绑定行」等于「全量工具」，不是「没有工具」。</b>
 *       这两种语义在表结构上无法区分（都是零行），所以本类的写入口用
 *       显式的 {@code restricted} 布尔来表达，绝不靠「传了空数组」去猜。</li>
 * </ol>
 */
@Slf4j
@Service
public class AgentAdminServiceImpl implements AgentAdminService {

    /** agentKey 形态：小写字母开头，只含小写字母/数字/下划线。 */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    /** 允许的端。多端解耦（阶段 3）就以这个字段分流。 */
    private static final Set<String> AUDIENCES = Set.of("all", "user", "doctor", "admin");

    @Resource
    private AgentConfigMapper agentConfigMapper;

    @Resource
    private AgentToolBindingMapper agentToolBindingMapper;

    @Resource
    private AgentHandoffMapper agentHandoffMapper;

    @Resource
    private AgentRunMapper agentRunMapper;

    @Resource
    private AgentRunStepMapper agentRunStepMapper;

    @Resource
    private AgentRegistry agentRegistry;

    @Resource
    private AgentOrchestrator agentOrchestrator;

    @Resource
    private ToolRegistry toolRegistry;

    @Resource
    private IntentResolver intentResolver;

    // ------------------------------------------------------------------
    // 配置 CRUD
    // ------------------------------------------------------------------

    @Override
    public Result<List<AgentConfig>> queryConfig(AgentConfigQueryDto dto) {
        try {
            List<AgentConfig> rows = agentConfigMapper.query(dto.getAgentKey(), dto.getName(),
                    dto.getAudience(), dto.getEnabled(), dto.getCurrent(), dto.getSize());
            Integer total = agentConfigMapper.queryCount(dto.getAgentKey(), dto.getName(),
                    dto.getAudience(), dto.getEnabled());
            return PageResult.success(rows, total);
        } catch (Exception e) {
            return emptyPageOnFailure("智能体配置", e);
        }
    }

    @Override
    public Result<Map<String, Object>> detail(Integer id) {
        AgentConfig config = id == null ? null : agentConfigMapper.getById(id);
        if (config == null) {
            return ApiResult.error("智能体不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("config", config);
        // 注意：这里读的是 DB 的原样绑定行，不是注册表解析后的结果。
        // 管理端要展示「管理员到底配了什么」，而注册表会做合并/剔除（例如自动补 call_agent）。
        List<AgentToolBinding> bindings = agentToolBindingMapper.queryByAgentId(id);
        List<String> boundTools = new ArrayList<>();
        for (AgentToolBinding b : bindings) {
            if (Boolean.TRUE.equals(b.getEnabled())) {
                boundTools.add(b.getToolName());
            }
        }
        boolean restricted = !bindings.isEmpty();
        data.put("restricted", restricted);
        data.put("toolNames", restricted ? boundTools : null);
        data.put("effectiveToolNames", effectiveTools(config.getAgentKey()));

        List<AgentHandoff> handoffs = agentHandoffMapper.queryByFromAgentId(id);
        List<String> targets = new ArrayList<>();
        for (AgentHandoff h : handoffs) {
            AgentConfig to = agentConfigMapper.getById(h.getToAgentId());
            if (to != null && Boolean.TRUE.equals(h.getEnabled())) {
                targets.add(to.getAgentKey());
            }
        }
        data.put("handoffTargets", targets);
        data.put("dbBacked", agentRegistry.isDbBacked());
        return ApiResult.success(data);
    }

    /**
     * 该 Agent 实际会拿到哪些工具（含注册表自动补/剔的 {@code call_agent}）。
     *
     * <p>管理端必须能看到这个值，否则「我明明没绑 call_agent，它怎么出现了」
     * 无法自查 —— 而它出现/消失的规则（有委派目标就出现）是配置的另一半。</p>
     *
     * @return null 表示全量（含以后新增的工具）
     */
    private List<String> effectiveTools(String agentKey) {
        Set<String> allowed = agentRegistry.allowedToolsOrNull(agentKey);
        if (allowed == null) {
            // 全量路径下 call_agent 的取舍见 BaseReActAgent.resolveAllowedTools()
            if (!agentRegistry.handoffTargets(agentKey).isEmpty()) {
                return null;
            }
            List<String> all = new ArrayList<>();
            for (Tool t : toolRegistry.getAll()) {
                if (!CallAgentTool.TOOL_NAME.equals(t.getName())) {
                    all.add(t.getName());
                }
            }
            return all;
        }
        Set<String> merged = new LinkedHashSet<>(allowed);
        if (agentRegistry.handoffTargets(agentKey).isEmpty()) {
            merged.remove(CallAgentTool.TOOL_NAME);
        } else {
            merged.add(CallAgentTool.TOOL_NAME);
        }
        return new ArrayList<>(merged);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Integer> saveConfig(AgentConfig config) {
        String invalid = validateError(config, null);
        if (invalid != null) {
            return ApiResult.error(invalid);
        }
        if (agentConfigMapper.getByKey(config.getAgentKey()) != null) {
            return ApiResult.error("智能体标识已存在: " + config.getAgentKey());
        }
        // 同端只能有一个兜底：先把同端的 is_default 全部清零，再插新的，
        // 否则会有两个 is_default=1，兜底选谁取决于 SQL 的返回顺序（不可预期）
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            agentConfigMapper.clearDefault(normalizeAudience(config.getAudience()));
        }
        agentConfigMapper.save(config);
        agentRegistry.invalidate();
        log.info("[AgentAdmin] 新建智能体: key={}, id={}", config.getAgentKey(), config.getId());
        return ApiResult.success(config.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateConfig(AgentConfig config) {
        if (config == null || config.getId() == null) {
            return ApiResult.error("缺少智能体 id");
        }
        AgentConfig existing = agentConfigMapper.getById(config.getId());
        if (existing == null) {
            return ApiResult.error("智能体不存在");
        }
        String invalid = validateError(config, existing);
        if (invalid != null) {
            return ApiResult.error(invalid);
        }
        // agentKey 是 URL/DB 双份存在的逻辑主键（agent_handoff 用 id，但 logger、
        // 绑定、prompt 里的引用都用 key），改名要挡住撞车
        if (config.getAgentKey() != null && !config.getAgentKey().equals(existing.getAgentKey())) {
            AgentConfig clash = agentConfigMapper.getByKey(config.getAgentKey());
            if (clash != null && !clash.getId().equals(config.getId())) {
                return ApiResult.error("智能体标识已被占用: " + config.getAgentKey());
            }
            log.warn("[AgentAdmin] 智能体标识被改名: {} → {}（历史运行记录里的旧 key 不会随之更新）",
                    existing.getAgentKey(), config.getAgentKey());
        }
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            String audience = normalizeAudience(
                    config.getAudience() != null ? config.getAudience() : existing.getAudience());
            agentConfigMapper.clearDefault(audience);
        }
        agentConfigMapper.update(config);
        agentRegistry.invalidate();
        return ApiResult.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> batchDelete(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return ApiResult.error("请选择要删除的智能体");
        }
        for (Integer id : ids) {
            // 兜底 Agent 不允许删：注册表在找不到兜底时会自行补一个内置的
            // general_assistant，管理员会发现「删了又回来了」，与其让他困惑，
            // 不如直接拒绝并说明。
            AgentConfig config = agentConfigMapper.getById(id);
            if (config != null && Boolean.TRUE.equals(config.getIsDefault())) {
                return ApiResult.error("「" + config.getName()
                        + "」是该人群的兜底智能体，不能删除；请先指定另一个兜底");
            }
            agentToolBindingMapper.deleteByAgentId(id);
            agentHandoffMapper.deleteByAgentId(id);   // 两侧都删，见 Mapper 注释
        }
        agentConfigMapper.deleteByIds(ids);
        agentRegistry.invalidate();
        log.info("[AgentAdmin] 删除智能体: ids={}", ids);
        return ApiResult.success();
    }

    @Override
    public Result<Void> setEnabled(Integer id, boolean enabled) {
        AgentConfig config = id == null ? null : agentConfigMapper.getById(id);
        if (config == null) {
            return ApiResult.error("智能体不存在");
        }
        if (!enabled && Boolean.TRUE.equals(config.getIsDefault())) {
            return ApiResult.error("兜底智能体不能停用；请先把另一个智能体设为兜底");
        }
        AgentConfig patch = new AgentConfig();
        patch.setId(id);
        patch.setEnabled(enabled);
        agentConfigMapper.update(patch);
        agentRegistry.invalidate();
        return ApiResult.success();
    }

    // ------------------------------------------------------------------
    // 工具绑定
    // ------------------------------------------------------------------

    @Override
    public Result<List<Map<String, Object>>> toolCatalog() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tool tool : toolRegistry.getAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool.getName());
            item.put("description", tool.getDescription());
            // call_agent 不参与绑定：它由「有没有委派目标」自动决定。
            // 若把它列进可选清单，管理员会绑了它却不生效，然后来问为什么。
            item.put("autoManaged", CallAgentTool.TOOL_NAME.equals(tool.getName()));
            out.add(item);
        }
        return ApiResult.success(out);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> setToolBindings(Integer id, Boolean restricted, List<String> toolNames) {
        AgentConfig config = id == null ? null : agentConfigMapper.getById(id);
        if (config == null) {
            return ApiResult.error("智能体不存在");
        }
        if (restricted == null) {
            return ApiResult.error("缺少 restricted 参数（false=不限制，true=按白名单）");
        }
        agentToolBindingMapper.deleteByAgentId(id);

        if (Boolean.TRUE.equals(restricted)) {
            Set<String> known = new LinkedHashSet<>();
            for (Tool t : toolRegistry.getAll()) {
                known.add(t.getName());
            }
            List<AgentToolBinding> rows = new ArrayList<>();
            int order = 0;
            for (String name : new LinkedHashSet<>(toolNames == null ? List.<String>of() : toolNames)) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                if (CallAgentTool.TOOL_NAME.equals(name)) {
                    return ApiResult.error("call_agent 由「可委派目标」自动管理，不能手动绑定；"
                            + "请在「转交关系」里配置它可以委派给谁");
                }
                if (!known.contains(name)) {
                    return ApiResult.error("未知工具: " + name);
                }
                AgentToolBinding row = new AgentToolBinding();
                row.setAgentId(id);
                row.setToolName(name);
                row.setEnabled(true);
                row.setSortOrder(order++);
                rows.add(row);
            }
            if (!rows.isEmpty()) {
                agentToolBindingMapper.batchSave(rows);
            }
            // 空白名单（rows 为空）是合法状态：一个纯路由 Agent 不直接调工具，
            // 只把问题委派出去。这里刻意不报错，也不退化成「全量」。
        }
        agentRegistry.invalidate();
        log.info("[AgentAdmin] 设置工具绑定: key={}, restricted={}, tools={}",
                config.getAgentKey(), restricted, toolNames);
        return ApiResult.success();
    }

    // ------------------------------------------------------------------
    // 转交关系
    // ------------------------------------------------------------------

    @Override
    public Result<List<Map<String, Object>>> handoffList() {
        List<AgentConfig> all = agentConfigMapper.query(null, null, null, null, null, null);
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentConfig from : all) {
            List<AgentHandoff> rows = agentHandoffMapper.queryByFromAgentId(from.getId());
            if (rows.isEmpty()) {
                continue;
            }
            List<String> targets = new ArrayList<>();
            for (AgentHandoff h : rows) {
                AgentConfig to = agentConfigMapper.getById(h.getToAgentId());
                // 目标已被删除的行理论上不该存在（batchDelete 会两侧清理），
                // 但历史数据/手工 SQL 可能留下；这里跳过而不是抛异常，
                // 免得一条脏行让整个「转交关系」页面打不开。
                if (to != null) {
                    targets.add(to.getAgentKey());
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fromAgentKey", from.getAgentKey());
            item.put("fromAgentId", from.getId());
            item.put("targetKeys", targets);
            out.add(item);
        }
        return ApiResult.success(out);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> setHandoffTargets(Integer id, List<String> targetKeys) {
        AgentConfig from = id == null ? null : agentConfigMapper.getById(id);
        if (from == null) {
            return ApiResult.error("智能体不存在");
        }
        List<AgentHandoff> rows = new ArrayList<>();
        for (String key : new LinkedHashSet<>(targetKeys == null ? List.<String>of() : targetKeys)) {
            if (key == null || key.isBlank()) {
                continue;
            }
            if (key.equals(from.getAgentKey())) {
                return ApiResult.error("不能委派给自己");
            }
            AgentConfig to = agentConfigMapper.getByKey(key);
            if (to == null) {
                return ApiResult.error("目标智能体不存在: " + key);
            }
            AgentHandoff row = new AgentHandoff();
            row.setFromAgentId(id);
            row.setToAgentId(to.getId());
            row.setEnabled(true);
            rows.add(row);
        }
        agentHandoffMapper.deleteByFromAgentId(id);
        if (!rows.isEmpty()) {
            agentHandoffMapper.batchSave(rows);
        }
        agentRegistry.invalidate();
        log.info("[AgentAdmin] 设置委派目标: {} → {}", from.getAgentKey(), targetKeys);
        return ApiResult.success();
    }

    // ------------------------------------------------------------------
    // 路由试跑与运行记录
    // ------------------------------------------------------------------

    @Override
    public Result<Map<String, Object>> previewRoute(String query, String audience) {
        if (query == null || query.isBlank()) {
            return ApiResult.error("请输入要试跑的问题");
        }
        // 与真实链路共用 IntentResolver + AgentOrchestrator，保证试跑结果不是在
        // 复刻一份可能已经跑偏的逻辑。⚠️ 两者都只读、无副作用（IntentResolver
        // 的 LLM 兜底只做分类，不落库），所以试跑不会污染线上数据。
        Resolution resolution = null;
        String intentError = null;
        try {
            resolution = intentResolver.resolve(query);
        } catch (Exception e) {
            intentError = e.getMessage();
        }
        AgentOrchestrator.RouteDecision decision =
                agentOrchestrator.decide(resolution, query, audience);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agentKey", decision.getAgentKey());
        data.put("agentName", decision.getAgentName());
        data.put("reason", decision.getReason());
        data.put("score", decision.getScore());
        data.put("intent", resolution != null && resolution.intent() != null
                ? resolution.intent().name() : null);
        if (intentError != null) {
            data.put("intentError", intentError);
        }
        // 命中详情：管理员调关键词时唯一能用的反馈。
        // 只给一个结论 key 的话，改关键词就是盲调。
        List<Map<String, Object>> hits = new ArrayList<>();
        String lower = query.toLowerCase();
        for (AgentDefinition def : agentRegistry.listAll()) {
            List<String> matched = new ArrayList<>();
            for (String kw : def.keywordsOrEmpty()) {
                if (kw != null && !kw.isEmpty() && lower.contains(kw.toLowerCase())) {
                    matched.add(kw);
                }
            }
            if (!matched.isEmpty()) {
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("agentKey", def.getAgentKey());
                hit.put("score", matched.size());
                hit.put("matchedKeywords", matched);
                hits.add(hit);
            }
        }
        hits.sort((a, b) -> ((Integer) b.get("score")) - ((Integer) a.get("score")));
        data.put("keywordHits", hits);
        return ApiResult.success(data);
    }

    @Override
    public Result<List<Map<String, Object>>> queryRuns(AgentRunQueryDto dto) {
        List<AgentRun> rows;
        Integer total;
        try {
            rows = agentRunMapper.query(dto.getRunId(), dto.getParentRunId(),
                    dto.getAgentKey(), dto.getSessionId(), dto.getUserId(), dto.getStatus(),
                    dto.getCurrent(), dto.getSize());
            total = agentRunMapper.queryCount(dto.getRunId(), dto.getParentRunId(),
                    dto.getAgentKey(), dto.getSessionId(), dto.getUserId(), dto.getStatus());
        } catch (Exception e) {
            return emptyPageOnFailure("运行记录", e);
        }
        // 返回 Map 而不是实体：运行记录是「给人看的观测数据」，需要带上
        // agentName、合并后的 tokens 这类实体里没有的派生字段。
        //
        // 注意「本机到底有没有在写这张表」由 /agent/admin/status 单独回答（recording /
        // dbBacked），不在这里逐行标注：表没建时这里恒为空列表，逐行标注等于标注一团空气；
        // 而管理员真正需要的是一句「你还没执行建表脚本」，那句话属于页头横幅，不属于表格。
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentRun run : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("runId", run.getRunId());
            item.put("parentRunId", run.getParentRunId());
            item.put("agentKey", run.getAgentKey());
            item.put("agentName", agentOrchestrator.agentName(run.getAgentKey()));
            item.put("sessionId", run.getSessionId());
            item.put("userId", run.getUserId());
            item.put("status", run.getStatus());
            item.put("iterations", run.getIterations());
            item.put("tokens", tokens(run));
            item.put("latencyMs", run.getLatencyMs());
            item.put("question", run.getQuestion());
            item.put("answerDigest", run.getAnswerDigest());
            item.put("errorMsg", run.getErrorMsg());
            item.put("createTime", run.getCreateTime());
            out.add(item);
        }
        return PageResult.success(out, total);
    }

    @Override
    public Result<Map<String, Object>> runTrace(String runId) {
        if (runId == null || runId.isBlank()) {
            return ApiResult.error("缺少 runId");
        }
        // 子运行的 runId 以父 runId 为前缀（见 AgentRunRecorder.newRunId），
        // 所以一次前缀查询就能拿到整棵委派树，不用递归查库。
        List<AgentRun> runs;
        try {
            runs = agentRunMapper.listByRunIdPrefix(runId);
        } catch (Exception e) {
            // 与列表不同，这里给一句能指导下一步动作的话：「未找到该运行的记录」
            // 会让管理员以为记录被清了，而实际原因只是表还没建。
            log.warn("[AgentAdmin] 运行轨迹查询失败: {}", e.getMessage());
            return ApiResult.error("运行记录表尚不可用，请先执行建表脚本（Data/02_迁移脚本/10_agent_architecture.sql）");
        }
        if (runs.isEmpty()) {
            return ApiResult.error("未找到该运行的记录");
        }
        Map<String, Object> tree = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (AgentRun run : runs) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("runId", run.getRunId());
            node.put("parentRunId", run.getParentRunId());
            node.put("agentKey", run.getAgentKey());
            node.put("agentName", agentOrchestrator.agentName(run.getAgentKey()));
            node.put("status", run.getStatus());
            node.put("iterations", run.getIterations());
            node.put("tokens", tokens(run));
            node.put("latencyMs", run.getLatencyMs());
            node.put("question", run.getQuestion());
            node.put("answerDigest", run.getAnswerDigest());
            node.put("errorMsg", run.getErrorMsg());
            node.put("createTime", run.getCreateTime());
            List<Map<String, Object>> steps = new ArrayList<>();
            for (AgentRunStep step : agentRunStepMapper.listByRunId(run.getRunId())) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("stepIndex", step.getStepIndex());
                s.put("stepType", step.getStepType());
                s.put("toolName", step.getToolName());
                s.put("targetAgent", step.getTargetAgent());
                s.put("argsJson", step.getArgsJson());
                s.put("resultDigest", step.getResultDigest());
                s.put("latencyMs", step.getLatencyMs());
                s.put("createTime", step.getCreateTime());
                steps.add(s);
            }
            node.put("steps", steps);
            nodes.add(node);
        }
        nodes.sort((a, b) -> String.valueOf(a.get("runId")).compareTo(String.valueOf(b.get("runId"))));
        tree.put("runId", runId);
        tree.put("nodes", nodes);
        tree.put("nodeCount", nodes.size());
        tree.put("handoffCount", Math.max(0, nodes.size() - 1));
        return ApiResult.success(tree);
    }

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    /**
     * 读路径的降级：表还没建时回一个空页，而不是把「还没建表」报成 500。
     *
     * <p><b>为什么只对读路径这么做</b>：管理端「智能体平台」页恰恰是管理员
     * 被告知「请先执行 10_agent_architecture.sql」之后会打开的地方，
     * 此时它必然还没有表。让这个页面 500，等于把一件<b>预期内的正常状态</b>
     * （脚本还没跑）呈现成功能故障，管理员会先去排查代码而不是去跑脚本。
     * 这与 {@code AgentRegistry} 对表未建的处理是同一个判断。</p>
     *
     * <p><b>写路径刻意不在此列</b>：把 {@code saveConfig} 的异常也吞掉会得到
     * 「点了保存、提示成功、数据没落库」—— 那比一个明确的 500 糟得多。
     * 所以只有「本来就没有数据可看」的查询走这里。</p>
     */
    private <T> Result<List<T>> emptyPageOnFailure(String what, Exception e) {
        log.warn("[AgentAdmin] {} 查询失败，按空结果返回: {}", what, e.getMessage());
        return PageResult.success(new ArrayList<>(), 0);
    }
    /**
     * 新增/修改的公共校验。
     *
     * <p>返回错误<b>消息</b>而不是 {@code Result}：新增走 {@code Result<Integer>}、
     * 修改走 {@code Result<Void>}，让校验方法返回具体类型会被泛型绑死，
     * 只能靠强转绕过去 —— 那等于把类型安全换成了「我看着没问题」。</p>
     *
     * @param existing 修改时的原行；新增传 null
     * @return null 表示通过，否则是给用户看的错误消息
     */
    private String validateError(AgentConfig config, AgentConfig existing) {
        if (config == null) {
            return "请求体为空";
        }
        // 新建时 key/name 必填；修改时只校验传了的字段（MyBatis 的 update 是
        // 「只更新非空字段」的语义，所以部分字段的 patch 是合法调用）
        boolean creating = existing == null;
        if (creating || config.getAgentKey() != null) {
            String key = config.getAgentKey();
            if (key == null || key.isBlank()) {
                return "智能体标识不能为空";
            }
            if (!KEY_PATTERN.matcher(key).matches()) {
                return "智能体标识只能用小写字母、数字、下划线，且以字母开头: " + key;
            }
            if (AgentRegistry.FALLBACK_KEY.equals(key) && !creating
                    && !key.equals(existing.getAgentKey())) {
                // 改名成兜底 key 会与内置兜底角色撞名（表未建时注册表会用它兜底，
                // 于是「表里的 general_assistant」和「内置的 general_assistant」
                // 会被当成同一个，配置互相覆盖）
                return "标识 " + key + " 是内置兜底角色保留名，请换一个";
            }
        }
        if ((creating || config.getName() != null)
                && (config.getName() == null || config.getName().isBlank())) {
            return "智能体名称不能为空";
        }
        if (config.getAudience() != null && !config.getAudience().isBlank()
                && !AUDIENCES.contains(config.getAudience())) {
            return "audience 只能是 " + AUDIENCES + " 之一";
        }
        if (config.getMaxIterations() != null
                && (config.getMaxIterations() < 1 || config.getMaxIterations() > 20)) {
            return "最大工具调用轮次应在 1~20 之间";
        }
        if (config.getTemperature() != null
                && (config.getTemperature().doubleValue() < 0
                || config.getTemperature().doubleValue() > 2)) {
            return "temperature 应在 0~2 之间";
        }
        return null;
    }

    private String normalizeAudience(String audience) {
        return audience == null || audience.isBlank() ? "all" : audience;
    }

    private int tokens(AgentRun run) {
        int p = run.getPromptTokens() == null ? 0 : run.getPromptTokens();
        int c = run.getCompletionTokens() == null ? 0 : run.getCompletionTokens();
        return p + c;
    }
}
