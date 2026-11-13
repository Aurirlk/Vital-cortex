package cn.kmbeast.core.agent.registry;

import cn.kmbeast.core.agent.AgentCoordinator;
import cn.kmbeast.mapper.AgentConfigMapper;
import cn.kmbeast.mapper.AgentHandoffMapper;
import cn.kmbeast.mapper.AgentToolBindingMapper;
import cn.kmbeast.pojo.entity.AgentConfig;
import cn.kmbeast.pojo.entity.AgentHandoff;
import cn.kmbeast.pojo.entity.AgentToolBinding;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 注册表：把「Agent 角色定义」从 Java 硬编码迁到 DB，并向执行期提供
 * 一个 60 秒 TTL 的不可变快照。
 *
 * <p><b>为什么必须有兜底而不是硬依赖 DB</b>：本模块的建表脚本由使用者自行执行
 * （`Data/02_迁移脚本/10_agent_architecture.sql`）。在表尚未创建、或表已创建但没有
 * 种子数据的环境里，任何一次 {@code SELECT} 都会抛
 * {@code Table 'xxx' doesn't exist}。如果注册表把这个异常放出去，服务会在
 * 第一次对话时崩掉。因此加载路径分三层降级，且每层都是**安全方向**
 * （宁可给全量能力，不可误伤）：</p>
 * <ol>
 *   <li>读 DB 成功且有启用行 → 用 DB 定义（{@code fromDb = true}）</li>
 *   <li>读 DB 失败（表未建 / 连不上）或零行 → 回退到
 *       {@link AgentCoordinator} 内置的 6 个角色，行为与改造前<b>逐字节一致</b></li>
 *   <li>连 {@code AgentCoordinator} 都不可用（裸单元测试里 {@code new} 出来的）→
 *       兜一个最小的 {@code general_assistant}，保证路由永远有落点</li>
 * </ol>
 *
 * <p>读取时机是**惰性**的（无 {@code @PostConstruct}）：本类依赖
 * {@code AgentCoordinator} 的 {@code @PostConstruct} 结果，而 bean 初始化顺序
 * 不可依赖；惰性加载 + TTL 把这个顺序问题彻底消掉。</p>
 */
@Slf4j
@Component
public class AgentRegistry {

    /** 快照有效期。改配置后最迟 60 秒生效，或由 {@link #invalidate()} 立即生效。 */
    private static final long TTL_MS = 60_000L;

    /** 兜底 Agent key，与 {@code ai_config.ai_default_agent} 的默认值对齐。 */
    public static final String FALLBACK_KEY = "general_assistant";

    /** 内置兜底定义没有 DB 行可读时的迭代数上限，与改造前 crmConfig 默认值一致。 */
    private static final int DEFAULT_MAX_ITERATIONS = 5;

    /** 工具名 / Agent key 的分隔符：中英文逗号都收，管理员两种都会打。 */
    private static final String KEYWORD_SEPARATOR = "[,，]";

    /**
     * 三个 Mapper 在 Spring 环境下必然存在（MyBatis 为每个 @Mapper 接口建 bean），
     * 但单元测试里本类是直接 {@code new} 出来的、不启动容器，此时它们是 null。
     * 下面的装载代码对每个 Mapper 都判了 null，就是为了让这种「没有 Mapper」的
     * 场景退化成「读不到 DB」→ 内置兜底，而不是 NPE。
     */
    @Resource
    private AgentConfigMapper agentConfigMapper;

    @Resource
    private AgentToolBindingMapper agentToolBindingMapper;

    @Resource
    private AgentHandoffMapper agentHandoffMapper;

    @Resource
    private AgentCoordinator agentCoordinator;

    private volatile Snapshot snapshot;
    private volatile long loadedAtMs;

    /**
     * 上一次加载是否走了降级路径。
     * 只用于把 WARN 压到「状态翻转时打一次」——否则 60 秒一次的全量重载会把
     * 「表还没建」这件事刷满日志，真正的故障反而被淹掉。
     */
    private volatile boolean degraded;

    // ------------------------------------------------------------------
    // 对外查询
    // ------------------------------------------------------------------

    /** 全部启用中的 Agent，按 sort_order 升序（即路由平票时的优先级序）。 */
    public List<AgentDefinition> listAll() {
        return current().ordered;
    }

    /** 按 key 取；未知 key 返回 null。 */
    public AgentDefinition get(String agentKey) {
        if (agentKey == null) {
            return null;
        }
        return current().byKey.get(agentKey);
    }

    /** 按 key 取；未知 key 回落到该端默认 Agent，**永不返回 null**。 */
    public AgentDefinition require(String agentKey) {
        AgentDefinition def = get(agentKey);
        return def != null ? def : getDefault(null);
    }

    /**
     * 取某端的兜底 Agent。
     *
     * <p>查找顺序：该端显式 is_default=1 → audience='all' 的 is_default=1 →
     * key 为 general_assistant → 首个启用定义 → 最终兜底常量。
     * 之所以逐级回落而不是直接返回 null：路由是每轮对话的必经之路，
     * 一旦这里返回 null，整个链路就没有 Agent 可用。</p>
     */
    public AgentDefinition getDefault(String audience) {
        Snapshot s = current();
        if (audience != null) {
            AgentDefinition hit = s.defaultByAudience.get(audience);
            if (hit != null) {
                return hit;
            }
        }
        AgentDefinition all = s.defaultByAudience.get("all");
        if (all != null) {
            return all;
        }
        AgentDefinition named = s.byKey.get(FALLBACK_KEY);
        if (named != null) {
            return named;
        }
        if (!s.ordered.isEmpty()) {
            return s.ordered.get(0);
        }
        return minimalFallback();
    }

    /**
     * 该 Agent 的启用工具白名单。
     *
     * @return {@code null} = 没有任何绑定行，调用方应下发**全量**工具；
     *         非 null（可能是空集）= 白名单生效，只给集合内的
     */
    public Set<String> allowedToolsOrNull(String agentKey) {
        AgentDefinition def = get(agentKey);
        if (def == null || !def.hasToolWhitelist()) {
            return null;
        }
        return def.getToolNames();
    }

    /**
     * 该 Agent 是否允许调用某工具。
     *
     * <p>未知 Agent 与无白名单 Agent 一律放行 —— 这是刻意的：本方法要成为
     * 一条**硬边界**，但它只应该收窄被显式配置过的 Agent，不应该因为注册表
     * 读不到数据而把所有人的工具都掐掉。</p>
     */
    public boolean isToolAllowed(String agentKey, String toolName) {
        Set<String> allowed = allowedToolsOrNull(agentKey);
        return allowed == null || allowed.contains(toolName);
    }

    /** 是否允许 from 委派到 to。任一方未知、或方向未配置，一律不允许。 */
    public boolean isHandoffAllowed(String fromAgentKey, String toAgentKey) {
        if (fromAgentKey == null || toAgentKey == null) {
            return false;
        }
        AgentDefinition from = get(fromAgentKey);
        if (from == null) {
            return false;
        }
        return from.handoffTargetsOrEmpty().contains(toAgentKey);
    }

    /** 该 Agent 可委派的目标集合（永不为 null）。 */
    public Set<String> handoffTargets(String agentKey) {
        AgentDefinition def = get(agentKey);
        return def == null ? Collections.emptySet() : def.handoffTargetsOrEmpty();
    }

    /**
     * 全库出现过的委派目标 Agent key（去重）。
     * 供 {@code call_agent} 的 {@code agent_key} 参数枚举使用 ——
     * 枚举是「可能是谁」的提示，具体某一轮能否委派到它由发起方自己的白名单决定。
     */
    public Set<String> allHandoffTargets() {
        Set<String> all = new LinkedHashSet<>();
        for (AgentDefinition def : current().ordered) {
            all.addAll(def.handoffTargetsOrEmpty());
        }
        return all;
    }

    /** true = 当前快照来自 DB；false = 走的内置兜底。管理端据此提示用户去执行建表脚本。 */
    public boolean isDbBacked() {
        return current().fromDb;
    }

    /**
     * 让下一次读取强制重载。管理端任何写操作（增删改 Agent / 绑定 / 委派）后
     * 必须调它，否则管理员会看到「改了但 60 秒内不生效」的假象。
     */
    public void invalidate() {
        loadedAtMs = 0L;
    }

    // ------------------------------------------------------------------
    // 快照装载
    // ------------------------------------------------------------------

    private Snapshot current() {
        long now = System.currentTimeMillis();
        Snapshot s = snapshot;
        if (s != null && now - loadedAtMs < TTL_MS) {
            return s;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (snapshot != null && now - loadedAtMs < TTL_MS) {
                return snapshot;
            }
            Snapshot fresh = load();
            // 先写 snapshot 再写 loadedAtMs，读侧用 loadedAtMs 判断新鲜度，
            // 顺序保证不会出现「新快照 + 旧时间戳」而被判过期。
            snapshot = fresh;
            loadedAtMs = now;
            return fresh;
        }
    }

    private Snapshot load() {
        if (agentConfigMapper == null) {
            return degradedTo("未注入 AgentConfigMapper（非 Spring 环境）");
        }
        List<AgentConfig> configs;
        try {
            configs = agentConfigMapper.listAllEnabled();
        } catch (Exception e) {
            return degradedTo("读取 agent_config 失败：" + rootMessage(e));
        }
        if (configs == null || configs.isEmpty()) {
            // 表建了但没种子 —— 与「表没建」同等对待，否则会退化成「零 Agent 可用」
            return degradedTo("agent_config 无启用行（种子脚本未执行？）");
        }

        // agent_handoff 存的是 agent_id，而运行时（路由、委派、admin 展示）一律用
        // agent_key —— key 是给人看、给配置引用的稳定标识，id 不是。
        // 先整体建好 id→key 映射，再逐行翻译，否则「A 委派给后定义出来的 B」会漏掉。
        Map<Integer, String> idToKey = new LinkedHashMap<>();
        for (AgentConfig c : configs) {
            String key = trimToNull(c.getAgentKey());
            if (c.getId() != null && key != null) {
                idToKey.put(c.getId(), key);
            }
        }

        LinkedHashMap<String, AgentDefinition> byKey = new LinkedHashMap<>();
        for (AgentConfig c : configs) {
            AgentDefinition def = toDefinition(c, idToKey);
            if (def != null && def.getAgentKey() != null) {
                byKey.put(def.getAgentKey(), def);
            }
        }
        if (byKey.isEmpty()) {
            return degradedTo("agent_config 的启用行缺少合法 agent_key");
        }
        recovered("已从 agent_config 加载 " + byKey.size() + " 个 Agent");
        return build(byKey, true);
    }

    /** 把一行 entity 转成运行时定义，并把两个关联关系一并查出来。 */
    private AgentDefinition toDefinition(AgentConfig c, Map<Integer, String> idToKey) {
        Integer id = c.getId();
        return AgentDefinition.builder()
                .id(id)
                .agentKey(trimToNull(c.getAgentKey()))
                .name(c.getName())
                .description(c.getDescription())
                .avatar(c.getAvatar())
                .systemPrompt(trimToNull(c.getSystemPrompt()))
                .model(trimToNull(c.getModel()))
                .temperature(toDouble(c.getTemperature()))
                .topP(toDouble(c.getTopP()))
                .maxTokens(c.getMaxTokens())
                .audience(c.getAudience() == null ? "all" : c.getAudience())
                // max_iterations 有 NOT NULL DEFAULT 5，但手工插入的行可能写 0/负数；
                // 那是「不设限」的意思，而 ReAct 循环必须有限，故按默认值纠偏。
                .maxIterations(c.getMaxIterations() == null || c.getMaxIterations() <= 0
                        ? DEFAULT_MAX_ITERATIONS : c.getMaxIterations())
                .routingKeywords(splitKeywords(c.getRoutingKeywords()))
                .enabled(!Boolean.FALSE.equals(c.getEnabled()))
                .isDefault(Boolean.TRUE.equals(c.getIsDefault()))
                .sortOrder(c.getSortOrder() == null ? 0 : c.getSortOrder())
                .toolNames(loadToolWhitelist(id))
                .handoffTargets(loadHandoffTargets(id, idToKey))
                .fromDb(true)
                .build();
    }

    /**
     * 装载工具白名单，返回 null 表示「没有绑定行 = 全量」。
     *
     * <p>刻意用 {@code queryByAgentId}（不过滤 enabled）而不是「只查启用行」：
     * 需要区分「一行都没配」（→ 全量，向后兼容）与「配了但全被停用」
     * （→ 空集，一个工具都不给）。若只查启用行，后者会被误判成前者，
     * 导致「管理员把工具全禁用，结果 Agent 拿到了全量工具」这种完全相反的后果。</p>
     */
    private Set<String> loadToolWhitelist(Integer agentId) {
        if (agentToolBindingMapper == null || agentId == null) {
            return null;
        }
        List<AgentToolBinding> rows;
        try {
            rows = agentToolBindingMapper.queryByAgentId(agentId);
        } catch (Exception e) {
            // 关联表读不到时按「无白名单」处理：宁可多给工具，不可误伤。
            log.warn("[AgentRegistry] 读取工具绑定失败(agentId={})，该 Agent 按全量工具处理: {}",
                    agentId, rootMessage(e));
            return null;
        }
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Set<String> names = new LinkedHashSet<>();
        for (AgentToolBinding b : rows) {
            if (b == null || Boolean.FALSE.equals(b.getEnabled())) {
                continue;
            }
            String name = trimToNull(b.getToolName());
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 装载委派目标。读不到时返回空集（不委派）—— 与白名单相反，这里的安全方向
     * 是「少做」：放任委派在异常状态下继续，会把一次失败放大成递归调用。
     *
     * <p>本库全表**没有物理外键**（引用完整性靠代码维护），所以
     * {@code to_agent_id} 完全可能指向一个已被删除或不存在的 Agent。
     * 这类悬空引用在这里就被丢掉，不能让一个不存在的目标进入运行时候选集合。</p>
     */
    private Set<String> loadHandoffTargets(Integer agentId, Map<Integer, String> idToKey) {
        if (agentHandoffMapper == null || agentId == null) {
            return Collections.emptySet();
        }
        List<AgentHandoff> rows;
        try {
            rows = agentHandoffMapper.queryByFromAgentId(agentId);
        } catch (Exception e) {
            log.warn("[AgentRegistry] 读取委派关系失败(agentId={})，该 Agent 不开放委派: {}",
                    agentId, rootMessage(e));
            return Collections.emptySet();
        }
        if (rows == null || rows.isEmpty()) {
            return Collections.emptySet();
        }
        String selfKey = idToKey.get(agentId);
        Set<String> keys = new LinkedHashSet<>();
        for (AgentHandoff h : rows) {
            if (h == null || Boolean.FALSE.equals(h.getEnabled())) {
                continue;
            }
            String targetKey = idToKey.get(h.getToAgentId());
            // 悬空引用直接丢弃（见方法注释）
            if (targetKey == null) {
                log.warn("[AgentRegistry] 委派关系指向不存在的 agent_id={}，已忽略", h.getToAgentId());
                continue;
            }
            // 自委派等价于死循环的种子，构造阶段就掐掉，不指望运行时的环检测兜底
            if (targetKey.equals(selfKey)) {
                continue;
            }
            keys.add(targetKey);
        }
        return keys;
    }

    // ------------------------------------------------------------------
    // 降级路径
    // ------------------------------------------------------------------

    private Snapshot degradedTo(String reason) {
        if (!degraded) {
            degraded = true;
            log.warn("[AgentRegistry] {} —— 本次回退到内置角色定义（与改造前行为一致）", reason);
        }
        return fallbackSnapshot();
    }

    private void recovered(String msg) {
        if (degraded) {
            degraded = false;
            log.info("[AgentRegistry] 已恢复 DB 驱动，{}", msg);
        }
    }

    /** 用 {@link AgentCoordinator} 的内置角色构造快照；无绑定行 = 全量工具。 */
    private Snapshot fallbackSnapshot() {
        LinkedHashMap<String, AgentDefinition> byKey = new LinkedHashMap<>();
        if (agentCoordinator != null) {
            Map<String, AgentCoordinator.AgentRole> roles;
            try {
                roles = agentCoordinator.getAllAgentRoles();
            } catch (Exception e) {
                roles = Collections.emptyMap();
            }
            for (Map.Entry<String, AgentCoordinator.AgentRole> e : roles.entrySet()) {
                AgentCoordinator.AgentRole role = e.getValue();
                byKey.put(e.getKey(), AgentDefinition.builder()
                        .agentKey(e.getKey())
                        .name(role.getName())
                        .description(role.getDescription())
                        .routingKeywords(role.getKeywords())
                        .audience("all")
                        .maxIterations(DEFAULT_MAX_ITERATIONS)
                        .enabled(true)
                        .isDefault(FALLBACK_KEY.equals(e.getKey()))
                        .fromDb(false)
                        // toolNames 留 null：无白名单 = 全量，与改造前完全一致
                        .handoffTargets(Collections.emptySet())
                        .build());
            }
        }
        return build(byKey, false);
    }

    /** 最后一道兜底：连内置角色都拿不到时，至少保证有一个可路由的目标。 */
    private AgentDefinition minimalFallback() {
        return AgentDefinition.builder()
                .agentKey(FALLBACK_KEY)
                .name("全能助手")
                .description("综合健康咨询（默认角色）")
                .routingKeywords(Collections.emptyList())
                .audience("all")
                .maxIterations(DEFAULT_MAX_ITERATIONS)
                .enabled(true)
                .isDefault(true)
                .fromDb(false)
                .handoffTargets(Collections.emptySet())
                .build();
    }

    private Snapshot build(LinkedHashMap<String, AgentDefinition> byKey, boolean fromDb) {
        List<AgentDefinition> ordered = new ArrayList<>(byKey.values());
        // 稳定排序：sort_order 升序，同值保持装载序（LinkedHashMap 的插入序）。
        // List.sort 是稳定排序，故这里不会打乱平票优先级。
        ordered.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));

        Map<String, AgentDefinition> defaultByAudience = new LinkedHashMap<>();
        for (AgentDefinition def : ordered) {
            if (def.isDefault() && !defaultByAudience.containsKey(def.getAudience())) {
                defaultByAudience.put(def.getAudience(), def);
            }
        }
        return new Snapshot(ordered, byKey, defaultByAudience, fromDb);
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private static List<String> splitKeywords(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(KEYWORD_SEPARATOR)) {
            String k = part.trim();
            if (!k.isEmpty()) {
                out.add(k);
            }
        }
        return out;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Double toDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null ? cur.getClass().getSimpleName() : msg;
    }

    /** 一次装载的不可变结果。整体替换，不做增量修改，避免读到半更新的状态。 */
    private static final class Snapshot {
        private final List<AgentDefinition> ordered;
        private final Map<String, AgentDefinition> byKey;
        private final Map<String, AgentDefinition> defaultByAudience;
        private final boolean fromDb;

        Snapshot(List<AgentDefinition> ordered, Map<String, AgentDefinition> byKey,
                 Map<String, AgentDefinition> defaultByAudience, boolean fromDb) {
            this.ordered = Collections.unmodifiableList(ordered);
            this.byKey = Collections.unmodifiableMap(byKey);
            this.defaultByAudience = Collections.unmodifiableMap(defaultByAudience);
            this.fromDb = fromDb;
        }
    }
}
