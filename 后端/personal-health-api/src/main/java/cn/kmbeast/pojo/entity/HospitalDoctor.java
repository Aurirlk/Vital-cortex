package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医院医生实体（独立身份，2026-10-03 与 user 表解耦）
 *
 * <p><b>解耦背景</b>：原先 {@code userId} 关联 {@code user} 表，导致医生既是执业身份又是登录账号，
 * 改一次资料要同步两张表，{@code user.role} 语义也混乱。现改为医生拥有独立账号体系，
 * 由管理员统一管理；医生登录后仅进入医生端，不可访问用户端与管理端。
 *
 * <p><b>登录约定</b>：
 * <ul>
 *   <li>{@code username} + {@code password}（BCrypt，口径与 user 表一致）</li>
 *   <li>存量医生迁移后 {@code password} 为 NULL、{@code needInitPassword=1}，
 *       首次登录时强制设置初始密码；管理员也可随时通过管理端重置</li>
 * </ul>
 *
 * <p><b>⚠️ 历史字段</b>：{@code userId} / {@code legacyUserId} 仅用于历史数据追溯，
 * 新代码<b>不得</b>再通过 userId 关联用户表。结构基线见 {@code docs/数据表结构基线-20261003.md}。
 */
@Data
public class HospitalDoctor {

    /** 职称：主任医师 */
    public static final String TITLE_TITLE = "TITLE";
    /** 职称：副主任医师 */
    public static final String TITLE_ASSOC = "ASSOC";
    /** 职称：主治医师 */
    public static final String TITLE_ATTENDING = "ATTENDING";
    /** 职称：住院医师 */
    public static final String TITLE_RESIDENT = "RESIDENT";

    /** 主键ID（现有数据为 3001/3002/3003 段位） */
    private Integer id;

    /**
     * 医生登录账号（唯一）。
     * <p>存量迁移规则：{@code doctor} + id，如 doctor3001。
     */
    private String username;

    /**
     * 登录密码（BCrypt 哈希）。
     * <p>⚠️ 严禁返回给前端。迁移后存量医生为 NULL，须先初始化密码。
     */
    private String password;

    /** 密码盐值（保留字段，当前 BCrypt 不依赖盐） */
    private String salt;

    /**
     * 是否需要初始化密码：1 表示首次登录须设置密码。
     * <p>管理员重置密码后应置 0。
     */
    private Integer needInitPassword;

    /**
     * 迁移前关联的 user.id，<b>仅历史追溯用</b>，业务逻辑不得引用。
     */
    private Integer legacyUserId;

    /**
     * 旧的 user 关联字段，已废弃。
     * <p>保留字段仅为兼容旧 SQL，迁移完成后应移除。
     *
     * @deprecated 医生与 user 已解耦，请改用 {@link #legacyUserId} 仅作追溯
     */
    @Deprecated
    private Integer userId;

    /** 医生姓名 */
    private String name;

    /** 头像 */
    private String avatar;

    /**
     * 职称文本（历史字段，如「主任医师」）。
     * <p>新代码请用结构化的 {@link #titleLevel}，本字段待前端切换后废弃。
     */
    private String title;

    /**
     * 职称枚举：TITLE 主任医师 / ASSOC 副主任医师 / ATTENDING 主治医师 / RESIDENT 住院医师。
     * <p>由旧 title 文本回填：主任医师→TITLE，副主任医师→ASSOC，主治医师→ATTENDING，住院医师→RESIDENT。
     */
    private String titleLevel;

    /** 所属主科室ID */
    private Integer departmentId;

    /**
     * 所属科室ID数组（JSON），支持一个医生兼多科。
     * <p>形如 {@code [1,3]}。多科室场景下应优先使用本字段而非 {@link #departmentId}。
     */
    private String deptIds;

    /** 擅长领域（JSON 字符串数组），形如 {@code ["高血压","糖尿病"]} */
    private String specialties;

    /** 医生简介 */
    private String introduction;

    /** 擅长描述（历史字段） */
    private String expertise;

    /** 执业资质 */
    private String qualifications;

    /** 执业证号 */
    private String regNo;

    /** 性别 */
    private String gender;

    /** 邮箱 */
    private String email;

    /** 联系电话 */
    private String phone;

    /** 是否在线（1 在线 / 0 离线） */
    private Integer isOnline;

    /** 累计接诊数 */
    private Integer visitCount;

    /** 评分 0~5 */
    private BigDecimal rating;

    /** 排序权重（小在前） */
    private Integer sortOrder;

    /** 状态：0停用 1启用 */
    private Integer status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 是否处于可登录状态：启用且已设置密码 */
    public boolean isLoginable() {
        return status != null && status == 1;
    }
}
