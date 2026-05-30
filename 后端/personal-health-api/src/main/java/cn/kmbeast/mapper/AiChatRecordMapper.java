package cn.kmbeast.mapper;

import cn.kmbeast.pojo.dto.query.extend.AiChatRecordQueryDto;
import cn.kmbeast.pojo.entity.AiChatRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI聊天记录持久化接口
 */
@Mapper
public interface AiChatRecordMapper {

    void save(AiChatRecord aiChatRecord);

    void batchSave(@Param("list") List<AiChatRecord> records);

    void deleteByConversationId(@Param("conversationId") Integer conversationId);

    void batchDeleteByConversationIds(@Param("conversationIds") List<Integer> conversationIds);

    List<AiChatRecord> query(AiChatRecordQueryDto queryDto);

    Integer queryCount(AiChatRecordQueryDto queryDto);

    /**
     * 获取指定会话的所有消息（按时间正序）
     */
    List<AiChatRecord> getByConversationId(@Param("conversationId") Integer conversationId);

    Integer countAll();

    Integer countToday();

    Integer countDistinctUsers();

    Double avgPerUser();
}
