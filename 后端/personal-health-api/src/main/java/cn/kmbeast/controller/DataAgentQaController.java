package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.core.nl2sql.DataAgentQaRequest;
import cn.kmbeast.core.nl2sql.DataAgentQaResult;
import cn.kmbeast.core.nl2sql.DataAgentQaService;
import cn.kmbeast.core.nl2sql.DataAgentSse;
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
 * 用户端数据问答（数据智能体）。
 *
 * <p>映射在 {@code /user/**} 下 → 由 {@code DoctorIsolation} 保证医生无法访问；
 * 身份取 {@code LocalThreadHolder}，<b>强制查本人</b>（忽略请求体 patientId）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/user/data-agent")
public class DataAgentQaController {

    @Resource
    private DataAgentQaService qaService;

    /** 同步问答。 */
    @Protector
    @PostMapping("/query")
    public Result<DataAgentQaResult> query(@RequestBody DataAgentQaRequest request, HttpServletRequest http) {
        return ApiResult.success(qaService.query(request, cn.kmbeast.core.nl2sql.StepSink.NOOP, http));
    }

    /** 流式问答：SSE 推送每一步工作流/思考/工具事件，最后推 result。 */
    @Protector
    @PostMapping("/query/stream")
    public void queryStream(@RequestBody DataAgentQaRequest request,
                            HttpServletRequest http, HttpServletResponse response) {
        PrintWriter writer = null;
        try {
            writer = DataAgentSse.prepare(response);
            DataAgentQaResult result = qaService.query(request, DataAgentSse.sink(writer), http);
            DataAgentSse.send(writer, DataAgentSse.EVENT_RESULT, result);
        } catch (Exception e) {
            log.error("[DataAgent] 用户端流式问答异常", e);
            DataAgentSse.send(writer, DataAgentSse.EVENT_ERROR, "{\"message\":\"数据问答异常\"}");
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    /** 我的数据问答历史（扁平消息流）。 */
    @Protector
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history() {
        return ApiResult.success(qaService.history());
    }

    // ---------- 会话历史管理（对齐 DataAgent 的 ChatSidebar） ----------

    /** 会话列表（会话维度，按最近活跃倒序）。 */
    @Protector
    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> sessions() {
        return ApiResult.success(qaService.sessions());
    }

    /** 指定会话的消息（非本人会话一律返回空）。 */
    @Protector
    @GetMapping("/sessions/{id}/messages")
    public Result<List<Map<String, Object>>> sessionMessages(@PathVariable("id") String id) {
        return ApiResult.success(qaService.sessionMessages(id));
    }

    /** 重命名会话。 */
    @Protector
    @PutMapping("/sessions/{id}/rename")
    public Result<Boolean> renameSession(@PathVariable("id") String id,
                                         @RequestParam("title") String title) {
        boolean ok = qaService.renameSession(id, title);
        return ok ? ApiResult.success(true) : ApiResult.error("重命名失败或无权操作");
    }

    /** 删除会话（连同消息）。 */
    @Protector
    @DeleteMapping("/sessions/{id}")
    public Result<Boolean> deleteSession(@PathVariable("id") String id) {
        boolean ok = qaService.deleteSession(id);
        return ok ? ApiResult.success(true) : ApiResult.error("删除失败或无权操作");
    }

    /** 清空我的全部会话。 */
    @Protector
    @DeleteMapping("/sessions")
    public Result<Integer> clearSessions() {
        return ApiResult.success(qaService.clearSessions());
    }

    /** 预设问题（管理员在「数据智能体」页维护，未配置时返回内置默认）。 */
    @Protector
    @GetMapping("/presets")
    public Result<List<String>> presets() {
        return ApiResult.success(qaService.presetQuestions(false));
    }
}
