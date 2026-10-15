package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体-工具绑定表
 */
@Data
public class AgentToolBinding {

    /**
     * ID
     */
    private Integer id;

    /**
     * 智能体ID
     */
    private Integer agentId;

    /**
     * 工具名
     */
    private String toolName;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 排序值
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
