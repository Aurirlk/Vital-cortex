package cn.kmbeast.Interceptor;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.utils.JwtUtil;
import com.alibaba.fastjson2.JSONObject;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Writer;

/**
 * Token拦截器
 * 校验JWT token，通过则放行请求，否则返回错误
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Resource
    private JwtUtil jwtUtil;

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
        // 将用户信息放入ThreadLocal
        LocalThreadHolder.setUserId(userId, roleId);
        return true;
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
    }
}
