package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User {

    /**
     * 用户编号
     */
    private Integer id;

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户密码
     */
    private String userPwd;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户邮箱
     */
    private String userEmail;

    /**
     * 手机号（唯一）
     *
     * <p>2026-10-03 新增：原先系统<b>根本没有手机号字段</b>（全库只有
     * {@code shipping_address.receiver_phone} 这个收货地址电话），导致短信登录是死功能、
     * 患者选择器只能显示裸 ID。本字段用途：
     * <ul>
     *   <li>短信验证码登录（替代原先「固定密码 123456」的演示桩）</li>
     *   <li>患者选择器显示「姓名 + 手机后4位」，便于人工核对</li>
     *   <li>医生端按手机号检索患者</li>
     * </ul>
     *
     * <p>⚠️ {@code user_account} 已被 admin / lily 这类逻辑账号占用，
     * 手机号必须独立成字段，不可复用 user_account。
     */
    private String phone;

    /**
     * 手机号是否已验证：0 未验证，1 已验证
     *
     * <p>未验证的手机号不允许用于短信登录，避免被冒用。
     */
    private Integer phoneVerified;

    /**
     * 用户角色
     */
    private Integer userRole;

    /**
     * 可登录状态(0:可用；1：不可用)
     */
    private Boolean isLogin;

    /**
     * 禁言状态(0:可用；1：不可用)
     */
    private Boolean isWord;

    /**
     * 是否 VIP（0:否；1:是）
     */
    private Boolean isVip;

    /**
     * VIP 到期时间（null=永久有效）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime vipExpireTime;

    /**
     * 用户注册时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // ================================================================
    // 用户设置（2026-10-10 新增）
    //
    // 这些字段原先在库里<b>没有任何落脚点</b>：既没有 user_settings 表（当初有意
    // 简化掉了，表太多），也没并进 user 表 —— 于是设置页只能读到硬编码默认值、
    // 保存必然失败。现按「并入 user 表」的原意直接加列。
    //
    // 布尔项一律 TINYINT(1)，与上面 is_login / is_word / is_vip 同一口径；
    // 实体里用 Boolean，NULL（历史行）由 Service 兜底成默认值。
    // 建表/迁移见 Data/02_迁移脚本/08_user_settings_columns.sql。
    // ================================================================

    /** 界面语言：zh-CN / en-US */
    private String language;

    /** 时区：Asia/Shanghai / America/New_York / Europe/London */
    private String timezone;

    /** 系统通知开关 */
    private Boolean notifySystem;

    /** 健康提醒开关 */
    private Boolean notifyHealthRemind;

    /** AI 对话通知开关 */
    private Boolean notifyAiChat;

    /** 预约提醒开关 */
    private Boolean notifyAppointment;

    /** 订单通知开关 */
    private Boolean notifyOrder;

    /** 是否允许 AI 读取健康数据 */
    private Boolean privacyHealthDataShare;

    /** 资料可见性：public 所有人 / private 仅自己 */
    private String privacyProfileVisibility;

    /** 默认 AI 角色 */
    private String aiDefaultAgent;

    /** 对话风格：professional / friendly / concise */
    private String aiChatStyle;

    /** 允许联网搜索 */
    private Boolean aiEnableWebSearch;

    /** 允许检索知识库 */
    private Boolean aiEnableKnowledgeBase;

    /** 保留上下文轮数（1~20） */
    private Integer aiContextRounds;
}
