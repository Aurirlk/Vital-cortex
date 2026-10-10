package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.mapper.PatientProfileMapper;
import cn.kmbeast.mapper.UserHealthMapper;
import cn.kmbeast.pojo.entity.PatientProfile;
import cn.kmbeast.pojo.vo.UserHealthVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 健康数据工具单元测试（阶段 0-A / A1）
 *
 * <p><b>整改背景</b>：原实现读磁盘 {@code ai_data/health/user_{id}.json}，而那 17 个文件
 * 全部 {@code total:0} —— AI 被系统提示词强制调这个工具，却永远拿到"暂无数据"，
 * 而库里真实的 160 条 {@code user_health} + 7 例 {@code patient_profile} 一条都够不着。
 *
 * <p>本测试锁死四件事：
 * <ol>
 *   <li><b>两种 value_range 格式都能解析</b>（🔴 隐藏 Bug：老数据 "{@code 90,140}" 逗号式、
 *       新数据 "{@code {"min":0,"max":11.1}}" JSON 式共存，旧代码只会 split(",") →
 *       新格式指标永远判不出异常）。返回 null 而非猜是。</li>
 *   <li><b>身份只信任 ThreadLocal</b>，LLM 传 userId 入参必须无效 —— 否则可越权读他人档案。</li>
 *   <li>三种 query_type 都能产出非空结果。</li>
 *   <li>days 参数被钳制，不会把 160 条全塞进 prompt。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("健康数据查询工具（get_health_data）")
class GetHealthDataToolTest {

    @Mock
    private UserHealthMapper userHealthMapper;

    @Mock
    private PatientProfileMapper patientProfileMapper;

    @InjectMocks
    private GetHealthDataTool tool;

    @BeforeEach
    void setUp() {
        ToolContext.setPhoneAndUserId("13800000000", 1001);
    }

    @AfterEach
    void tearDown() {
        ToolContext.clear();
    }

    // ==================== 一、value_range 双格式兼容 ====================

    @Test
    @DisplayName("正常范围：逗号式(老数据)可解析")
    void rangeCommaStyle() {
        double[] r = GetHealthDataTool.parseValueRange("90,140");
        assertNotNull(r, "逗号式应可解析");
        assertEquals(90.0, r[0]);
        assertEquals(140.0, r[1]);
    }

    @Test
    @DisplayName("正常范围：JSON式(新数据)也可解析 ← 隐藏Bug修复点")
    void rangeJsonStyle() {
        double[] r = GetHealthDataTool.parseValueRange("{\"min\":0,\"max\":11.1}");
        assertNotNull(r, "JSON 式必须可解析，否则餐后血糖等 7 项指标永远判定不了异常");
        assertEquals(0.0, r[0]);
        assertEquals(11.1, r[1], 0.001);
    }

    @Test
    @DisplayName("正常范围：JSON式实样(总胆固醇)")
    void rangeJsonStyleRealSample() {
        double[] r = GetHealthDataTool.parseValueRange("{\"min\":0,\"max\":8.0}");
        assertNotNull(r);
        assertEquals(8.0, r[1], 0.001);
    }

    @Test
    @DisplayName("正常范围：中文逗号容错")
    void rangeChineseComma() {
        double[] r = GetHealthDataTool.parseValueRange("90，140");
        assertNotNull(r, "中文逗号应容错");
        assertEquals(140.0, r[1]);
    }

    @Test
    @DisplayName("正常范围：null/空/非法一律返回null，不当成正常")
    void rangeInvalidReturnsNull() {
        assertNull(GetHealthDataTool.parseValueRange(null));
        assertNull(GetHealthDataTool.parseValueRange(""));
        assertNull(GetHealthDataTool.parseValueRange("   "));
        assertNull(GetHealthDataTool.parseValueRange("血压偏高要吃药"));
        assertNull(GetHealthDataTool.parseValueRange("90"));
        // JSON 缺字段
        assertNull(GetHealthDataTool.parseValueRange("{\"min\":0}"));
    }

    @Test
    @DisplayName("正常范围：min>max 视为脏数据返回null")
    void rangeReversedRejected() {
        assertNull(GetHealthDataTool.parseValueRange("140,90"),
                "min>max 说明配置填反了，静默接受会导致所有记录被判异常");
    }

    // ==================== 二、JSON 列格式化 ====================

    @Test
    @DisplayName("JSON数组列：字符串数组")
    void stringListBasic() {
        assertEquals("高血压、糖尿病", GetHealthDataTool.formatStringList("[\"高血压\",\"糖尿病\"]"));
    }

    @Test
    @DisplayName("JSON数组列：空数组/null 返回空串")
    void stringListEmpty() {
        assertEquals("", GetHealthDataTool.formatStringList("[]"));
        assertEquals("", GetHealthDataTool.formatStringList(null));
        assertEquals("", GetHealthDataTool.formatStringList("  "));
    }

    @Test
    @DisplayName("JSON数组列：元素为对象时取name字段")
    void stringListObjectItems() {
        assertEquals("青霉素", GetHealthDataTool.formatStringList("[{\"name\":\"青霉素\"}]"));
    }

    @Test
    @DisplayName("JSON数组列：非JSON内容原样返回，不吞异常")
    void stringListNonJson() {
        assertEquals("花粉过敏", GetHealthDataTool.formatStringList("花粉过敏"));
    }

    @Test
    @DisplayName("生活习惯JSON对象转中文短句")
    void lifestyleFormat() {
        String s = GetHealthDataTool.formatLifestyle(
                "{\"smoking\": false, \"drinking\": \"偶尔\", \"exercise\": \"每周3次\"}");
        assertTrue(s.contains("吸烟否"), s);
        assertTrue(s.contains("饮酒偶尔"), s);
        assertTrue(s.contains("运动每周3次"), s);
    }

    @Test
    @DisplayName("生活习惯：空对象返回空串")
    void lifestyleEmpty() {
        assertEquals("", GetHealthDataTool.formatLifestyle("{}"));
        assertEquals("", GetHealthDataTool.formatLifestyle(null));
    }

    // ==================== 三、身份安全 ====================

    @Test
    @DisplayName("🔴 参数schema里不含userId —— 防止LLM构造越权入参")
    void schemaHasNoUserId() {
        Map<String, Object> schema = tool.getParametersSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertFalse(props.containsKey("userId"),
                "绝不能开放 userId 入参，否则 LLM 可读取任意用户健康档案");
        assertFalse(props.containsKey("patientId"));
        assertTrue(props.containsKey("query_type"));
    }

    @Test
    @DisplayName("🔴 无身份时拒绝查库，且返回友好提示")
    void noIdentityRefusesQuery() {
        ToolContext.clear();
        ToolResult result = tool.execute(Map.of("query_type", "profile"));
        assertNotNull(result);
        assertTrue(result.isSuccess(), "无身份应是友好降级，不是报错");
        verifyNoInteractions(userHealthMapper, patientProfileMapper);
    }

    @Test
    @DisplayName("🔴 LLM传入userId也无效，仍用ThreadLocal身份")
    void llmCannotOverrideIdentity() {
        java.util.Map<String, Object> args = new java.util.HashMap<>();
        args.put("query_type", "recent");
        args.put("userId", 9999);  // 攻击：试图读别人数据

        when(userHealthMapper.query(any())).thenReturn(Collections.emptyList());

        tool.execute(args);

        org.mockito.ArgumentCaptor<cn.kmbeast.pojo.dto.query.extend.UserHealthQueryDto> captor =
                org.mockito.ArgumentCaptor.forClass(cn.kmbeast.pojo.dto.query.extend.UserHealthQueryDto.class);
        verify(userHealthMapper).query(captor.capture());
        assertEquals(1001, captor.getValue().getUserId(),
                "必须忽略 LLM 传入的 userId，只用 ThreadLocal 里的 1001");
    }

    // ==================== 四、三种查询类型 ====================

    @Test
    @DisplayName("profile：档案+最新指标都出现在结果里")
    void profileIncludesBothParts() {
        when(patientProfileMapper.getByUserId(1001)).thenReturn(PatientProfile.builder()
                .userId(1001).gender("女").age(28).height(163.0).weight(52.0).bmi(19.6)
                .chronicDiseases("[]").allergies("[\"花粉\"]").medications("[]")
                .build());
        when(userHealthMapper.query(any())).thenReturn(List.of(
                vo("收缩压", "118", "mmHg", "90,140")));

        ToolResult r = tool.execute(Map.of("query_type", "profile"));
        String text = textOf(r);
        assertTrue(text.contains("花粉"), "过敏史未输出: " + text);
        assertTrue(text.contains("163.0"), "身高未输出: " + text);
        assertTrue(text.contains("收缩压"), "最新指标未输出: " + text);
        assertTrue(text.contains("118"), "指标值未输出: " + text);
    }

    @Test
    @DisplayName("profile：库里无档案时不报错，仍输出指标部分")
    void profileNoProfileRow() {
        when(patientProfileMapper.getByUserId(1001)).thenReturn(null);
        when(userHealthMapper.query(any())).thenReturn(List.of(
                vo("心率", "78", "次/分", "60,100")));

        ToolResult r = tool.execute(Map.of("query_type", "profile"));
        assertTrue(r.isSuccess());
        assertTrue(textOf(r).contains("心率"), textOf(r));
    }

    @Test
    @DisplayName("recent：无记录时给出引导而不是空白")
    void recentEmptyGivesGuidance() {
        when(userHealthMapper.query(any())).thenReturn(Collections.emptyList());
        ToolResult r = tool.execute(Map.of("query_type", "recent", "days", 7));
        assertTrue(textOf(r).contains("没有健康记录"), textOf(r));
    }

    @Test
    @DisplayName("recent：days 极大值被钳制，不会炸数据库")
    void recentDaysClamped() {
        when(userHealthMapper.query(any())).thenReturn(Collections.emptyList());
        ToolResult r = tool.execute(Map.of("query_type", "recent", "days", 99999999));
        assertTrue(r.isSuccess(), "极大 days 不应抛异常");
    }

    @Test
    @DisplayName("recent：days 为 0/负数时钳制为至少1天")
    void recentDaysNonPositive() {
        when(userHealthMapper.query(any())).thenReturn(Collections.emptyList());
        assertTrue(tool.execute(Map.of("query_type", "recent", "days", -5)).isSuccess());
        assertTrue(tool.execute(Map.of("query_type", "recent", "days", 0)).isSuccess());
    }

    // ==================== 五、异常指标判定 ====================

    @Test
    @DisplayName("abnormal：超出范围的指标被识别（逗号式）")
    void abnormalDetectedCommaRange() {
        when(userHealthMapper.query(any())).thenReturn(List.of(
                vo("收缩压", "155", "mmHg", "90,140")));

        String text = textOf(tool.execute(Map.of("query_type", "abnormal")));
        assertTrue(text.contains("收缩压"), text);
        assertTrue(text.contains("偏高"), text);
    }

    @Test
    @DisplayName("abnormal：JSON式范围也能识别 ← 隐藏Bug修复后必须过")
    void abnormalDetectedJsonRange() {
        when(userHealthMapper.query(any())).thenReturn(List.of(
                vo("总胆固醇", "9.5", "mmol/L", "{\"min\":0,\"max\":8.0}")));

        String text = textOf(tool.execute(Map.of("query_type", "abnormal")));
        assertTrue(text.contains("总胆固醇"), text);
        assertTrue(text.contains("偏高"), text);
    }

    @Test
    @DisplayName("abnormal：全部正常时明确说正常")
    void abnormalAllNormal() {
        when(userHealthMapper.query(any())).thenReturn(List.of(
                vo("收缩压", "118", "mmHg", "90,140")));

        String text = textOf(tool.execute(Map.of("query_type", "abnormal")));
        assertTrue(text.contains("正常"), text);
        assertFalse(text.contains("偏高"), text);
    }

    @Test
    @DisplayName("abnormal：缺少范围配置的指标归入未知，不谎报正常")
    void abnormalUnknownNotSilentlyNormal() {
        when(userHealthMapper.query(any())).thenReturn(List.of(
                vo("自定义指标", "12", "", null)));

        String text = textOf(tool.execute(Map.of("query_type", "abnormal")));
        assertTrue(text.contains("自定义指标"), text);
        assertTrue(text.contains("未参与判定"),
                "缺范围的指标必须显式告知 AI，否则它会误以为没提就是正常");
    }

    @Test
    @DisplayName("abnormal：非数值型value不崩")
    void abnormalNonNumericValue() {
        when(userHealthMapper.query(any())).thenReturn(List.of(
                vo("自觉症状", "良好", "", "90,140")));

        assertTrue(tool.execute(Map.of("query_type", "abnormal")).isSuccess());
    }

    // ==================== 六、参数健壮性 ====================

    @Test
    @DisplayName("缺失 query_type 报 error")
    void missingQueryType() {
        ToolResult r = tool.execute(Map.of());
        assertFalse(r.isSuccess());
    }

    @Test
    @DisplayName("未知 query_type 报 error 并给出可选值")
    void unknownQueryType() {
        ToolResult r = tool.execute(Map.of("query_type", "whichever"));
        assertFalse(r.isSuccess());
    }

    @Test
    @DisplayName("Mapper 抛异常时降级为 error，不往外冒")
    void mapperExceptionDowngrades() {
        when(userHealthMapper.query(any())).thenThrow(new RuntimeException("connection refused"));
        ToolResult r = tool.execute(Map.of("query_type", "recent"));
        assertFalse(r.isSuccess(), "底层异常应转成 ToolResult.error，避免打断 ReAct 循环");
    }

    @Test
    @DisplayName("record 上限：只取 MAX_RECORDS 条")
    void sizeLimitApplied() {
        when(userHealthMapper.query(any())).thenReturn(Collections.emptyList());
        tool.execute(Map.of("query_type", "recent"));

        org.mockito.ArgumentCaptor<cn.kmbeast.pojo.dto.query.extend.UserHealthQueryDto> captor =
                org.mockito.ArgumentCaptor.forClass(cn.kmbeast.pojo.dto.query.extend.UserHealthQueryDto.class);
        verify(userHealthMapper).query(captor.capture());
        assertEquals(0, captor.getValue().getCurrent(),
                "QueryDto.current 是偏移量不是页码，取最新N条必须传0");
        assertNotNull(captor.getValue().getSize());
        assertTrue(captor.getValue().getSize() <= 50);
    }

    // ==================== 辅助 ====================

    private static UserHealthVO vo(String name, String value, String unit, String range) {
        UserHealthVO v = new UserHealthVO();
        v.setName(name);
        v.setValue(value);
        v.setUnit(unit);
        v.setValueRange(range);
        v.setCreateTime(LocalDateTime.now());
        return v;
    }

    private static String textOf(ToolResult r) {
        // ToolResult 的内容字段是 content（不是 data）
        return r.getContent() == null ? "" : r.getContent();
    }
}
