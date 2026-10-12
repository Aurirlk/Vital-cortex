package cn.kmbeast.config;

import cn.kmbeast.Interceptor.ActuatorAccessInterceptor;
import cn.kmbeast.Interceptor.CrmApiKeyInterceptor;
import cn.kmbeast.Interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;

/**
 * API拦截器配置
 *
 * <p>SEC-02 / MM-05 整改要点：
 * <ul>
 *   <li>{@code /crm/**} 仍不走 JWT（它是机器对机器接口，用 API Key 认证），
 *       但改由 {@link CrmApiKeyInterceptor} 统一 fail-closed 拦截，只放行健康检查。</li>
 *   <li>{@code /file/upload}、{@code /file/getFile} 移出 JWT 白名单，恢复登录态校验，
 *       堵住匿名上传与匿名遍历下载体检报告的通道。</li>
 * </ul>
 *
 * <p>2026-10-10 补充：{@code /actuator/**} 免鉴权是抓取端（Prometheus，无 token）所必需的，
 * 但 21090 直接对外发布，等于把 JVM/HTTP 指标挂在公网上。现由
 * {@link ActuatorAccessInterceptor} 限定为仅回环/私有网段可访问，
 * 且<b>先于 JWT 拦截器注册</b>以便最早短路。
 */
@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    @Value("${my-server.api-context-path}")
    private String API;

    @Resource
    private JwtInterceptor jwtInterceptor;

    @Resource
    private CrmApiKeyInterceptor crmApiKeyInterceptor;

    @Resource
    private ActuatorAccessInterceptor actuatorAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 先注册：不满足来源要求的请求在第一个拦截器就 404，不必再走 JWT 判定
        registry.addInterceptor(actuatorAccessInterceptor)
                .addPathPatterns(API + "/actuator/**", "/actuator/**");

        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        API + "/user/login",
                        API + "/user/register",
                        "/user/login",
                        "/user/register",
                        "/error",
                        "/crm/**",
                        API + "/actuator/**",
                        "/actuator/**"
                );

        // CRM 接口独立的 API Key 校验：默认全保护，仅健康检查放行
        registry.addInterceptor(crmApiKeyInterceptor)
                .addPathPatterns("/crm/**")
                .excludePathPatterns("/crm/health");
    }
}
