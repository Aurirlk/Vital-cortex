package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class MallProductQueryDto extends QueryDto {
    private Integer categoryId;
    private String name;
    private Integer status;
    private Integer isHot;
    private Integer isNew;
    private String keyword;
}
