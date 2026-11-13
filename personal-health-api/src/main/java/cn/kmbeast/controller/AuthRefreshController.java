package cn.kmbeast.controller;

import cn.kmbeast.core.auth.AuthSessionManager;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.utils.JwtUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * refresh token 轮换端点（阶段 11 · A1）。
 *
 * <p><b>必须匿名可达</b>：调用它的场景正是 access token 已过期，若还要求带有效 JWT
 * 就自相矛盾了。因此 {@code /auth/refresh} 已同步加入 JWT 白名单的两处
 * （{@code InterceptorConfig#addInterceptors} 配置侧 +
 * {@code JwtInterceptor#preHandle} 代码侧）。身份完全由
 * {@link AuthSessionManager#rotateRefreshToken} 从服务端存储换出，
 * <b>不接受客户端自报 userId</b>。
 *
 * <p>失败返回业务错误码（HTTP 200 + code 400），由前端 refresh 逻辑判定后清登录态；
 * 这样不会触发 request.js 里「HTTP 401 清 token」的通用分支而造成刷新与登出互相打架。
 */
@RestController
@RequestMapping("/auth")
public class AuthRefreshController {

    @Resource
    private AuthSessionManager authSessionManager;

    @Resource
    private JwtUtil jwtUtil;

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody(required = false) Map<String, String> body) {
        String refreshToken = body == null ? null : body.get("refreshToken");
        AuthSessionManager.RefreshIdentity identity = authSessionManager.rotateRefreshToken(refreshToken);
        if (identity == null) {
            return ApiResult.error("登录状态已失效，请重新登录");
        }
        String accessToken = jwtUtil.toToken(identity.userId(), identity.roleId(), identity.ver());
        Map<String, Object> data = new HashMap<>();
        data.put("token", accessToken);
        data.put("refreshToken", identity.refreshToken());
        return ApiResult.success(data);
    }
}
