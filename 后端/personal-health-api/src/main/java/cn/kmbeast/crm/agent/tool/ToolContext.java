package cn.kmbeast.crm.agent.tool;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具执行上下文（线程级）。
 *
 * <p>存在理由：{@code BaseReActAgent} 把工具丢进 {@code toolExecutor} 线程池执行，
 * 工具跑在<b>与请求线程不同的线程</b>上。请求线程里 {@code LocalThreadHolder}
 * （{@code userId}/{@code roleId}）无法跨线程传递，因此本类在提交线程池前把身份
 * 捕获、在池线程里恢复，是 Agent 工具获取"我是谁、我是哪类角色"的唯一可信来源。
 *
 * <p>🔴 <b>铁律</b>：工具内部<b>禁止</b>接受来自 LLM 参数的 userId/patientId/doctorId
 * 作为真实身份。用户端一律用 {@link #getUserId()}（来自 JWT）；医生端身份由调用方
 * 通过 {@link #setIdentity(Integer, Integer)} 注入，工具内不可被参数篡改。
 */
public class ToolContext {

    private static final ThreadLocal<Map<String, Object>> context = new ThreadLocal<>();

    /** 用户/医生 ID（与 LocalThreadHolder.getUserId 同源） */
    public static final String KEY_USER_ID = "userId";
    /** 手机号（CRM 历史链路遗留，部分工具用于落会话） */
    public static final String KEY_PHONE = "phoneNumber";
    /** 角色编码：1=管理员 2=用户 3=医生（见 RoleEnum） */
    public static final String KEY_ROLE_ID = "roleId";
    /** 医生账号 ID（仅医生端有意义） */
    public static final String KEY_DOCTOR_ID = "doctorId";
    /** 客户端 IP（审计用，P1-3 补齐工具层审计上下文） */
    public static final String KEY_CLIENT_IP = "clientIp";
    /** User-Agent（审计用） */
    public static final String KEY_USER_AGENT = "userAgent";
    /** 请求方法（审计用） */
    public static final String KEY_REQUEST_METHOD = "requestMethod";
    /** 请求 URL（审计用） */
    public static final String KEY_REQUEST_URL = "requestUrl";
    /** 本轮执行对话的 Agent key（阶段 1：委派工具据此判断「我是谁、能委派给谁」） */
    public static final String KEY_AGENT_KEY = "agentKey";
    /** 当前 Agent 运行的 run_id（阶段 1：委派产生的子运行要挂到它下面） */
    public static final String KEY_RUN_ID = "runId";
    /** 已发生的 Agent 委派层数（阶段 1：防止委派无限递归） */
    public static final String KEY_HANDOFF_DEPTH = "handoffDepth";

    public static void set(String key, Object value) {
        Map<String, Object> ctx = context.get();
        if (ctx == null) {
            ctx = new HashMap<>();
            context.set(ctx);
        }
        ctx.put(key, value);
    }

    public static Object get(String key) {
        Map<String, Object> ctx = context.get();
        return ctx != null ? ctx.get(key) : null;
    }

    public static String getString(String key) {
        Object val = get(key);
        return val != null ? val.toString() : null;
    }

    public static Integer getInt(String key) {
        Object val = get(key);
        if (val instanceof Integer integer) return integer;
        if (val instanceof Number number) return number.intValue();
        return null;
    }

    public static void clear() {
        context.remove();
    }

    /** 当前调用方用户/医生 ID（与 LocalThreadHolder.getUserId 同源） */
    public static Integer getUserId() {
        return getInt(KEY_USER_ID);
    }

    /**
     * 一次性写入身份（手机号 + 用户ID），兼容旧调用方。
     */
    public static void setPhoneAndUserId(String phoneNumber, Integer userId) {
        set(KEY_PHONE, phoneNumber);
        set(KEY_USER_ID, userId);
    }

    /**
     * 写入角色身份。
     *
     * @param roleId   角色编码（1 管理员 / 2 用户 / 3 医生）
     * @param doctorId 医生账号 ID；非医生传 null
     */
    public static void setIdentity(Integer roleId, Integer doctorId) {
        set(KEY_ROLE_ID, roleId);
        set(KEY_DOCTOR_ID, doctorId);
    }

    /**
     * 当前调用方角色（1 管理员 / 2 用户 / 3 医生）。
     *
     * <p>原注释写的是「null 视为普通用户（2）」，但本方法<b>不会</b>做这个归一 ——
     * 没写身份就是 null。差一个字，读的人就会写出 {@code if (roleId == 2)} 这种
     * 漏掉 null 的判定；「null 该算哪一端」是各调用点自己的决定
     * （例如 {@code DataQueryTool} 把它归到用户轨，而运行审计把它记成「端未知」）。</p>
     */
    public static Integer getRoleId() {
        return getInt(KEY_ROLE_ID);
    }

    /** 当前医生账号 ID（仅医生端非空） */
    public static Integer getDoctorId() {
        return getInt(KEY_DOCTOR_ID);
    }

    /**
     * 本轮执行对话的 Agent key。
     *
     * <p>工具跑在线程池里，{@code BaseReActAgent} 的 {@code agentKey} ThreadLocal
     * 在那个线程上是空的。委派工具必须靠这份透传值才知道「发起方是谁」，
     * 否则无法校验「A 是否被允许委派给 B」。</p>
     */
    public static String getAgentKey() {
        return getString(KEY_AGENT_KEY);
    }

    /** 当前 Agent 运行的 run_id；无记录（表未建 / 未开启记录）时为 null。 */
    public static String getRunId() {
        return getString(KEY_RUN_ID);
    }

    /** 已发生的委派层数；0 表示当前是根 Agent，没有发生过委派。 */
    public static int getHandoffDepth() {
        Integer depth = getInt(KEY_HANDOFF_DEPTH);
        return depth == null ? 0 : depth;
    }

    // ==================== HTTP 上下文（P1-3：工具层审计补齐） ====================

    /**
     * 写入 HTTP 请求上下文（客户端 IP / UA / 方法 / URL）。
     *
     * <p>工具运行在线程池里，没有 {@code HttpServletRequest}；但审计需要记录来源。
     * 调用方在请求线程内通过 {@link #applyHttpMetaFromRequest()} 从
     * {@code RequestContextHolder} 取一次写入，{@code BaseReActAgent} 会随身份一起
     * 跨线程透传，工具内即可读出用于审计。</p>
     */
    public static void setHttpMeta(String clientIp, String userAgent, String method, String url) {
        set(KEY_CLIENT_IP, clientIp);
        set(KEY_USER_AGENT, userAgent);
        set(KEY_REQUEST_METHOD, method);
        set(KEY_REQUEST_URL, url);
    }

    /** 客户端 IP（无则 null） */
    public static String getClientIp() {
        return getString(KEY_CLIENT_IP);
    }

    /** User-Agent（无则 null） */
    public static String getUserAgent() {
        return getString(KEY_USER_AGENT);
    }

    /** 请求方法（无则 null） */
    public static String getRequestMethod() {
        return getString(KEY_REQUEST_METHOD);
    }

    /** 请求 URL（无则 null） */
    public static String getRequestUrl() {
        return getString(KEY_REQUEST_URL);
    }

    /**
     * 从当前请求线程的 {@code HttpServletRequest} 抓取 HTTP 上下文写入本上下文。
     *
     * <p>异步/测试环境（无请求属性）安全跳过，绝不抛异常。</p>
     */
    public static void applyHttpMetaFromRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return;
            }
            HttpServletRequest req = attrs.getRequest();
            if (req == null) {
                return;
            }
            setHttpMeta(clientIpOf(req), trimToNull(req.getHeader("User-Agent")),
                    req.getMethod(), req.getRequestURI());
        } catch (Exception e) {
            // 异步/非 Web 环境：静默跳过，审计该字段留 null
        }
    }

    private static String clientIpOf(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return req.getRemoteAddr();
    }

    private static String trimToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
