package cn.kmbeast.pojo.vo;

import cn.kmbeast.pojo.entity.MallProduct;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class MallProductVO extends MallProduct {
    private String categoryName;
}
