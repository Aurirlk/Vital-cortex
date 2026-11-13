package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.AgentToolBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 智能体-工具绑定持久化接口。
 *
 * <p><b>为什么只有 batchSave 没有 save</b>：绑定集恒以「整组替换」的方式写入
 * （先 deleteByAgentId 再 batchSave）。留一个单条 save 会诱导出「加一条」的
 * 局部修改路径，而局部修改与 {@code AgentRegistry} 的缓存失效边界（整表刷新）
 * 不配套 —— 加了一条却忘了 invalidate，就会表现为「刚加的工具要等 60 秒才生效」。</p>
 */
@Mapper
public interface AgentToolBindingMapper {

    int batchSave(@Param("list") List<AgentToolBinding> list);

    int deleteByAgentId(@Param("agentId") Integer agentId);

    List<AgentToolBinding> queryByAgentId(@Param("agentId") Integer agentId);

}
