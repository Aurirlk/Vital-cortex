package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.AgentRunStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 智能体运行步骤持久化接口（追加写入，无删除）
 */
@Mapper
public interface AgentRunStepMapper {

    int save(AgentRunStep step);

    int batchSave(@Param("list") List<AgentRunStep> list);

    /**
     * 取某次运行的全部步骤（按步骤序号正序）
     */
    List<AgentRunStep> listByRunId(@Param("runId") String runId);
}
