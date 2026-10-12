package cn.kmbeast.context;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户身份支持器
 *
 * @author 【B站：程序员晨星】
 */
public class LocalThreadHolder {

    private static final ThreadLocal<Map<String, Integer>> USER_HOLDER = new ThreadLocal<>();

    /**
     * 设置用户信息
     *
     * @param userId   用户ID
     * @param userRole 用户角色
     * @author 【B站：程序员晨星】
     */
    public static void setUserId(Integer userId, Integer userRole) {
        Map<String, Integer> map = new HashMap<>();
        map.put("userId", userId);
        map.put("userRole", userRole);
        USER_HOLDER.set(map);
    }

    /**
     * 取出用户ID
     *
     * @return Integer，未设置时返回 null
     */
    public static Integer getUserId() {
        Map<String, Integer> map = USER_HOLDER.get();
        return map != null ? map.get("userId") : null;
    }

    /**
     * 取出用户角色
     *
     * @return Integer，未设置时返回 null
     */
    public static Integer getRoleId() {
        Map<String, Integer> map = USER_HOLDER.get();
        return map != null ? map.get("userRole") : null;
    }

    /**
     * 当前请求者是否为管理员。
     *
     * <p>2026-10-10 新增：项目里存在多处「资源归属校验」需要「本人或管理员」语义，
     * 此前各处以 {@code Integer.valueOf(RoleEnum.ADMIN.getRole()).equals(
     * LocalThreadHolder.getRoleId())} 重复书写（见 {@code AgentController}、
     * {@code MessageServiceImpl}）。收敛到一处，避免判定口径不一致导致越权。
     *
     * @return true 表示当前请求者角色为管理员；未登录 / 角色缺失时返回 false
     */
    public static boolean isAdmin() {
        return Integer.valueOf(cn.kmbeast.pojo.em.RoleEnum.ADMIN.getRole()).equals(getRoleId());
    }

    /**
     * 判断给定归属人是否为当前请求者本人（<b>不含</b>管理员特权）。
     *
     * <p>2026-10-10 新增。与 {@link #isSelfOrAdmin} 的差别只有「管理员算不算」这一点，
     * 但语义差别很大：需要区分「这次操作是不是用户自己做的」时必须用本方法 ——
     * 例如取消预约要判断「是本人自己取消，还是医生/管理员替他取消」，
     * 只有后者才该给他推提醒。用 {@code isSelfOrAdmin} 会把管理员的取消误判成
     * 「本人操作」而漏发。
     *
     * @param ownerId 资源归属人的用户 ID；为 null 返回 false（fail-closed）
     * @return true 表示当前请求者就是该归属人
     */
    public static boolean isSelf(Integer ownerId) {
        Integer current = getUserId();
        return ownerId != null && current != null && ownerId.equals(current);
    }

    /**
     * 判断给定归属人是否为当前请求者本人（或当前请求者是管理员）。
     *
     * <p>用于 IDOR 防护：写/删/读私有资源前必须校验。
     * {@code ownerId} 为 null 视为无法确认归属，一律拒绝（fail-closed），
     * 避免脏数据把校验「绕过」成放行。
     *
     * @param ownerId 资源归属人的用户 ID
     * @return true 表示允许访问
     */
    public static boolean isSelfOrAdmin(Integer ownerId) {
        return isAdmin() || isSelf(ownerId);
    }

    /**
     * 防止内存溢出，当前线程结束，释放资源
     *
     * @author 【B站：程序员晨星】
     */
    public static void clear() {
        USER_HOLDER.remove();
    }

}
