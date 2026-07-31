package cn.kmbeast.core.agent;

import cn.kmbeast.core.workspace.AgentWorkspace;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/**
 * Agent 控制器
 * 提供 Agent 相关的 REST API
 */
@Slf4j
@RestController
@RequestMapping("/agent")
public class AgentController {

    @Resource
    private AgentCoordinator agentCoordinator;

    @Resource
    private AgentMemoryService agentMemoryService;

    @Resource
    private AgentWorkspace agentWorkspace;

    /**
     * 获取所有 Agent 角色
     */
    @GetMapping("/roles")
    public Result<Map<String, AgentCoordinator.AgentRole>> getRoles() {
        return ApiResult.success(agentCoordinator.getAllAgentRoles());
    }

    /**
     * 意图识别
     */
    @PostMapping("/identify")
    public Result<String> identifyAgent(@RequestBody JSONObject request) {
        String message = request.getString("message");
        String agentType = agentCoordinator.identifyAgent(message);
        return ApiResult.success(agentType);
    }

    /**
     * 获取用户偏好
     */
    @GetMapping("/preferences/{userId}")
    public Result<Map<String, String>> getUserPreferences(@PathVariable Integer userId) {
        return ApiResult.success(agentMemoryService.getUserPreferences(userId));
    }

    /**
     * 保存用户偏好
     */
    @PostMapping("/preferences/{userId}")
    public Result<Void> saveUserPreference(@PathVariable Integer userId, @RequestBody JSONObject request) {
        agentMemoryService.saveUserPreference(userId, request.getString("key"), request.getString("value"));
        return ApiResult.success();
    }
}
