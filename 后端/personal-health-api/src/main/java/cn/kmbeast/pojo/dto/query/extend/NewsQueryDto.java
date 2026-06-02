package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class NewsQueryDto extends QueryDto {

    /**
     * 资讯名
     */
    private String name;
    /**
     * 标签ID
     */
    private Integer tagId;
    /**
     * 是否推荐
     */
    private Boolean isTop;
    /**
     * 是否轮播图
     */
    private Boolean isBanner;

}
