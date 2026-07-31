package cn.kmbeast.Interceptor;

import cn.kmbeast.crm.config.CrmConfig;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * CRM 接口 API Key 拦截器（SEC-02 整改）。
 *
 * <p>问题背景：{@code /crm/**} 被整体排除在 JWT 拦截器之外，而 API Key 校验散落在各个
 * Controller 方法里逐个手写。结果 {@code /crm/history/{phone}}、{@code /crm/sqlite/backup}
 * 三个方法漏写校验，形成匿名遍历手机号即可拖走全平台问诊记录的通道。
 *
 * <p>整改思路：把校验从「每个方法各自记得写」改为「拦截器统一 fail-closed」——
 * 新增接口默认受保护，除非显式加入白名单。
 */
@Component
public class CrmApiKeyInterceptor implements HandlerInterceptor {

    private static final String HEADER_NAME = "X-CRM-API-Key";

    @Resource
    private CrmConfig crmConfig;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 放行 CORS 预检
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String provided = request.getHeader(HEADER_NAME);
        String expected = crmConfig.getCrmApiKey();

        if (expected == null || expected.trim().isEmpty()) {
            // 服务端未配置密钥时一律拒绝，绝不退化为"无需认证"
            reject(response, "CRM 服务端未配置 API 密钥，拒绝访问");
            return false;
        }

        if (provided == null || !constantTimeEquals(expected, provided)) {
            reject(response, "无效的API密钥");
            return false;
        }
        return true;
    }

    /**
     * 定长时间比较，避免通过响应时间差逐字节爆破密钥。
     */
    private boolean constantTimeEquals(String expected, String provided) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    private void reject(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Result<String> error = ApiResult.error(message);
        try (Writer writer = response.getWriter()) {
            writer.write(JSONObject.toJSONString(error));
            writer.flush();
        }
    }
}
