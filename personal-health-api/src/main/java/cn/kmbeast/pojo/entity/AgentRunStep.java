package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能体运行步骤表（追加写入，只增不改不删）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentRunStep {

    /**
     * ID
     */
    private Long id;

    /**
     * 所属运行的唯一标识
     */
    private String runId;

    /**
     * 步骤序号
     */
    private Integer stepIndex;

    /**
     * 步骤类型：llm / tool / handoff
     */
    private String stepType;

    /**
     * 工具名
     */
    private String toolName;

    /**
     * 目标智能体标识
     */
    private String targetAgent;

    /**
     * 工具入参 JSON
     */
    private String argsJson;

    /**
     * 结果摘要
     */
    private String resultDigest;

    /**
     * 耗时（毫秒）
     */
    private Integer latencyMs;

    /**
     * 创建时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
