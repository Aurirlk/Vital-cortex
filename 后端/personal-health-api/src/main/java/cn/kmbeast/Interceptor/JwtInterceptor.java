package cn.kmbeast.Interceptor;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.core.auth.AuthSessionManager;
import cn.kmbeast.core.auth.DoctorIsolation;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.em.RoleEnum;
import cn.kmbeast.utils.JwtUtil;
import com.alibaba.fastjson2.JSONObject;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        //
        // 2026-10-04 补：/doctor/login 与 /doctor/reset-password 同样必须匿名可达 ——
        // 医生端是独立登录入口（不复用 /user/login），此前漏加导致医生根本无法登录。
        if (requestURI.endsWith("/user/login")
                || requestURI.endsWith("/user/register")
                || requestURI.endsWith("/doctor/login")
                || requestURI.endsWith("/file/getFile")
                || requestURI.endsWith("/error")) {
            return true;
        }

        String token = request.getHeader("token");
        Claims claims = jwtUtil.fromToken(token);

        // 解析不成功，直接返回错误
        if (claims == null) {
            writeUnauthorized(request, response, "身份认证异常，请先登录");
            return false;
        }

        Integer userId = claims.get("id", Integer.class);
        Integer roleId = claims.get("role", Integer.class);
        if (userId == null) {
            writeUnauthorized(request, response, "身份认证异常，请重新登录");
            return false;
        }

        // 2026-10-04 三端隔离：医生端与用户端互不可越权。
        // 放在会话版本校验之前，避免无权请求还去打扰 Redis。
        if (!DoctorIsolation.isAllowed(requestURI, roleId)) {
            writeForbidden(request, response, roleId);
            return false;
        }

        // 2026-10-03 医生与 user 解耦：医生（role=3）不是 user 表记录，账号状态看
        // hospital_doctor.status（在 DoctorAuthServiceImpl 登录时校验）。
        // 原实现对医生也会走 isAccountLocked → 查 user 表 → 永远查不到 → 每个请求白查一次库。
        boolean isDoctor = Integer.valueOf(RoleEnum.DOCTOR.getRole()).equals(roleId);

        if (!isDoctor) {
            // roadmap §1.3：账号锁定（is_login=1）即时生效——存量 token 立即失效并强制退出。
            // 走 Redis 缓存（60s TTL，miss 查库回填），Redis 不可用时降级查库。
            if (authSessionManager.isAccountLocked(userId)) {
                writeAccountDisabled(response);
                return false;
            }
        }

        // roadmap §1.3：会话版本校验——登录/锁定/登出/改密都会递增版本，
        // token 携带的 ver 与当前版本不一致说明会话已失效（被登出/改密/重新登录）。
        // Redis 不可用时 getVersion 返回 null，跳过该校验（仅保留签名校验）。
        // 注：版本键为 auth:user:{id}:ver，对医生而言该 id 是 hospital_doctor.id（3001+），
        //     与 user 表主键段位（1~18）不重叠，因此不会互相覆盖。
        Integer tokenVer = claims.get("ver", Integer.class);
        Integer currentVer = authSessionManager.getVersion(userId);
        if (currentVer != null && tokenVer != null && !currentVer.equals(tokenVer)) {
            writeUnauthorized(request, response, "登录状态已失效，请重新登录");
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

    /**
     * 403：已登录但无权访问该端（三端隔离拦截）。
     *
     * <p>与 401 区分开是刻意的：401 表示「你是谁我不知道 / 会话失效」，
     * 前端应清 token 跳登录；403 表示「知道你是谁，但你不能来这里」，
     * 前端应跳回各自首页。<b>不要</b>对 403 清 token。
     */
    private void writeForbidden(HttpServletRequest request, HttpServletResponse response, Integer roleId) throws Exception {
        boolean isDoctor = Integer.valueOf(RoleEnum.DOCTOR.getRole()).equals(roleId);
        String msg = isDoctor ? "医生端无权访问用户端功能" : "无权访问医生端功能";
        log.warn("[Auth] 越权拦截 403: uri={}, role={}", request.getRequestURI(), roleId);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        Writer stream = response.getWriter();
        stream.write(JSONObject.toJSONString(ApiResult.error(msg)));
        stream.flush();
        stream.close();
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, String message) throws Exception {
        // 原实现返回 HTTP 200 + 业务错误码，前端难以统一拦截跳转登录，这里补上 401
        // 诊断日志：必须记录被拒的 URI 与 token 状态，否则线上只能看到"跳登录页"却无从定位。
        // 注意不要打印 token 原文，只打印是否存在与长度，避免密钥泄漏到日志。
        String token = request.getHeader("token");
        log.warn("[Auth] 鉴权失败 401: uri={}, reason={}, hasToken={}, tokenLen={}",
                request.getRequestURI(),
                message,
                token != null && !token.isEmpty(),
                token == null ? 0 : token.length());
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
