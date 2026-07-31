package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子举报实体
 */
@Data
public class PostReport {
    /** 主键ID */
    private Integer id;
    /** 举报用户ID */
    private Integer userId;
    /** 帖子ID */
    private Integer postId;
    /** 回复ID */
    private Integer replyId;
    /** 举报原因 */
    private String reason;
    /** 状态(0:待处理;1:已处理;2:已驳回) */
    private Integer status;
    /** 创建时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /** 更新时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
