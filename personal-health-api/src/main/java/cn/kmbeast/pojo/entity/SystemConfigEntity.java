package cn.kmbeast.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置实体类
 * 存储MySQL、OTA、WebSocket、SQLite等系统级配置
 */
@Data
public class SystemConfigEntity {
    private Integer id;
    
    /**
     * 配置分组：mysql, server, websocket, sqlite, ota等
     */
    private String configGroup;
    
    /**
     * 配置键名
     */
    private String configKey;
    
    /**
     * 配置值
     */
    private String configValue;
    
    /**
     * 配置描述
     */
    private String description;
    
    /**
     * 是否敏感配置（如密码），查看时需要密码验证
     */
    private Boolean sensitive;
    
    /**
     * 配置值类型：string, number, boolean, json
     */
    private String valueType;
    
    /**
     * 默认值
     */
    private String defaultValue;
    
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
