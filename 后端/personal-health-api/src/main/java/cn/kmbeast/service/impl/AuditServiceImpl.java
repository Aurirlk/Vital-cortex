package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.AuditLogMapper;
import cn.kmbeast.pojo.entity.AuditLog;
import cn.kmbeast.service.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class AuditServiceImpl implements AuditService {

    @Resource
    private AuditLogMapper auditLogMapper;

    @Override
    public void log(Integer userId, String userName, String action, String resource,
                    String resourceId, String description, String ipAddress, String userAgent,
                    String requestMethod, String requestUrl, Integer status, String errorMsg) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setUserName(userName);
            auditLog.setAction(action);
            auditLog.setResource(resource);
            auditLog.setResourceId(resourceId);
            auditLog.setDescription(description);
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLog.setRequestMethod(requestMethod);
            auditLog.setRequestUrl(requestUrl);
            auditLog.setStatus(status);
            auditLog.setErrorMsg(errorMsg);
            auditLog.setCreateTime(LocalDateTime.now());
            auditLogMapper.save(auditLog);
        } catch (Exception e) {
            log.error("记录审计日志失败", e);
        }
    }

    @Override
    public void logSuccess(Integer userId, String userName, String action, String resource,
                           String resourceId, String description, String ipAddress, String userAgent,
                           String requestMethod, String requestUrl) {
        log(userId, userName, action, resource, resourceId, description,
            ipAddress, userAgent, requestMethod, requestUrl, 1, null);
    }

    @Override
    public void logFailure(Integer userId, String userName, String action, String resource,
                           String resourceId, String description, String ipAddress, String userAgent,
                           String requestMethod, String requestUrl, String errorMsg) {
        log(userId, userName, action, resource, resourceId, description,
            ipAddress, userAgent, requestMethod, requestUrl, 0, errorMsg);
    }

    @Override
    public List<AuditLog> query(Integer userId, String action, String resource, Integer current, Integer size) {
        return auditLogMapper.query(userId, action, resource, current, size);
    }

    @Override
    public Integer queryCount(Integer userId, String action, String resource) {
        return auditLogMapper.queryCount(userId, action, resource);
    }
}
