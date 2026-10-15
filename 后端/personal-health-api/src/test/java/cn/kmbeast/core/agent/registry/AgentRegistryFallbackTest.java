package cn.kmbeast.core.agent.registry;

import cn.kmbeast.core.agent.AgentCoordinator;
import cn.kmbeast.crm.config.CrmConfig;
import cn.kmbeast.mapper.AgentConfigMapper;
import cn.kmbeast.mapper.AgentHandoffMapper;
import cn.kmbeast.mapper.AgentToolBindingMapper;
import cn.kmbeast.pojo.entity.AgentConfig;
import cn.kmbeast.pojo.entity.AgentHandoff;
import cn.kmbeast.pojo.entity.AgentToolBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AgentRegistry} 降级链与三态语义的单测。
 *
 * <p><b>这组用例保护的是一件很具体的事</b>：新增的 5 张表<b>不是所有人都会立刻执行</b>
 * （本次就是「用户自己跑脚本」的模式）。在表不存在、表为空、Mapper 抛异常、
 * 甚至根本没注入 Mapper 的四种环境下，路由都必须照常工作、工具也不能被误掐。
 * 这类降级逻辑的特点是：<b>写错了在生产里表现为「某个角色的能力悄悄少了一块」</b>，
 * 不报错、不告警，只有用户抱怨「营养师回答得不像营养师」时才可能被发现。</p>
 *
 * <p>另外锁死工具白名单的<b>三态</b>：没有任何绑定行 → 全量（{@code null}）；
 * 绑了但全被停用 → 一个都不给（空集）。这两者在表结构上都是「零个启用行」，
 * 只能靠查询方式区分（用 {@code queryByAgentId} 而非「只查启用行」）。
 * 一旦有人图省事改成只查启用行，就会出现「管理员把工具全禁了，
 * Agent 反而拿到了全部工具」——后果与意图完全相反。</p>
 */
@DisplayName("AgentRegistry 降级链与工具白名单三态")
class AgentRegistryFallbackTest {

    /** 与 Spring 环境一致地装配一个注册表。传 null 表示「该依赖不存在」。 */
    private static AgentRegistry registry(AgentConfigMapper configMapper,
                                          AgentToolBindingMapper bindingMapper,
                                          AgentHandoffMapper handoffMapper,
                                          AgentCoordinator coordinator) {
        AgentRegistry registry = new AgentRegistry();
        ReflectionTestUtils.setField(registry, "agentConfigMapper", configMapper);
        ReflectionTestUtils.setField(registry, "agentToolBindingMapper", bindingMapper);
        ReflectionTestUtils.setField(registry, "agentHandoffMapper", handoffMapper);
        ReflectionTestUtils.setField(registry, "agentCoordinator", coordinator);
        return registry;
    }

    /** 走 @PostConstruct 初始化的内置角色表（与运行时的 6 个角色同源）。 */
    private static AgentCoordinator builtinCoordinator() {
        AgentCoordinator coordinator = new AgentCoordinator();
        ReflectionTestUtils.setField(coordinator, "crmConfig", new CrmConfig());
        coordinator.init();
        return coordinator;
    }

    private static AgentConfig config(int id, String key, String name, int sortOrder) {
        AgentConfig c = new AgentConfig();
        c.setId(id);
        c.setAgentKey(key);
        c.setName(name);
        c.setSortOrder(sortOrder);
        c.setEnabled(true);
        c.setAudience("all");
        return c;
    }

    // ==================== 第一级：DB ====================

    @Test
    @DisplayName("DB 有数据时以 DB 为准：顺序按 sort_order、关键词同时支持中英文逗号")
    void dbRowsWinOverBuiltin() {
        AgentConfigMapper configMapper = mock(AgentConfigMapper.class);
        AgentConfig analyst = config(5, "analyst", "数据小助手", 20);
        analyst.setRoutingKeywords("趋势, 统计，对比");
        AgentConfig general = config(1, "general_assistant", "全能助手", 999);
        general.setIsDefault(true);
        when(configMapper.listAllEnabled()).thenReturn(List.of(analyst, general));

        AgentRegistry registry = registry(configMapper, null, null, builtinCoordinator());

        assertTrue(registry.isDbBacked(), "读到了 agent_config 就该是 DB 驱动");
        List<AgentDefinition> all = registry.listAll();
        assertEquals(2, all.size());
        // sort_order 升序：analyst(20) 在 general_assistant(999) 之前
        assertEquals("analyst", all.get(0).getAgentKey());
        // 中文逗号也要切开：管理员在 Excel/Word 里写关键词时打的是「，」
        assertEquals(List.of("趋势", "统计", "对比"), all.get(0).getRoutingKeywords());
        assertTrue(all.get(0).isFromDb());
        assertFalse(all.get(0).isDefault());
        assertTrue(registry.getDefault(null).getAgentKey().equals("general_assistant"));
    }

    @Test
    @DisplayName("表不存在（Mapper 抛异常）→ 退回内置 6 角色，不抛给调用方")
    void mapperFailureDegradesToBuiltin() {
        AgentConfigMapper configMapper = mock(AgentConfigMapper.class);
        when(configMapper.listAllEnabled())
                .thenThrow(new RuntimeException("Table 'personal_health.agent_config' doesn't exist"));

        AgentRegistry registry = registry(configMapper, null, null, builtinCoordinator());

        assertFalse(registry.isDbBacked());
        assertEquals(6, registry.listAll().size(),
                "表没建时必须退回内置 6 角色，否则路由直接没有可用 Agent");
        assertEquals("全科医生", registry.get("doctor").getName());
        assertEquals("general_assistant", registry.getDefault(null).getAgentKey(),
                "内置兜底必须是 general_assistant（与改造前一致）");
    }

    @Test
    @DisplayName("表建了但没种子（空结果）→ 与表不存在同等降级，不能变成「零 Agent 可用」")
    void emptyRowsDegradeToBuiltin() {
        AgentConfigMapper configMapper = mock(AgentConfigMapper.class);
        when(configMapper.listAllEnabled()).thenReturn(List.of());

        AgentRegistry registry = registry(configMapper, null, null, builtinCoordinator());

        assertFalse(registry.isDbBacked());
        assertEquals(6, registry.listAll().size());
        assertNotNull(registry.require("doctor"));
    }

    @Test
    @DisplayName("非 Spring 环境（未注入 Mapper）→ 内置兜底，不 NPE")
    void noMapperAtAllStillUsable() {
        AgentRegistry registry = registry(null, null, null, builtinCoordinator());
        assertFalse(registry.isDbBacked());
        assertEquals(6, registry.listAll().size());
    }

    @Test
    @DisplayName("连内置角色都拿不到（裸 new）→ 仍有唯一可路由目标，getDefault 永不为 null")
    void minimalFallbackWhenNothingAvailable() {
        AgentRegistry registry = registry(null, null, null, null);
        assertEquals(0, registry.listAll().size());
        AgentDefinition def = registry.getDefault(null);
        assertNotNull(def, "路由每轮都要取兜底 Agent，这里返回 null 会让整条链路没有角色可用");
        assertEquals("general_assistant", def.getAgentKey());
        assertEquals("general_assistant", registry.require("完全不存在的 key").getAgentKey(),
                "require 语义是「永不返回 null」");
    }

    // ==================== 第二级：工具白名单三态 ====================

    @Test
    @DisplayName("工具白名单三态：无绑定行=全量(null)、全停用=空集、部分启用=子集")
    void toolWhitelistHasThreeStates() {
        AgentConfigMapper configMapper = mock(AgentConfigMapper.class);
        AgentToolBindingMapper bindingMapper = mock(AgentToolBindingMapper.class);
        when(configMapper.listAllEnabled()).thenReturn(List.of(
                config(1, "no_binding", "没配绑定", 1),
                config(2, "all_disabled", "全停用", 2),
                config(3, "partial", "部分启用", 3)));

        // id=1 → 没有任何行
        when(bindingMapper.queryByAgentId(1)).thenReturn(List.of());
        // id=2 → 有行但全部 enabled=0
        when(bindingMapper.queryByAgentId(2)).thenReturn(List.of(
                binding(2, "search_drug", false), binding(2, "web_search", false)));
        // id=3 → 一行启用、一行停用
        when(bindingMapper.queryByAgentId(3)).thenReturn(List.of(
                binding(3, "search_drug", true), binding(3, "web_search", false)));

        AgentRegistry registry = registry(configMapper, bindingMapper, null, builtinCoordinator());

        assertNull(registry.allowedToolsOrNull("no_binding"),
                "没有绑定行 = 全量工具，必须是 null 而不是空集（否则升级后所有 Agent 集体失去工具）");
        assertEquals(Set.of(), registry.allowedToolsOrNull("all_disabled"),
                "绑了但全停用 = 一个都不给，必须是空集而不是 null（否则与意图完全相反）");
        assertEquals(Set.of("search_drug"), registry.allowedToolsOrNull("partial"));
    }

    @Test
    @DisplayName("关联表读不到时按「全量」处理：宁可多给工具，不可误伤")
    void bindingReadFailureFallsBackToFullSet() {
        AgentConfigMapper configMapper = mock(AgentConfigMapper.class);
        AgentToolBindingMapper bindingMapper = mock(AgentToolBindingMapper.class);
        when(configMapper.listAllEnabled()).thenReturn(List.of(config(1, "doctor", "全科医生", 1)));
        when(bindingMapper.queryByAgentId(anyInt()))
                .thenThrow(new RuntimeException("Table 'personal_health.agent_tool_binding' doesn't exist"));

        AgentRegistry registry = registry(configMapper, bindingMapper, null, builtinCoordinator());

        assertNull(registry.allowedToolsOrNull("doctor"));
        assertTrue(registry.isToolAllowed("doctor", "execute_sql"));
    }

    @Test
    @DisplayName("isToolAllowed 是硬边界，但只收窄被显式配置过的 Agent")
    void isToolAllowedOnlyNarrowsConfiguredAgents() {
        AgentConfigMapper configMapper = mock(AgentConfigMapper.class);
        AgentToolBindingMapper bindingMapper = mock(AgentToolBindingMapper.class);
        when(configMapper.listAllEnabled()).thenReturn(List.of(
                config(1, "restricted", "受限", 1), config(2, "free", "不受限", 2)));
        when(bindingMapper.queryByAgentId(1)).thenReturn(List.of(binding(1, "search_drug", true)));
        when(bindingMapper.queryByAgentId(2)).thenReturn(List.of());

        AgentRegistry registry = registry(configMapper, bindingMapper, null, builtinCoordinator());

        assertTrue(registry.isToolAllowed("restricted", "search_drug"));
        assertFalse(registry.isToolAllowed("restricted", "execute_sql"));
        assertTrue(registry.isToolAllowed("free", "execute_sql"), "无白名单 = 全放行");
        assertTrue(registry.isToolAllowed("从来没见过的 key", "execute_sql"),
                "未知 Agent 放行 —— 注册表读不到数据时不该把所有人的工具都掐掉");
    }

    // ==================== 第三级：委派关系 ====================

    @Test
    @DisplayName("委派关系：id 翻译成 key，悬空引用与自委派被丢弃，停用行不计入")
    void handoffResolvesKeysAndDropsGarbage() {
        AgentConfigMapper configMapper = mock(AgentConfigMapper.class);
        AgentHandoffMapper handoffMapper = mock(AgentHandoffMapper.class);
        // 注意 id=1 的委派目标（id=2）在列表里排在它后面 —— idToKey 必须整体先建，
        // 否则「A 委派给后定义出来的 B」会漏掉
        when(configMapper.listAllEnabled()).thenReturn(List.of(
                config(1, "general_assistant", "全能助手", 999),
                config(2, "doctor", "全科医生", 10),
                config(3, "nutritionist", "营养师", 20)));
        when(handoffMapper.queryByFromAgentId(1)).thenReturn(List.of(
                handoff(1, 2, true),      // 正常
                handoff(1, 3, false),     // 已停用
                handoff(1, 999, true),    // 悬空：本库没有外键，这种行真的会出现
                handoff(1, 1, true)));    // 自委派
        when(handoffMapper.queryByFromAgentId(2)).thenReturn(List.of());

        AgentRegistry registry = registry(configMapper, null, handoffMapper, builtinCoordinator());

        assertEquals(Set.of("doctor"), registry.handoffTargets("general_assistant"));
        assertTrue(registry.isHandoffAllowed("general_assistant", "doctor"));
        assertFalse(registry.isHandoffAllowed("doctor", "general_assistant"),
                "方向是授权的一部分：没配 doctor→general_assistant 就不该放行");
        assertTrue(registry.handoffTargets("doctor").isEmpty());
        assertEquals(Set.of("doctor"), registry.allHandoffTargets());
        assertFalse(registry.isHandoffAllowed(null, "doctor"));
    }

    @Test
    @DisplayName("委派关系读不到时按「不委派」处理（与白名单相反：这个方向要少做）")
    void handoffReadFailureMeansNoDelegation() {
        AgentConfigMapper configMapper = mock(AgentConfigMapper.class);
        AgentHandoffMapper handoffMapper = mock(AgentHandoffMapper.class);
        when(configMapper.listAllEnabled()).thenReturn(List.of(config(1, "doctor", "全科医生", 1)));
        when(handoffMapper.queryByFromAgentId(anyInt()))
                .thenThrow(new RuntimeException("Table 'personal_health.agent_handoff' doesn't exist"));

        AgentRegistry registry = registry(configMapper, null, handoffMapper, builtinCoordinator());

        assertTrue(registry.handoffTargets("doctor").isEmpty());
        assertFalse(registry.isHandoffAllowed("doctor", "nutritionist"));
    }

    // ==================== 缓存 ====================

    @Test
    @DisplayName("invalidate 后立刻看到新数据（管理端改完不必等 60 秒 TTL）")
    void invalidateForcesReload() {
        AgentConfigMapper configMapper = mock(AgentConfigMapper.class);
        when(configMapper.listAllEnabled()).thenReturn(List.of(config(1, "doctor", "全科医生", 1)));

        AgentRegistry registry = registry(configMapper, null, null, builtinCoordinator());
        assertEquals(1, registry.listAll().size());

        when(configMapper.listAllEnabled()).thenReturn(List.of(
                config(1, "doctor", "全科医生", 1), config(2, "nutritionist", "营养师", 2)));
        assertEquals(1, registry.listAll().size(), "TTL 内应命中缓存（这正是需要 invalidate 的原因）");

        registry.invalidate();
        assertEquals(2, registry.listAll().size(),
                "写操作后未失效缓存 → 管理员会看到「改了不生效」，然后反复重试");
    }

    @Test
    @DisplayName("多端兜底：该端有 is_default 就用它，没有就回落到 all")
    void defaultIsPerAudience() {
        AgentConfigMapper configMapper = mock(AgentConfigMapper.class);
        AgentConfig globalDefault = config(1, "general_assistant", "全能助手", 999);
        globalDefault.setIsDefault(true);
        AgentConfig userDefault = config(2, "user_helper", "用户助手", 1);
        userDefault.setIsDefault(true);
        userDefault.setAudience("user");
        when(configMapper.listAllEnabled()).thenReturn(List.of(userDefault, globalDefault));

        AgentRegistry registry = registry(configMapper, null, null, builtinCoordinator());

        assertEquals("user_helper", registry.getDefault("user").getAgentKey());
        assertEquals("general_assistant", registry.getDefault("admin").getAgentKey(),
                "该端没有自己的兜底时应回落到 audience='all' 的那个");
        assertEquals("general_assistant", registry.getDefault(null).getAgentKey());
    }

    @Test
    @DisplayName("max_iterations 手工写 0/负数时按默认值纠偏（ReAct 循环必须有限）")
    void nonPositiveMaxIterationsIsCorrected() {
        AgentConfigMapper configMapper = mock(AgentConfigMapper.class);
        AgentConfig broken = config(1, "doctor", "全科医生", 1);
        broken.setMaxIterations(0);
        when(configMapper.listAllEnabled()).thenReturn(List.of(broken));

        AgentRegistry registry = registry(configMapper, null, null, builtinCoordinator());

        assertEquals(5, registry.get("doctor").getMaxIterations());
    }

    private static AgentToolBinding binding(int agentId, String tool, boolean enabled) {
        AgentToolBinding b = new AgentToolBinding();
        b.setAgentId(agentId);
        b.setToolName(tool);
        b.setEnabled(enabled);
        return b;
    }

    private static AgentHandoff handoff(int from, int to, boolean enabled) {
        AgentHandoff h = new AgentHandoff();
        h.setFromAgentId(from);
        h.setToAgentId(to);
        h.setEnabled(enabled);
        return h;
    }
}
