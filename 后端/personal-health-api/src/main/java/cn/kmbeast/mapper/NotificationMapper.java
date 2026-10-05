package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface NotificationMapper {
    void save(Notification notification);
    void markAsRead(@Param("id") Integer id);
    void markAllAsRead(@Param("userId") Integer userId);
    List<Notification> queryByUserId(@Param("userId") Integer userId);
    Integer countUnread(@Param("userId") Integer userId);

    // ==================== 消息中心能力（2026-10-04 合并 message 后新增） ====================
    // 承接原 message 表的职责：分页查询 / 批量删除 / 按类型分组统计。
    // API 路径 /message/* 保持不变，由 MessageServiceImpl 转调本 Mapper。

    /** 批量插入（系统通知要推给全站用户，逐条 insert 太慢） */
    int batchSave(@Param("list") List<Notification> list);

    /**
     * 消息中心分页查询
     *
     * @param userId      接收人
     * @param messageType 用户视角分类，null 表示全部
     * @param isRead      已读状态，null 表示全部
     * @param offset      偏移量（由 @Pager 注解从页码换算）
     * @param size        每页条数
     */
    List<Notification> queryMessages(@Param("userId") Integer userId,
                                     @Param("messageType") Integer messageType,
                                     @Param("isRead") Integer isRead,
                                     @Param("offset") Integer offset,
                                     @Param("size") Integer size);

    /** 消息总数（配合 queryMessages 做分页） */
    Integer countMessages(@Param("userId") Integer userId,
                          @Param("messageType") Integer messageType,
                          @Param("isRead") Integer isRead);

    /** 批量删除（仅限本人消息） */
    int batchDelete(@Param("ids") List<Long> ids, @Param("userId") Integer userId);

    /** 消息类型字典（供前端 /message/types 下拉框） */
    List<Map<String, Object>> queryMessageTypes(@Param("userId") Integer userId);
}
