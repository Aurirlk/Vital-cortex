package cn.kmbeast.service;

import cn.kmbeast.pojo.em.NotificationSource;

/**
 * 用户通知偏好判定 —— 2026-10-10 新增。
 *
 * <p>设置页的五个通知开关此前是纯装饰：发送方各写各的，没人读它们。
 * 所有业务通知在落库前统一过这里，开关才真正生效。
 */
public interface NotificationPreferenceService {

    /**
     * 该用户是否愿意接收这一类通知。
     *
     * @param userId 收件人；为 null 时返回 false（没有收件人就不发）
     * @param source 通知来源分类
     * @return true = 可以发
     */
    boolean allows(Integer userId, NotificationSource source);
}
