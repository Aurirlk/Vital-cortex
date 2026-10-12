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
            // 开关读不出来不该让整条业务链路失败（例如漏跑 08 迁移脚本时列不存在）。
            // 这里放行：宁可多发一条通知，也不要因为「通知配置查不到」把下单/挂号打挂。
            log.warn("读取通知开关失败，按开启处理: userId={}, source={}", userId, source, e);
            return true;
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
     * NULL 视为开启。
     *
     * <p>列在迁移脚本里是 {@code DEFAULT 1}，但可空；漏跑迁移或历史行可能读到 NULL。
     * 通知属于「默认想收到」的东西，读不到按开启处理，与 {@code DEFAULT 1} 一致 ——
     * 反向默认（NULL 当关闭）会让一次漏跑迁移静默吃掉全站通知，且毫无报错。
     */
    private static boolean enabled(Boolean value) {
        return value == null || value;
    }
}
