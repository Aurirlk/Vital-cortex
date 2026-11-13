package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.core.auth.WsTicketRegistry;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 一次性票据签发端点（阶段 11 · S6）。
 *
 * <p>必须登录才能调用：身份只从 {@link LocalThreadHolder} 取（JwtInterceptor 写入），
 * <b>不接受任何客户端传入的 userId</b>。票据 30 秒有效、一次性消费，且只通过
 * Sec-WebSocket-Protocol 传给握手，不再出现在 URL 里。
 */
@RestController
@RequestMapping("/ws")
public class WsTicketController {

    @Resource
    private WsTicketRegistry wsTicketRegistry;

    @Protector
    @GetMapping("/ticket")
    public Result<Map<String, Object>> ticket() {
        Integer userId = LocalThreadHolder.getUserId();
        Integer roleId = LocalThreadHolder.getRoleId();
        if (userId == null) {
            return ApiResult.error("请先登录");
        }
        String ticket = wsTicketRegistry.issue(userId, roleId);
        Map<String, Object> data = new HashMap<>();
        data.put("ticket", ticket);
        data.put("expiresIn", WsTicketRegistry.DEFAULT_TTL.toSeconds());
        return ApiResult.success(data);
    }
}
