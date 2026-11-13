package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.MessageQueryDto;
import cn.kmbeast.pojo.entity.Message;
import cn.kmbeast.pojo.vo.MessageVO;

import java.util.List;

/**
 * 消息业务逻辑接口
 */
public interface MessageService {

    Result<Void> save(List<Message> messages);

    Result<Void> evaluationsReplySave(Message message);

    Result<Void> evaluationsUpvoteSave(Message message);

    Result<Void> systemInfoSave(List<Message> messages);

    Result<Void> dataWordSave(List<Message> messages);

    Result<Void> batchDelete(List<Long> ids);

    Result<List<MessageVO>> query(MessageQueryDto messageQueryDto);

    Result<Void> systemInfoUsersSave(Message message);

    Result<Void> clearMessage();


}
