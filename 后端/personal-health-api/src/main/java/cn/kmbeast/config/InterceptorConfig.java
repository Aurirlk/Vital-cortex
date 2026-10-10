package cn.kmbeast.config;

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
 *   <li>{@code /file/upload} 移出 JWT 白名单，恢复登录态校验，堵住匿名上传刷盘通道。</li>
 * </ul>
 *
 * <p><b>⚠️ 白名单有两处，改动必须同步（2026-10-07 审计发现）</b>：本类是<b>配置侧</b>白名单，
 * {@link JwtInterceptor#preHandle} 内部还有一份<b>代码侧</b>白名单。二者内容曾不一致 ——
 * {@code /doctor/login} 与 {@code /file/getFile} 只存在于代码侧，从配置里看不到，
 * 极易误判「这两个端点需要登录」。现把 {@code /doctor/login} 补进本类，
 * 两处保持一致；{@code /file/getFile} 因前端以 {@code <img src>} 直接引用、
 * 无法携带 token 头，<b>只能</b>由代码侧放行，其安全性由 122 位随机文件名（capability URL）承担。</p>
 */
@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    @Value("${my-server.api-context-path}")
    private String API;

    @Resource
    private JwtInterceptor jwtInterceptor;

    @Resource
    private CrmApiKeyInterceptor crmApiKeyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        API + "/user/login",
                        API + "/user/register",
                        "/user/login",
                        "/user/register",
                        // 2026-10-07 审计补齐：医生端是独立登录入口（不复用 /user/login），
                        // 此前只写在 JwtInterceptor 内部，白名单分散两处、配置上看不到
                        API + "/doctor/login",
                        "/doctor/login",
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
