package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.core.nl2sql.DataAgentConfigHolder;
import cn.kmbeast.core.nl2sql.DataAgentQaRequest;
import cn.kmbeast.core.nl2sql.DataAgentQaResult;
import cn.kmbeast.core.nl2sql.DataAgentQaService;
import cn.kmbeast.core.nl2sql.StepSink;
import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.mapper.UserHealthMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据智能体工具（P1-2，2026-10-07）。
 *
 * <p><b>定位</b>：把「数据智能体」作为 OpenAI function calling 的 tool 暴露给对话 Agent
 * （用户端 ReAct / 医生端 ReAct 均可调用）。模型通过自然语言提问，本工具把请求交给
 * {@link DataAgentQaService#queryForTool} 跑 NL2SQL 流水线，再把结果裁剪后回传。</p>
 *
 * <h3>双轨访问控制（铁律，与 {@link QueryHealthRecordsTool} 同范式）</h3>
 * <ul>
 *   <li>身份<b>只</b>来自 {@link ToolContext}（JWT 透传），绝不使用 LLM 参数里的
 *       userId/patientId/doctorId 作为真实身份；</li>
 *   <li><b>用户端（roleId≠3）</b>：强制查<b>本人</b>数据，忽略 LLM 传入的 patientId，防越权；</li>
 *   <li><b>医生端（roleId==3）</b>：必须传 patientId，且先校验医生与患者有
 *       {@code appointment}/{@code visit_record} 关联，<b>未授权 fail-closed</b>。</li>
 * </ul>
 *
 * <p>🔴 <b>授权写在工具内</b>：这是项目统一的工具安全边界（见 {@code QueryHealthRecordsTool}）。
 * {@link DataAgentQaService#queryForTool} 只负责「跑流水线 + 写审计 + 不落会话库」，
 * 不再二次鉴权，避免重复调用授权 mapper。任何绕过工具直接调 service 的入口都应先做授权。</p>
 */
@Slf4j
@Component
public class DataQueryTool implements Tool {

    /** 问题最大长度（防超长 prompt 拖垮 NL2SQL）。 */
    private static final int MAX_QUESTION = 500;
    /** 时间窗默认 90 天。 */
    private static final int DEFAULT_DAYS = 90;
    /** 时间窗上限 10 年。 */
    private static final int MAX_DAYS = 3650;

    @Resource
    private DataAgentQaService dataAgentQaService;

    @Resource
    private UserHealthMapper userHealthMapper;

    @Resource
    private DataAgentConfigHolder configHolder;

    @Override
    public String getName() {
        return "query_health_data";
    }

    @Override
    public String getDescription() {
        return "用自然语言查询用户的健康数据（指标统计、趋势、分布、排名、对比等）。"
                + "例如：「最近 30 天我的平均收缩压是多少」「各月份体重变化趋势」「高血压人群占比」。"
                + "适用于需要汇总、统计、趋势、对比的分析型提问。返回真实数据与简要结论。"
                + "医生端查询患者数据时请务必传入 patientId。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> questionProp = new LinkedHashMap<>();
        questionProp.put("type", "string");
        questionProp.put("description", "自然语言查询问题，例如「最近30天我的平均收缩压」");
        questionProp.put("maxLength", MAX_QUESTION);
        properties.put("question", questionProp);

        Map<String, Object> patientProp = new LinkedHashMap<>();
        patientProp.put("type", "integer");
        patientProp.put("description", "患者ID（仅医生端查询他人时需要，用户端查询自己请忽略）");
        properties.put("patientId", patientProp);

        Map<String, Object> reportProp = new LinkedHashMap<>();
        reportProp.put("type", "boolean");
        reportProp.put("description", "是否在结果后附 Markdown 分析报告，默认 false");
        reportProp.put("default", false);
        properties.put("enableReport", reportProp);

        Map<String, Object> daysProp = new LinkedHashMap<>();
        daysProp.put("type", "integer");
        daysProp.put("description", "关注最近多少天的数据，默认90，最大3650");
        daysProp.put("default", DEFAULT_DAYS);
        daysProp.put("minimum", 1);
        daysProp.put("maximum", MAX_DAYS);
        properties.put("days", daysProp);

        Map<String, Object> sessionProp = new LinkedHashMap<>();
        sessionProp.put("type", "string");
        sessionProp.put("description", "会话ID（多轮追问用，可空）");
        properties.put("sessionId", sessionProp);

        schema.put("properties", properties);
        schema.put("required", List.of("question"));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (arguments == null) {
            return ToolResult.error("缺少参数");
        }
        String question = str(arguments.get("question"));
        if (question == null || question.trim().isEmpty()) {
            return ToolResult.error("缺少必填参数 question");
        }
        if (question.length() > MAX_QUESTION) {
            question = question.substring(0, MAX_QUESTION);
        }

        // 🔴 身份只来自 ToolContext（JWT 透传），绝不信任 LLM 参数
        Integer roleId = ToolContext.getRoleId();
        Integer callerId = ToolContext.getUserId();
        Integer doctorId = ToolContext.getDoctorId();
        if (callerId == null) {
            // fail-closed：无任何身份时连授权 mapper 都不碰
            return ToolResult.error("未登录，无法查询数据");
        }

        Integer patientId = toInt(arguments.get("patientId"));
        Long dataOwnerId;
        String audience;
        boolean doctorTrack = Integer.valueOf(3).equals(roleId);
        if (doctorTrack) {
            if (patientId == null) {
                return ToolResult.error("医生端查询患者数据必须指定 patientId");
            }
            int access = 0;
            try {
                access = userHealthMapper.countDoctorPatientAccess(doctorId, patientId);
            } catch (Exception e) {
                log.warn("[DataQueryTool] 授权校验失败: doctor={}, patient={}, {}",
                        doctorId, patientId, e.getMessage());
            }
            if (access <= 0) {
                // 🔴 fail-closed：无授权，不返回数据。审计由 queryForTool 写（无 http 时读 ToolContext）
                return ToolResult.error("无权访问该患者数据（无接诊/就诊记录）");
            }
            dataOwnerId = patientId.longValue();
            audience = DataAgentConfigHolder.AUDIENCE_DOCTOR;
        } else {
            // 🔴 用户端强制查本人，忽略 LLM 传入的 patientId
            if (patientId != null) {
                log.warn("[DataQueryTool] 用户端传入 patientId={} 已忽略，强制查本人 userId={}",
                        patientId, callerId);
            }
            dataOwnerId = callerId.longValue();
            audience = DataAgentConfigHolder.AUDIENCE_USER;
        }

        DataAgentQaRequest req = new DataAgentQaRequest();
        req.setQuestion(buildQuestion(question, arguments));
        req.setSessionId(str(arguments.get("sessionId")));
        req.setPatientId(patientId);
        req.setEnableReport(toBool(arguments.get("enableReport"), false));

        DataAgentQaResult result;
        try {
            // 工具已鉴权；service 仅跑流水线 + 写审计 + 不落会话库
            result = dataAgentQaService.queryForTool(req, dataOwnerId, audience, callerId, StepSink.NOOP);
        } catch (Exception e) {
            log.error("[DataQueryTool] 执行异常", e);
            return ToolResult.error("数据查询执行异常：" + e.getMessage());
        }

        if (!result.isSuccess()) {
            return ToolResult.error(result.getMessage());
        }
        return ToolResult.ok(render(result));
    }

    /** 把 days 作为自然语言时间窗提示拼进问题，给 NL2SQL 模型一个范围线索。 */
    private String buildQuestion(String question, Map<String, Object> arguments) {
        int days = clampDays(arguments.get("days"), DEFAULT_DAYS);
        if (days <= 0 || days == DEFAULT_DAYS) {
            return question;
        }
        return question + "（关注最近 " + days + " 天的数据）";
    }

    /** 裁剪结果到沙箱字节上限，并附元信息（行数/耗时/隔离）。 */
    private String render(DataAgentQaResult result) {
        String body = (result.getReport() != null && !result.getReport().isEmpty())
                ? result.getReport() : result.getAnswer();
        if (body == null) {
            body = "";
        }
        int maxBytes = configHolder != null ? configHolder.getSandboxMaxBytes() : 512 * 1024;
        // UTF-8 最坏 3 字节/字符，按字符数估算截断（与流水线字节预算同量级）
        int maxChars = Math.max(200, maxBytes / 3);
        boolean truncated = body.length() > maxChars;
        if (truncated) {
            body = body.substring(0, maxChars) + "\n…（结果过长已截断，请缩小查询范围）";
        }

        StringBuilder sb = new StringBuilder(body);
        sb.append("\n\n— 数据查询结果 —");
        sb.append("\n行数：").append(result.getRowCount());
        sb.append("\n耗时：").append(result.getElapsedMs()).append(" ms");
        if (result.isTenantApplied()) {
            sb.append("\n已按当前用户/患者数据隔离");
        }
        return sb.toString();
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

    static int clampDays(Object raw, int fallback) {
        Integer v = toInt(raw);
        if (v != null && v > 0) {
            return Math.max(1, Math.min(v, MAX_DAYS));
        }
        return fallback;
    }

    private static boolean toBool(Object raw, boolean fallback) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            return "true".equalsIgnoreCase(s.trim()) || "1".equals(s.trim());
        }
        return fallback;
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

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
