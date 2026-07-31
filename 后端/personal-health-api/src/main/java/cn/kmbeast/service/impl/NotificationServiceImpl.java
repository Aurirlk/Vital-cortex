package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.NotificationMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.Notification;
import cn.kmbeast.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

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
        return ApiResult.success();
    }

    @Override
    public Result<List<Notification>> getByUserId(Integer userId) {
        return ApiResult.success(notificationMapper.queryByUserId(userId));
    }

    @Override
    public Result<Void> markAsRead(Integer id) {
        notificationMapper.markAsRead(id);
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
