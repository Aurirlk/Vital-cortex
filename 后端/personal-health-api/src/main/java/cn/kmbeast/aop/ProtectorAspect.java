package cn.kmbeast.aop;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.em.RoleEnum;
import cn.kmbeast.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;

/**
 * 接口鉴权保护切面
 *
 * <p>2026-10-03：角色校验增加空值防护与诊断日志。原先 {@code Math.toIntExact(roleId)} 在
 * roleId 缺失时会抛异常（返回 500 而非可读的鉴权失败），且「无操作权限」无法区分
 * 「角色不匹配」与「角色编码非法」。
 *
 * <p>2026-10-24：鉴权失败改回真实 HTTP 状态（身份问题 401、越权 403），
 * 口径与 {@code JwtInterceptor} 对齐，详见 {@link #reject}。
 */
@Slf4j
@Aspect
@Component
public class ProtectorAspect {

    @Resource
    private JwtUtil jwtUtil;

    @Around("@annotation(cn.kmbeast.aop.Protector)")
    public Object auth(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attributes.getRequest();
            String token = request.getHeader("token");

            if (token == null) {
                return reject(attributes, HttpServletResponse.SC_UNAUTHORIZED, "身份认证失败，请先登录");
            }

            Claims claims = jwtUtil.fromToken(token);
            if (claims == null) {
                return reject(attributes, HttpServletResponse.SC_UNAUTHORIZED, "身份认证失败，请先登录");
            }

            Integer userId = claims.get("id", Integer.class);
            Integer roleId = claims.get("role", Integer.class);

            // 获取@Protector注解
            MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
            Protector protectorAnnotation = signature.getMethod().getAnnotation(Protector.class);
            if (protectorAnnotation == null) {
                // 切点按注解匹配，读不到说明切面/代理配置不对；按未认证拒绝，不放过
                log.warn("[Protector] 目标方法上读不到 @Protector 注解，按未认证拒绝: uri={}, method={}",
                        request.getRequestURI(), signature.getMethod());
                return reject(attributes, HttpServletResponse.SC_UNAUTHORIZED, "身份认证失败，请先登录");
            }

            // 验证用户角色
            String role = protectorAnnotation.role();
            if (!"".equals(role)) {
                // 2026-10-03 加固：原实现直接 Math.toIntExact(roleId)，roleId 为 null 时会抛
                // NullPointerException/ArithmeticException（500），而非返回可读的鉴权失败。
                // 同时补日志，便于定位「无操作权限」的真实原因（角色不匹配 vs 角色编码非法）。
                if (roleId == null) {
                    log.warn("[Protector] token 缺少 role 声明，拒绝访问: uri={}, need={}",
                            request.getRequestURI(), role);
                    return reject(attributes, HttpServletResponse.SC_UNAUTHORIZED, "身份认证失败，请重新登录");
                }
                String actualRole = RoleEnum.ROLE(roleId);
                if (!Objects.equals(actualRole, role)) {
                    log.warn("[Protector] 角色不匹配，拒绝访问: uri={}, need={}, actual={}",
                            request.getRequestURI(), role, actualRole);
                    return reject(attributes, HttpServletResponse.SC_FORBIDDEN, "无操作权限");
                }
            }

            // 设置ThreadLocal
            LocalThreadHolder.setUserId(userId, roleId);
            return proceedingJoinPoint.proceed();
        } finally {
            // 请求结束，释放资源
            LocalThreadHolder.clear();
        }
    }

    /**
     * 拒绝请求：把 HTTP 状态码和业务体一起回给前端。
     *
     * <p>2026-10-24（bug.md §7.2-5）：原先只 {@code return ApiResult.error(...)}，
     * HTTP 状态恒为 200，而 {@code JwtInterceptor} 的同类拒绝走的是真 401。
     * 前端两个响应拦截器都按 <b>HTTP 状态</b>判断
     * （{@code utils/request.js} 的 401 → 清 token 跳登录、
     * {@code utils/doctorRequest.js} 的 401/403 分支），
     * 于是经切面拒掉的接口不会跳登录页 —— 用户停在原页面反复看到「请先登录」。
     *
     * <p>状态码分工与 {@code JwtInterceptor} 一致：身份问题用 401（前端清 token 重登），
     * 角色不匹配用 403（知道你是谁但没这个权限，<b>不能</b>清 token 登出）。
     */
    private Object reject(ServletRequestAttributes attributes, int httpStatus, String message) {
        HttpServletResponse response = attributes.getResponse();
        if (response != null) {
            response.setStatus(httpStatus);
        }
        return ApiResult.error(message);
    }
}
