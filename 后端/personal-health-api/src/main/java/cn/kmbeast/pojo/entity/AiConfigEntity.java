package cn.kmbeast.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI配置实体类
 */
@Data
public class AiConfigEntity {
    private Integer id;
    private String configKey;
    private String configValue;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
