package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子回复实体
 */
@Data
public class PostReply {
    /** 主键ID */
    private Integer id;
    /** 帖子ID */
    private Integer postId;
    /** 回复用户ID */
    private Integer userId;
    /** 父回复ID */
    private Integer parentId;
    /** 回复内容 */
    private String content;
    /** 点赞数 */
    private Integer likeCount;
    /** 创建时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
