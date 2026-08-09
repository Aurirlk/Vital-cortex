package cn.kmbeast.config;

import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.update.AiChatRequest;
import cn.kmbeast.pojo.dto.update.UserLoginDTO;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Sentinel 限流兜底处理（@SentinelResource blockHandler 集中定义）。
 *
 * <p>规则：blockHandler 必须是 static，签名 = 原始方法参数 + 末尾追加 BlockException，
 * 返回类型与原始方法一致。触发限流时由 Sentinel 直接调用，返回 429 语义的业务错误。
 */
@Slf4j
public class SentinelBlockHandlers {

    private SentinelBlockHandlers() {
    }

    /** 登录限流兜底：返回可读错误，避免撞库刷接口 */
    public static Result<Object> loginBlocked(UserLoginDTO dto, BlockException ex) {
        log.warn("[Sentinel] 登录触发限流: ip 请求过于频繁");
        return ApiResult.error("请求过于频繁，请稍后再试");
    }

    /** AI 对话（非流式）限流兜底 */
    public static Result<Map<String, String>> chatBlocked(AiChatRequest req, BlockException ex) {
        log.warn("[Sentinel] AI 对话触发限流: message={}",
                req != null && req.getMessage() != null
                        ? req.getMessage().substring(0, Math.min(20, req.getMessage().length())) : "");
        Map<String, String> data = new HashMap<>();
        data.put("reply", "请求过于频繁，请稍后再试");
        return ApiResult.error("AI 服务繁忙，请稍后再试");
    }

    /** AI 流式对话限流兜底：以 SSE error 事件告知前端（保持流式协议） */
    public static void chatStreamBlocked(AiChatRequest req, HttpServletResponse response, BlockException ex) {
        log.warn("[Sentinel] AI 流式对话触发限流");
        try {
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            PrintWriter w = response.getWriter();
            w.write("event: error\n");
            w.write("data: {\"message\":\"AI 服务繁忙，请稍后再试\"}\n\n");
            w.flush();
        } catch (Exception e) {
            log.warn("[Sentinel] 流式限流兜底写入失败: {}", e.getMessage());
        }
    }

    /** 文件上传限流兜底 */
    public static Result<Map<String, String>> uploadBlocked(MultipartFile file, BlockException ex) {
        log.warn("[Sentinel] 文件上传触发限流");
        return ApiResult.error("上传过于频繁，请稍后再试");
    }
}
