package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.AgentHandoff;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 智能体转交关系持久化接口
 */
@Mapper
public interface AgentHandoffMapper {

    int batchSave(@Param("list") List<AgentHandoff> list);

    int deleteByFromAgentId(@Param("fromAgentId") Integer fromAgentId);

    List<AgentHandoff> queryByFromAgentId(@Param("fromAgentId") Integer fromAgentId);

    /**
     * 删除与某个 Agent 相关的全部转交关系（<b>两侧都删</b>）。
     *
     * <p>删 Agent 时专用。只删发起侧会留下悬空的 {@code to_agent_id} ——
     * 本库没有外键约束，数据库不会替我们挡；注册表虽然会在加载时丢掉解析不出的
     * 目标（见 {@code AgentRegistry}），但那些行会永远留在表里，越积越多，
     * 管理员在「转交关系」页面上还会看到指向不存在 Agent 的脏行。</p>
     */
    int deleteByAgentId(@Param("agentId") Integer agentId);

}
