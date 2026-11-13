package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论坛标签实体
 */
@Data
public class PostTag {
    /** 主键ID */
    private Integer id;
    /** 标签名称 */
    private String name;
    /** 排序 */
    private Integer sortOrder;
    /** 创建时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
