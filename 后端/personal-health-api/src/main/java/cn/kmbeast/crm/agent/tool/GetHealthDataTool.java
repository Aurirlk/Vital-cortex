package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.mapper.PatientProfileMapper;
import cn.kmbeast.mapper.UserHealthMapper;
import cn.kmbeast.pojo.dto.query.extend.UserHealthQueryDto;
import cn.kmbeast.pojo.entity.PatientProfile;
import cn.kmbeast.pojo.vo.UserHealthVO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 获取用户健康数据工具
 *
 * <p><b>2026-10-06 重写（阶段 0-A / A1）</b>：原实现读磁盘 JSON 文件
 * {@code ai_data/health/user_{id}.json}，而该文件全部 17 份均为 {@code total:0}、
 * 合计 0 条 —— 但 {@code BaseReActAgent} 的系统提示词<b>强制</b> AI 必须调用本工具，
 * 于是 AI 永远读到"暂无健康数据"，而库里真实存在 160 条 {@code user_health} 记录
 * 与 7 例完整 {@code patient_profile} 档案（完全读不到）。
 *
 * <p>现改为直连 MySQL 查询，数据来源：
 * <ul>
 *   <li>{@code user_health}（160 条指标时序）JOIN {@code health_model_config}（12 项指标字典，
 *       提供名称/单位/正常范围）</li>
 *   <li>{@code patient_profile}（7 例档案：身高体重BMI + 慢病/过敏/用药/手术/家族史 JSON 列 + 生活习惯）</li>
 * </ul>
 *
 * <p>遵守「强耦合原则」：<b>未新增任何数据表</b>，复用既有 Mapper
 * （{@link UserHealthMapper#query} 已经 JOIN 好了指标字典）。
 *
 * <p>顺带修复一个隐藏 Bug：{@code health_model_config.value_range} 存在<b>两种互不兼容的格式</b>——
 * 老 4 行（id 1~5）是逗号式 {@code "90,140"}，新 7 行（id 6~12）是 JSON 式
 * {@code {"min":0,"max":11.1}}。原实现只会 {@code split(",")}，对新格式完全失效。
 * 见 {@link #parseValueRange(String)}。
 */
@Slf4j
@Component
public class GetHealthDataTool implements Tool {

    /**
     * 单次返回的记录上限。
     * user_health 有 160 条，全塞进 prompt 会挤爆上下文，且 LLM 也用不了这么多。
     */
    private static final int MAX_RECORDS = 50;

    /**
     * 异常指标判定时的回溯窗口（天）。
     * 只看"太久之前的数据"对健康建议没有意义。
     */
    private static final int ABNORMAL_LOOKBACK_DAYS = 180;

    @Resource
    private UserHealthMapper userHealthMapper;

    @Resource
    private PatientProfileMapper patientProfileMapper;

    @Override
    public String getName() {
        return "get_health_data";
    }

    @Override
    public String getDescription() {
        return "获取当前登录用户本人的健康数据：① profile=健康档案摘要（身高/体重/BMI/年龄/性别、" +
                "慢性病史/过敏史/用药史/家族史/生活习惯）；② recent=最近N天的指标记录明细；" +
                "③ abnormal=超出正常范围的异常指标分析。" +
                "只能查询当前登录用户自己的数据。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> typeProp = new LinkedHashMap<>();
        typeProp.put("type", "string");
        typeProp.put("description", "查询类型");
        typeProp.put("enum", Arrays.asList("profile", "recent", "abnormal"));
        properties.put("query_type", typeProp);

        Map<String, Object> daysProp = new LinkedHashMap<>();
        daysProp.put("type", "integer");
        daysProp.put("description", "查询最近多少天的记录(仅query_type=recent时有效)，默认30，最大3650");
        daysProp.put("default", 30);
        // 2026-10-07：补结构化上下界（原先只写在 description 里，模型无法机器感知）
        daysProp.put("minimum", 1);
        daysProp.put("maximum", 3650);
        properties.put("days", daysProp);

        // 🔴 安全约束：身份一律取自 ThreadLocal 上下文，本工具不接受 userId 入参。
        // 若开放入参，LLM 即可构造 {"userId": 3} 越权读取他人健康档案。
        schema.put("properties", properties);
        schema.put("required", Collections.singletonList("query_type"));

        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String queryType = arguments == null ? null : (String) arguments.get("query_type");
        if (queryType == null || queryType.trim().isEmpty()) {
            return ToolResult.error("缺少必填参数 query_type，可选值: profile / recent / abnormal");
        }

        // 身份只能从上下文取，绝不信任 LLM 传入的参数
        Integer userId = ToolContext.getInt("userId");
        if (userId == null) {
            return ToolResult.ok("无法确定用户身份，请告知用户需要先登录。");
        }

        try {
            switch (queryType.trim().toLowerCase()) {
                case "profile":
                    return ToolResult.ok(formatHealthProfile(userId));
                case "recent":
                    int days = readDays(arguments);
                    return ToolResult.ok(formatRecentRecords(userId, days));
                case "abnormal":
                    return ToolResult.ok(formatAbnormalIndicators(userId));
                default:
                    return ToolResult.error("未知的查询类型: " + queryType
                            + "，可选值: profile / recent / abnormal");
            }
        } catch (Exception e) {
            log.error("[GetHealthDataTool] 健康数据查询失败: userId={}, queryType={}", userId, queryType, e);
            return ToolResult.error("健康数据查询异常: " + e.getMessage());
        }
    }

    // ==================== 私密辅助 ====================

    /**
     * 读取 days 参数并钳制到合法区间。
     * LLM 常出现 "最近N天" 语义下传 null / 负数 / 极大值的情况，必须钳制而非直接透传。
     */
    private int readDays(Map<String, Object> arguments) {
        Object raw = arguments.get("days");
        int days = 30;
        if (raw instanceof Number) {
            days = ((Number) raw).intValue();
        }
        return Math.max(1, Math.min(days, 3650));
    }

    /**
     * 查指定用户在最近 days 天内的健康记录。
     *
     * <p>《深层》UserHealthMapper.query() 的 LIMIT 用的是 {@code #{current},#{size}}，
     * 而 QueryDto.current 是<b>偏移量</b>不是页码 —— 取"最新 N 条"必须传 current=0。
     */
    private List<UserHealthVO> queryRecent(Integer userId, int days, int limit) {
        UserHealthQueryDto dto = new UserHealthQueryDto();
        dto.setUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        dto.setStartTime(now.minusDays(days));
        dto.setEndTime(now);
        dto.setCurrent(0);
        dto.setSize(limit);
        return userHealthMapper.query(dto);
    }

    /**
     * 档案摘要 = patient_profile 的静态资料 + user_health 的各指标最新值。
     */
    private String formatHealthProfile(Integer userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 用户健康档案 ===\n\n");

        PatientProfile profile = safeGetProfile(userId);

        // ---- 第一部分：静态档案 ----
        if (profile != null) {
            sb.append("【基本信息】\n");
            appendIfPresent(sb, "性别", profile.getGender());
            if (profile.getAge() != null) {
                sb.append("年龄: ").append(profile.getAge()).append(" 岁\n");
            }
            if (profile.getHeight() != null) {
                sb.append("身高: ").append(profile.getHeight()).append(" cm\n");
            }
            if (profile.getWeight() != null) {
                sb.append("体重: ").append(profile.getWeight()).append(" kg\n");
            }
            if (profile.getBmi() != null) {
                sb.append("BMI: ").append(String.format("%.1f", profile.getBmi()))
                        .append(" (").append(bmiComment(profile.getBmi())).append(")\n");
            }

            String diseases = formatStringList(profile.getChronicDiseases());
            sb.append("慢性病史: ").append(diseases.isEmpty() ? "无" : diseases).append("\n");
            String allergies = formatStringList(profile.getAllergies());
            sb.append("过敏史: ").append(allergies.isEmpty() ? "无" : allergies).append("\n");
            String meds = formatStringList(profile.getMedications());
            sb.append("用药史: ").append(meds.isEmpty() ? "无" : meds).append("\n");
            String surgeries = formatStringList(profile.getSurgeries());
            sb.append("手术史: ").append(surgeries.isEmpty() ? "无" : surgeries).append("\n");
            String family = formatStringList(profile.getFamilyHistory());
            sb.append("家族病史: ").append(family.isEmpty() ? "无" : family).append("\n");

            String lifestyle = formatLifestyle(profile.getLifestyle());
            if (!lifestyle.isEmpty()) {
                sb.append("生活习惯: ").append(lifestyle).append("\n");
            }
            String goals = formatStringList(profile.getHealthGoals());
            if (!goals.isEmpty()) {
                sb.append("健康目标: ").append(goals).append("\n");
            }
        } else {
            sb.append("【基本信息】暂无健康档案记录\n");
        }

        // ---- 第二部分：各指标最新实测值 ----
        List<UserHealthVO> records = queryRecent(userId, ABNORMAL_LOOKBACK_DAYS, MAX_RECORDS);
        Map<String, UserHealthVO> latest = latestByIndicator(records);

        sb.append("\n【各指标最新实测值】\n");
        if (latest.isEmpty()) {
            sb.append("暂无指标记录，建议引导用户到「健康数据」页面录入。\n");
        } else {
            for (UserHealthVO v : latest.values()) {
                sb.append("- ").append(nullToEmpty(v.getName())).append(": ")
                        .append(nullToEmpty(v.getValue()))
                        .append(" ").append(nullToEmpty(v.getUnit()));
                String range = v.getValueRange();
                if (range != null && !range.isEmpty()) {
                    double[] bounds = parseValueRange(range);
                    if (bounds != null) {
                        sb.append(" (正常参考: ").append(fmtNum(bounds[0])).append("~")
                                .append(fmtNum(bounds[1])).append(")");
                    }
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 最近 N 天明细。
     */
    private String formatRecentRecords(Integer userId, int days) {
        List<UserHealthVO> records = queryRecent(userId, days, MAX_RECORDS);

        StringBuilder sb = new StringBuilder();
        sb.append("=== 最近").append(days).append("天健康记录 ===\n\n");

        if (records == null || records.isEmpty()) {
            sb.append("最近").append(days).append("天内没有健康记录。\n");
            sb.append("建议：引导用户到「健康数据」页面录入血压、血糖等指标。\n");
            return sb.toString();
        }

        sb.append("共 ").append(records.size()).append(" 条记录（按时间倒序）:\n");
        for (UserHealthVO v : records) {
            sb.append("- ").append(fmtTime(v.getCreateTime()))
                    .append(" | ").append(nullToEmpty(v.getName()))
                    .append(": ").append(nullToEmpty(v.getValue()))
                    .append(" ").append(nullToEmpty(v.getUnit()))
                    .append("\n");
        }
        return sb.toString();
    }

    /**
     * 异常指标分析：取每个指标最近一次测量，与正常范围比对。
     */
    private String formatAbnormalIndicators(Integer userId) {
        List<UserHealthVO> records = queryRecent(userId, ABNORMAL_LOOKBACK_DAYS, MAX_RECORDS);
        Map<String, UserHealthVO> latest = latestByIndicator(records);

        StringBuilder sb = new StringBuilder();
        sb.append("=== 异常指标分析 ===\n\n");

        if (latest.isEmpty()) {
            sb.append("暂无健康数据，无法分析异常指标。\n");
            return sb.toString();
        }

        List<String> abnormal = new ArrayList<>();
        List<String> normal = new ArrayList<>();
        List<String> unknown = new ArrayList<>();

        for (UserHealthVO v : latest.values()) {
            String name = nullToEmpty(v.getName());
            String value = v.getValue();
            String unit = nullToEmpty(v.getUnit());

            double[] bounds = parseValueRange(v.getValueRange());
            if (bounds == null || value == null) {
                unknown.add(name);
                continue;
            }
            double val;
            try {
                val = Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                unknown.add(name);
                continue;
            }

            if (val < bounds[0]) {
                abnormal.add(name + ": " + value + " " + unit + " (偏低，参考 " +
                        fmtNum(bounds[0]) + "~" + fmtNum(bounds[1]) + ")");
            } else if (val > bounds[1]) {
                abnormal.add(name + ": " + value + " " + unit + " (偏高，参考 " +
                        fmtNum(bounds[0]) + "~" + fmtNum(bounds[1]) + ")");
            } else {
                normal.add(name + " " + value + " " + unit);
            }
        }

        if (abnormal.isEmpty()) {
            sb.append("所有指标均在正常范围内。\n");
        } else {
            sb.append("发现 ").append(abnormal.size()).append(" 项异常指标:\n");
            for (String item : abnormal) {
                sb.append("- ").append(item).append("\n");
            }
            sb.append("\n请结合用户病史解读，并建议必要时咨询医生。\n");
        }

        if (!normal.isEmpty()) {
            sb.append("\n正常指标: ").append(String.join("、", normal)).append("\n");
        }
        if (!unknown.isEmpty()) {
            // 透明告知 AI 哪些指标没跑通判定，避免它误以为"没提就是正常"
            sb.append("\n（以下指标缺少正常范围配置或数值格式不可解析，未参与判定: ")
                    .append(String.join("、", unknown)).append("）\n");
        }

        return sb.toString();
    }

    // ==================== 工具方法 ====================

    /**
     * 🔴 核心兼容逻辑：{@code health_model_config.value_range} 有两种历史格式共存。
     *
     * <ul>
     *   <li>逗号式（id 1~5，早期）：{@code "90,140"}</li>
     *   <li>JSON 式（id 6~12，后期）：{@code "{"min":0,"max":11.1}"}</li>
     * </ul>
     *
     * <p>返回 {@code double[]{min, max}}；格式无法识别时返回 null（调用方应归入"未知"，
     * 而不是当成"正常"—— 后者会让 AI 给出错误的安全暗示）。
     */
    static double[] parseValueRange(String range) {
        if (range == null || range.trim().isEmpty()) {
            return null;
        }
        String s = range.trim();

        // 格式一：JSON {"min":0,"max":11.1}
        if (s.startsWith("{")) {
            try {
                JSONObject obj = JSON.parseObject(s);
                Double min = obj.getDouble("min");
                Double max = obj.getDouble("max");
                if (min != null && max != null) {
                    return new double[]{min, max};
                }
            } catch (Exception e) {
                log.debug("[GetHealthDataTool] value_range JSON 解析失败: {}", s);
                return null;
            }
            return null;
        }

        // 格式二：逗号式 "90,140" / "90，140"（兼容中文逗号）
        String[] parts = s.replace('，', ',').split(",");
        if (parts.length == 2) {
            try {
                double min = Double.parseDouble(parts[0].trim());
                double max = Double.parseDouble(parts[1].trim());
                if (min <= max) {
                    return new double[]{min, max};
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 把 JSON 字符串数组列（如 {@code ["高血压","糖尿病"]}）格式化为中文顿号串。
     * 兼容元素是对象的情况（取 name 字段），以及非 JSON 的裸串。
     */
    static String formatStringList(String json) {
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) {
            return "";
        }
        try {
            JSONArray arr = JSON.parseArray(json.trim());
            List<String> out = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                Object item = arr.get(i);
                if (item instanceof JSONObject obj) {
                    // 兼容 [{"name":"高血压"}] 形态
                    String n = obj.getString("name");
                    if (n == null) {
                        n = obj.getString("value");
                    }
                    if (n != null) {
                        out.add(n);
                    }
                } else if (item != null) {
                    String s = String.valueOf(item).trim();
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                }
            }
            return String.join("、", out);
        } catch (Exception e) {
            // 不是 JSON 数组，当成普通字符串原样返回
            return json.trim();
        }
    }

    /**
     * 生活习惯 JSON 对象 → 中文短句。
     * 实际存储的 key 是 smoking/drinking/exercise（并非实体注释写的 exerciseFrequency）。
     */
    static String formatLifestyle(String json) {
        if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) {
            return "";
        }
        try {
            JSONObject obj = JSON.parseObject(json.trim());
            StringBuilder sb = new StringBuilder();
            Boolean smoking = obj.getBoolean("smoking");
            if (smoking != null) {
                sb.append("吸烟").append(smoking ? "是" : "否").append("，");
            }
            String drinking = obj.getString("drinking");
            if (drinking != null && !drinking.trim().isEmpty()) {
                sb.append("饮酒").append(drinking).append("，");
            }
            String exercise = obj.getString("exercise");
            if (exercise != null && !exercise.trim().isEmpty()) {
                sb.append("运动").append(exercise).append("，");
            }
            String s = sb.toString();
            return s.isEmpty() ? "" : s.substring(0, s.length() - 1);
        } catch (Exception e) {
            return json.trim();
        }
    }

    /**
     * 按指标名分组，保留每组最新的一条。
     * 入参已由 SQL 按 create_time DESC 排序，故首次出现即最新。
     */
    private Map<String, UserHealthVO> latestByIndicator(List<UserHealthVO> records) {
        Map<String, UserHealthVO> latest = new LinkedHashMap<>();
        if (records == null) {
            return latest;
        }
        for (UserHealthVO v : records) {
            String name = v.getName();
            if (name != null && !name.isEmpty()) {
                latest.putIfAbsent(name, v);
            }
        }
        return latest;
    }

    private PatientProfile safeGetProfile(Integer userId) {
        try {
            return patientProfileMapper.getByUserId(userId);
        } catch (Exception e) {
            log.warn("[GetHealthDataTool] patient_profile 查询失败: userId={}, msg={}", userId, e.getMessage());
            return null;
        }
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.trim().isEmpty()) {
            sb.append(label).append(": ").append(value.trim()).append("\n");
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String fmtNum(double d) {
        return d == (long) d ? String.valueOf((long) d) : String.valueOf(d);
    }

    private String fmtTime(LocalDateTime t) {
        return t == null ? "未知时间" : t.toString().replace('T', ' ');
    }

    private String bmiComment(double bmi) {
        if (bmi < 18.5) {
            return "偏瘦";
        }
        if (bmi < 24) {
            return "正常";
        }
        if (bmi < 28) {
            return "超重";
        }
        return "肥胖";
    }
}
