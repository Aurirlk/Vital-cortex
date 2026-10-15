package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体转交关系表
 */
@Data
public class AgentHandoff {

    /**
     * ID
     */
    private Integer id;

    /**
     * 源智能体ID
     */
    private Integer fromAgentId;

    /**
     * 目标智能体ID
     */
    private Integer toAgentId;

    /**
     * 触发转交的条件描述
     */
    private String conditionDesc;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 创建时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
