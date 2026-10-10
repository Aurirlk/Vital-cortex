package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.mapper.AuditLogMapper;
import cn.kmbeast.mapper.UserHealthMapper;
import cn.kmbeast.pojo.entity.AuditLog;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 健康记录参数化查询工具（阶段 2，2026-10-07）。
 *
 * <p><b>定位</b>：把「查一下我最近的血压」「我的空腹血糖正常吗」这类<b>指标型</b>提问
 * 落到真实 MySQL 数据上，而不是靠模型编造数字。
 *
 * <h3>与 DataAgent 的关系</h3>
 * <p>对应迁移方案"第 1 层：参数化查询"。DataAgent 的 NL2SQL 让 LLM 直接产出 SQL，
 * 本项目<b>刻意不做</b> —— 指标名走 {@link HealthMetric} 硬编码白名单、时间窗与条数
 * 由代码钳制、全部 MyBatis 参数化。LLM <b>不产出任何 SQL 片段</b>，从源头消除注入面。
 *
 * <h3>双轨访问控制（铁律）</h3>
 * <ul>
 *   <li><b>用户端（roleId != 3）</b>：强制查<b>本人</b>数据，忽略 LLM 传入的 patientId，
 *       杜绝"传个 patientId 偷看别人"。</li>
 *   <li><b>医生端（roleId == 3）</b>：必须传 patientId，且先校验医生与患者有
 *       {@code appointment}/{@code visit_record} 关联，<b>未授权则 fail-closed</b>
 *       （不返回数据、记一条 status=0 审计），并写正常查询审计。</li>
 * </ul>
 *
 * <p>🔴 身份<b>只</b>来自 {@link ToolContext}（JWT 透传），绝不使用 LLM 参数里的
 * userId/patientId/doctorId 作为真实身份。
 */
@Slf4j
@Component
public class QueryHealthRecordsTool implements Tool {

    /** 时间窗上限：10 年，防止 days 传极大值拖垮查询 */
    private static final int MAX_DAYS = 3650;
    /** 时间窗默认 90 天 */
    private static final int DEFAULT_DAYS = 90;
    /** 返回条数上限 */
    private static final int MAX_LIMIT = 500;
    /** 返回条数默认 100 */
    private static final int DEFAULT_LIMIT = 100;

    @Resource
    private UserHealthMapper userHealthMapper;

    @Resource
    private AuditLogMapper auditLogMapper;

    @Override
    public String getName() {
        return "query_health_records";
    }

    @Override
    public String getDescription() {
        return "查询用户健康指标记录（如血压、血糖、心率、体重、胆固醇等）。" +
                "适用于「查一下我最近的血压」「我的空腹血糖正常吗」「最近的心率数据」这类指标型提问。" +
                "返回真实测量值、单位、正常范围及统计（均值/最小值/最大值/最新值）。" +
                "参数 metric 支持口语说法（如「血压」「血糖」「胆固醇」）。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> metricProp = new LinkedHashMap<>();
        metricProp.put("type", "string");
        metricProp.put("description",
                "指标名称，支持口语说法。可选：血压(收缩压/舒张压)、血糖(空腹/餐后)、心率、" +
                        "体重指数(BMI)、体重、身高、胆固醇/血脂(总胆固醇/甘油三酯/高密度/低密度脂蛋白)");
        properties.put("metric", metricProp);

        Map<String, Object> patientProp = new LinkedHashMap<>();
        patientProp.put("type", "integer");
        patientProp.put("description", "患者ID（仅医生端查询他人时需要，用户端查询自己请忽略）");
        properties.put("patientId", patientProp);

        Map<String, Object> daysProp = new LinkedHashMap<>();
        daysProp.put("type", "integer");
        daysProp.put("description", "查询最近多少天的数据，默认90，最大3650");
        daysProp.put("default", DEFAULT_DAYS);
        // 2026-10-07：补结构化上下界（原先只写在 description 里，模型无法机器感知）
        daysProp.put("minimum", 1);
        daysProp.put("maximum", 3650);
        properties.put("days", daysProp);

        Map<String, Object> limitProp = new LinkedHashMap<>();
        limitProp.put("type", "integer");
        limitProp.put("description", "返回最大条数，默认100，最大500");
        limitProp.put("default", DEFAULT_LIMIT);
        limitProp.put("minimum", 1);
        limitProp.put("maximum", 500);
        properties.put("limit", limitProp);

        schema.put("properties", properties);
        schema.put("required", List.of("metric"));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (arguments == null) {
            return ToolResult.error("缺少参数");
        }
        String metricPhrase = str(arguments.get("metric"));
        if (metricPhrase == null || metricPhrase.trim().isEmpty()) {
            return ToolResult.error("缺少必填参数 metric");
        }
        Set<HealthMetric> metrics = HealthMetric.resolve(metricPhrase.trim());
        if (metrics.isEmpty()) {
            return ToolResult.ok("未识别到指标「" + metricPhrase.trim() + "」。目前支持查询：" +
                    String.join("、", HealthMetric.allDbNames()) + "（及其口语说法）。");
        }

        int days = clampDays(arguments.get("days"));
        int limit = clampLimit(arguments.get("limit"));
        Integer argPatientId = toInt(arguments.get("patientId"));

        Integer roleId = ToolContext.getRoleId();
        boolean doctorTrack = roleId != null && roleId == 3;

        try {
            if (doctorTrack) {
                return doctorQuery(metrics, days, limit, argPatientId);
            }
            return userQuery(metrics, days, limit, argPatientId);
        } catch (Exception e) {
            // 降级：查询异常不应让 ReAct 空转重试（数据问题重试无意义）
            log.warn("[QueryHealthRecordsTool] 查询异常，降级: metric={}, msg={}",
                    metricPhrase, e.getMessage(), e);
            return ToolResult.ok("健康记录查询暂时不可用，请稍后再试或基于医学常识回答。");
        }
    }

    // ==================== 用户端（单轨） ====================

    private ToolResult userQuery(Set<HealthMetric> metrics, int days, int limit, Integer argPatientId) {
        Integer userId = ToolContext.getUserId();
        if (userId == null) {
            return ToolResult.ok("未获取到用户身份，无法查询健康记录。");
        }
        if (argPatientId != null) {
            // 🔴 用户端传入 patientId 一律忽略，强制查本人，防越权
            log.warn("[QueryHealthRecordsTool] 用户端传入 patientId={} 已忽略，强制查本人 userId={}",
                    argPatientId, userId);
        }

        StringBuilder sb = new StringBuilder();
        int totalMetrics = metrics.size();
        for (HealthMetric m : metrics) {
            if (totalMetrics > 1) {
                sb.append("\n【").append(m.dbName()).append("】\n");
            }
            sb.append(queryAndRender(userId, m, days, limit));
        }
        return ToolResult.ok(sb.toString().trim());
    }

    // ==================== 医生端（双轨） ====================

    private ToolResult doctorQuery(Set<HealthMetric> metrics, int days, int limit, Integer argPatientId) {
        Integer doctorId = ToolContext.getDoctorId();
        if (doctorId == null || argPatientId == null) {
            return ToolResult.ok("医生查询患者健康记录需同时具备医生身份与患者ID（patientId）。");
        }

        int access = 0;
        try {
            access = userHealthMapper.countDoctorPatientAccess(doctorId, argPatientId);
        } catch (Exception e) {
            log.warn("[QueryHealthRecordsTool] 授权校验失败: doctor={}, patient={}, {}",
                    doctorId, argPatientId, e.getMessage());
        }

        if (access <= 0) {
            // 🔴 fail-closed：无授权，不返回任何数据，记审计
            writeAudit(doctorId, argPatientId, metrics, false,
                    "医生查询患者健康记录：无接诊/就诊授权");
            return ToolResult.ok("无权访问该患者（patientId=" + argPatientId +
                    "）的健康数据：当前医生与该患者无预约或就诊记录。");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("（医生视图：患者 patientId=").append(argPatientId).append("，近 ")
                .append(days).append(" 天）\n");
        for (HealthMetric m : metrics) {
            sb.append("\n【").append(m.dbName()).append("】\n");
            sb.append(queryAndRender(argPatientId, m, days, limit));
        }
        String description = "医生查询患者健康记录：" +
                String.join("/", metrics.stream().map(HealthMetric::dbName).toList());
        writeAudit(doctorId, argPatientId, metrics, true, description);
        return ToolResult.ok(sb.toString().trim());
    }

    /**
     * 按指标查询并生成「记录列表 + 统计」文本。
     */
    private String queryAndRender(Integer userId, HealthMetric metric, int days, int limit) {
        List<Map<String, Object>> rows = userHealthMapper.queryByMetric(
                userId, metric.dbName(), days, limit);
        if (rows == null || rows.isEmpty()) {
            return "近 " + days + " 天无「" + metric.dbName() + "」测量记录。\n";
        }

        BigDecimal[] range = parseRange(first(rows.get(0), "valueRange"));
        List<BigDecimal> values = new ArrayList<>();
        StringBuilder list = new StringBuilder();
        int shown = 0;
        for (Map<String, Object> row : rows) {
            String value = str(row.get("value"));
            String createTime = str(row.get("createTime"));
            String unit = str(row.get("unit"));
            if (shown < 30) {
                list.append("- ").append(createTime).append("：").append(value)
                        .append(unit == null ? "" : " " + unit).append("\n");
                shown++;
            }
            BigDecimal v = toDecimal(value);
            if (v != null) {
                values.add(v);
            }
        }

        StringBuilder out = new StringBuilder();
        out.append(list);
        if (rows.size() > shown) {
            out.append("（共 ").append(rows.size()).append(" 条，显示最近 ").append(shown).append(" 条）\n");
        }

        if (!values.isEmpty()) {
            BigDecimal latest = values.get(0); // 已按时间倒序
            BigDecimal avg = average(values);
            BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(latest);
            BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(latest);
            out.append("统计：最新 ").append(latest)
                    .append("，均值 ").append(avg)
                    .append("，最小 ").append(min)
                    .append("，最大 ").append(max).append("\n");
            if (range != null) {
                boolean low = latest.compareTo(range[0]) < 0;
                boolean high = latest.compareTo(range[1]) > 0;
                if (low || high) {
                    out.append("⚠️ 最新值").append(low ? "偏低" : "偏高")
                            .append("（正常范围 ").append(range[0]).append("~").append(range[1])
                            .append("），建议结合临床判断。\n");
                } else {
                    out.append("✅ 最新值在正常范围内（").append(range[0]).append("~")
                            .append(range[1]).append("）。\n");
                }
            }
        }
        return out.toString();
    }

    // ==================== 审计 ====================

    private void writeAudit(Integer doctorId, Integer patientId, Set<HealthMetric> metrics,
                            boolean granted, String description) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(doctorId);
            log.setUserName("医生#" + doctorId);
            log.setAction("QUERY_PATIENT_HEALTH");
            log.setResource("user_health");
            log.setResourceId(String.valueOf(patientId));
            log.setDescription(description + "（授权=" + (granted ? "通过" : "拒绝") + "）");
            log.setStatus(granted ? 1 : 0);
            log.setCreateTime(LocalDateTime.now());
            auditLogMapper.save(log);
        } catch (Exception e) {
            log.warn("[QueryHealthRecordsTool] 审计写入失败（不影响主流程）: {}", e.getMessage());
        }
    }

    // ==================== 参数钳制 / 解析 ====================

    static int clampDays(Object raw) {
        int days = DEFAULT_DAYS;
        Integer v = toInt(raw);
        if (v != null && v > 0) {
            days = v;
        }
        return Math.max(1, Math.min(days, MAX_DAYS));
    }

    static int clampLimit(Object raw) {
        int limit = DEFAULT_LIMIT;
        Integer v = toInt(raw);
        if (v != null && v > 0) {
            limit = v;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    /** 解析正常范围：支持 "90,140" 与 JSON {"min":x,"max":y} 两种形态 */
    static BigDecimal[] parseRange(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String s = raw.trim();
        try {
            if (s.startsWith("{")) {
                Map<String, Object> json = JSON.parseObject(s, Map.class);
                BigDecimal min = toDecimal(String.valueOf(json.get("min")));
                BigDecimal max = toDecimal(String.valueOf(json.get("max")));
                if (min != null && max != null) {
                    return new BigDecimal[]{min, max};
                }
                return null;
            }
            // "90,140"
            String[] parts = s.split(",");
            if (parts.length != 2) {
                return null;
            }
            BigDecimal min = toDecimal(parts[0].trim());
            BigDecimal max = toDecimal(parts[1].trim());
            if (min != null && max != null) {
                return new BigDecimal[]{min, max};
            }
        } catch (Exception e) {
            log.debug("[QueryHealthRecordsTool] 正常范围解析失败: {}", raw);
        }
        return null;
    }

    private static BigDecimal average(List<BigDecimal> values) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            sum = sum.add(v);
        }
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal toDecimal(String s) {
        if (s == null) {
            return null;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    @SuppressWarnings("unchecked")
    private static String first(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
