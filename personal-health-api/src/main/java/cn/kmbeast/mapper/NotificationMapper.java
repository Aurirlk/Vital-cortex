package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface NotificationMapper {
    void save(Notification notification);

    /**
     * 将单条通知标记为已读。
     *
     * <p>2026-10-10 修复（IDOR）：增加 {@code userId} 归属参数并由 SQL 强制过滤，
     * 只能操作收件人本人的通知，返回受影响行数供调用方判断是否越权。
     *
     * @param id     通知 ID
     * @param userId 当前登录用户 ID
     * @return 实际更新行数
     */
    int markAsRead(@Param("id") Integer id, @Param("userId") Integer userId);

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

    // ==================== 幂等判定（2026-10-10） ====================

    /**
     * 统计某条业务通知是否已经发给过某个用户
     *
     * <p><b>为什么需要它</b>：定时任务（就诊前一天提醒）没有天然的「只执行一次」
     * 保证 —— 服务重启、多实例部署、任务被手动触发，都会让同一条提醒重复落库。
     * 发通知不像下单，重复一次不会有人报错，只会在消息中心堆出一串一样的行，
     * 用户只会觉得这系统坏了。因此发送前先查一次重。
     *
     * <p><b>为什么去重键里必须有 title</b>：{@code (user_id, biz_type, biz_id,
     * message_type)} 四元组<b>不足以</b>唯一标识一条通知 —— 同一个业务实体本来就会
     * 合法地产生多条通知：
     * <ul>
     *   <li>一笔订单：已创建 → 已支付 → 已发货 → 已完成，四条同一个
     *       {@code (MALL, orderId, 6)}；</li>
     *   <li>一次预约：预约成功 与 就诊前一天提醒，同一个
     *       {@code (APPOINTMENT, appointmentId, 5)}。</li>
     * </ul>
     * 只按四元组去重会把后面几条全判成重复而丢掉。加上 title
     * （发送方代码里的固定文案）才能把「同一件事重复发」与
     * 「同一实体不同的事」区分开。代价是改文案会重置去重基准 ——
     * 可接受，且已在此说明。
     *
     * <p>不按 {@code create_time} 圈时间窗：同一条预约的同一个提醒，
     * <b>有史以来只该存在一条</b>，圈了时间窗反而会在跨天重启时漏判。
     *
     * @param userId      收件人
     * @param bizType     业务类型（{@code NotificationSource#getBizType()}）
     * @param bizId       业务ID（预约ID / 订单ID）的字符串形式
     * @param messageType 用户视角分类（{@code NotificationSource#messageTypeValue()}）
     * @param title       通知标题（文案常量）
     * @return 已存在的条数；&gt;0 表示已发过，跳过
     */
    int countBizNotification(@Param("userId") Integer userId,
                             @Param("bizType") String bizType,
                             @Param("bizId") String bizId,
                             @Param("messageType") Integer messageType,
                             @Param("title") String title);
}
