package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 健康内容实体（资讯 / 帖子 / 文章统一表）
 *
 * <p>2026-10-03 数据模型重构：本类原先只对应旧的 {@code news} 表（9 字段，无作者与统计），
 * 现已吸收旧 {@code post} 表的能力，两张表合并为一张 {@code news}：
 * <ul>
 *   <li>{@code contentType} 区分内容来源：NEWS 资讯 / POST 帖子 / ARTICLE 文章</li>
 *   <li>{@code title} 为统一标题字段（迁移期与旧 {@code name} 并存，代码统一用 title）</li>
 *   <li>补齐作者与浏览/点赞/收藏/评论/分享统计，以及发布状态与热度分</li>
 * </ul>
 *
 * <p>⚠️ 字段名 {@code title} / {@code userId} / {@code status} 等与前端接口契约一一对应，
 * 改动前请先查阅 {@code docs/数据表结构基线-20261003.md}。
 */
@Data
public class News {

    /** 内容类型：资讯 */
    public static final String TYPE_NEWS = "NEWS";
    /** 内容类型：帖子 */
    public static final String TYPE_POST = "POST";
    /** 内容类型：文章 */
    public static final String TYPE_ARTICLE = "ARTICLE";

    /** 状态：草稿 */
    public static final int STATUS_DRAFT = 0;
    /** 状态：已发布 */
    public static final int STATUS_PUBLISHED = 1;
    /** 状态：已下架 */
    public static final int STATUS_OFFLINE = 2;

    /** 主键ID */
    private Integer id;

    /** 内容类型：NEWS资讯 / POST帖子 / ARTICLE文章 */
    private String contentType;

    /** 作者用户ID（资讯可为空） */
    private Integer userId;

    /**
     * 统一标题字段。
     * <p>⚠️ 迁移期与旧字段 {@code name} 并存（title 由 name 回填），代码统一使用本字段。
     * 旧 name 待前端全部切换后废弃。
     */
    private String title;

    /**
     * 旧标题字段，仅用于迁移期向后兼容。
     * <p>新代码请使用 {@link #title}。前端若仍在读 name，需同步改造。
     */
    private String name;

    /** 摘要/导读 */
    private String summary;

    /** 内容正文 */
    private String content;

    /** 标签ID */
    private Integer tagId;

    /** 封面图URL */
    private String cover;

    /** 阅读者ID列表，以逗号分隔（历史字段，新逻辑建议走 user_health 之外的阅读记录） */
    private String readerIds;

    /** 浏览量 */
    private Integer viewCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 收藏数 */
    private Integer favoriteCount;

    /** 评论数 */
    private Integer commentCount;

    /** 分享数 */
    private Integer shareCount;

    /** 热度分（由定时任务维护，见 HotScoreServiceImpl） */
    private BigDecimal hotScore;

    /** 是否推荐（置顶） */
    private Boolean isTop;

    /** 是否进首页轮播（资讯专有字段） */
    private Boolean isBanner;

    /** 状态：0草稿 1已发布 2已下架 */
    private Integer status;

    /** 发布时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishedAt;

    /** 创建时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /** 迁移期标签名快照，仅用于数据回溯，业务逻辑不要使用 */
    private String tagNameSnapshot;

    /**
     * 兼容旧逻辑：统一返回标题，title 为空时回落到 name。
     * <p>供仍在读 {@code name} 的旧 Service/前端过渡使用，迁移完成后应改为直接读 title。
     */
    public String resolveTitle() {
        if (title != null && !title.isEmpty()) {
            return title;
        }
        return name;
    }
}
