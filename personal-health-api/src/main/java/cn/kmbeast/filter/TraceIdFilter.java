package cn.kmbeast.filter;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 请求链路追踪过滤器（D-010 整改）
 *
 * <p>为每个进入的请求生成 16 位 traceId 并写入 SLF4J MDC，使一次请求的全部日志可用
 * traceId 串联；同时将 traceId 回写到响应头 {@code X-Trace-Id}，方便用户报障时直接提供。
 * 异常发生时 {@link cn.kmbeast.config.GlobalExceptionHandler} 也会在返回体中附带该 ID。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends org.springframework.web.filter.OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String URI = "uri";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(TRACE_ID, traceId);
        MDC.put(URI, request.getRequestURI());
        try {
            response.setHeader("X-Trace-Id", traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
            MDC.remove(URI);
        }
    }
}
