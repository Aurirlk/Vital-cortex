package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PostQueryDto extends QueryDto {
    /** 帖子标题 */
    private String title;
    /** 分类ID */
    private Integer tagId;
    /** 用户ID */
    private Integer userId;
    /** 状态 */
    private Integer status;
    /** 是否置顶 */
    private Boolean isTop;
    /** 搜索关键词 */
    private String keyword;
    /** 排序方式(hot/time) */
    private String orderBy;
}
