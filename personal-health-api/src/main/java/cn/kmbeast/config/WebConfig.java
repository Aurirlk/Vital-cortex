package cn.kmbeast.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置
 * 使用环境变量配置允许的源
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 本地开发放开所有来源（含 localhost / 127.0.0.1 / 局域网 IP / file://），
     * 避免前端直连后端时被浏览器 CORS 拦截。
     * 生产环境请改为具体的可信来源（allowedOriginPatterns 指定域名）。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedHeaders("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
