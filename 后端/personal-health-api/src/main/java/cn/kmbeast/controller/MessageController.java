package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.MessageQueryDto;
import cn.kmbeast.pojo.em.IsReadEnum;
import cn.kmbeast.pojo.em.MessageType;
import cn.kmbeast.pojo.entity.Message;
import cn.kmbeast.pojo.vo.MessageTypeVO;
import cn.kmbeast.pojo.vo.MessageVO;
import cn.kmbeast.service.MessageService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息的 Controller
 */
@RestController
@RequestMapping(value = "/message")
public class MessageController {

    @Resource
    private MessageService messageService;

    /**
     * 查询全部的消息类型
     */
    @Protector
    @GetMapping(value = "/types")
    public Result<List<MessageTypeVO>> all() {
        MessageType[] messageTypes = MessageType.values();
        List<MessageTypeVO> messageTypeVOS = new ArrayList<>();
        for (MessageType messageType : messageTypes) {
            MessageTypeVO messageTypeVO = new MessageTypeVO(messageType.getType(), messageType.getDetail());
            messageTypeVOS.add(messageTypeVO);
        }
        return ApiResult.success(messageTypeVOS);
    }

    /**
     * 全站的系统通知（管理员）
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/systemInfoUsersSave")
    public Result<Void> systemInfoUsersSave(@RequestBody Message message) {
        return messageService.systemInfoUsersSave(message);
    }

    /**
     * 消息通知（管理员）
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/systemInfoSave")
    public Result<Void> systemInfoSave(@RequestBody List<Message> messages) {
        messages.forEach(message -> {
            message.setMessageType(MessageType.SYSTEM_INFO.getType());
            message.setIsRead(IsReadEnum.READ_NO.getStatus());
            message.setCreateTime(LocalDateTime.now());
        });
        return messageService.systemInfoSave(messages);
    }

    /**
     * 消息删除
     */
    @Protector
    @PostMapping(value = "/batchDelete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        return messageService.batchDelete(ids);
    }

    /**
     * 将全部消息设置为已读
     */
    @Protector
    @PutMapping(value = "/clearMessage")
    public Result<Void> clearMessage() {
        return messageService.clearMessage();
    }

    /**
     * 消息查询
     */
    @Pager
    @Protector
    @PostMapping(value = "/query")
    public Result<List<MessageVO>> query(@RequestBody MessageQueryDto messageQueryDto) {
        return messageService.query(messageQueryDto);
    }
}
