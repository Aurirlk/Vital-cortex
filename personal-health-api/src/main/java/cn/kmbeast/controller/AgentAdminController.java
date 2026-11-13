package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.core.agent.registry.AgentRegistry;
import cn.kmbeast.core.agent.run.AgentRunRecorder;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.AgentConfigQueryDto;
import cn.kmbeast.pojo.dto.query.extend.AgentRunQueryDto;
import cn.kmbeast.pojo.entity.AgentConfig;
import cn.kmbeast.service.AgentAdminService;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体管理（管理员）。
 *
 * <p>对应管理端「智能体管理」页面。所有端点都要求 {@code 管理员} 角色 ——
 * 这里能改的是<b>系统的行为</b>（某个 Agent 有没有权限跑 SQL、能不能把问题
 * 转交给别人），而不是普通业务数据，权限面比其他 admin 接口更大。</p>
 *
 * <p><b>为什么单独开 {@code /agent/admin} 而不是塞进 {@code /agent}</b>：
 * {@code /agent} 下的端点登录即可访问（角色清单、偏好读写），把它和
 * 「改系统行为」的管理端点混在一个前缀下，任何一次 {@code @RequestMapping}
 * 的宽松匹配改动都可能把管理端点暴露给普通用户。物理隔离比逐条 @Protector 稳。</p>
 */
@Slf4j
@RestController
@RequestMapping("/agent/admin")
public class AgentAdminController {

    @Resource
    private AgentAdminService agentAdminService;

    @Resource
    private AgentRegistry agentRegistry;

    @Resource
    private AgentRunRecorder agentRunRecorder;

    // ------------------------------------------------------------------
    // 配置
    // ------------------------------------------------------------------

    /** 分页查询智能体。 */
    @Pager
    @Protector(role = "管理员")
    @PostMapping("/config/query")
    public Result<List<AgentConfig>> queryConfig(@RequestBody AgentConfigQueryDto dto) {
        return agentAdminService.queryConfig(dto);
    }

    /** 单个智能体详情：配置 + 绑定 + 生效工具 + 委派目标。 */
    @Protector(role = "管理员")
    @GetMapping("/config/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Integer id) {
        return agentAdminService.detail(id);
    }

    @Protector(role = "管理员")
    @PostMapping("/config/save")
    public Result<Integer> save(@RequestBody AgentConfig config) {
        return agentAdminService.saveConfig(config);
    }

    @Protector(role = "管理员")
    @PutMapping("/config/update")
    public Result<Void> update(@RequestBody AgentConfig config) {
        return agentAdminService.updateConfig(config);
    }

    @Protector(role = "管理员")
    @PostMapping("/config/batchDelete")
    public Result<Void> batchDelete(@RequestBody List<Integer> ids) {
        return agentAdminService.batchDelete(ids);
    }

    /**
     * 启用 / 停用。
     *
     * <p>单独一个端点而不是复用 update：停用是个高频开关动作，
     * 走 update 需要前端先读全量行再回传，中途被别的管理员改过就会把对方的修改覆盖掉。
     * 这里只改 enabled 一列。</p>
     */
    @Protector(role = "管理员")
    @PostMapping("/config/{id}/toggle")
    public Result<Void> toggle(@PathVariable("id") Integer id,
                               @RequestParam("enabled") boolean enabled) {
        return agentAdminService.setEnabled(id, enabled);
    }

    // ------------------------------------------------------------------
    // 工具绑定与转交关系
    // ------------------------------------------------------------------

    /** 全量工具清单，供多选框渲染（call_agent 带 autoManaged 标记，前端应置灰）。 */
    @Protector(role = "管理员")
    @GetMapping("/tools")
    public Result<List<Map<String, Object>>> tools() {
        return agentAdminService.toolCatalog();
    }

    /**
     * 覆盖式设置工具绑定。
     *
     * <p>请求体：{@code {"restricted": false}} = 不限制（全量，含以后新增的工具）；
     * {@code {"restricted": true, "toolNames": ["search_drug"]}} = 白名单，
     * 传空数组即「一个工具都不给」。<b>必须显式传 restricted</b> —— 表结构上
     * 「零绑定行」和「全量」是同一件事，靠空数组去猜会永久地配错。</p>
     */
    @Protector(role = "管理员")
    @PostMapping("/config/{id}/tools")
    public Result<Void> setTools(@PathVariable("id") Integer id,
                                 @RequestBody JSONObject body) {
        Boolean restricted = body.getBoolean("restricted");
        List<String> toolNames = body.getList("toolNames", String.class);
        return agentAdminService.setToolBindings(id, restricted, toolNames);
    }

    /** 全部转交关系（供关系图/列表展示）。 */
    @Protector(role = "管理员")
    @GetMapping("/handoffs")
    public Result<List<Map<String, Object>>> handoffs() {
        return agentAdminService.handoffList();
    }

    /** 覆盖式设置某智能体可委派给谁。传空数组 = 不允许委派。 */
    @Protector(role = "管理员")
    @PostMapping("/config/{id}/handoffs")
    public Result<Void> setHandoffs(@PathVariable("id") Integer id,
                                    @RequestBody JSONObject body) {
        List<String> targetKeys = body.getList("targetKeys", String.class);
        return agentAdminService.setHandoffTargets(id, targetKeys);
    }

    // ------------------------------------------------------------------
    // 试跑与运行记录
    // ------------------------------------------------------------------

    /** 路由试跑：看清「这句话为什么被路由到它」。 */
    @Protector(role = "管理员")
    @PostMapping("/route/preview")
    public Result<Map<String, Object>> previewRoute(@RequestBody JSONObject body) {
        return agentAdminService.previewRoute(
                body.getString("query"), body.getString("audience"));
    }

    /** 分页查询运行记录。 */
    @Pager
    @Protector(role = "管理员")
    @PostMapping("/runs/query")
    public Result<List<Map<String, Object>>> queryRuns(@RequestBody AgentRunQueryDto dto) {
        return agentAdminService.queryRuns(dto);
    }

    /** 一次运行的完整轨迹（父运行 + 全部子委派 + 每步的工具调用）。 */
    @Protector(role = "管理员")
    @GetMapping("/runs/{runId}")
    public Result<Map<String, Object>> runTrace(@PathVariable("runId") String runId) {
        return agentAdminService.runTrace(runId);
    }

    /**
     * 运行观测的自我说明。
     *
     * <p>管理端页面靠它渲染横幅：表没建的时候（本机还没执行
     * {@code 10_agent_architecture.sql}）运行记录一定是空的，
     * 不说明的话管理员只会看到「一片空白」，然后怀疑是功能坏了。</p>
     */
    @Protector(role = "管理员")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dbBacked", agentRegistry.isDbBacked());
        data.put("agentCount", agentRegistry.listAll().size());
        data.put("recording", agentRunRecorder != null);
        data.put("currentRunId", agentRunRecorder == null ? null : agentRunRecorder.currentRunId());
        if (!agentRegistry.isDbBacked()) {
            data.put("hint", "未检测到 agent_config 数据，当前使用内置 6 角色兜底。"
                    + "请执行 Data/02_迁移脚本/10_agent_architecture.sql 后刷新。");
        }
        return ApiResult.success(data);
    }
}
