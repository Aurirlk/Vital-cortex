package cn.kmbeast.core.agent;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.em.RoleEnum;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * Agent 能力控制器（2026-10-04 加固）
 *
 * <p><b>本次修复的越权漏洞</b>：原实现全部接口无鉴权，且
 * {@code /preferences/{userId}} 直接把路径变量当查询/写入目标 ——
 * 任意登录用户只要改一下 URL 里的 id，就能<b>读写他人的健康偏好</b>
 * （可能含饮食禁忌、运动限制等敏感信息）。
 *
 * <p><b>处置</b>：
 * <ul>
 *   <li>全部端点加 {@code @Protector}，要求登录</li>
 *   <li>userId 一律以 token 为准；仅管理员可指定他人</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/agent")
public class AgentController {

    @Resource
    private AgentCoordinator agentCoordinator;

    /**
     * 阶段 1 起角色清单与意图识别的真值源。
     *
     * <p>保留 {@link #agentCoordinator} 仍有用：它是注册表在表未建/不可用时的
     * 兜底角色源，两者不是替代关系。</p>
     */
    @Resource
    private cn.kmbeast.core.agent.orchestrator.AgentOrchestrator agentOrchestrator;

    @Resource
    private cn.kmbeast.core.agent.registry.AgentRegistry agentRegistry;

    @Resource
    private AgentMemoryService agentMemoryService;

    /** 2026-10-04：健康槽位引导（原为死代码，现接入对话与表单两条路径） */
    @Resource
    private cn.kmbeast.core.harness.HarnessEngine harnessEngine;

    /**
     * Agent 角色列表（登录即可见，不含用户数据）。
     *
     * <p><b>为什么改成读注册表</b>：原来直读 {@code AgentCoordinator} 的内置 Map，
     * 那是写死的 6 个角色。管理员在后台新建的智能体<b>永远不会出现在这里</b>，
     * 前端「AI 健康分析」页的角色下拉框也就永远只有 6 项 —— 后台加了个智能体，
     * 用户端却看不见、选不到，功能等于没上线。</p>
     *
     * <p>响应结构保持 {@link AgentCoordinator.AgentRole} 不变（type/name/keywords/description），
     * 前端无需改动；表未建时注册表回退到内置 6 角色，返回内容与改造前一致。</p>
     */
    @Protector
    @GetMapping("/roles")
    public Result<Map<String, AgentCoordinator.AgentRole>> getRoles() {
        Map<String, AgentCoordinator.AgentRole> roles = new java.util.LinkedHashMap<>();
        for (cn.kmbeast.core.agent.registry.AgentDefinition def : agentRegistry.listAll()) {
            if (!def.isEnabled()) {
                // 停用的不应出现在可选清单里，否则用户选中后路由会退回兜底，
                // 表现为「选了营养师，回答的却像全能助手」
                continue;
            }
            roles.put(def.getAgentKey(), new AgentCoordinator.AgentRole(
                    def.getAgentKey(), def.getName(),
                    def.keywordsOrEmpty(), def.getDescription()));
        }
        if (roles.isEmpty()) {
            // 注册表为空（极小概率：DB 有数据但全部停用）时给出内置清单，
            // 总比返回空对象让前端渲染出一个空白下拉框好
            return ApiResult.success(agentCoordinator.getAllAgentRoles());
        }
        return ApiResult.success(roles);
    }

    /** 意图识别：返回最适合处理该消息的 Agent 类型 */
    @Protector
    @PostMapping("/identify")
    public Result<String> identifyAgent(@RequestBody JSONObject request) {
        String message = request.getString("message");
        return ApiResult.success(agentOrchestrator.identifyAgent(message));
    }

    /**
     * 读取用户偏好
     *
     * <p>{@code userId} 路径变量仅对管理员有效；普通用户强制读自己的偏好。
     */
    @Protector
    @GetMapping("/preferences/{userId}")
    public Result<Map<String, String>> getUserPreferences(@PathVariable Integer userId) {
        Integer current = LocalThreadHolder.getUserId();
        if (!canAccess(userId, current)) {
            log.warn("[Agent] 拒绝读取他人偏好: current={}, target={}", current, userId);
            return ApiResult.error("无权查看他人的偏好数据");
        }
        return ApiResult.success(agentMemoryService.getUserPreferences(userId));
    }

    /** 写入用户偏好（同上，普通用户只能写自己的） */
    @Protector
    @PostMapping("/preferences/{userId}")
    public Result<Void> saveUserPreference(@PathVariable Integer userId,
                                           @RequestBody JSONObject request) {
        Integer current = LocalThreadHolder.getUserId();
        if (!canAccess(userId, current)) {
            log.warn("[Agent] 拒绝写入他人偏好: current={}, target={}", current, userId);
            return ApiResult.error("无权修改他人的偏好数据");
        }
        agentMemoryService.saveUserPreference(userId,
                request.getString("key"), request.getString("value"));
        return ApiResult.success();
    }

    /**
     * 缓存统计（仅管理员，用于监控偏好缓存是否触达上限）
     */
    @Protector(role = "管理员")
    @GetMapping("/memory/stats")
    public Result<Map<String, Object>> getMemoryStats() {
        return ApiResult.success(agentMemoryService.getCacheStats());
    }

    /**
     * 查询当前用户的档案缺失项（SlotFilling）
     *
     * <p>供前端渲染「完善健康档案」引导：返回缺失字段 → 引导话术，
     * 以及全部关键槽位名（决定表单要渲染哪些输入框）。
     *
     * <p>只返回<b>自己的</b>缺失项，不接受 userId 参数 —— 从设计上杜绝越权。
     */
    @Protector
    @GetMapping("/slots/missing")
    public Result<Map<String, Object>> getMissingSlots() {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("missing", harnessEngine.currentUserMissingSlots());
        data.put("requiredKeys", harnessEngine.getRequiredSlotKeys());
        return ApiResult.success(data);
    }

    /**
     * 手动提交健康槽位（表单提交路径）
     *
     * <p>对话中的自动抽取只覆盖「用户顺口说出」的情况，
     * 用户在引导表单里主动填写走这个接口。
     */
    @Protector
    @PostMapping("/slots")
    public Result<Map<String, Object>> saveSlots(@RequestBody JSONObject request) {
        Integer userId = LocalThreadHolder.getUserId();
        if (userId == null) {
            return ApiResult.error("未登录");
        }
        JSONObject payload = request.getJSONObject("slots");
        if (payload == null || payload.isEmpty()) {
            return ApiResult.error("没有需要保存的信息");
        }
        Map<String, String> slots = new java.util.LinkedHashMap<>();
        payload.forEach((k, v) -> {
            if (v != null) {
                slots.put(k, String.valueOf(v));
            }
        });

        int changed = harnessEngine.persistSlots(userId, slots);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("savedFields", changed);
        data.put("stillMissing", harnessEngine.currentUserMissingSlots());
        return ApiResult.success(data);
    }

    /**
     * 越权校验：本人或管理员才可访问。
     *
     * <p>fail-closed：查不到当前身份一律拒绝，避免 token 异常时误放行。
     */
    private boolean canAccess(Integer targetUserId, Integer currentUserId) {
        if (targetUserId == null || currentUserId == null) {
            return false;
        }
        if (targetUserId.equals(currentUserId)) {
            return true;
        }
        return Integer.valueOf(RoleEnum.ADMIN.getRole()).equals(LocalThreadHolder.getRoleId());
    }
}
