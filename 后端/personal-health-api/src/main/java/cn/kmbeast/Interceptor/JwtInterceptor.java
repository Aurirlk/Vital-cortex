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
        // 登录、注册、文件上传等请求不做拦截
        if (requestURI.contains("/login") || requestURI.contains("/error")
                || requestURI.contains("/file") || requestURI.contains("/register")) {
            return true;
        }

        String token = request.getHeader("token");
        Claims claims = jwtUtil.fromToken(token);

        // 解析不成功，直接返回错误
        if (claims == null) {
            Result<String> error = ApiResult.error("身份认证异常，请先登录");
            response.setContentType("application/json;charset=UTF-8");
            Writer stream = response.getWriter();
            stream.write(JSONObject.toJSONString(error));
            stream.flush();
            stream.close();
            return false;
        }

        Integer userId = claims.get("id", Integer.class);
        Integer roleId = claims.get("role", Integer.class);
        // 将用户信息放入ThreadLocal
        LocalThreadHolder.setUserId(userId, roleId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束后清理ThreadLocal，防止线程池复用导致用户身份泄漏
        LocalThreadHolder.clear();
    }
}
