package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能体运行记录表（追加写入，行内状态原地更新）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentRun {

    /**
     * ID
     */
    private Long id;

    /**
     * 本次运行的唯一标识
     */
    private String runId;

    /**
     * 父运行标识（子智能体调用时非空）
     */
    private String parentRunId;

    /**
     * 智能体ID
     */
    private Integer agentId;

    /**
     * 智能体标识
     */
    private String agentKey;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 面向人群
     */
    private String audience;

    /**
     * 用户问题
     */
    private String question;

    /**
     * 运行状态：RUNNING / SUCCESS / FAILED
     */
    private String status;

    /**
     * 已执行轮次
     */
    private Integer iterations;

    /**
     * 提示词 token 数
     */
    private Integer promptTokens;

    /**
     * 补全 token 数
     */
    private Integer completionTokens;

    /**
     * 耗时（毫秒）
     */
    private Integer latencyMs;

    /**
     * 回答摘要
     */
    private String answerDigest;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 创建时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
