package cn.kmbeast.core.nl2sql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据智能体运行时配置：默认值回落行为。
 *
 * <p>不连数据库（dataSource 为 null，reload 内部捕获异常后回落默认值），
 * 验证「未做任何配置时行为与 {@code crm.ai.nl2sql.*} 一致」这一安全前提。</p>
 */
@DisplayName("数据智能体运行时配置：默认回落")
class DataAgentConfigHolderTest {

    private DataAgentConfigHolder holder(Nl2SqlProperties props) throws Exception {
        DataAgentConfigHolder h = new DataAgentConfigHolder();
        Field pf = DataAgentConfigHolder.class.getDeclaredField("properties");
        pf.setAccessible(true);
        pf.set(h, props);
        Field df = DataAgentConfigHolder.class.getDeclaredField("dataSource");
        df.setAccessible(true);
        df.set(h, null);
        return h;
    }

    private Nl2SqlProperties props(boolean enabled, int maxRows, int timeout) {
        Nl2SqlProperties p = new Nl2SqlProperties();
        p.setEnabled(enabled);
        p.setMaxRows(maxRows);
        p.setQueryTimeout(timeout);
        return p;
    }

    @Test
    @DisplayName("无库配置时回落到 crm.ai.nl2sql.*，默认关闭")
    void fallsBackToProperties() throws Exception {
        DataAgentConfigHolder h = holder(props(false, 150, 7));

        assertFalse(h.isEnabled(), "properties.enabled=false 时必须回落为关闭");
        assertEquals(150, h.getMaxRows());
        assertEquals(7, h.getQueryTimeout());
        assertTrue(h.isReportEnabled(), "报告开关默认开启");
    }

    @Test
    @DisplayName("properties 打开时回落为打开（保证管理员未配置也可用 crm 开关）")
    void enabledFollowsProperties() throws Exception {
        DataAgentConfigHolder h = holder(props(true, 200, 10));
        assertTrue(h.isEnabled());
    }

    @Test
    @DisplayName("快照包含 4 项配置与生效值")
    void snapshotShape() throws Exception {
        DataAgentConfigHolder h = holder(props(true, 200, 10));
        Map<String, Object> snap = h.snapshot();

        assertNotNull(snap.get(DataAgentConfigHolder.KEY_ENABLED));
        assertNotNull(snap.get(DataAgentConfigHolder.KEY_REPORT_ENABLED));
        assertNotNull(snap.get(DataAgentConfigHolder.KEY_MAX_ROWS));
        assertNotNull(snap.get(DataAgentConfigHolder.KEY_QUERY_TIMEOUT));
        assertNotNull(snap.get("effective"));

        Object enabledItem = snap.get(DataAgentConfigHolder.KEY_ENABLED);
        assertTrue(enabledItem instanceof Map, "每项应为含 key/description/valueType/value 的 Map");
        assertEquals("boolean", ((Map<?, ?>) enabledItem).get("valueType"));
    }

    @Test
    @DisplayName("预设问题：无库配置时回落内置默认（医生端与用户端各一份，可开箱使用）")
    void presetQuestionsFallback() throws Exception {
        DataAgentConfigHolder h = holder(props(true, 200, 10));
        List<String> user = h.presetQuestions(false);
        List<String> doctor = h.presetQuestions(true);

        assertEquals(DataAgentConfigHolder.DEFAULT_PRESETS_USER, user);
        assertEquals(DataAgentConfigHolder.DEFAULT_PRESETS_DOCTOR, doctor);
        assertTrue(doctor.get(0).contains("患者"), "医生端预设应面向患者，实际: " + doctor.get(0));
    }

    @Test
    @DisplayName("预设问题：已配置 JSON 数组时按配置返回，空串项被过滤")
    void presetQuestionsFromConfig() throws Exception {
        DataAgentConfigHolder h = holder(props(true, 200, 10));
        Field cf = DataAgentConfigHolder.class.getDeclaredField("cache");
        cf.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> cache = (Map<String, String>) cf.get(h);
        cache.put(DataAgentConfigHolder.KEY_PRESETS_USER, "[\"问A\",\"\",\"  \",\"问B\"]");
        Field lf = DataAgentConfigHolder.class.getDeclaredField("loadedAt");
        lf.setAccessible(true);
        lf.setLong(h, System.currentTimeMillis());

        assertEquals(List.of("问A", "问B"), h.presetQuestions(false));
    }

    @Test
    @DisplayName("预设问题：配置为非法 JSON 时回落默认值，不抛异常")
    void presetQuestionsOnBrokenJson() throws Exception {
        DataAgentConfigHolder h = holder(props(true, 200, 10));
        Field cf = DataAgentConfigHolder.class.getDeclaredField("cache");
        cf.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> cache = (Map<String, String>) cf.get(h);
        cache.put(DataAgentConfigHolder.KEY_PRESETS_DOCTOR, "{不是数组}");
        Field lf = DataAgentConfigHolder.class.getDeclaredField("loadedAt");
        lf.setAccessible(true);
        lf.setLong(h, System.currentTimeMillis());

        assertEquals(DataAgentConfigHolder.DEFAULT_PRESETS_DOCTOR, h.presetQuestions(true));
    }
}
