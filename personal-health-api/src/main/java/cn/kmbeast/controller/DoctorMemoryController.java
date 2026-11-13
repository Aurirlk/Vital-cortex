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
 * 医生端结构化 AI 记忆端点（阶段 8 / D3 改写）。
 *
 * <p>医生 JWT 中的 {@code userId} 实为 {@code hospital_doctor.id}（3001+ 段位），
 * 与 {@code user.id} 是两套互不相交的 id 空间；本端点固定使用
 * {@link StructuredMemoryService.Namespace#DOCTOR}，即 {@code hospital_doctor} 表，
 * 因此即使医生 id 与某个用户 id 数值相同也互不干扰。</p>
 *
 * <p><b>隔离</b>：{@code /doctor/**} 由 {@code core/auth/DoctorIsolation} 保证仅医生可进，
 * 这里再加 {@code @Protector(role="医生")} 二次把关（与 {@link DoctorChatController} 同口径）。
 * 端点不接收 doctorId / userId 参数。</p>
 */
@RestController
@RequestMapping("/doctor/ai-memory")
public class DoctorMemoryController {

    @Resource
    private MemoryApiSupport memoryApi;

    private Integer myId() {
        return LocalThreadHolder.getUserId();
    }

    private Result<Map<String, Object>> needLogin() {
        return ApiResult.error("未登录，无法管理 AI 记忆");
    }

    @Protector(role = "医生")
    @GetMapping
    public Result<Map<String, Object>> get() {
        Integer id = myId();
        return id == null ? needLogin()
                : memoryApi.view(StructuredMemoryService.Namespace.DOCTOR, id);
    }

    @Protector(role = "医生")
    @PutMapping
    public Result<Map<String, Object>> save(@RequestBody(required = false) JSONObject body) {
        Integer id = myId();
        return id == null ? needLogin()
                : memoryApi.save(StructuredMemoryService.Namespace.DOCTOR, id, body);
    }

    @Protector(role = "医生")
    @DeleteMapping("/{key}")
    public Result<Map<String, Object>> delete(@PathVariable String key) {
        Integer id = myId();
        return id == null ? needLogin()
                : memoryApi.delete(StructuredMemoryService.Namespace.DOCTOR, id, key);
    }

    @Protector(role = "医生")
    @DeleteMapping
    public Result<Map<String, Object>> clear() {
        Integer id = myId();
        return id == null ? needLogin()
                : memoryApi.clear(StructuredMemoryService.Namespace.DOCTOR, id);
    }

    @Protector(role = "医生")
    @PostMapping("/extract")
    public Result<Map<String, Object>> extract() {
        Integer id = myId();
        return id == null ? needLogin()
                : memoryApi.extract(StructuredMemoryService.Namespace.DOCTOR, id);
    }
}
