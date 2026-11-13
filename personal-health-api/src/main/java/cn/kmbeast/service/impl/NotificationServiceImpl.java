package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.NotificationMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.Notification;
import cn.kmbeast.service.NotificationService;
import cn.kmbeast.websocket.WebSocketServer;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    @Resource
    private NotificationMapper notificationMapper;

    @Override
    public Result<Void> save(Notification notification) {
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());
        notificationMapper.save(notification);
        // D-004 整改：入库后经 WebSocket 实时推送给目标用户。
        // 消息体包裹 type=notification，与前端 NotificationBell 监听器对齐；
        // createTime 格式化为与 REST 接口一致的字符串，避免前端 new Date() 解析异常。
        Integer userId = notification.getUserId();
        if (userId != null) {
            Map<String, Object> data = new HashMap<>(8);
            data.put("id", notification.getId());
            data.put("userId", userId);
            data.put("title", notification.getTitle());
            data.put("content", notification.getContent());
            data.put("type", notification.getType());
            data.put("isRead", notification.getIsRead());
            // 2026-10-12：改推 bizId。原 relatedId 对应的列不存在，恒为 null，
            // 前端全站零引用该键，故此处更名不影响任何消费方。
            data.put("bizId", notification.getBizId());
            data.put("createTime", notification.getCreateTime() != null
                    ? notification.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : null);
            Map<String, Object> payload = new HashMap<>(2);
            payload.put("type", "notification");
            payload.put("data", data);
            WebSocketServer.sendToUser(String.valueOf(userId), JSON.toJSONString(payload));
        }
        return ApiResult.success();
    }

    @Override
    public Result<List<Notification>> getByUserId(Integer userId) {
        return ApiResult.success(notificationMapper.queryByUserId(userId));
    }

    @Override
    public Result<Void> markAsRead(Integer id) {
        // 2026-10-10 修复（IDOR）：原先只按 id 置已读，任何登录用户传一个 id
        // 就能把他人通知标记为已读。现限定只能操作收件人本人的通知。
        Integer userId = LocalThreadHolder.getUserId();
        if (userId == null) {
            return ApiResult.error("未登录");
        }
        int affected = notificationMapper.markAsRead(id, userId);
        if (affected == 0) {
            log.warn("标记通知已读失败或无权限: notificationId={}, operatorId={}", id, userId);
            return ApiResult.error("通知不存在或无权操作");
        }
        return ApiResult.success();
    }

    @Override
    public Result<Void> markAllAsRead(Integer userId) {
        notificationMapper.markAllAsRead(userId);
        return ApiResult.success();
    }

    @Override
    public Result<Integer> countUnread(Integer userId) {
        return ApiResult.success(notificationMapper.countUnread(userId));
    }
}
