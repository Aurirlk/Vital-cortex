package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.AgentConfigQueryDto;
import cn.kmbeast.pojo.dto.query.extend.AgentRunQueryDto;
import cn.kmbeast.pojo.entity.AgentConfig;

import java.util.List;
import java.util.Map;

/**
 * 智能体管理（管理员）业务接口。
 *
 * <p><b>它存在的理由</b>：改造前那 6 个角色是写死在 {@code AgentCoordinator} 里的
 * Java Map —— 管理员能做的只有「看」。而「后台能新建智能体、能配它用哪些工具、
 * 能配它可以把问题交给谁」，才是把 6 个角色变成一个<b>可运营的 Agent 平台</b>
 * 与「6 个硬编码角色」区别开的地方。</p>
 *
 * <p><b>所有写操作都必须让 {@code AgentRegistry} 的缓存失效</b>，
 * 否则管理员改完要等 60 秒 TTL 到期才生效，而他会以为没保存成功、再改一遍。
 * 实现里统一在写方法末尾调 {@code invalidate()}，而不是指望调用方记得。</p>
 */
public interface AgentAdminService {

    /** 分页查询智能体配置。 */
    Result<List<AgentConfig>> queryConfig(AgentConfigQueryDto dto);

    /**
     * 单个智能体的完整配置：配置行 + 生效工具 + 可委派目标。
     *
     * @return data 含 {@code config} / {@code toolNames}（null=全量）/ {@code handoffTargets}
     */
    Result<Map<String, Object>> detail(Integer id);

    /** 新建智能体。 */
    Result<Integer> saveConfig(AgentConfig config);

    /** 修改智能体（仅更新传入的非空字段）。 */
    Result<Void> updateConfig(AgentConfig config);

    /** 批量删除智能体，并清理其工具绑定与转交关系（无外键，必须手写级联）。 */
    Result<Void> batchDelete(List<Integer> ids);

    /** 启用 / 停用。 */
    Result<Void> setEnabled(Integer id, boolean enabled);

    /** 全量工具清单（供管理端下拉多选）。 */
    Result<List<Map<String, Object>>> toolCatalog();

    /**
     * 覆盖式设置某智能体的工具绑定。
     *
     * @param restricted false = 清空绑定行（该 Agent 自动获得全量工具，含以后新增的）；
     *                   true  = 按 {@code toolNames} 建立白名单（传空列表 = 一个工具都不给）
     */
    Result<Void> setToolBindings(Integer id, Boolean restricted, List<String> toolNames);

    /** 全部转交关系（发起方 key → 目标 key 列表），供管理端画关系图。 */
    Result<List<Map<String, Object>>> handoffList();

    /** 覆盖式设置某智能体的可委派目标。传空列表 = 不允许委派。 */
    Result<Void> setHandoffTargets(Integer id, List<String> targetKeys);

    /** 路由试跑：给定一句话，返回会被路由到哪个智能体、依据是什么。 */
    Result<Map<String, Object>> previewRoute(String query, String audience);

    /** 分页查询运行记录。 */
    Result<List<Map<String, Object>>> queryRuns(AgentRunQueryDto dto);

    /** 一次运行树（父运行 + 全部子委派）的完整轨迹。 */
    Result<Map<String, Object>> runTrace(String runId);
}
