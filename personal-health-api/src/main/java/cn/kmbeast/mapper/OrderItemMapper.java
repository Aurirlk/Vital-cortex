package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface OrderItemMapper {
    void save(OrderItem item);
    void batchSave(@Param("items") List<OrderItem> items);
    List<OrderItem> queryByOrderId(@Param("orderId") Integer orderId);
}
