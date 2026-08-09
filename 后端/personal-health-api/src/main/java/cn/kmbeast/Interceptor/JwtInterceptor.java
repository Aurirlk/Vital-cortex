package cn.kmbeast.Interceptor;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.core.auth.AuthSessionManager;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.utils.JwtUtil;
import com.alibaba.fastjson2.JSONObject;
import io.jsonwebtoken.Claims;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.Writer;

/**
 * Token拦截器
 * 校验JWT token，通过则放行请求，否则返回错误
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    /** 业务码：账号已被禁用（锁定），前端据此提示并强制退出（roadmap §1.3） */
    private static final int CODE_ACCOUNT_DISABLED = 4010;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private AuthSessionManager authSessionManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestMethod = request.getMethod();
        // 放行预检请求
        if ("OPTIONS".equals(requestMethod)) {
            return true;
        }

        String requestURI = request.getRequestURI();
        // MM-05 整改：原白名单用 contains 宽松匹配，且整段放行了 "/file"，
        // 导致 /file/upload 匿名可用（任意人可刷盘）。现收敛为精确后缀匹配，
        // 仅保留 /file/getFile —— 前端以 <img src> 直接引用，无法携带 token 头，
        // 其安全性由 122 位随机文件名（capability URL）承担。
        if (requestURI.endsWith("/user/login")
                || requestURI.endsWith("/user/register")
                || requestURI.endsWith("/file/getFile")
                || requestURI.endsWith("/error")) {
            return true;
        }

        String token = request.getHeader("token");
        Claims claims = jwtUtil.fromToken(token);

        // 解析不成功，直接返回错误
        if (claims == null) {
            writeUnauthorized(response, "身份认证异常，请先登录");
            return false;
        }

        Integer userId = claims.get("id", Integer.class);
        Integer roleId = claims.get("role", Integer.class);
        if (userId == null) {
            writeUnauthorized(response, "身份认证异常，请重新登录");
            return false;
        }

        // roadmap §1.3：账号锁定（is_login=1）即时生效——存量 token 立即失效并强制退出。
        // 走 Redis 缓存（60s TTL，miss 查库回填），Redis 不可用时降级查库。
        if (authSessionManager.isAccountLocked(userId)) {
            writeAccountDisabled(response);
            return false;
        }

        // roadmap §1.3：会话版本校验——登录/锁定/登出/改密都会递增版本，
        // token 携带的 ver 与当前版本不一致说明会话已失效（被登出/改密/重新登录）。
        // Redis 不可用时 getVersion 返回 null，跳过该校验（仅保留签名校验）。
        Integer tokenVer = claims.get("ver", Integer.class);
        Integer currentVer = authSessionManager.getVersion(userId);
        if (currentVer != null && tokenVer != null && !currentVer.equals(tokenVer)) {
            writeUnauthorized(response, "登录状态已失效，请重新登录");
            return false;
        }

        // 将用户信息放入ThreadLocal
        LocalThreadHolder.setUserId(userId, roleId);
        // 注入 MDC，使日志能关联到具体用户（配合 TraceIdFilter 的 traceId）
        MDC.put("userId", String.valueOf(userId));
        return true;
    }

    /**
     * 401 + 业务码 4010：账号被禁用，前端清 token 并提示后跳登录。
     */
    private void writeAccountDisabled(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        JSONObject body = new JSONObject();
        body.put("code", CODE_ACCOUNT_DISABLED);
        body.put("msg", "账号已被禁用，请联系管理员");
        body.put("data", null);
        Writer stream = response.getWriter();
        stream.write(body.toJSONString());
        stream.flush();
        stream.close();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        // 原实现返回 HTTP 200 + 业务错误码，前端难以统一拦截跳转登录，这里补上 401
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Result<String> error = ApiResult.error(message);
        Writer stream = response.getWriter();
        stream.write(JSONObject.toJSONString(error));
        stream.flush();
        stream.close();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束后清理ThreadLocal，防止线程池复用导致用户身份泄漏
        LocalThreadHolder.clear();
        MDC.remove("userId");
    }
}
