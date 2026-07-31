package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论坛帖子实体
 */
@Data
public class Post {
    /** 主键ID */
    private Integer id;
    /** 发帖用户ID */
    private Integer userId;
    /** 帖子标题 */
    private String title;
    /** 帖子内容 */
    private String content;
    /** 封面图 */
    private String cover;
    /** 分类ID */
    private Integer tagId;
    /** 浏览数 */
    private Integer viewCount;
    /** 点赞数 */
    private Integer likeCount;
    /** 收藏数 */
    private Integer favoriteCount;
    /** 评论数 */
    private Integer commentCount;
    /** 分享数 */
    private Integer shareCount;
    /** 热度分 */
    private Double hotScore;
    /** 状态(0:草稿;1:已发布;2:已锁定) */
    private Integer status;
    /** 是否置顶 */
    private Boolean isTop;
    /** 创建时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /** 更新时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
