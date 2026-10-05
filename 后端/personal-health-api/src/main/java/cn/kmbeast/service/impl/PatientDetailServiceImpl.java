package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.AppointmentMapper;
import cn.kmbeast.mapper.PatientProfileMapper;
import cn.kmbeast.mapper.UserHealthMapper;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.mapper.VisitRecordMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.UserHealthQueryDto;
import cn.kmbeast.pojo.entity.PatientProfile;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.pojo.entity.UserHealth;
import cn.kmbeast.pojo.entity.VisitRecord;
import cn.kmbeast.pojo.vo.AppointmentVO;
import cn.kmbeast.pojo.vo.FollowupTaskVO;
import cn.kmbeast.pojo.vo.UserHealthVO;
import cn.kmbeast.pojo.vo.VisitRecordVO;
import cn.kmbeast.mapper.FollowupTaskMapper;
import cn.kmbeast.service.PatientDetailService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 医生端患者档案实现（2026-10-04）
 *
 * <p><b>防越权</b>：本类所有入口都先用
 * {@code appointmentMapper.countDoctorPatientRelation(doctorId, patientId)}
 * 校验医患关系。医生身份取自 {@code LocalThreadHolder}（已验签 JWT 写入），
 * patientId 虽由前端传入但受该关系约束 —— 无法遍历他人档案。
 */
@Slf4j
@Service
public class PatientDetailServiceImpl implements PatientDetailService {

    /** 趋势数据默认天数 */
    private static final int DEFAULT_TREND_DAYS = 90;
    /** 趋势数据上限，防拖库 */
    private static final int MAX_TREND_DAYS = 365;
    /** 历史就诊返回条数上限 */
    private static final int MAX_VISIT_LIMIT = 20;
    /** 健康趋势按指标分组后单个指标的点数上限 */
    private static final int MAX_TREND_POINTS = 200;

    @Resource
    private AppointmentMapper appointmentMapper;

    @Resource
    private PatientProfileMapper profileMapper;

    @Resource
    private UserHealthMapper userHealthMapper;

    @Resource
    private VisitRecordMapper visitRecordMapper;

    @Resource
    private FollowupTaskMapper followupTaskMapper;

    @Resource
    private UserMapper userMapper;

    private Integer currentDoctorId() {
        Integer id = LocalThreadHolder.getUserId();
        if (id == null) {
            throw new IllegalStateException("未获取到当前医生身份");
        }
        return id;
    }

    @Override
    public Result<Map<String, Object>> detail(Integer patientId, Integer trendDays) {
        Integer doctorId = currentDoctorId();

        if (patientId == null) {
            return ApiResult.error("缺少患者ID");
        }

        // ===== 医患关系闸门：无关系直接拒绝，防遍历 patientId 查全库档案 =====
        Integer relation;
        try {
            relation = appointmentMapper.countDoctorPatientRelation(doctorId, patientId);
        } catch (Exception e) {
            log.error("[PatientDetail] 医患关系校验失败: doctorId={}, patientId={}", doctorId, patientId, e);
            return ApiResult.error("查询失败，请稍后重试");
        }
        if (relation == null || relation == 0) {
            log.warn("[PatientDetail] 拒绝访问无医患关系的患者: doctorId={}, patientId={}", doctorId, patientId);
            return ApiResult.error("无权查看该患者档案");
        }

        Map<String, Object> data = new LinkedHashMap<>();

        // ===== 1. 基础信息（user 表）=====
        // 注意：user 表**没有** name/gender/age/phone 之外的画像字段 ——
        // 性别与年龄存在 patient_profile 里（见下方 profile 区块）。
        // 早期误以为 user 表有 name/userPhone/userGender/userAge，那是
        // AppointmentVO 里 SQL 别名（patient_name 等）造成的错觉。
        User user = safeGet(() -> userMapper.getUserById(patientId));
        if (user == null) {
            return ApiResult.error("患者不存在");
        }
        Map<String, Object> basic = new LinkedHashMap<>();
        basic.put("patientId", user.getId());
        basic.put("userName", user.getUserName());
        basic.put("account", user.getUserAccount());
        basic.put("phone", user.getPhone());
        basic.put("avatar", user.getUserAvatar());
        basic.put("isVip", user.getIsVip());
        data.put("basic", basic);

        // ===== 2. 健康档案 =====
        PatientProfile profile = safeGet(() -> profileMapper.getByUserId(patientId));
        data.put("profileExists", profile != null);
        data.put("profile", profile == null ? buildEmptyProfile() : buildProfile(profile));
        // 性别/年龄从档案取；未建档时回退到 userAccount 不合理，故置未知
        if (profile != null) {
            basic.put("gender", normalizeGender(profile.getGender()));
            basic.put("age", profile.getAge());
        } else {
            basic.put("gender", "未知");
            basic.put("age", null);
        }

        // ===== 3. 体征与生化指标（医生判断的核心）=====
        data.put("vitals", buildVitals(profile));

        // ===== 4. 近 N 天健康趋势 =====
        int days = normalizeTrendDays(trendDays);
        data.put("trendDays", days);
        data.put("trend", buildTrend(patientId, days));

        // ===== 5. 历史就诊 =====
        List<VisitRecordVO> visits = safeList(
                () -> visitRecordMapper.queryByPatientId(patientId, MAX_VISIT_LIMIT));
        data.put("visitHistory", visits == null ? List.of() : visits);

        // ===== 6. 随访任务 =====
        List<FollowupTaskVO> followups = safeList(
                () -> followupTaskMapper.queryByPatientId(patientId));
        data.put("followups", followups == null ? List.of() : followups);

        // ===== 7. 与我的预约关系（含已接诊标记，供前端置灰按钮）=====
        Map<String, Object> appointmentInfo = buildAppointmentInfo(doctorId, patientId);
        data.put("currentAppointment", appointmentInfo);

        return ApiResult.success(data);
    }

    // ---------------------------------------------------------------- 各区块构建

    /**
     * 档案区块：把 6 个 JSON 文本字段解析成数组。
     *
     * <p>⚠️ 必须防御性解析：{@code 02_patient_profile_normalize.sql} 只修了存量数据，
     * 用户后续仍可能写入非法 JSON 或 {@code ["无"]} 这类占位内容。
     * 解析失败一律降级为空数组，<b>绝不让医生端整个页面 500</b>。
     */
    private Map<String, Object> buildProfile(PatientProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gender", normalizeGender(p.getGender()));
        m.put("age", p.getAge());
        m.put("birthDate", p.getBirthDate());
        m.put("height", p.getHeight());
        m.put("weight", p.getWeight());
        m.put("bmi", resolveBmi(p));
        m.put("bmiLabel", bmiLabel(resolveBmi(p)));
        m.put("chronicDiseases", parseStringArray(p.getChronicDiseases()));
        m.put("allergies", parseStringArray(p.getAllergies()));
        m.put("medications", parseStringArray(p.getMedications()));
        m.put("surgeries", parseStringArray(p.getSurgeries()));
        m.put("familyHistory", parseStringArray(p.getFamilyHistory()));
        m.put("healthGoals", parseStringArray(p.getHealthGoals()));
        m.put("lifestyle", parseLifestyle(p.getLifestyle()));
        m.put("lastUpdateTime", p.getLastUpdateTime());
        return m;
    }

    /** 未建档时的占位结构 —— 前端无需判空即可渲染 */
    private Map<String, Object> buildEmptyProfile() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gender", "未知");
        m.put("age", null);
        m.put("birthDate", null);
        m.put("height", null);
        m.put("weight", null);
        m.put("bmi", null);
        m.put("bmiLabel", "—");
        m.put("chronicDiseases", List.of());
        m.put("allergies", List.of());
        m.put("medications", List.of());
        m.put("surgeries", List.of());
        m.put("familyHistory", List.of());
        m.put("healthGoals", List.of());
        m.put("lifestyle", new HashMap<String, Object>());
        m.put("lastUpdateTime", null);
        return m;
    }

    /**
     * 体征区块。血压额外给出「是否高血压」的判断依据（140/90 mmHg 切点），
     * 医生不必自己心算。
     */
    private Map<String, Object> buildVitals(PatientProfile p) {
        Map<String, Object> v = new LinkedHashMap<>();
        if (p == null) {
            v.put("hasData", false);
            return v;
        }
        Integer sys = p.getSystolicPressure();
        Integer dia = p.getDiastolicPressure();
        v.put("hasData", sys != null || p.getRestingHeartRate() != null
                || p.getFastingBloodGlucose() != null);
        v.put("systolicPressure", sys);
        v.put("diastolicPressure", dia);
        v.put("bloodPressure", (sys != null && dia != null) ? sys + "/" + dia + " mmHg" : null);
        v.put("bloodPressureLevel", bloodPressureLevel(sys, dia));
        v.put("restingHeartRate", p.getRestingHeartRate());
        v.put("fastingBloodGlucose", p.getFastingBloodGlucose());
        v.put("postprandialBloodGlucose", p.getPostprandialBloodGlucose());
        v.put("totalCholesterol", p.getTotalCholesterol());
        v.put("triglycerides", p.getTriglycerides());
        v.put("hdlCholesterol", p.getHdlCholesterol());
        v.put("ldlCholesterol", p.getLdlCholesterol());
        return v;
    }

    /**
     * 健康趋势：按指标分桶，每桶内按时间正序（画折线图需要）。
     *
     * <p>结构：{ "3": {name:"收缩压", unit:"mmHg", points:[{date,value}, ...]}, ... }
     * key 为 {@code health_model_config_id}。
     */
    private List<Map<String, Object>> buildTrend(Integer patientId, int days) {
        List<UserHealthVO> rows;
        try {
            UserHealthQueryDto dto = new UserHealthQueryDto();
            dto.setUserId(patientId);
            rows = userHealthMapper.query(dto);
        } catch (Exception e) {
            log.warn("[PatientDetail] 趋势数据查询失败: patientId={}", patientId, e);
            return List.of();
        }
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        // 保持插入顺序用 LinkedHashMap，避免前端每次刷新指标顺序都变
        Map<Integer, Map<String, Object>> grouped = new LinkedHashMap<>();

        for (UserHealthVO r : rows) {
            if (r.getHealthModelConfigId() == null || r.getValue() == null) {
                continue;
            }
            // 过滤区间外
            if (r.getCreateTime() != null && r.getCreateTime().isBefore(since)) {
                continue;
            }
            Double value = parseDouble(r.getValue());
            if (value == null) {
                continue;   // 脏值（如 "正常"、"偏高"）跳过，不让图表崩
            }

            Map<String, Object> series = grouped.computeIfAbsent(r.getHealthModelConfigId(), k -> {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("configId", k);
                s.put("name", r.getName());
                s.put("unit", r.getUnit());
                s.put("symbol", r.getSymbol());
                s.put("valueRange", r.getValueRange());
                s.put("points", new ArrayList<Map<String, Object>>());
                return s;
            });

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> points = (List<Map<String, Object>>) series.get("points");
            if (points.size() >= MAX_TREND_POINTS) {
                continue;
            }
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("date", r.getCreateTime());
            pt.put("value", value);
            points.add(pt);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> s : grouped.values()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> points = (List<Map<String, Object>>) s.get("points");
            if (points.isEmpty()) {
                continue;
            }
            // query 是 create_time DESC，反转成升序便于前端直接画折线
            points.sort((a, b) -> String.valueOf(a.get("date")).compareTo(String.valueOf(b.get("date"))));
            result.add(s);
        }
        return result;
    }

    /**
     * 与当前医生的预约关系。
     *
     * <p>带上 {@code hasVisitRecord} 标记，前端据此把「开始接诊」置灰 ——
     * {@code visit_record.appointment_id} 有 UNIQUE 约束，重复插入会 500。
     */
    private Map<String, Object> buildAppointmentInfo(Integer doctorId, Integer patientId) {
        Map<String, Object> m = new LinkedHashMap<>();
        AppointmentVO appt = safeGet(() -> appointmentMapper.getLatestByDoctorAndPatient(doctorId, patientId));
        if (appt == null) {
            m.put("hasAppointment", false);
            return m;
        }
        m.put("hasAppointment", true);
        m.put("appointmentId", appt.getId());
        m.put("appointmentDate", appt.getAppointmentDate());
        m.put("timeSlot", appt.getTimeSlot());
        m.put("status", appt.getStatus());
        m.put("symptomDescription", appt.getSymptomDescription());
        m.put("serialNumber", appt.getSerialNumber());
        m.put("departmentName", appt.getDepartmentName());

        Integer cnt = safeGet(() -> visitRecordMapper.countByAppointmentId(appt.getId()));
        m.put("hasVisitRecord", cnt != null && cnt > 0);
        return m;
    }

    // ---------------------------------------------------------------- 归一化工具

    /**
     * 性别归一化：历史数据中英混杂（男/male/female），迁移脚本只修了存量，
     * 这里对返回值再兜一层，避免任何残留英文字面值直达医生端。
     */
    private String normalizeGender(String g) {
        if (g == null || g.trim().isEmpty()) {
            return "未知";
        }
        String s = g.trim().toLowerCase();
        if (s.equals("male") || s.equals("m") || s.equals("1") || s.equals("男")) {
            return "男";
        }
        if (s.equals("female") || s.equals("f") || s.equals("2") || s.equals("女")) {
            return "女";
        }
        return "未知";
    }

    /**
     * 解析字符串数组型 JSON 字段。
     *
     * <p>三道防御：
     * <ol>
     *   <li>空/null → 空列表</li>
     *   <li>非法 JSON → 空列表（记 warn，不抛）</li>
     *   <li>元素为「无/没有/null/-/空串」→ 视为占位符剔除</li>
     * </ol>
     */
    private List<String> parseStringArray(String json) {
        if (json == null || json.trim().isEmpty()) {
            return List.of();
        }
        JSONArray arr;
        try {
            arr = JSON.parseArray(json);
        } catch (Exception e) {
            log.warn("[PatientDetail] JSON 数组解析失败，降级为空: {}", json, e);
            return List.of();
        }
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            String v = arr.getString(i);
            if (v == null) {
                continue;
            }
            String t = v.trim();
            // 过滤占位符：把 ["无"] 当成真实内容会让医生误判为「有一条叫"无"的过敏史」
            if (t.isEmpty() || isPlaceholder(t)) {
                continue;
            }
            out.add(t);
        }
        return out;
    }

    private boolean isPlaceholder(String v) {
        return "无".equals(v) || "没有".equals(v) || "null".equalsIgnoreCase(v)
                || "-".equals(v) || "暂无".equals(v) || "未知".equals(v);
    }

    /**
     * 解析生活方式对象。
     *
     * <p>同时兼容历史两套键名：{@code smoking/drinking} 与 {@code smoke/drink}，
     * 统一输出为前者，前端只需处理一种结构。
     */
    private Map<String, Object> parseLifestyle(String json) {
        Map<String, Object> out = new HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return out;
        }
        JSONObject obj;
        try {
            obj = JSON.parseObject(json);
        } catch (Exception e) {
            log.warn("[PatientDetail] lifestyle 解析失败，降级为空: {}", json);
            return out;
        }
        if (obj == null) {
            return out;
        }
        out.put("smoking", firstPresent(obj, "smoking", "smoke"));
        out.put("drinking", firstPresent(obj, "drinking", "drink"));
        out.put("exercise", firstPresent(obj, "exercise", "sport"));
        return out;
    }

    private Object firstPresent(JSONObject obj, String... keys) {
        for (String k : keys) {
            Object v = obj.get(k);
            if (v != null) {
                // 旧数据的 "否" 归一为布尔 false，前端不必处理两种类型
                if (v instanceof String) {
                    String s = ((String) v).trim();
                    if ("否".equals(s) || "不吸烟".equals(s)) {
                        return Boolean.FALSE;
                    }
                    if ("是".equals(s)) {
                        return Boolean.TRUE;
                    }
                }
                return v;
            }
        }
        return null;
    }

    private Double resolveBmi(PatientProfile p) {
        if (p == null) {
            return null;
        }
        if (p.getBmi() != null && p.getBmi() > 0) {
            return p.getBmi();
        }
        // BMI 缺失时按身高体重现算（迁移脚本已补，这里再兜底防新增脏数据）
        if (p.getHeight() != null && p.getWeight() != null
                && p.getHeight() > 0 && p.getWeight() > 0) {
            double h = p.getHeight() / 100.0;
            return round1(p.getWeight() / (h * h));
        }
        return null;
    }

    /** 中国成人 BMI 判定标准 */
    private String bmiLabel(Double bmi) {
        if (bmi == null) {
            return "—";
        }
        if (bmi < 18.5) return "偏瘦";
        if (bmi < 24) return "正常";
        if (bmi < 28) return "超重";
        return "肥胖";
    }

    /** 血压分级（切点 140/90 mmHg） */
    private String bloodPressureLevel(Integer sys, Integer dia) {
        if (sys == null && dia == null) {
            return "—";
        }
        if (sys == null || dia == null) {
            return "数据不全";
        }
        if (sys >= 160 || dia >= 100) return "3级高血压";
        if (sys >= 140 || dia >= 90) return "2级高血压";
        if (sys >= 120 || dia >= 80) return "1级高血压";
        return "正常";
    }

    private int normalizeTrendDays(Integer days) {
        if (days == null || days <= 0) {
            return DEFAULT_TREND_DAYS;
        }
        return Math.min(days, MAX_TREND_DAYS);
    }

    private Double parseDouble(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Double.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;   // 脏值如「正常」「偏高」，跳过而非让图表崩
        }
    }

    private Double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    // ---------------------------------------------------------------- 容错包装

    /**
     * 附属数据查询的统一容错。
     *
     * <p>设计取舍：患者详情是一个<b>聚合视图</b>，任何一个附属区块（档案/趋势/随访）
     * 查询失败都不应让医生<b>整页打不开</b> —— 那等于门诊无法接诊。
     * 因此单块失败降级为空，主流程继续。
     */
    private <T> T safeGet(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("[PatientDetail] 附属数据查询失败，降级处理", e);
            return null;
        }
    }

    private <T> List<T> safeList(java.util.function.Supplier<List<T>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("[PatientDetail] 附属列表查询失败，降级为空列表", e);
            return List.of();
        }
    }

    // ---------------------------------------------------------------- 开始接诊

    @Override
    public Result<Void> startConsultation(Integer appointmentId, Map<String, Object> record) {
        Integer doctorId = currentDoctorId();

        if (appointmentId == null) {
            return ApiResult.error("缺少预约ID");
        }

        // 1) 预约必须属于当前医生（防替他人患者建就诊记录）
        AppointmentVO appt = safeGet(() -> appointmentMapper.getById(appointmentId));
        if (appt == null) {
            return ApiResult.error("预约不存在");
        }
        if (!doctorId.equals(appt.getDoctorId())) {
            log.warn("[PatientDetail] 拒绝为非本人预约建就诊记录: doctorId={}, appointmentId={}",
                    doctorId, appointmentId);
            return ApiResult.error("无权操作该预约");
        }

        // 2) 已建过就诊记录则拒绝（appointment_id 唯一约束，重复插入会 500）
        Integer cnt = safeGet(() -> visitRecordMapper.countByAppointmentId(appointmentId));
        if (cnt != null && cnt > 0) {
            return ApiResult.error("该预约已有就诊记录，请勿重复提交");
        }

        // 3) 诊断是就诊记录的核心产出，缺失则记录价值为零
        String diagnosis = str(record, "diagnosis");
        if (diagnosis == null || diagnosis.trim().isEmpty()) {
            return ApiResult.error("请填写诊断结论");
        }

        VisitRecord vr = new VisitRecord();
        vr.setAppointmentId(appointmentId);
        vr.setPatientId(appt.getPatientId());
        vr.setDoctorId(doctorId);
        vr.setChiefComplaint(str(record, "chiefComplaint"));
        vr.setPresentIllness(str(record, "presentIllness"));
        vr.setDiagnosis(diagnosis.trim());
        vr.setPrescription(str(record, "prescription"));
        vr.setExaminationResults(str(record, "examinationResults"));
        vr.setFollowUpPlan(str(record, "followUpPlan"));
        vr.setCreateTime(LocalDateTime.now());
        vr.setUpdateTime(LocalDateTime.now());

        try {
            visitRecordMapper.save(vr);
        } catch (Exception e) {
            // 并发场景：两次点击同时通过第 2 步校验，其中一条会撞唯一约束
            log.warn("[PatientDetail] 写入就诊记录失败（可能并发重复提交）: appointmentId={}", appointmentId, e);
            return ApiResult.error("提交失败，该预约可能已被接诊");
        }

        // 4) 建完就诊记录把预约置为已完成
        try {
            appointmentMapper.completeByDoctor(appointmentId, doctorId);
        } catch (Exception e) {
            // 预约状态没同步成功不影响就诊记录本身，仅记 warn
            log.warn("[PatientDetail] 就诊记录已建但预约状态未同步: appointmentId={}", appointmentId, e);
        }

        log.info("[PatientDetail] 医生 {} 完成接诊: appointmentId={}, patientId={}",
                doctorId, appointmentId, appt.getPatientId());
        return ApiResult.success("接诊记录已保存");
    }

    private String str(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
