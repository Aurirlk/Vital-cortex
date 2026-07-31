package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 安全审计日志实体
 */
@Data
public class AuditLog {
    /** 主键ID */
    private Long id;
    /** 操作用户ID */
    private Integer userId;
    /** 操作用户名 */
    private String userName;
    /** 操作类型(login/create/update/delete/export) */
    private String action;
    /** 资源类型(user/ai_config/drug/news等) */
    private String resource;
    /** 资源ID */
    private String resourceId;
    /** 操作描述 */
    private String description;
    /** IP地址 */
    private String ipAddress;
    /** User-Agent */
    private String userAgent;
    /** 请求方法 */
    private String requestMethod;
    /** 请求URL */
    private String requestUrl;
    /** 状态(0:失败;1:成功) */
    private Integer status;
    /** 错误信息 */
    private String errorMsg;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
