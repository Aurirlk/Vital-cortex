package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.AgentConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 智能体配置持久化接口
 */
@Mapper
public interface AgentConfigMapper {

    int save(AgentConfig agentConfig);

    int update(AgentConfig agentConfig);

    int deleteByIds(@Param("ids") List<Integer> ids);

    AgentConfig getById(@Param("id") Integer id);

    AgentConfig getByKey(@Param("agentKey") String agentKey);

    List<AgentConfig> query(@Param("agentKey") String agentKey, @Param("name") String name,
                            @Param("audience") String audience, @Param("enabled") Boolean enabled,
                            @Param("current") Integer current, @Param("size") Integer size);

    Integer queryCount(@Param("agentKey") String agentKey, @Param("name") String name,
                       @Param("audience") String audience, @Param("enabled") Boolean enabled);

    /**
     * 全部启用的智能体，按 sort_order 升序；注册表每次刷新缓存都会调用，不能分页
     */
    List<AgentConfig> listAllEnabled();

    /**
     * 清空某人群下所有智能体的默认标记，用于原子地重指向兜底智能体
     */
    int clearDefault(@Param("audience") String audience);
}
