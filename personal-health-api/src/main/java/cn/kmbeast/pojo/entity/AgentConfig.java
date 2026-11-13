package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 智能体配置表
 */
@Data
public class AgentConfig {

    /**
     * ID
     */
    private Integer id;

    /**
     * 智能体唯一标识
     */
    private String agentKey;

    /**
     * 智能体名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 使用的模型
     */
    private String model;

    /**
     * 采样温度
     */
    private BigDecimal temperature;

    /**
     * 核采样阈值
     */
    private BigDecimal topP;

    /**
     * 单次回复最大 token 数
     */
    private Integer maxTokens;

    /**
     * 面向人群：all / user / admin
     */
    private String audience;

    /**
     * 最大工具调用轮次
     */
    private Integer maxIterations;

    /**
     * 路由关键词（命中则优先路由到该智能体）
     */
    private String routingKeywords;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 是否为该人群的默认兜底智能体
     */
    private Boolean isDefault;

    /**
     * 排序值
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
