package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.core.nl2sql.DataAgentQaRequest;
import cn.kmbeast.core.nl2sql.DataAgentQaResult;
import cn.kmbeast.core.nl2sql.DataAgentQaService;
import cn.kmbeast.core.nl2sql.DataAgentSse;
import cn.kmbeast.core.nl2sql.StepSink;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * 医生端数据问答（数据智能体）。
 *
 * <p>映射在 {@code /doctor/**} 下 → 仅医生可访问；{@code @Protector(role="医生")} 二次把关。
 * <b>必须</b>在请求体里指定 {@code patientId}，服务端会先校验接诊授权，
 * 未授权直接 fail-closed 并记审计，绝不返回任何患者数据。</p>
 *
 * <p>与用户端的关键差别：这里是「医生查患者」，因此租户条件被重写为
 * {@code user_id = patientId}，而不是医生自己的 id。</p>
 */
@Slf4j
@RestController
@RequestMapping("/doctor/data-agent")
public class DoctorDataAgentController {

    @Resource
    private DataAgentQaService qaService;

    /** 同步问答。 */
    @Protector(role = "医生")
    @PostMapping("/query")
    public Result<DataAgentQaResult> query(@RequestBody DataAgentQaRequest request, HttpServletRequest http) {
        return ApiResult.success(qaService.query(request, StepSink.NOOP, http));
    }

    /** 流式问答（SSE）。 */
    @Protector(role = "医生")
    @PostMapping("/query/stream")
    public void queryStream(@RequestBody DataAgentQaRequest request,
                            HttpServletRequest http, HttpServletResponse response) {
        PrintWriter writer = null;
        try {
            writer = DataAgentSse.prepare(response);
            DataAgentQaResult result = qaService.query(request, DataAgentSse.sink(writer), http);
            DataAgentSse.send(writer, DataAgentSse.EVENT_RESULT, result);
        } catch (Exception e) {
            log.error("[DataAgent] 医生端流式问答异常", e);
            DataAgentSse.send(writer, DataAgentSse.EVENT_ERROR, "{\"message\":\"数据问答异常\"}");
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    /** 我的数据问答历史（扁平消息流）。 */
    @Protector(role = "医生")
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history() {
        return ApiResult.success(qaService.history());
    }

    // ---------- 会话历史管理（对齐 DataAgent 的 ChatSidebar） ----------

    /** 会话列表（会话维度，按最近活跃倒序）。 */
    @Protector(role = "医生")
    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> sessions() {
        return ApiResult.success(qaService.sessions());
    }

    /** 指定会话的消息（非本人会话一律返回空）。 */
    @Protector(role = "医生")
    @GetMapping("/sessions/{id}/messages")
    public Result<List<Map<String, Object>>> sessionMessages(@PathVariable("id") String id) {
        return ApiResult.success(qaService.sessionMessages(id));
    }

    /** 重命名会话。 */
    @Protector(role = "医生")
    @PutMapping("/sessions/{id}/rename")
    public Result<Boolean> renameSession(@PathVariable("id") String id,
                                         @RequestParam("title") String title) {
        boolean ok = qaService.renameSession(id, title);
        return ok ? ApiResult.success(true) : ApiResult.error("重命名失败或无权操作");
    }

    /** 删除会话（连同消息）。 */
    @Protector(role = "医生")
    @DeleteMapping("/sessions/{id}")
    public Result<Boolean> deleteSession(@PathVariable("id") String id) {
        boolean ok = qaService.deleteSession(id);
        return ok ? ApiResult.success(true) : ApiResult.error("删除失败或无权操作");
    }

    /** 清空我的全部会话。 */
    @Protector(role = "医生")
    @DeleteMapping("/sessions")
    public Result<Integer> clearSessions() {
        return ApiResult.success(qaService.clearSessions());
    }

    /** 预设问题（管理员在「数据智能体」页维护，未配置时返回内置默认）。 */
    @Protector(role = "医生")
    @GetMapping("/presets")
    public Result<List<String>> presets() {
        return ApiResult.success(qaService.presetQuestions(true));
    }
}
