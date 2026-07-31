package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子点赞实体
 */
@Data
public class PostLike {
    /** 主键ID */
    private Integer id;
    /** 用户ID */
    private Integer userId;
    /** 帖子ID */
    private Integer postId;
    /** 创建时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
