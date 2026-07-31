package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.Notification;
import java.util.List;

public interface NotificationService {
    Result<Void> save(Notification notification);
    Result<List<Notification>> getByUserId(Integer userId);
    Result<Void> markAsRead(Integer id);
    Result<Void> markAllAsRead(Integer userId);
    Result<Integer> countUnread(Integer userId);
}
