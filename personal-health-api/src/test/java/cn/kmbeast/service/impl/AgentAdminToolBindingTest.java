package cn.kmbeast.service.impl;

import cn.kmbeast.core.agent.registry.AgentRegistry;
import cn.kmbeast.crm.agent.ToolRegistry;
import cn.kmbeast.crm.agent.tool.CallAgentTool;
import cn.kmbeast.crm.agent.tool.Tool;
import cn.kmbeast.mapper.AgentConfigMapper;
import cn.kmbeast.mapper.AgentToolBindingMapper;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.api.ResultCode;
import cn.kmbeast.pojo.entity.AgentConfig;
import cn.kmbeast.pojo.entity.AgentToolBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具白名单保存的安全语义（2026-10-20 由 fail-open 改为 fail-safe）。
 *
 * <p><b>被锁死的不变式</b>：校验没全通过之前，库里一行都不许动。
 * 旧实现是「先 {@code deleteByAgentId} 再逐个校验」，而校验失败走的是
 * {@code return ApiResult.error} 而不是抛异常 —— {@code @Transactional}
 * 对正常返回并不回滚，于是名单里一个工具名拼错就把原白名单清空；
 * 读侧 {@code restricted = !bindings.isEmpty()} 又把「零绑定」判为「不限制」，
 * 一次输入错误换来的是权限放大。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("智能体工具白名单保存：校验失败不得清空既有绑定")
class AgentAdminToolBindingTest {

    @Mock
    private AgentConfigMapper agentConfigMapper;

    @Mock
    private AgentToolBindingMapper agentToolBindingMapper;

    @Mock
    private AgentRegistry agentRegistry;

    @Mock
    private ToolRegistry toolRegistry;

    @InjectMocks
    private AgentAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        AgentConfig config = new AgentConfig();
        config.setId(7);
        config.setAgentKey("health_assistant");
        when(agentConfigMapper.getById(7)).thenReturn(config);
        // 先建好工具替身再 stub：写在 thenReturn(...) 的参数里会撞上
        // 「外层 stubbing 未结束就开内层 stubbing」，Mockito 直接抛 UnfinishedStubbingException。
        List<Tool> tools = List.of(tool("query_health_data"), tool("search_drug"));
        when(toolRegistry.getAll()).thenReturn(tools);
    }

    private static Tool tool(String name) {
        Tool t = mock(Tool.class);
        when(t.getName()).thenReturn(name);
        return t;
    }

    @Test
    @DisplayName("名单含未知工具：拒绝，且不删不插不失效缓存")
    void unknownToolKeepsExistingBindings() {
        Result<Void> result = service.setToolBindings(
                7, true, List.of("query_health_data", "not_a_real_tool"));

        assertEquals(ResultCode.REQUEST_ERROR.getCode(), result.getCode());
        assertTrue(result.getMsg().contains("未知工具"), result.getMsg());
        verify(agentToolBindingMapper, never()).deleteByAgentId(any());
        verify(agentToolBindingMapper, never()).batchSave(any());
        verify(agentRegistry, never()).invalidate();
    }

    @Test
    @DisplayName("手动把 call_agent 绑进白名单：拒绝，库不动")
    void manualCallAgentBindingRejected() {
        Result<Void> result = service.setToolBindings(7, true, List.of(CallAgentTool.TOOL_NAME));

        assertEquals(ResultCode.REQUEST_ERROR.getCode(), result.getCode());
        assertTrue(result.getMsg().contains("call_agent"), result.getMsg());
        verify(agentToolBindingMapper, never()).deleteByAgentId(any());
        verify(agentToolBindingMapper, never()).batchSave(any());
    }

    @Test
    @DisplayName("合法名单：先删旧再插新，重复项去重")
    void validListReplacesBindings() {
        Result<Void> result = service.setToolBindings(7, true,
                List.of("query_health_data", "search_drug", "query_health_data"));

        assertEquals(ResultCode.REQUEST_SUCCESS.getCode(), result.getCode());
        InOrder order = inOrder(agentToolBindingMapper);
        order.verify(agentToolBindingMapper).deleteByAgentId(7);
        ArgumentCaptor<List<AgentToolBinding>> captor = ArgumentCaptor.forClass(List.class);
        order.verify(agentToolBindingMapper).batchSave(captor.capture());

        List<AgentToolBinding> rows = captor.getValue();
        assertEquals(2, rows.size(), "重复的工具名应去重");
        assertEquals("query_health_data", rows.get(0).getToolName());
        assertEquals("search_drug", rows.get(1).getToolName());
        assertEquals(0, rows.get(0).getSortOrder());
        assertEquals(1, rows.get(1).getSortOrder());
        verify(agentRegistry).invalidate();
    }

    @Test
    @DisplayName("restricted=false：清空绑定表示不限制，不写任何行")
    void unrestrictedClearsBindingsWithoutInserting() {
        Result<Void> result = service.setToolBindings(7, false, List.of());

        assertEquals(ResultCode.REQUEST_SUCCESS.getCode(), result.getCode());
        verify(agentToolBindingMapper).deleteByAgentId(7);
        verify(agentToolBindingMapper, never()).batchSave(any());
    }

    @Test
    @DisplayName("restricted 缺失：直接拒绝，不碰库")
    void missingRestrictedFlagIsRejected() {
        Result<Void> result = service.setToolBindings(7, null, List.of("search_drug"));

        assertEquals(ResultCode.REQUEST_ERROR.getCode(), result.getCode());
        verify(agentToolBindingMapper, never()).deleteByAgentId(any());
    }
}
