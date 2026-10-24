package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.em.NotificationSource;
import cn.kmbeast.pojo.vo.NotifyPreferenceVO;
import cn.kmbeast.service.NotificationPreferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 用户通知偏好判定实现 —— 2026-10-10 新增。
 *
 * @see NotificationSource 来源分类与它受哪个开关控制
 */
@Slf4j
@Service
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    @Resource
    private UserMapper userMapper;

    @Override
    public boolean allows(Integer userId, NotificationSource source) {
        if (userId == null || source == null) {
            return false;
        }
        NotifyPreferenceVO pref;
        try {
            pref = userMapper.getNotifyPreference(userId);
        } catch (RuntimeException e) {
            // 2026-10-24 改向（用户定：读不到就不发）：原实现按「开启」放行，效果是
            // 用户明明关掉了「预约提醒」，一旦读开关失败（例如漏跑 08 迁移脚本、列不存在）
            // 就重新开始被打扰，且与他设置的界面状态相反。
            // 现在跳过投递 —— 少发一条通知可由消息中心补看，错发打扰补不回来。
            // 打成 error 是必须的：这条分支一旦持续命中，等于全站通知静默停发，不能只留一行 warn。
            log.error("读取通知开关失败，本次不投递（请确认 08_notify_preference 迁移是否已执行）: "
                    + "userId={}, source={}", userId, source, e);
            return false;
        }
        if (pref == null) {
            // 用户查不到（已注销/脏数据）：不投递
            log.warn("通知开关查询无结果，跳过发送: userId={}, source={}", userId, source);
            return false;
        }
        return switch (source) {
            case APPOINTMENT -> enabled(pref.getAppointmentNotify());
            case ORDER -> enabled(pref.getOrderNotify());
            case AI_CHAT -> enabled(pref.getAiChatNotify());
            case SYSTEM -> enabled(pref.getSystemNotify());
            case FOLLOWUP -> enabled(pref.getHealthRemindNotify());
        };
    }

    /**
     * NULL 视为开启 —— 这与「读不到就不发」不矛盾，是两件事。
     *
     * <p>列在迁移脚本里是 {@code DEFAULT 1} 但可空。读到 NULL 说明<b>这一行确实存在了、
     * 只是这个开关从没被设置过</b>，按 schema 默认值（1=开启）解释才是对的；
     * 把 NULL 当关闭，等于让从没进过设置页的用户静默收不到服药/复诊提醒 ——
     * 在健康管理系统里这类漏发的代价比打扰高，所以只有<b>整行读不出来</b>（异常）才不发。
     */
    private static boolean enabled(Boolean value) {
        return value == null || value;
    }
}
