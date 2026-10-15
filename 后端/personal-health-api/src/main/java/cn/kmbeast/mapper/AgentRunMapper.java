package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.AgentRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 智能体运行记录持久化接口（追加写入，无删除）
 */
@Mapper
public interface AgentRunMapper {

    int save(AgentRun run);

    /**
     * 仅更新非空字段，用于把 RUNNING 行原地收敛为终态
     */
    int update(AgentRun run);

    List<AgentRun> query(@Param("runId") String runId, @Param("parentRunId") String parentRunId,
                         @Param("agentKey") String agentKey, @Param("sessionId") String sessionId,
                         @Param("userId") Integer userId, @Param("status") String status,
                         @Param("current") Integer current, @Param("size") Integer size);

    Integer queryCount(@Param("runId") String runId, @Param("parentRunId") String parentRunId,
                       @Param("agentKey") String agentKey, @Param("sessionId") String sessionId,
                       @Param("userId") Integer userId, @Param("status") String status);

    AgentRun getByRunId(@Param("runId") String runId);

    /**
     * 按 runId 前缀取一次运行树的所有节点（父运行与子运行共享前缀）
     */
    List<AgentRun> listByRunIdPrefix(@Param("runIdPrefix") String runIdPrefix);
}
