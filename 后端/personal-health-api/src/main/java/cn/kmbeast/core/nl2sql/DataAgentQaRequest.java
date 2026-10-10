package cn.kmbeast.core.nl2sql;

import lombok.Data;

/**
 * 数据问答请求。
 *
 * <p>注意：执行主体（用户 id / 患者 id）<b>不</b>由本请求决定，而是由
 * {@code DataAgentQaService} 依据 {@code LocalThreadHolder} 身份 + 授权校验后得出，
 * 防止请求体篡改越权。</p>
 */
@Data
public class DataAgentQaRequest {

    /** 自然语言问题。 */
    private String question;

    /** 会话 id（多轮用，可空）。 */
    private String sessionId;

    /** 目标患者 id —— <b>仅医生端</b>使用，且必须先通过接诊授权校验。 */
    private Integer patientId;

    /** 历史对话摘要（多轮上下文，可空）。 */
    private String multiTurn;

    /** 是否生成报告；null 时取运行时配置。 */
    private Boolean enableReport;

    public String normalizedQuestion() {
        return question == null ? "" : question.trim();
    }
}
