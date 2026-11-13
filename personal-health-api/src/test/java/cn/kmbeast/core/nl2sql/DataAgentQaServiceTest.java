package cn.kmbeast.core.nl2sql;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.UserHealthMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 数据智能体门面服务测试。
 *
 * <p>本类此前<b>零测试覆盖</b>，而它承担的全是安全边界：用户端强制查本人、医生端接诊授权
 * fail-closed、会话归属校验、审计落库。多轮上下文回填也在这里。</p>
 *
 * <p>不连库：{@code DataAgentQaService#jdbc()} 被本测试的子类替换成 Mockito 桩；
 * {@code persist()} / {@code writeAudit()} 虽为 private，但两者整体都被 try/catch 包住，
 * 桩返回 null 也不会让测试失败 —— 我们只验证「送进 pipeline 的参数」与「是否被拒绝」。</p>
 */
class DataAgentQaServiceTest {

    // ==================== toMultiTurnText（纯函数） ====================

    private static Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    @Test
    @DisplayName("多轮文本：null / 空列表 → 空串")
    void multiTurnEmptyInput() {
        assertEquals("", DataAgentQaService.toMultiTurnText(null));
        assertEquals("", DataAgentQaService.toMultiTurnText(new ArrayList<>()));
    }

    @Test
    @DisplayName("多轮文本：角色映射为 user: / assistant:，空白内容被跳过")
    void multiTurnMapsRoles() {
        List<Map<String, Object>> in = new ArrayList<>();
        in.add(msg("user", "我最近的血压趋势如何？"));
        in.add(msg("assistant", "近 30 天收缩压平均 128 mmHg。"));
        in.add(msg("user", "   "));          // 纯空白，应跳过、不产生空行
        in.add(msg("assistant", ""));        // 空串，应跳过
        String text = DataAgentQaService.toMultiTurnText(in);
        assertTrue(text.startsWith("user: 我最近的血压趋势如何？"), "实际: " + text);
        assertTrue(text.contains("\nassistant: 近 30 天收缩压平均 128 mmHg。"), "实际: " + text);
        assertEquals(2, text.split("\n").length, "只应有 2 行（空白与空串被跳过），实际: " + text);
    }

    @Test
    @DisplayName("多轮文本：role 为 null 或非 assistant 时按 user 处理")
    void multiTurnDefaultsToUserRole() {
        List<Map<String, Object>> in = new ArrayList<>();
        in.add(msg(null, "无角色"));
        in.add(msg("tool", "工具角色"));
        String text = DataAgentQaService.toMultiTurnText(in);
        assertEquals("user: 无角色\nuser: 工具角色", text);
    }

    @Test
    @DisplayName("多轮文本：超过 6 条只保留末尾 6 条（最近的轮次才相关）")
    void multiTurnKeepsTailOnly() {
        List<Map<String, Object>> in = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            in.add(msg(i % 2 == 0 ? "assistant" : "user", "第" + i + "轮"));
        }
        String text = DataAgentQaService.toMultiTurnText(in);
        assertFalse(text.contains("第1轮"), "最早的轮次应被丢弃: " + text);
        assertFalse(text.contains("第4轮"), "第 5~10 条才是保留区间: " + text);
        assertTrue(text.contains("第10轮"), "最后一轮必须保留: " + text);
    }

    @Test
    @DisplayName("多轮文本：超长内容按字符截断且保留最近部分")
    void multiTurnTruncatesByChars() {
        List<Map<String, Object>> in = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4000; i++) {
            sb.append('x');
        }
        in.add(msg("assistant", sb.toString()));
        String text = DataAgentQaService.toMultiTurnText(in);
        assertTrue(text.length() < 2000, "应被截断，实际长度 " + text.length());
        assertTrue(text.contains("更早的对话已省略"), "截断后应有省略提示: " + text);
    }

    // ==================== 身份 / 授权（安全边界） ====================

    /** 捕获 run() 收到的参数，避免真跑流水线。 */
    static class CapturingPipeline extends DataAgentPipeline {
        Long owner;
        String multiTurn;
        int calls;
        /** 2026-10-07 新增：捕获「使用者类型」，验证分层配置确实传到了流水线。 */
        String audience;

        @Override
        public DataAgentQaResult run(DataAgentQaRequest req, Long dataOwnerId, StepSink sink,
                                      String audience) {
            calls++;
            owner = dataOwnerId;
            multiTurn = req.getMultiTurn();
            this.audience = audience;
            DataAgentQaResult r = new DataAgentQaResult();
            r.setSuccess(true);
            r.setAnswer("ok");
            return r;
        }

        /** 3 参重载也转发到 4 参，保证任何调用路径都能被捕获。 */
        @Override
        public DataAgentQaResult run(DataAgentQaRequest req, Long dataOwnerId, StepSink sink) {
            return run(req, dataOwnerId, sink, null);
        }
    }

    static class TestableService extends DataAgentQaService {
        private final JdbcTemplate stub;

        TestableService(JdbcTemplate stub) {
            this.stub = stub;
        }

        @Override
        protected JdbcTemplate jdbc() {
            return stub;
        }
    }

    /**
     * 沿类链向上查找字段再注入。
     *
     * <p>本测试的目标对象是 {@link TestableService}（子类），而 {@code pipeline} 等字段
     * 声明在父类 {@code DataAgentQaService} 上 —— 直接用
     * {@code target.getClass().getDeclaredField(...)} 会抛 {@code NoSuchFieldException}。</p>
     */
    private static void inject(Object target, String field, Object value) throws Exception {
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException("字段未在任何父类中找到: " + field);
    }

    private static JdbcTemplate stubJdbc() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // 会话归属校验：返回"属于本人"。
        // ⚠️ 必须用 any(Object[].class) 整体匹配 varargs：JdbcTemplate 的 queryForObject /
        // queryForList 都有「(String, Class, Object...)」与「(String, Object...)」两个重载，
        // 裸 any() 会被误配到 Class 形参，导致 stub 静默不命中（返回 null）而测试报「回填为空」。
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        return jdbc;
    }

    @AfterEach
    void clearIdentity() {
        LocalThreadHolder.clear();
    }

    @Test
    @DisplayName("用户端强制查本人：请求里带 patientId 也被忽略")
    void userEndIgnoresPatientId() throws Exception {
        LocalThreadHolder.setUserId(7, 2);
        CapturingPipeline pipeline = new CapturingPipeline();
        TestableService svc = new TestableService(stubJdbc());
        inject(svc, "pipeline", pipeline);

        DataAgentQaRequest req = new DataAgentQaRequest();
        req.setQuestion("我最近的血压趋势如何？");
        req.setPatientId(999);   // 越权尝试：想去查别人

        DataAgentQaResult r = svc.query(req, StepSink.NOOP, null);

        assertTrue(r.isSuccess());
        assertEquals(1, pipeline.calls);
        assertEquals(7L, pipeline.owner, "patientId=999 必须被忽略，主体恒为 JWT 里的 userId=7");
        assertEquals(DataAgentConfigHolder.AUDIENCE_USER, pipeline.audience,
                "用户端请求必须传 audience=user，否则管理员按端配置的分层开关不生效");
    }

    @Test
    @DisplayName("2026-10-07 分层配置：医生端请求传 audience=doctor")
    void doctorEndPassesDoctorAudience() throws Exception {
        LocalThreadHolder.setUserId(3, 3);
        CapturingPipeline pipeline = new CapturingPipeline();
        UserHealthMapper mapper = mock(UserHealthMapper.class);
        when(mapper.countDoctorPatientAccess(3, 100)).thenReturn(1);   // 已授权
        TestableService svc = new TestableService(stubJdbc());
        inject(svc, "pipeline", pipeline);
        inject(svc, "userHealthMapper", mapper);

        DataAgentQaRequest req = new DataAgentQaRequest();
        req.setQuestion("这位患者最近血压如何？");
        req.setPatientId(100);

        DataAgentQaResult r = svc.query(req, StepSink.NOOP, null);

        assertTrue(r.isSuccess());
        assertEquals(DataAgentConfigHolder.AUDIENCE_DOCTOR, pipeline.audience,
                "医生端必须传 audience=doctor，管理员才能对该端单独收紧/放宽");
    }

    @Test
    @DisplayName("医生端未传 patientId → fail-closed，流水线都不进")
    void doctorEndRequiresPatientId() throws Exception {
        LocalThreadHolder.setUserId(3, 3);
        CapturingPipeline pipeline = new CapturingPipeline();
        TestableService svc = new TestableService(stubJdbc());
        inject(svc, "pipeline", pipeline);

        DataAgentQaRequest req = new DataAgentQaRequest();
        req.setQuestion("这位患者最近血压如何？");

        DataAgentQaResult r = svc.query(req, StepSink.NOOP, null);

        assertFalse(r.isSuccess());
        assertEquals(0, pipeline.calls, "未授权时绝不能进入流水线");
        assertTrue(r.getMessage().contains("patientId"), "错误信息应点明缺少 patientId，实际: " + r.getMessage());
    }

    @Test
    @DisplayName("医生端无接诊授权 → fail-closed（拒绝访问该患者数据）")
    void doctorEndRequiresAccessGrant() throws Exception {
        LocalThreadHolder.setUserId(3, 3);
        CapturingPipeline pipeline = new CapturingPipeline();
        UserHealthMapper mapper = mock(UserHealthMapper.class);
        when(mapper.countDoctorPatientAccess(3, 100)).thenReturn(0);
        TestableService svc = new TestableService(stubJdbc());
        inject(svc, "pipeline", pipeline);
        inject(svc, "userHealthMapper", mapper);

        DataAgentQaRequest req = new DataAgentQaRequest();
        req.setQuestion("这位患者最近血压如何？");
        req.setPatientId(100);

        DataAgentQaResult r = svc.query(req, StepSink.NOOP, null);

        assertFalse(r.isSuccess());
        assertEquals(0, pipeline.calls, "无授权时绝不能进入流水线");
        assertTrue(r.getMessage().contains("无权访问"), "实际: " + r.getMessage());
    }

    @Test
    @DisplayName("医生端有授权 → 主体为 patientId（而非医生自己）")
    void doctorEndUsesPatientIdAsOwner() throws Exception {
        LocalThreadHolder.setUserId(3, 3);
        CapturingPipeline pipeline = new CapturingPipeline();
        UserHealthMapper mapper = mock(UserHealthMapper.class);
        when(mapper.countDoctorPatientAccess(3, 100)).thenReturn(2);
        TestableService svc = new TestableService(stubJdbc());
        inject(svc, "pipeline", pipeline);
        inject(svc, "userHealthMapper", mapper);

        DataAgentQaRequest req = new DataAgentQaRequest();
        req.setQuestion("这位患者最近血压如何？");
        req.setPatientId(100);

        DataAgentQaResult r = svc.query(req, StepSink.NOOP, null);

        assertTrue(r.isSuccess());
        assertEquals(100L, pipeline.owner, "医生端主体必须是患者 id");
    }

    @Test
    @DisplayName("多轮回填：sessionId 有值且会话归属本人时，历史被注入 prompt")
    void multiTurnIsBackFilledFromSession() throws Exception {
        LocalThreadHolder.setUserId(7, 2);
        CapturingPipeline pipeline = new CapturingPipeline();

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(msg("user", "我最近的血压趋势如何？"));
        history.add(msg("assistant", "近 30 天收缩压平均 128 mmHg。"));
        // varargs 必须整体匹配，见 stubJdbc() 的注释
        doReturn(history).when(jdbc).queryForList(anyString(), any(Object[].class));

        TestableService svc = new TestableService(jdbc);
        inject(svc, "pipeline", pipeline);

        DataAgentQaRequest req = new DataAgentQaRequest();
        req.setQuestion("那血糖呢？");
        req.setSessionId("42");

        svc.query(req, StepSink.NOOP, null);

        assertTrue(pipeline.multiTurn != null && !pipeline.multiTurn.isEmpty(),
                "multiTurn 应被后端自取回填，实际为: " + pipeline.multiTurn);
        assertTrue(pipeline.multiTurn.contains("血压"), "实际: " + pipeline.multiTurn);
    }

    @Test
    @DisplayName("多轮回填：无 sessionId 时保持空（退化为单轮，不报错）")
    void multiTurnEmptyWithoutSession() throws Exception {
        LocalThreadHolder.setUserId(7, 2);
        CapturingPipeline pipeline = new CapturingPipeline();
        TestableService svc = new TestableService(stubJdbc());
        inject(svc, "pipeline", pipeline);

        DataAgentQaRequest req = new DataAgentQaRequest();
        req.setQuestion("我最近的血压趋势如何？");

        DataAgentQaResult r = svc.query(req, StepSink.NOOP, null);

        assertTrue(r.isSuccess());
        assertEquals("", pipeline.multiTurn, "没有会话就没有历史可注入");
    }

    @Test
    @DisplayName("多轮回填：前端已自带 multiTurn 时尊重前端取值，不覆盖")
    void multiTurnRespectsClientValue() throws Exception {
        LocalThreadHolder.setUserId(7, 2);
        CapturingPipeline pipeline = new CapturingPipeline();
        TestableService svc = new TestableService(stubJdbc());
        inject(svc, "pipeline", pipeline);

        DataAgentQaRequest req = new DataAgentQaRequest();
        req.setQuestion("那血糖呢？");
        req.setSessionId("42");
        req.setMultiTurn("user: 前端自己带的历史");

        svc.query(req, StepSink.NOOP, null);

        assertEquals("user: 前端自己带的历史", pipeline.multiTurn);
    }
}
