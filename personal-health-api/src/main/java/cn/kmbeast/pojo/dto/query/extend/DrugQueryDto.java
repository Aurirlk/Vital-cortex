package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DrugQueryDto extends QueryDto {

    private Integer id;
    private String name;
    private String category;
    private Boolean isOtc;
    private Boolean status;
    private Integer userId;
}
