package cn.kmbeast.pojo.vo;

import cn.kmbeast.pojo.entity.ShoppingCart;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
public class ShoppingCartVO extends ShoppingCart {
    private String productName;
    private String productCover;
    private BigDecimal productPrice;
    private Integer stock;
}
