package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.core.nl2sql.DataAgentConfigHolder;
import cn.kmbeast.core.nl2sql.DataAgentQaRequest;
import cn.kmbeast.core.nl2sql.DataAgentQaResult;
import cn.kmbeast.core.nl2sql.DataAgentQaService;
import cn.kmbeast.core.nl2sql.StepSink;
import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.mapper.UserHealthMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DataQueryTool 授权测试（P1-2 / T8，2026-10-07）。
 *
 * <p>🔴 身份只来自 {@link ToolContext}（JWT 透传），工具内授权是安全边界；
 * 任何绕过登录、信任 LLM 传入的 userId/patientId/doctorId 都等于越权。
 *
 * <p>🚨 反向验证：把 {@code DataQueryTool.execute} 里的
 * {@code dataOwnerId = callerId.longValue()} 临时改成 {@code toInt(arguments.get("patientId"))}
 * （信任 LLM 参数），下面用例 2（用户传 patientId=999）与用例 3（医生无授权却传 patientId）
 * 必须变红。</p>
 */
@ExtendWith(MockitoExtension.class)
public class DataQueryToolAuthTest {

    @Mock
    DataAgentQaService qaService;
    @Mock
    UserHealthMapper userHealthMapper;
    @Mock
    DataAgentConfigHolder configHolder;

    @InjectMocks
    DataQueryTool tool;

    @BeforeEach
    void setUp() {
        ToolContext.clear();
    }

    @AfterEach
    void tearDown() {
        ToolContext.clear();
    }

    private DataAgentQaResult okResult() {
        DataAgentQaResult r = new DataAgentQaResult();
        r.setSuccess(true);
        r.setRowCount(3);
        r.setElapsedMs(10);
        return r;
    }

    private Map<String, Object> args(String q, Integer patientId, boolean report) {
        Map<String, Object> m = new HashMap<>();
        m.put("question", q);
        if (patientId != null) {
            m.put("patientId", patientId);
        }
        m.put("enableReport", report);
        return m;
    }

    /** 用例 1：无身份 → 连授权 mapper 都不碰，queryForTool 零调用。 */
    @Test
    void noIdentity_noServiceCall() {
        ToolContext.setPhoneAndUserId(null, null); // userId == null
        ToolResult res = tool.execute(Map.of("question", "最近血压"));
        assertFalse(res.isSuccess());
        assertTrue(res.getContent().contains("未登录"));
        verifyNoInteractions(qaService);
        verifyNoInteractions(userHealthMapper);
    }

    /** 用例 2：用户端传 patientId=999 → 必须忽略，dataOwnerId 仍是本人 callerId=1。 */
    @Test
    void userIgnoresPatientId() {
        ToolContext.setPhoneAndUserId("13800000000", 1); // userId = 1
        ToolContext.setIdentity(2, null);
        when(configHolder.getSandboxMaxBytes()).thenReturn(512 * 1024);
        when(qaService.queryForTool(any(), any(), any(), any(), any())).thenReturn(okResult());

        ToolResult res = tool.execute(args("最近血压", 999, false));
        assertTrue(res.isSuccess(), "用户端查询应成功: " + res.getContent());

        ArgumentCaptor<Long> owner = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> aud = ArgumentCaptor.forClass(String.class);
        verify(qaService).queryForTool(any(), owner.capture(), aud.capture(), any(), any());
        assertEquals(1L, owner.getValue(), "用户端必须强制查本人，忽略 LLM 的 patientId");
        assertEquals(DataAgentConfigHolder.AUDIENCE_USER, aud.getValue());
        verify(userHealthMapper, never()).countDoctorPatientAccess(anyInt(), anyInt());
    }

    /** 用例 3：医生端无接诊授权 → fail-closed，queryForTool 零调用（pipeline.run 零调用）。 */
    @Test
    void doctorNoAccess_noPipelineRun() {
        ToolContext.setPhoneAndUserId("13900000000", 7); // doctorId = 7
        ToolContext.setIdentity(3, 7);
        when(userHealthMapper.countDoctorPatientAccess(7, 999)).thenReturn(0);

        ToolResult res = tool.execute(args("患者血压", 999, false));
        assertFalse(res.isSuccess());
        assertTrue(res.getContent().contains("无权"), "无授权应被拒绝: " + res.getContent());
        verifyNoInteractions(qaService);
    }

    /** 用例 4：医生端有授权 → queryForTool 收到 (patientId=999, "doctor")。 */
    @Test
    void doctorWithAccess_sendsPatientAndDoctorAudience() {
        ToolContext.setPhoneAndUserId("13900000000", 7); // doctorId = 7
        ToolContext.setIdentity(3, 7);
        when(userHealthMapper.countDoctorPatientAccess(7, 999)).thenReturn(1);
        when(configHolder.getSandboxMaxBytes()).thenReturn(512 * 1024);
        when(qaService.queryForTool(any(), any(), any(), any(), any())).thenReturn(okResult());

        ToolResult res = tool.execute(args("患者血压", 999, false));
        assertTrue(res.isSuccess(), "有授权应成功: " + res.getContent());

        ArgumentCaptor<Long> owner = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> aud = ArgumentCaptor.forClass(String.class);
        verify(qaService).queryForTool(any(), owner.capture(), aud.capture(), any(), any());
        assertEquals(999L, owner.getValue(), "医生端 dataOwnerId 必须是已授权 patientId");
        assertEquals(DataAgentConfigHolder.AUDIENCE_DOCTOR, aud.getValue());
    }
}
