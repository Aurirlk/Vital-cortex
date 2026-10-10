package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.mapper.AuditLogMapper;
import cn.kmbeast.mapper.UserHealthMapper;
import cn.kmbeast.pojo.entity.AuditLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 阶段 2：双轨参数化查询工具的单元测试。
 *
 * <p>重点覆盖<b>越权防护</b>：用户端忽略 patientId 强制查本人、医生端无授权 fail-closed。
 */
@DisplayName("QueryHealthRecordsTool 双轨查询")
class QueryHealthRecordsToolTest {

    private UserHealthMapper userHealthMapper;
    private AuditLogMapper auditLogMapper;
    private QueryHealthRecordsTool tool;

    @BeforeEach
    void setUp() throws Exception {
        userHealthMapper = mock(UserHealthMapper.class);
        auditLogMapper = mock(AuditLogMapper.class);
        tool = new QueryHealthRecordsTool();
        setField(tool, "userHealthMapper", userHealthMapper);
        setField(tool, "auditLogMapper", auditLogMapper);
        ToolContext.clear();
    }

    @AfterEach
    void tearDown() {
        ToolContext.clear();
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    private static Map<String, Object> row(String value, String time, String unit, String range) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("createTime", time);
        m.put("unit", unit);
        m.put("valueRange", range);
        return m;
    }

    @Test
    @DisplayName("用户端：强制查本人，忽略 LLM 传入的 patientId（越权防护）")
    void userTrackIgnoresPatientId() {
        ToolContext.setPhoneAndUserId("13800000000", 5);
        ToolContext.setIdentity(2, null);

        when(userHealthMapper.queryByMetric(eq(5), eq("收缩压"), anyInt(), anyInt()))
                .thenReturn(List.of(row("120", "2026-10-01 08:00", "mmHg", "90,140")));

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("metric", "血压");
        args.put("patientId", 999); // 恶意/多余传参

        var result = tool.execute(args);

        assertTrue(result.isSuccess());
        // 关键断言：查询必须以本人 userId=5 执行，绝不能用 999
        // （"血压" 解析为收缩压+舒张压两个指标，故至少查询 1 次）
        verify(userHealthMapper, atLeastOnce()).queryByMetric(eq(5), anyString(), anyInt(), anyInt());
        verify(userHealthMapper, never()).queryByMetric(eq(999), anyString(), anyInt(), anyInt());
        assertTrue(result.getContent().contains("120"), "应含本人真实测量值");
    }

    @Test
    @DisplayName("用户端：无身份时拒绝查询")
    void userTrackNoIdentity() {
        ToolContext.setIdentity(2, null); // 没有 userId
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("metric", "血压");
        var result = tool.execute(args);
        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("未获取到用户身份"));
    }

    @Test
    @DisplayName("医生端：缺少 patientId 时拒绝")
    void doctorTrackMissingPatientId() {
        ToolContext.setIdentity(3, 7); // 医生身份，但没传 patientId 参数
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("metric", "血压");
        var result = tool.execute(args);
        assertTrue(result.getContent().contains("需同时具备医生身份与患者ID"));
        verify(userHealthMapper, never()).queryByMetric(anyInt(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("医生端：无接诊/就诊授权时 fail-closed，不返回数据，记 status=0 审计")
    void doctorTrackNoAccess() {
        ToolContext.setIdentity(3, 7);
        when(userHealthMapper.countDoctorPatientAccess(7, 42)).thenReturn(0);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("metric", "血糖");
        args.put("patientId", 42);
        var result = tool.execute(args);

        assertTrue(result.getContent().contains("无权访问"), "无授权必须拒绝");
        verify(userHealthMapper, never()).queryByMetric(anyInt(), anyString(), anyInt(), anyInt());
        // 审计：status=0
        verify(auditLogMapper).save(argThat(a -> a instanceof AuditLog
                && Integer.valueOf(0).equals(((AuditLog) a).getStatus())));
    }

    @Test
    @DisplayName("医生端：有授权时返回患者数据，记 status=1 审计")
    void doctorTrackGranted() {
        ToolContext.setIdentity(3, 7);
        when(userHealthMapper.countDoctorPatientAccess(7, 42)).thenReturn(1);
        when(userHealthMapper.queryByMetric(eq(42), eq("空腹血糖"), anyInt(), anyInt()))
                .thenReturn(List.of(row("5.4", "2026-10-01 08:00", "mmol/L", "3.9,6.1")));

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("metric", "血糖");
        args.put("patientId", 42);
        var result = tool.execute(args);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("5.4"), "应返回患者真实数据");
        verify(auditLogMapper).save(argThat(a -> a instanceof AuditLog
                && Integer.valueOf(1).equals(((AuditLog) a).getStatus())));
    }

    @Test
    @DisplayName("未识别指标：返回 ok 提示，不查库")
    void unrecognizedMetric() {
        ToolContext.setPhoneAndUserId("13800000000", 5);
        ToolContext.setIdentity(2, null);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("metric", "尿酸"); // 不在白名单
        var result = tool.execute(args);
        assertTrue(result.getContent().contains("未识别到指标"));
        verify(userHealthMapper, never()).queryByMetric(anyInt(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("指标解析：口语说法映射到正确白名单")
    void metricResolve() {
        assertTrue(HealthMetric.resolve("血压").contains(HealthMetric.SYSTOLIC));
        assertTrue(HealthMetric.resolve("血压").contains(HealthMetric.DIASTOLIC));
        assertTrue(HealthMetric.resolve("血糖").contains(HealthMetric.FASTING_GLUCOSE));
        assertTrue(HealthMetric.resolve("血糖").contains(HealthMetric.POSTPRANDIAL_GLUCOSE));
        assertTrue(HealthMetric.resolve("胆固醇").contains(HealthMetric.TOTAL_CHOLESTEROL));
        assertTrue(HealthMetric.resolve("胆固醇").contains(HealthMetric.HDL));
        assertTrue(HealthMetric.resolve("胆固醇").contains(HealthMetric.LDL));
        assertTrue(HealthMetric.resolve("尿酸").isEmpty());
        assertEquals(HealthMetric.SYSTOLIC, HealthMetric.resolve("收缩压").iterator().next());
    }

    @Test
    @DisplayName("天数/条数钳制：极大值被压到上限")
    void clamp() {
        assertEquals(3650, QueryHealthRecordsTool.clampDays(999999));
        assertEquals(90, QueryHealthRecordsTool.clampDays(null));
        assertEquals(90, QueryHealthRecordsTool.clampDays(0)); // 0 视为"使用默认 90 天"
        assertEquals(500, QueryHealthRecordsTool.clampLimit(999999));
        assertEquals(100, QueryHealthRecordsTool.clampLimit(null));
    }

    @Test
    @DisplayName("正常范围解析：支持 'a,b' 与 JSON 两种形态")
    void parseRange() {
        assertArrayEquals(new java.math.BigDecimal[]{new java.math.BigDecimal("90"), new java.math.BigDecimal("140")},
                QueryHealthRecordsTool.parseRange("90,140"));
        assertArrayEquals(new java.math.BigDecimal[]{new java.math.BigDecimal("0"), new java.math.BigDecimal("11.1")},
                QueryHealthRecordsTool.parseRange("{\"min\":0,\"max\":11.1}"));
        assertNull(QueryHealthRecordsTool.parseRange("乱码"));
        assertNull(QueryHealthRecordsTool.parseRange(null));
    }
}
