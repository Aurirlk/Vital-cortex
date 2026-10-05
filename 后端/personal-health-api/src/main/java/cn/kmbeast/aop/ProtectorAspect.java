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
import java.util.Objects;

/**
 * 接口鉴权保护切面
 *
 * <p>2026-10-03：角色校验增加空值防护与诊断日志。原先 {@code Math.toIntExact(roleId)} 在
 * roleId 缺失时会抛异常（返回 500 而非可读的鉴权失败），且「无操作权限」无法区分
 * 「角色不匹配」与「角色编码非法」。
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
                return ApiResult.error("身份认证失败，请先登录");
            }

            Claims claims = jwtUtil.fromToken(token);
            if (claims == null) {
                return ApiResult.error("身份认证失败，请先登录");
            }

            Integer userId = claims.get("id", Integer.class);
            Integer roleId = claims.get("role", Integer.class);

            // 获取@Protector注解
            MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
            Protector protectorAnnotation = signature.getMethod().getAnnotation(Protector.class);
            if (protectorAnnotation == null) {
                return ApiResult.error("身份认证失败，请先登录");
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
                    return ApiResult.error("身份认证失败，请重新登录");
                }
                String actualRole = RoleEnum.ROLE(roleId);
                if (!Objects.equals(actualRole, role)) {
                    log.warn("[Protector] 角色不匹配，拒绝访问: uri={}, need={}, actual={}",
                            request.getRequestURI(), role, actualRole);
                    return ApiResult.error("无操作权限");
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
}
