package cn.kmbeast.service;

import cn.kmbeast.pojo.entity.AuditLog;
import java.util.List;

public interface AuditService {
    void log(Integer userId, String userName, String action, String resource,
             String resourceId, String description, String ipAddress, String userAgent,
             String requestMethod, String requestUrl, Integer status, String errorMsg);

    void logSuccess(Integer userId, String userName, String action, String resource,
                    String resourceId, String description, String ipAddress, String userAgent,
                    String requestMethod, String requestUrl);

    void logFailure(Integer userId, String userName, String action, String resource,
                    String resourceId, String description, String ipAddress, String userAgent,
                    String requestMethod, String requestUrl, String errorMsg);

    List<AuditLog> query(Integer userId, String action, String resource, Integer current, Integer size);

    Integer queryCount(Integer userId, String action, String resource);
}
