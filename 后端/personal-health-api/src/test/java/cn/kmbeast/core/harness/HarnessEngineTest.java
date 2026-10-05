package cn.kmbeast.core.harness;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.PatientProfileMapper;
import cn.kmbeast.pojo.entity.PatientProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 数据引导引擎单元测试（2026-10-04）
 *
 * <p>{@code HarnessEngine} 此前是<b>全项目无人调用的死代码</b>。
 * 本测试在它接入对话流程后补上，重点验证两件容易出错的事：
 * <ol>
 *   <li><b>槽位抽取不能抽错</b>：抽错会污染患者档案（如把「我34岁」里的 34 当身高），
 *       宁可漏抽也不能抽错。</li>
 *   <li><b>不能覆盖已有数据</b>：用户口误不能改掉已核实的档案值。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("数据引导引擎（SlotFilling）")
class HarnessEngineTest {

    @Mock
    private PatientProfileMapper profileMapper;

    @InjectMocks
    private HarnessEngine engine;

    @BeforeEach
    void setUp() {
        LocalThreadHolder.setUserId(3, 2);
    }

    @AfterEach
    void tearDown() {
        LocalThreadHolder.clear();
    }

    // ================================================================ 完整性检查

    @Test
    @DisplayName("全空档案：5 个关键槽位全部判为缺失")
    void emptyProfileShouldReportAllMissing() {
        Map<String, String> missing = engine.checkDataCompleteness(new HashMap<>());

        assertEquals(5, missing.size());
        assertTrue(missing.containsKey("gender"));
        assertTrue(missing.containsKey("age"));
        assertTrue(missing.containsKey("height"));
        assertTrue(missing.containsKey("weight"));
        assertTrue(missing.containsKey("chronicDiseases"));
    }

    @Test
    @DisplayName("档案完整：无缺失项")
    void completeProfileShouldReportNoMissing() {
        Map<String, String> data = new HashMap<>();
        data.put("gender", "男");
        data.put("age", "34");
        data.put("height", "175");
        data.put("weight", "72.5");
        data.put("chronicDiseases", "[\"高血压\"]");

        assertTrue(engine.checkDataCompleteness(data).isEmpty());
    }

    @Test
    @DisplayName("空字符串与 null 都算缺失（不能因为有 key 就认为已填）")
    void blankAndNullShouldCountAsMissing() {
        Map<String, String> data = new HashMap<>();
        data.put("gender", "   ");      // 空白
        data.put("age", null);          // 显式 null
        data.put("height", "");
        data.put("weight", "  ");
        data.put("chronicDiseases", "[]");   // 空数组

        assertEquals(5, engine.checkDataCompleteness(data).size(),
                "\"[]\" 语义上等于没有病史，必须算缺失");
    }

    @Test
    @DisplayName("null 入参不抛异常，按全缺失处理")
    void nullDataShouldNotThrow() {
        assertEquals(5, engine.checkDataCompleteness(null).size());
    }

    // ================================================================ 槽位抽取

    @Test
    @DisplayName("标准表述：我34岁，身高175厘米，体重70公斤")
    void shouldExtractStandardPhrasing() {
        Map<String, String> slots = engine.extractSlots("我34岁，身高175厘米，体重70公斤");

        assertEquals("34", slots.get("age"));
        assertEquals("175", slots.get("height"));
        assertEquals("70", slots.get("weight"));
    }

    @Test
    @DisplayName("带英文单位：175cm / 70kg")
    void shouldExtractWithEnglishUnits() {
        Map<String, String> slots = engine.extractSlots("我 175cm，70kg");

        assertEquals("175", slots.get("height"));
        assertEquals("70", slots.get("weight"));
    }

    @Test
    @DisplayName("体重用「斤」表述时自动换算为公斤")
    void shouldConvertJinToKg() {
        Map<String, String> slots = engine.extractSlots("我体重140斤");

        assertEquals("70", slots.get("weight"), "140斤 = 70kg");
    }

    @Test
    @DisplayName("识别性别关键词")
    void shouldExtractGender() {
        assertEquals("男", engine.extractSlots("我是男的").get("gender"));
        assertEquals("女", engine.extractSlots("我是女性").get("gender"));
        assertEquals("男", engine.extractSlots("男").get("gender"));
    }

    @Test
    @DisplayName("否定表述不误判性别")
    void shouldNotExtractGenderFromNegation() {
        assertNull(engine.extractSlots("我不是男的").get("gender"),
                "\"我不是男的\" 不应被识别为男性");
    }

    @Test
    @DisplayName("无关消息不抽错（宁可漏抽不可抽错）")
    void shouldNotExtractFromIrrelevantMessage() {
        Map<String, String> slots = engine.extractSlots("今天天气不错，适合出去散步");

        assertTrue(slots.isEmpty(), "无关消息不应产生槽位，实际: " + slots);
    }

    @Test
    @DisplayName("超出人体合理范围的值不抽取（防止误匹配）")
    void shouldRejectOutOfRangeValues() {
        // 身高 500cm 不可能
        assertNull(engine.extractSlots("身高500cm").get("height"));
        // 年龄 300 岁不可能
        assertNull(engine.extractSlots("我300岁了").get("age"));
    }

    @Test
    @DisplayName("年龄不会被误当身高（裸数字不猜）")
    void shouldNotGuessBareNumberAsHeight() {
        // 「我今年34」既无身高也无体重的单位标记，不应抽取身高
        Map<String, String> slots = engine.extractSlots("我今年34");

        assertNull(slots.get("height"), "无单位的裸数字不能猜成身高");
    }

    @Test
    @DisplayName("空消息不抛异常")
    void emptyMessageShouldNotThrow() {
        assertTrue(engine.extractSlots(null).isEmpty());
        assertTrue(engine.extractSlots("").isEmpty());
        assertTrue(engine.extractSlots("   ").isEmpty());
    }

    // ================================================================ 引导 Prompt

    @Test
    @DisplayName("无缺失时不生成追问指令（不浪费 token）")
    void noMissingShouldProduceEmptyPrompt() {
        assertEquals("", engine.buildGuidancePrompt(new HashMap<>()));
        assertEquals("", engine.buildGuidancePrompt(null));
    }

    @Test
    @DisplayName("有缺失时生成追问指令，且一次最多问 2 项")
    void shouldGenerateGuidancePrompt() {
        Map<String, String> missing = new HashMap<>();
        missing.put("gender", "请告诉我性别");
        missing.put("age", "请告诉我年龄");
        missing.put("height", "请告诉我身高");
        missing.put("weight", "请告诉我体重");
        missing.put("chronicDiseases", "请告诉我病史");

        String prompt = engine.buildGuidancePrompt(missing);

        assertTrue(prompt.contains("主动追问"));
        // 5 项缺失但只问 2 项
        assertTrue(prompt.contains("最多问 2 项"));
        // 前两项应出现在 prompt 里
        assertTrue(prompt.contains("请告诉我性别"));
        assertTrue(prompt.contains("请告诉我年龄"));
        // 后三项本轮不问
        assertFalse(prompt.contains("请告诉我病史"), "一次最多问 2 项，其余留到后续");
    }

    // ================================================================ 落库

    @Test
    @DisplayName("新用户：写入抽取到的槽位并自动算 BMI")
    void shouldPersistSlotsForNewUser() {
        when(profileMapper.getByUserId(3)).thenReturn(null);
        ArgumentCaptor<PatientProfile> captor = ArgumentCaptor.forClass(PatientProfile.class);

        Map<String, String> slots = new HashMap<>();
        slots.put("height", "175");
        slots.put("weight", "70");
        int changed = engine.persistSlots(3, slots);

        assertEquals(2, changed);
        verify(profileMapper).insertOrUpdate(captor.capture());
        PatientProfile saved = captor.getValue();
        assertEquals(175.0, saved.getHeight(), 0.01);
        assertEquals(70.0, saved.getWeight(), 0.01);
        // BMI 应自动补算：70 / 1.75^2 = 22.86 → 22.9
        assertNotNull(saved.getBmi());
        assertEquals(22.9, saved.getBmi(), 0.05);
    }

    @Test
    @DisplayName("已有档案：只补空缺，不覆盖既有值")
    void shouldNotOverwriteExistingValues() {
        PatientProfile existing = new PatientProfile();
        existing.setUserId(3);
        existing.setHeight(180.0);      // 已有身高
        existing.setWeight(65.0);
        when(profileMapper.getByUserId(3)).thenReturn(existing);
        ArgumentCaptor<PatientProfile> captor = ArgumentCaptor.forClass(PatientProfile.class);

        // 用户这次口误说 175
        Map<String, String> slots = new HashMap<>();
        slots.put("height", "175");
        int changed = engine.persistSlots(3, slots);

        assertEquals(0, changed, "已有值不应被覆盖");
        verify(profileMapper, never()).insertOrUpdate(any());
    }

    @Test
    @DisplayName("档案为空时全部写入")
    void shouldFillAllEmptyFields() {
        PatientProfile existing = new PatientProfile();
        existing.setUserId(3);
        when(profileMapper.getByUserId(3)).thenReturn(existing);
        ArgumentCaptor<PatientProfile> captor = ArgumentCaptor.forClass(PatientProfile.class);

        Map<String, String> slots = new HashMap<>();
        slots.put("gender", "男");
        slots.put("age", "34");
        int changed = engine.persistSlots(3, slots);

        assertEquals(2, changed);
        verify(profileMapper).insertOrUpdate(captor.capture());
        assertEquals("男", captor.getValue().getGender());
        assertEquals(34, captor.getValue().getAge());
    }

    @Test
    @DisplayName("档案查询抛异常：降级为全缺失，不让对话失败")
    void profileQueryFailureShouldDegradeGracefully() {
        when(profileMapper.getByUserId(anyInt())).thenThrow(new RuntimeException("表不存在"));

        // 不抛异常，且判定为全部缺失
        assertEquals(5, engine.currentUserMissingSlots().size());
    }

    @Test
    @DisplayName("写入抛异常：返回 0 而不向上传播（不影响对话）")
    void persistFailureShouldReturnZero() {
        when(profileMapper.getByUserId(anyInt())).thenReturn(null);
        doThrow(new RuntimeException("磁盘满")).when(profileMapper).insertOrUpdate(any());

        Map<String, String> slots = new HashMap<>();
        slots.put("height", "175");
        assertEquals(0, engine.persistSlots(3, slots),
                "写库失败应降级返回 0，而不是让整个对话 500");
    }

    @Test
    @DisplayName("null userId / 空 slots 直接返回 0，不打库")
    void nullInputShouldBeNoop() {
        assertEquals(0, engine.persistSlots(null, Map.of("height", "175")));
        assertEquals(0, engine.persistSlots(3, new HashMap<>()));
        verify(profileMapper, never()).insertOrUpdate(any());
    }

    // ================================================================ 档案读取

    @Test
    @DisplayName("读取档案：映射为槽位视图")
    void shouldLoadSlotsFromProfile() throws Exception {
        PatientProfile p = new PatientProfile();
        p.setUserId(3);
        p.setGender("male");            // 英文需归一化
        p.setAge(34);
        p.setHeight(175.0);
        p.setWeight(72.5);
        p.setChronicDiseases("[\"高血压\"]");
        when(profileMapper.getByUserId(3)).thenReturn(p);

        Map<String, String> slots = engine.loadUserSlots(3);

        assertEquals("男", slots.get("gender"), "英文性别应归一化为中文");
        assertEquals("34", slots.get("age"));
        assertEquals("175", slots.get("height"));
        assertEquals("72.5", slots.get("weight"));
        assertTrue(engine.checkDataCompleteness(slots).isEmpty(), "档案完整应无缺失项");
    }

    @Test
    @DisplayName("未建档：返回全 null 结构而非报错")
    void shouldReturnNullSlotsWhenNoProfile() {
        when(profileMapper.getByUserId(3)).thenReturn(null);

        Map<String, String> slots = engine.loadUserSlots(3);

        assertEquals(5, slots.size());
        assertTrue(engine.checkDataCompleteness(slots).size() == 5);
    }

    // ================================================================ BMI（回归）

    @Test
    @DisplayName("BMI 计算与分级")
    void bmiShouldCalculateAndClassify() {
        assertEquals(22.9, engine.calculateBMI(175.0, 70.0), 0.05);
        assertEquals("正常", engine.evaluateBMI(22.0));
        assertEquals("偏瘦", engine.evaluateBMI(17.0));
        assertEquals("超重", engine.evaluateBMI(26.0));
        assertEquals("肥胖", engine.evaluateBMI(30.0));
        assertEquals("未知", engine.evaluateBMI(null));
    }

    @Test
    @DisplayName("非法 BMI 输入返回 null 而不是抛异常")
    void invalidBmiInputShouldReturnNull() {
        assertNull(engine.calculateBMI(null, 70.0));
        assertNull(engine.calculateBMI(175.0, null));
        assertNull(engine.calculateBMI(0.0, 70.0));
        assertNull(engine.calculateBMI(-175.0, 70.0));
    }
}
