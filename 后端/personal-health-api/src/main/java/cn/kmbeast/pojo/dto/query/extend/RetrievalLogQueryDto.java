package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 检索日志查询 DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class RetrievalLogQueryDto extends QueryDto {

    /** 检索词（模糊） */
    private String queryText;

    /** 调用端 */
    private String audience;

    /** 实际走的路径：hybrid / sparse_only / dense_only / none */
    private String path;

    /** 提问用户 */
    private Integer userId;
}
