package cn.kmbeast.pojo.vo;

import cn.kmbeast.pojo.entity.Comment;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评论视图对象
 *
 * <p>在 {@link Comment} 基础上补充前端渲染所需的展示字段（评论人昵称/头像、被回复人昵称、
 * 子回复条数），避免前端再发一次用户查询请求（规避 N+1 调用）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class CommentVO extends Comment {

    /** 评论人昵称 */
    private String userName;

    /** 评论人头像 */
    private String userAvatar;

    /** 被回复人昵称（仅子回复有值） */
    private String replyToName;

    /** 子回复条数（一级评论用） */
    private Integer childCount;
}
