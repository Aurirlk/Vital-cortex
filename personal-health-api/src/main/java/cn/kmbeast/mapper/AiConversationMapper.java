package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI对话会话持久化接口
 */
@Mapper
public interface AiConversationMapper {

    /**
     * 创建新会话
     */
    void save(AiConversation conversation);

    /**
     * 更新会话（消息数、最后消息时间、标题）
     */
    void update(AiConversation conversation);

    /**
     * 删除会话及其所有消息
     */
    void deleteById(@Param("id") Integer id);

    /**
     * 批量删除会话
     */
    void batchDelete(@Param("ids") List<Integer> ids);

    /**
     * 根据ID获取会话
     */
    AiConversation getById(@Param("id") Integer id);

    /**
     * 查询用户的会话列表（按最后消息时间倒序）
     */
    List<AiConversation> queryByUserId(@Param("userId") Integer userId,
                                         @Param("agentType") String agentType);

    /**
     * 获取用户会话总数
     */
    Integer countByUserId(@Param("userId") Integer userId);

    /**
     * 查询该用户当前所有会话 id（用于磁盘副本的孤儿文件清理）。
     *
     * <p>2026-10-07 新增：{@code chat_history/} 的定时清理需要知道「MySQL 里还有哪些会话」
     * 才能判定磁盘上的文件是不是孤儿。</p>
     *
     * @param userId 用户 id
     * @return 会话 id 列表；无则返回空列表（非 null）
     */
    List<Integer> selectIdsByUserId(@Param("userId") Integer userId);
}
