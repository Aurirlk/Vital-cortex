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

    @Resource
    private AgentMemoryService agentMemoryService;

    /** 2026-10-04：健康槽位引导（原为死代码，现接入对话与表单两条路径） */
    @Resource
    private cn.kmbeast.core.harness.HarnessEngine harnessEngine;

    /** Agent 角色列表（登录即可见，不含用户数据） */
    @Protector
    @GetMapping("/roles")
    public Result<Map<String, AgentCoordinator.AgentRole>> getRoles() {
        return ApiResult.success(agentCoordinator.getAllAgentRoles());
    }

    /** 意图识别：返回最适合处理该消息的 Agent 类型 */
    @Protector
    @PostMapping("/identify")
    public Result<String> identifyAgent(@RequestBody JSONObject request) {
        String message = request.getString("message");
        return ApiResult.success(agentCoordinator.identifyAgent(message));
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
