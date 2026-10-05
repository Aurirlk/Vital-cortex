package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.NotificationMapper;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.MessageQueryDto;
import cn.kmbeast.pojo.dto.query.extend.UserQueryDto;
import cn.kmbeast.pojo.em.IsReadEnum;
import cn.kmbeast.pojo.em.MessageType;
import cn.kmbeast.pojo.em.RoleEnum;
import cn.kmbeast.pojo.entity.Message;
import cn.kmbeast.pojo.entity.Notification;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.pojo.vo.MessageTypeVO;
import cn.kmbeast.pojo.vo.MessageVO;
import cn.kmbeast.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 消息业务逻辑实现（2026-10-04 合并 message → notification 后重写）
 *
 * <p><b>变更原因</b>：{@code message} 与 {@code notification} 是同一业务概念的两套
 * 实现（消息系统双轨）。现统一到 {@code notification} 表。
 *
 * <p><b>API 契约完全不变</b>：{@code MessageService} 方法签名与
 * {@code /message/*} 路径均未改动，前端（MessageManage.vue、LevelMenu.vue 等）零改动。
 *
 * <p><b>字段映射</b>：
 * <pre>
 *   Message.receiverId → Notification.userId      （接收人）
 *   Message.senderId   → Notification.senderId    （发送人）
 *   Message.messageType→ Notification.messageType （用户视角分类）
 *   Message.contentId  → Notification.relatedId
 * </pre>
 */
@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    @Resource
    private NotificationMapper notificationMapper;

    @Resource
    private UserMapper userMapper;

    // ================================================================ 写入

    @Override
    public Result<Void> save(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return ApiResult.success();
        }
        List<Notification> list = new ArrayList<>(messages.size());
        for (Message m : messages) {
            list.add(toNotification(m));
        }
        notificationMapper.batchSave(list);
        return ApiResult.success();
    }

    /** 评论被别人回复了 → 给被回复者发通知 */
    @Override
    public Result<Void> evaluationsReplySave(Message message) {
        message.setMessageType(MessageType.EVALUATIONS_BY_REPLY.getType());
        message.setContent("你的评论被回复了");
        message.setIsRead(false);   // Boolean 语义：false=未读
        message.setCreateTime(LocalDateTime.now());
        return save(new ArrayList<>(List.of(message)));
    }

    /** 评论被点赞 → 给评论所属者发通知 */
    @Override
    public Result<Void> evaluationsUpvoteSave(Message message) {
        message.setMessageType(MessageType.EVALUATIONS_BY_UPVOTE.getType());
        message.setContent("你的评论被点赞了");
        message.setIsRead(false);   // Boolean 语义：false=未读
        message.setCreateTime(LocalDateTime.now());
        return save(new ArrayList<>(List.of(message)));
    }

    @Override
    public Result<Void> systemInfoSave(List<Message> messages) {
        return save(messages);
    }

    @Override
    public Result<Void> dataWordSave(List<Message> messages) {
        return save(messages);
    }

    /**
     * 全站系统通知：给所有普通用户各推一条。
     *
     * <p>只推给普通用户（role=2），不推管理员 —— 保留原逻辑。
     */
    @Override
    public Result<Void> systemInfoUsersSave(Message message) {
        String content = message.getContent();
        if (content == null || content.trim().isEmpty()) {
            return ApiResult.error("通知内容不能为空");
        }

        List<User> userList = userMapper.query(new UserQueryDto());
        if (userList == null || userList.isEmpty()) {
            return ApiResult.success();
        }

        List<Notification> list = new ArrayList<>(userList.size());
        LocalDateTime now = LocalDateTime.now();
        for (User user : userList) {
            if (!Objects.equals(RoleEnum.USER.getRole(), user.getUserRole())) {
                continue;
            }
            Notification n = new Notification();
            n.setUserId(user.getId());
            n.setContent(content);
            n.setMessageType(MessageType.SYSTEM_INFO.getType());
            n.setType(0);                              // 业务分类归为「公告」
            n.setIsRead(0);   // 0=未读
            n.setCreateTime(now);
            list.add(n);
        }
        if (list.isEmpty()) {
            return ApiResult.success();
        }
        notificationMapper.batchSave(list);
        return ApiResult.success();
    }

    // ================================================================ 读取

    /**
     * 消息分页查询
     *
     * <p>接收人以 token 为准，不采纳 DTO 里的 userId ——
     * 否则可查他人消息。管理员查全站时才用 DTO 传入的 userId。
     */
    @Override
    public Result<List<MessageVO>> query(MessageQueryDto dto) {
        Integer currentUserId = LocalThreadHolder.getUserId();
        if (currentUserId == null) {
            return PageResult.success(new ArrayList<>(), 0);
        }
        if (dto == null) {
            dto = new MessageQueryDto();
        }

        // 管理员可指定 userId 查指定用户；普通用户只能查自己
        Integer userId = currentUserId;
        if (dto.getUserId() != null && !dto.getUserId().equals(currentUserId)) {
            boolean isAdmin = Integer.valueOf(RoleEnum.ADMIN.getRole())
                    .equals(LocalThreadHolder.getRoleId());
            if (!isAdmin) {
                return ApiResult.error("无权查询他人消息");
            }
            userId = dto.getUserId();
        }

        Integer isRead = dto.getIsRead() == null ? null : (dto.getIsRead() ? 1 : 0);
        // QueryDto.current 是偏移量（由 @Pager 注解换算），不是页码
        Integer offset = dto.getCurrent() != null && dto.getCurrent() > 0 ? dto.getCurrent() : 0;
        Integer size = dto.getSize() != null && dto.getSize() > 0 ? dto.getSize() : 10;

        List<Notification> rows = notificationMapper.queryMessages(
                userId, dto.getMessageType(), isRead, offset, size);
        Integer total = notificationMapper.countMessages(userId, dto.getMessageType(), isRead);

        return PageResult.success(toMessageVOs(rows), total);
    }

    /**
     * 消息类型字典
     *
     * <p>返回前端下拉框需要的 [{type, detail}]，detail 取自 {@link MessageType} 枚举。
     *
     * <p>注：本方法不在 {@code MessageService} 接口里 —— {@code /message/types}
     * 由 {@code MessageController} 直接提供，不经 Service 层。
     * 保留为 public 便于 Controller 复用。
     */
    public List<MessageTypeVO> all() {
        List<MessageTypeVO> list = new ArrayList<>();
        for (MessageType mt : MessageType.values()) {
            // MessageTypeVO 只有全参构造（@AllArgsConstructor）
            list.add(new MessageTypeVO(mt.getType(), mt.getDetail()));
        }
        return list;
    }

    // ================================================================ 更新与删除

    /**
     * 批量删除
     *
     * <p>只允许删自己的消息（SQL 带 user_id 条件）。
     */
    @Override
    public Result<Void> batchDelete(List<Long> ids) {
        Integer userId = LocalThreadHolder.getUserId();
        if (userId == null) {
            return ApiResult.error("未登录");
        }
        if (ids == null || ids.isEmpty()) {
            return ApiResult.success();
        }
        notificationMapper.batchDelete(ids, userId);
        return ApiResult.success();
    }

    /** 全部标为已读 */
    @Override
    public Result<Void> clearMessage() {
        Integer userId = LocalThreadHolder.getUserId();
        if (userId == null) {
            return ApiResult.error("未登录");
        }
        notificationMapper.markAllAsRead(userId);
        return ApiResult.success();
    }

    // ================================================================ 内部工具

    /** Message → Notification */
    private Notification toNotification(Message m) {
        Notification n = new Notification();
        n.setUserId(m.getReceiverId());
        n.setSenderId(m.getSenderId());
        n.setContent(m.getContent());
        n.setMessageType(m.getMessageType());
        n.setType(0);                                  // 业务分类：原 message 无此语义，归公告
        // Message.isRead 是 Boolean，而 notification.is_read 是 tinyint(0/1)
        n.setIsRead(toDbIsRead(m.getIsRead()));
        n.setRelatedId(m.getContentId() == null ? null : String.valueOf(m.getContentId()));
        n.setCreateTime(m.getCreateTime() == null ? LocalDateTime.now() : m.getCreateTime());
        return n;
    }

    /**
     * Boolean → tinyint
     *
     * <p>注意：{@code IsReadEnum.READ_OK.getStatus()} 返回的是 {@code Boolean.TRUE}，
     * 不是 1。直接塞进 tinyint 列会存成 1（MySQL 隐式转换），
     * 但反向读取时 Java 侧拿到的是 Integer 1，需显式转 Boolean，故统一走这里。
     */
    private Integer toDbIsRead(Boolean read) {
        return Boolean.TRUE.equals(read) ? 1 : 0;
    }

    /**
     * Notification → MessageVO
     *
     * <p>返回旧 VO 结构以保持前端不变。发送者/接收者昵称暂不联表查询 ——
     * 消息列表页不需要展示昵称（前端只显示 content 与类型），
     * 避免每条消息一次 user 表查询。
     */
    private List<MessageVO> toMessageVOs(List<Notification> rows) {
        List<MessageVO> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (Notification n : rows) {
            MessageVO vo = new MessageVO();
            vo.setId(n.getId());
            vo.setReceiverId(n.getUserId());
            vo.setSenderId(n.getSenderId());
            vo.setContent(n.getContent());
            vo.setMessageType(n.getMessageType());
            vo.setIsRead(n.getIsRead() != null && n.getIsRead() == 1);
            vo.setContentId(parseInt(n.getRelatedId()));
            vo.setCreateTime(n.getCreateTime());
            list.add(vo);
        }
        return list;
    }

    private Integer parseInt(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
