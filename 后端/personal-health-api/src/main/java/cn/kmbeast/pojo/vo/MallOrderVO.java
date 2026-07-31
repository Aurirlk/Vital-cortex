package cn.kmbeast.pojo.vo;

import cn.kmbeast.pojo.entity.MallOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import cn.kmbeast.pojo.entity.OrderItem;

@EqualsAndHashCode(callSuper = true)
@Data
public class MallOrderVO extends MallOrder {
    private String userName;
    private String addressDetail;
    private List<OrderItem> items;
}
