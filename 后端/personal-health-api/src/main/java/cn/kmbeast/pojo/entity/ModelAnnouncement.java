package cn.kmbeast.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型公告/横幅通知实体类
 */
@Data
public class ModelAnnouncement {
    /** 主键ID */
    private Integer id;
    
    /** 模型标识（如 zhikangyun-local, deepseek 等） */
    private String modelKey;
    
    /** 模型展示名称 */
    private String modelName;
    
    /** 横幅标题（如 "本草大模型已上线"） */
    private String title;
    
    /** 横幅描述文字 */
    private String content;
    
    /** 横幅背景色 */
    private String bgColor;
    
    /** 图标名 */
    private String icon;
    
    /** 是否上线 0=下线 1=上线 */
    private Integer isOnline;
    
    /** 是否当前展示 0=否 1=是 */
    private Integer isActive;
    
    /** 排序 */
    private Integer sortOrder;
    
    /** 创建时间 */
    private LocalDateTime createTime;
    
    /** 更新时间 */
    private LocalDateTime updateTime;
}
