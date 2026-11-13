package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一评论实体（2026-10-03 合并 evaluations + post_reply）
 *
 * <p><b>合并说明</b>：原系统存在两套评论实现——
 * <ul>
 *   <li>{@code evaluations}：多态设计（content_type + content_id），可评论任意内容，设计正确</li>
 *   <li>{@code post_reply}：只能评论 post 的专用表，属重复建设</li>
 * </ul>
 * 现在统一为 {@code comment} 表，保留多态设计。
 *
 * <p>⚠️ 字段名与旧 {@code evaluations} 不同（target/targetId 取代 content/contentId），
 * 前端与 Service 需同步改造。结构基线见 {@code docs/数据表结构基线-20261003.md}。
 */
@Data
public class Comment {

    /** 目标类型：内容（资讯/帖子） */
    public static final String TARGET_NEWS = "NEWS";
    /** 目标类型：题目 */
    public static final String TARGET_QUIZ = "QUIZ";

    /** 状态：已删除 */
    public static final int STATUS_DELETED = 0;
    /** 状态：正常 */
    public static final int STATUS_NORMAL = 1;

    /** 主键ID */
    private Integer id;

    /** 目标类型：NEWS / QUIZ */
    private String targetType;

    /** 目标ID（对应 targetType 指向表的业务主键） */
    private Integer targetId;

    /** 评论人 */
    private Integer userId;

    /** 被回复人（同 evaluations.replier_id） */
    private Integer replyToId;

    /** 父评论ID（树形结构，顶级评论为 null） */
    private Integer parentId;

    /** 评论内容 */
    private String content;

    /** 点赞数 */
    private Integer likeCount;

    /** 点赞用户ID列表（JSON，兼容旧 evaluations.upvote_list） */
    private String upvoteList;

    /** 状态：0已删除 1正常 */
    private Integer status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
