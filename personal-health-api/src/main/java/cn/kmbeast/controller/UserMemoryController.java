package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.core.memory.MemoryApiSupport;
import cn.kmbeast.core.memory.StructuredMemoryService;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 用户端结构化 AI 记忆端点（阶段 8 / D3 改写）。
 *
 * <p><b>删除权 / 隔离</b>：所有端点只操作「我自己的」记忆，主体 id 恒取
 * {@link LocalThreadHolder#getUserId()}；<b>端点不接收 userId / patientId / doctorId</b>，
 * 因此不存在「传别人的 id 删别人记忆」的入口。{@code /user/**} 由
 * {@code core/auth/DoctorIsolation} 挡住医生角色。</p>
 *
 * <p><b>与旧画像方案的区别</b>：不再提供 {@code initial_prompt} / persona 自由文本，
 * 也不把任何自由文本写进 system prompt。保存的是结构化 preference facts，
 * 渲染时只输出 {@code - key: value} bullets，并整体过防投毒校验。</p>
 */
@RestController
@RequestMapping("/user/ai-memory")
public class UserMemoryController {

    @Resource
    private MemoryApiSupport memoryApi;

    /** 身份唯一入口；未登录直接拒绝，不回落任何请求体里的 id。 */
    private Integer myId() {
        return LocalThreadHolder.getUserId();
    }

    private Result<Map<String, Object>> needLogin() {
        return ApiResult.error("未登录，无法管理 AI 记忆");
    }

    @Protector
    @GetMapping
    public Result<Map<String, Object>> get() {
        Integer id = myId();
        return id == null ? needLogin()
                : memoryApi.view(StructuredMemoryService.Namespace.USER, id);
    }

    /** 整批保存结构化偏好 / 开关。请求体白名单见 {@link MemoryApiSupport#save}。 */
    @Protector
    @PutMapping
    public Result<Map<String, Object>> save(@RequestBody(required = false) JSONObject body) {
        Integer id = myId();
        return id == null ? needLogin()
                : memoryApi.save(StructuredMemoryService.Namespace.USER, id, body);
    }

    /** 删除我的某一条事实。 */
    @Protector
    @DeleteMapping("/{key}")
    public Result<Map<String, Object>> delete(@PathVariable String key) {
        Integer id = myId();
        return id == null ? needLogin()
                : memoryApi.delete(StructuredMemoryService.Namespace.USER, id, key);
    }

    /** 清空我的全部事实。 */
    @Protector
    @DeleteMapping
    public Result<Map<String, Object>> clear() {
        Integer id = myId();
        return id == null ? needLogin()
                : memoryApi.clear(StructuredMemoryService.Namespace.USER, id);
    }

    /** 自动抽取（默认关闭；用户 opt-out 时连读都不做）。 */
    @Protector
    @PostMapping("/extract")
    public Result<Map<String, Object>> extract() {
        Integer id = myId();
        return id == null ? needLogin()
                : memoryApi.extract(StructuredMemoryService.Namespace.USER, id);
    }
}
