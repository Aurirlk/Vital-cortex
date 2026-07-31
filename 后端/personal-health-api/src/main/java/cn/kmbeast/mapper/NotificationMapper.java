package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface NotificationMapper {
    void save(Notification notification);
    void markAsRead(@Param("id") Integer id);
    void markAllAsRead(@Param("userId") Integer userId);
    List<Notification> queryByUserId(@Param("userId") Integer userId);
    Integer countUnread(@Param("userId") Integer userId);
}
