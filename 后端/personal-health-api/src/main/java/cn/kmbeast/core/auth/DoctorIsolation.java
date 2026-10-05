package cn.kmbeast.core.auth;

import cn.kmbeast.pojo.em.RoleEnum;

/**
 * 三端隔离规则（2026-10-04）
 *
 * <p><b>为什么需要它</b>：原先全系统只有「登录校验」与「角色匹配」两种判断，
 * 医生（role=3）登录后虽然无法通过 {@code @Protector(role="管理员")}，
 * 但可以自由访问所有仅需登录的端点（{@code @Protector} 无 role 声明）——
 * 其中包括 {@code /user/**} 下的个人资料、健康数据、商城订单等。
 * 反向也一样：普通用户拿自己的 token 可以调 {@code /doctor/**}
 * 中任何只要求登录的端点。这不是理论风险，而是已存在的越权面。
 *
 * <p><b>隔离矩阵</b>（端前缀 → 允许的角色）：
 * <pre>
 *   /doctor/**  → 仅 医生
 *   /user/**    → 管理员、用户          （医生被拒：医生不是 user 表记录，无个人健康数据）
 *   其余端点    → 管理员、用户、医生      （公告/资讯/商品等公共读，医生可看）
 * </pre>
 *
 * <p>刻意<b>不</b>把管理员挡在 {@code /user/**} 之外：管理端后台需要查看用户数据
 * （UserManage 等），这是既有业务要求。
 *
 * <p>白名单中的 {@code /doctor/login}、{@code /doctor/reset-password} 是匿名端点，
 * 由 {@code JwtInterceptor} 先行放行，**不会走到本规则**。
 */
public final class DoctorIsolation {

    /** 医生端路径前缀 */
    public static final String DOCTOR_PREFIX = "/doctor";
    /** 用户端路径前缀 */
    public static final String USER_PREFIX = "/user";

    private DoctorIsolation() {
    }

    /**
     * 判断某个角色能否访问指定 URI。
     *
     * @param requestUri 请求 URI（可能含 context-path，如 {@code /api/personal-health/v1.0/user/xxx}）
     * @param roleId     token 中的角色编码；null 视为非法
     * @return true 允许访问
     */
    public static boolean isAllowed(String requestUri, Integer roleId) {
        if (requestUri == null || roleId == null) {
            return false;
        }

        // 非法角色编码（不在枚举内的 0/99/-1 等）一律拒绝。
        // 否则 `!isDoctor(99)` 会得到 true，把未知角色当成「非医生」放进用户端 ——
        // 属于典型的「鉴权靠否定判断」漏洞：无法识别的身份应当拒绝，而非放行。
        if (!isKnownRole(roleId)) {
            return false;
        }

        String path = normalize(requestUri);

        if (matchesPrefix(path, DOCTOR_PREFIX)) {
            // 医生端只有医生能进 —— 用户/管理员一律拒绝，避免越权查看他人接诊数据
            return isDoctor(roleId);
        }
        if (matchesPrefix(path, USER_PREFIX)) {
            // 用户端拒绝医生：医生没有 user 表记录，读到的会是 null 或他人数据
            return !isDoctor(roleId);
        }
        // 公共端点（公告/资讯/商品/AI 对话等）：三端都放行
        return true;
    }

    /**
     * 前缀匹配，必须以 {@code "/"} 或全等作为边界。
     *
     * <p>避免 {@code /doctor-admin/xxx} 被 {@code startsWith("/doctor")} 误判为医生端，
     * 同样避免未来新增 {@code /doctorXxx} 端点时误伤。
     */
    private static boolean matchesPrefix(String path, String prefix) {
        if (!path.startsWith(prefix)) {
            return false;
        }
        return path.length() == prefix.length()
                || path.charAt(prefix.length()) == '/';
    }

    /** 角色编码是否在枚举内 */
    private static boolean isKnownRole(Integer roleId) {
        return RoleEnum.exists(roleId);
    }

    /**
     * 提取可比较的路径部分。
     *
     * <p>请求 URI 形如 {@code /api/personal-health/v1.0/doctor/home}，
     * 需剥掉 context-path 之后才能判断前缀。
     *
     * <p><b>实现要点</b>：context-path 形如 {@code /api/<name>/v<major>.<minor>.<patch>}，
     * 锚点是<b>「以 /v 开头、且后面紧跟数字</b>的那一段，取它<b>之后</b>的子串。
     *
     * <p>⚠️ 曾经的错误实现用 {@code lastIndexOf("/v")} 定位锚点 ——
     * 它会匹配到路径<b>最后</b>出现的 {@code /v}（例如用户访问
     * {@code /api/.../v1.0/doctor/home} 时，{@code /doctor} 之后若无 /v 则锚点仍指向
     * {@code /v1.0}），随后 {@code indexOf('/')} 又在 {@code /v1.0/doctor/home}
     * 中找到位置 5，误判为「context-path 后无子路径」而返回 {@code "/"}，
     * 导致所有前缀判断失效、<b>隔离规则退化为全部放行</b>。
     * 该 bug 由 {@code DoctorIsolationTest} 捕获。
     */
    static String normalize(String uri) {
        if (uri == null) {
            return "";
        }
        // 去掉查询串
        int q = uri.indexOf('?');
        String path = q >= 0 ? uri.substring(0, q) : uri;

        // 逐段扫描，定位「/v + 数字」开头的版本段
        String[] segments = path.split("/");
        int versionStart = -1;
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if (seg.length() >= 2 && (seg.charAt(0) == 'v' || seg.charAt(0) == 'V')
                    && Character.isDigit(seg.charAt(1))) {
                versionStart = i;
                break;
            }
        }
        if (versionStart < 0) {
            return path;      // 不含 context-path，原样返回
        }
        // 版本段之后若无内容，归一为 "/"
        if (versionStart >= segments.length - 1) {
            return "/";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = versionStart + 1; i < segments.length; i++) {
            sb.append('/').append(segments[i]);
        }
        return sb.length() == 0 ? "/" : sb.toString();
    }

    private static boolean isDoctor(Integer roleId) {
        return Integer.valueOf(RoleEnum.DOCTOR.getRole()).equals(roleId);
    }
}
