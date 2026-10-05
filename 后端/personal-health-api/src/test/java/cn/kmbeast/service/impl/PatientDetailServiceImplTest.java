package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.AppointmentMapper;
import cn.kmbeast.mapper.FollowupTaskMapper;
import cn.kmbeast.mapper.PatientProfileMapper;
import cn.kmbeast.mapper.UserHealthMapper;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.mapper.VisitRecordMapper;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.PatientProfile;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.pojo.entity.VisitRecord;
import cn.kmbeast.pojo.vo.AppointmentVO;
import cn.kmbeast.pojo.vo.VisitRecordVO;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 医生端患者档案单元测试（2026-10-04）
 *
 * <p>两条主线：
 * <ol>
 *   <li><b>医患关系闸门</b> —— 无预约关系一律拒绝。缺这一条，医生可构造
 *       {@code patientId=1..N} 遍历全库患者健康档案（身高体重/慢病/用药/血压血糖），
 *       属严重隐私泄露。</li>
 *   <li><b>脏数据防御</b> —— 迁移脚本只修存量，用户后续仍会写入非法 JSON、
 *       {@code ["无"]} 占位、中英混杂性别。任一项未防御都会让医生看到错误信息，
 *       甚至整个详情页 500。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("医生端患者档案")
class PatientDetailServiceImplTest {

    private static final Integer MY_ID = 3001;
    private static final Integer PATIENT = 3;
    private static final Integer OTHER_PATIENT = 4;

    @Mock private AppointmentMapper appointmentMapper;
    @Mock private PatientProfileMapper profileMapper;
    @Mock private UserHealthMapper userHealthMapper;
    @Mock private VisitRecordMapper visitRecordMapper;
    @Mock private FollowupTaskMapper followupTaskMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private PatientDetailServiceImpl service;

    @BeforeEach
    void setUp() {
        LocalThreadHolder.setUserId(MY_ID, 3);

        // 默认：有医患关系、档案完整
        when(appointmentMapper.countDoctorPatientRelation(anyInt(), anyInt())).thenReturn(1);
        when(userMapper.getUserById(PATIENT)).thenReturn(user(PATIENT, "张小明"));
        when(profileMapper.getByUserId(PATIENT)).thenReturn(profile());
        when(userHealthMapper.query(any())).thenReturn(List.of());
        when(visitRecordMapper.queryByPatientId(anyInt(), anyInt())).thenReturn(List.of());
        when(followupTaskMapper.queryByPatientId(anyInt())).thenReturn(List.of());
        when(appointmentMapper.getLatestByDoctorAndPatient(anyInt(), anyInt())).thenReturn(appointment());
        // startConsultation 走的是 getById（详情页用 getLatest…），两个都要 stub
        when(appointmentMapper.getById(anyInt())).thenReturn(appointment());
        when(visitRecordMapper.countByAppointmentId(anyInt())).thenReturn(0);
    }

    @AfterEach
    void tearDown() {
        LocalThreadHolder.clear();
    }

    private User user(Integer id, String name) {
        User u = new User();
        u.setId(id);
        u.setUserName(name);
        u.setUserAccount(name);
        u.setPhone("13800000000");
        return u;
    }

    private PatientProfile profile() {
        PatientProfile p = new PatientProfile();
        p.setUserId(PATIENT);
        p.setGender("男");
        p.setAge(34);
        p.setHeight(175.0);
        p.setWeight(72.5);
        p.setBmi(23.67);
        p.setChronicDiseases("[\"高血压\"]");
        p.setAllergies("[\"青霉素\"]");
        p.setMedications("[\"氨氯地平\"]");
        p.setSurgeries("[]");
        p.setFamilyHistory("[\"糖尿病\"]");
        p.setHealthGoals("[\"控制血压\"]");
        p.setLifestyle("{\"smoking\": false, \"drinking\": \"偶尔\", \"exercise\": \"每周3次\"}");
        p.setSystolicPressure(138);
        p.setDiastolicPressure(88);
        p.setRestingHeartRate(76);
        p.setFastingBloodGlucose(5.4);
        return p;
    }

    private AppointmentVO appointment() {
        AppointmentVO a = new AppointmentVO();
        a.setId(5001);
        a.setPatientId(PATIENT);
        a.setDoctorId(MY_ID);
        a.setStatus(0);
        a.setSerialNumber(1);
        a.setTimeSlot("morning");
        a.setDepartmentName("内科");
        return a;
    }

    // ================================================================ 医患关系闸门

    @Test
    @DisplayName("无医患关系：直接拒绝，不返回任何档案数据")
    void shouldRejectWhenNoDoctorPatientRelation() {
        when(appointmentMapper.countDoctorPatientRelation(MY_ID, OTHER_PATIENT)).thenReturn(0);

        Result<Map<String, Object>> r = service.detail(OTHER_PATIENT, null);

        assertNotEquals(200, r.getCode());
        assertTrue(r.getMsg().contains("无权"), "实际: " + r.getMsg());
        // 关键：拒绝时绝不能碰档案表
        verify(profileMapper, never()).getByUserId(anyInt());
        verify(userHealthMapper, never()).query(any());
    }

    @Test
    @DisplayName("关系校验返回 null：按拒绝处理（fail-closed，不乐观放行）")
    void shouldRejectWhenRelationCheckReturnsNull() {
        when(appointmentMapper.countDoctorPatientRelation(MY_ID, PATIENT)).thenReturn(null);

        Result<Map<String, Object>> r = service.detail(PATIENT, null);

        assertNotEquals(200, r.getCode());
        verify(profileMapper, never()).getByUserId(anyInt());
    }

    @Test
    @DisplayName("关系校验必须用 ThreadLocal 里的医生 ID")
    void relationCheckShouldUseThreadLocalDoctorId() {
        service.detail(PATIENT, null);

        verify(appointmentMapper).countDoctorPatientRelation(MY_ID, PATIENT);
    }

    @Test
    @DisplayName("关系校验 SQL 抛异常：返回可读错误，不抛出 500")
    void relationCheckFailureShouldReturnReadableError() {
        when(appointmentMapper.countDoctorPatientRelation(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("数据库连接失败"));

        Result<Map<String, Object>> r = service.detail(PATIENT, null);

        assertNotEquals(200, r.getCode());
        assertTrue(r.getMsg().contains("稍后重试"), "实际: " + r.getMsg());
    }

    @Test
    @DisplayName("缺少 patientId：直接拒绝")
    void nullPatientIdShouldReject() {
        Result<Map<String, Object>> r = service.detail(null, null);

        assertNotEquals(200, r.getCode());
        verify(appointmentMapper, never()).countDoctorPatientRelation(anyInt(), anyInt());
    }

    @Test
    @DisplayName("患者不存在：返回明确错误")
    void nonExistentPatientShouldReturnError() {
        when(userMapper.getUserById(anyInt())).thenReturn(null);

        Result<Map<String, Object>> r = service.detail(PATIENT, null);

        assertNotEquals(200, r.getCode());
        assertTrue(r.getMsg().contains("不存在"));
    }

    // ================================================================ 未建档场景

    @Test
    @DisplayName("患者未建档：不报错，返回 profileExists=false 与占位结构")
    void patientWithoutProfileShouldNotFail() {
        when(profileMapper.getByUserId(PATIENT)).thenReturn(null);

        Result<Map<String, Object>> r = service.detail(PATIENT, null);
        Map<String, Object> data = r.getData();

        assertEquals(200, r.getCode(), "未建档不应让医生进不了页面");
        assertEquals(Boolean.FALSE, data.get("profileExists"));

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) data.get("profile");
        assertNotNull(profile);
        // 前端无需判空即可渲染
        assertEquals("未知", profile.get("gender"));
        assertEquals("—", profile.get("bmiLabel"));
        assertEquals(List.of(), profile.get("chronicDiseases"));
        assertEquals(List.of(), profile.get("allergies"));
    }

    @Test
    @DisplayName("未建档时性别/年龄为未知，不从 user 表臆造")
    void withoutProfileGenderShouldBeUnknown() {
        when(profileMapper.getByUserId(PATIENT)).thenReturn(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> basic = (Map<String, Object>) service.detail(PATIENT, null).getData().get("basic");

        assertEquals("未知", basic.get("gender"));
        assertNull(basic.get("age"));
    }

    // ================================================================ 脏数据防御

    @Test
    @DisplayName("非法 JSON：降级为空数组，不让详情页 500")
    void malformedJsonShouldDegradeToEmptyList() {
        PatientProfile p = profile();
        p.setChronicDiseases("{不是合法JSON");
        p.setAllergies("[[[");
        p.setLifestyle("<<<>>>");
        when(profileMapper.getByUserId(PATIENT)).thenReturn(p);

        Result<Map<String, Object>> r = service.detail(PATIENT, null);

        assertEquals(200, r.getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) r.getData().get("profile");
        assertEquals(List.of(), profile.get("chronicDiseases"));
        assertEquals(List.of(), profile.get("allergies"));
        @SuppressWarnings("unchecked")
        Map<String, Object> lifestyle = (Map<String, Object>) profile.get("lifestyle");
        assertTrue(lifestyle.isEmpty());
    }

    @Test
    @DisplayName("[\"无\"] 占位符被剔除，不会让医生误判为有过敏史")
    void placeholderNoneShouldBeStripped() {
        PatientProfile p = profile();
        p.setAllergies("[\"无\"]");
        p.setChronicDiseases("[\"无\", \"高血压\"]");
        when(profileMapper.getByUserId(PATIENT)).thenReturn(p);

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) service.detail(PATIENT, null).getData().get("profile");

        @SuppressWarnings("unchecked")
        List<String> allergies = (List<String>) profile.get("allergies");
        assertTrue(allergies.isEmpty(), "\"无\" 不应作为过敏史展示，实际: " + allergies);

        // 混合值里只剔占位符，保留真实内容
        @SuppressWarnings("unchecked")
        List<String> chronic = (List<String>) profile.get("chronicDiseases");
        assertEquals(List.of("高血压"), chronic);
    }

    @Test
    @DisplayName("性别中英混杂一律归一化为中文")
    void genderShouldNormalizeToChinese() {
        String[][] cases = {
                {"男", "男"}, {"male", "男"}, {"MALE", "男"}, {" m ", "男"},
                {"女", "女"}, {"female", "女"}, {"FEMALE", "女"},
                {"", "未知"}, {null, "未知"}, {"未知abc", "未知"},
        };
        for (String[] c : cases) {
            PatientProfile p = profile();
            p.setGender(c[0]);
            when(profileMapper.getByUserId(PATIENT)).thenReturn(p);

            @SuppressWarnings("unchecked")
            Map<String, Object> basic = (Map<String, Object>) service.detail(PATIENT, null).getData().get("basic");
            assertEquals(c[1], basic.get("gender"),
                    "性别归一化失败: '" + c[0] + "' 期望 " + c[1]);
        }
    }

    @Test
    @DisplayName("lifestyle 两套键名统一输出为 smoking/drinking")
    void lifestyleKeysShouldBeUnified() {
        // 旧键名
        PatientProfile p = profile();
        p.setLifestyle("{\"smoke\": \"否\", \"drink\": \"偶尔\", \"exercise\": \"每周2次\"}");
        when(profileMapper.getByUserId(PATIENT)).thenReturn(p);

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) service.detail(PATIENT, null).getData().get("profile");
        @SuppressWarnings("unchecked")
        Map<String, Object> lifestyle = (Map<String, Object>) profile.get("lifestyle");

        assertEquals(Boolean.FALSE, lifestyle.get("smoking"), "\"否\" 应归一为 false");
        assertEquals("偶尔", lifestyle.get("drinking"));
        assertEquals("每周2次", lifestyle.get("exercise"));
        assertFalse(lifestyle.containsKey("smoke"), "旧键名不应残留");
        assertFalse(lifestyle.containsKey("drink"), "旧键名不应残留");
    }

    @Test
    @DisplayName("BMI 缺失时按身高体重现算")
    void bmiShouldBeComputedWhenMissing() {
        PatientProfile p = profile();
        p.setBmi(null);
        when(profileMapper.getByUserId(PATIENT)).thenReturn(p);

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) service.detail(PATIENT, null).getData().get("profile");

        // 72.5 / 1.75^2 = 23.67 → 四舍五入 23.7
        assertEquals(23.7, (Double) profile.get("bmi"), 0.01);
        assertEquals("正常", profile.get("bmiLabel"));
    }

    @Test
    @DisplayName("BMI 等级判定：偏瘦/正常/超重/肥胖")
    void bmiLabelShouldFollowChineseStandard() {
        double[][] cases = {{17.0, 0}, {22.0, 0}, {26.0, 0}, {30.0, 0}};
        String[] expected = {"偏瘦", "正常", "超重", "肥胖"};
        for (int i = 0; i < cases.length; i++) {
            PatientProfile p = profile();
            p.setBmi(cases[i][0]);
            when(profileMapper.getByUserId(PATIENT)).thenReturn(p);

            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) service.detail(PATIENT, null).getData().get("profile");
            assertEquals(expected[i], profile.get("bmiLabel"), "BMI " + cases[i][0]);
        }
    }

    @Test
    @DisplayName("血压分级：按中国标准给出医生可直接判断的结论")
    void bloodPressureLevelShouldBeClassified() {
        // 中国高血压标准：正常 <120/80；1级 120~139/80~89；2级 140~159/90~99；3级 ≥160/≥100
        int[][] cases = {
                {115, 75},   // 正常
                {128, 84},   // 1级
                {150, 95},   // 2级
                {170, 105},  // 3级
                {145, 78},   // 单纯收缩期：收缩压达 2 级 → 按 2 级判
        };
        String[] expected = {"正常", "1级高血压", "2级高血压", "3级高血压", "2级高血压"};
        for (int i = 0; i < cases.length; i++) {
            PatientProfile p = profile();
            p.setSystolicPressure(cases[i][0]);
            p.setDiastolicPressure(cases[i][1]);
            when(profileMapper.getByUserId(PATIENT)).thenReturn(p);

            @SuppressWarnings("unchecked")
            Map<String, Object> vitals = (Map<String, Object>) service.detail(PATIENT, null).getData().get("vitals");
            assertEquals(expected[i], vitals.get("bloodPressureLevel"),
                    cases[i][0] + "/" + cases[i][1]);
        }
    }

    @Test
    @DisplayName("血压只测到一侧：提示「数据不全」而非误判为正常")
    void partialBloodPressureShouldReportIncomplete() {
        PatientProfile p = profile();
        p.setSystolicPressure(150);
        p.setDiastolicPressure(null);
        when(profileMapper.getByUserId(PATIENT)).thenReturn(p);

        @SuppressWarnings("unchecked")
        Map<String, Object> vitals = (Map<String, Object>) service.detail(PATIENT, null).getData().get("vitals");

        assertEquals("数据不全", vitals.get("bloodPressureLevel"));
    }

    // ================================================================ 降级容错

    @Test
    @DisplayName("趋势查询抛异常：详情仍可打开，趋势降为空")
    void trendFailureShouldDegradeGracefully() {
        when(userHealthMapper.query(any())).thenThrow(new RuntimeException("趋势表查询失败"));

        Result<Map<String, Object>> r = service.detail(PATIENT, null);

        assertEquals(200, r.getCode(), "趋势失败不应让医生打不开患者详情");
        assertEquals(List.of(), r.getData().get("trend"));
    }

    @Test
    @DisplayName("档案查询抛异常：详情仍可打开")
    void profileFailureShouldDegradeGracefully() {
        when(profileMapper.getByUserId(PATIENT)).thenThrow(new RuntimeException("档案表不存在"));

        Result<Map<String, Object>> r = service.detail(PATIENT, null);

        assertEquals(200, r.getCode());
        assertEquals(Boolean.FALSE, r.getData().get("profileExists"));
    }

    @Test
    @DisplayName("随访查询抛异常：详情仍可打开")
    void followupFailureShouldDegradeGracefully() {
        when(followupTaskMapper.queryByPatientId(anyInt()))
                .thenThrow(new RuntimeException("随访表查询失败"));

        Result<Map<String, Object>> r = service.detail(PATIENT, null);

        assertEquals(200, r.getCode());
        assertEquals(List.of(), r.getData().get("followups"));
    }

    @Test
    @DisplayName("趋势天数非法/超限：夹到合法区间")
    void trendDaysShouldBeClamped() {
        service.detail(PATIENT, null);
        assertEquals(90, service.detail(PATIENT, null).getData().get("trendDays"));

        assertEquals(30, service.detail(PATIENT, 30).getData().get("trendDays"));
        assertEquals(365, service.detail(PATIENT, 9999).getData().get("trendDays"));
        assertEquals(90, service.detail(PATIENT, 0).getData().get("trendDays"));
        assertEquals(90, service.detail(PATIENT, -5).getData().get("trendDays"));
    }

    // ================================================================ 接诊信息

    @Test
    @DisplayName("已建就诊记录时带 hasVisitRecord=true，前端据此置灰按钮")
    void shouldFlagExistingVisitRecord() {
        when(visitRecordMapper.countByAppointmentId(5001)).thenReturn(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> appt = (Map<String, Object>)
                service.detail(PATIENT, null).getData().get("currentAppointment");

        assertEquals(Boolean.TRUE, appt.get("hasVisitRecord"),
                "不置灰会导致医生重复提交，撞 visit_record.appointment_id 唯一约束");
    }

    @Test
    @DisplayName("无预约记录时 hasAppointment=false，结构仍完整")
    void noAppointmentShouldStillReturnStructure() {
        when(appointmentMapper.getLatestByDoctorAndPatient(anyInt(), anyInt())).thenReturn(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> appt = (Map<String, Object>)
                service.detail(PATIENT, null).getData().get("currentAppointment");

        assertEquals(Boolean.FALSE, appt.get("hasAppointment"));
    }

    // ================================================================ 开始接诊

    @Test
    @DisplayName("开始接诊：保存就诊记录并把预约置为已完成")
    void startConsultationShouldSaveAndComplete() {
        Map<String, Object> record = new HashMap<>();
        record.put("chiefComplaint", "头晕");
        record.put("diagnosis", "原发性高血压 1 级");
        record.put("prescription", "氨氯地平 5mg qd");

        Result<Void> r = service.startConsultation(5001, record);

        assertEquals(200, r.getCode());

        ArgumentCaptor<VisitRecord> captor = ArgumentCaptor.forClass(VisitRecord.class);
        verify(visitRecordMapper).save(captor.capture());
        VisitRecord saved = captor.getValue();
        assertEquals(5001, saved.getAppointmentId());
        assertEquals(PATIENT, saved.getPatientId());
        assertEquals(MY_ID, saved.getDoctorId(), "医生 ID 必须取自 token，不能被前端伪造");
        assertEquals("原发性高血压 1 级", saved.getDiagnosis());

        verify(appointmentMapper).completeByDoctor(5001, MY_ID);
    }

    @Test
    @DisplayName("开始接诊：操作他人预约被拒绝")
    void startConsultationOnOthersAppointmentShouldReject() {
        AppointmentVO others = appointment();
        others.setDoctorId(3002);
        when(appointmentMapper.getById(5001)).thenReturn(others);

        Result<Void> r = service.startConsultation(5001, Map.of("diagnosis", "x"));

        assertNotEquals(200, r.getCode());
        assertTrue(r.getMsg().contains("无权"), "实际: " + r.getMsg());
        verify(visitRecordMapper, never()).save(any());
    }

    @Test
    @DisplayName("开始接诊：预约不存在被拒绝")
    void startConsultationOnMissingAppointmentShouldReject() {
        when(appointmentMapper.getById(anyInt())).thenReturn(null);

        Result<Void> r = service.startConsultation(9999, Map.of("diagnosis", "x"));

        assertNotEquals(200, r.getCode());
        verify(visitRecordMapper, never()).save(any());
    }

    @Test
    @DisplayName("开始接诊：重复提交被拦截（避免撞唯一约束 500）")
    void startConsultationTwiceShouldBeRejected() {
        when(visitRecordMapper.countByAppointmentId(5001)).thenReturn(1);

        Result<Void> r = service.startConsultation(5001, Map.of("diagnosis", "高血压"));

        assertNotEquals(200, r.getCode());
        assertTrue(r.getMsg().contains("重复"), "实际: " + r.getMsg());
        verify(visitRecordMapper, never()).save(any());
    }

    @Test
    @DisplayName("开始接诊：并发插入失败时返回可读提示而非 500")
    void concurrentInsertFailureShouldReturnReadableError() {
        doThrow(new RuntimeException("Duplicate entry for key 'appointment_id'"))
                .when(visitRecordMapper).save(any());

        Result<Void> r = service.startConsultation(5001, Map.of("diagnosis", "高血压"));

        assertNotEquals(200, r.getCode());
        assertTrue(r.getMsg().contains("失败") || r.getMsg().contains("接诊"),
                "实际: " + r.getMsg());
    }

    @Test
    @DisplayName("开始接诊：缺少诊断结论被拒绝（诊断是就诊记录的核心产出）")
    void startConsultationWithoutDiagnosisShouldReject() {
        Result<Void> r = service.startConsultation(5001, Map.of("chiefComplaint", "头晕"));

        assertNotEquals(200, r.getCode());
        assertTrue(r.getMsg().contains("诊断"), "实际: " + r.getMsg());
        verify(visitRecordMapper, never()).save(any());
    }

    @Test
    @DisplayName("开始接诊：缺少 appointmentId 直接拒绝")
    void startConsultationWithoutIdShouldReject() {
        Result<Void> r = service.startConsultation(null, Map.of("diagnosis", "x"));

        assertNotEquals(200, r.getCode());
        verify(appointmentMapper, never()).getById(anyInt());
    }

    @Test
    @DisplayName("就诊记录已保存但预约状态同步失败：仍算成功（数据已落地）")
    void appointmentSyncFailureShouldNotFailRequest() {
        when(appointmentMapper.completeByDoctor(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("状态同步失败"));

        Result<Void> r = service.startConsultation(5001, Map.of("diagnosis", "高血压"));

        assertEquals(200, r.getCode(), "就诊记录已写入，不应因状态同步失败而让医生以为没保存");
        verify(visitRecordMapper).save(any());
    }
}
