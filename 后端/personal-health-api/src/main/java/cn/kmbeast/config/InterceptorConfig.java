package cn.kmbeast.config;

import cn.kmbeast.Interceptor.CrmApiKeyInterceptor;
import cn.kmbeast.Interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

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
                        "/error",
                        "/crm/**"
                );

        // CRM 接口独立的 API Key 校验：默认全保护，仅健康检查放行
        registry.addInterceptor(crmApiKeyInterceptor)
                .addPathPatterns("/crm/**")
                .excludePathPatterns("/crm/health");
    }
}
