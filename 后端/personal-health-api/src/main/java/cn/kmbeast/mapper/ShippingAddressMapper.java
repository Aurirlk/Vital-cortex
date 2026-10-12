package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.ShippingAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ShippingAddressMapper {
    void save(ShippingAddress address);
    void update(ShippingAddress address);
    void delete(@Param("id") Integer id);
    List<ShippingAddress> queryByUserId(@Param("userId") Integer userId);
    ShippingAddress getDefault(@Param("userId") Integer userId);

    /**
     * 按主键取收货地址。
     *
     * <p>2026-10-10 新增：改 / 删地址、下单绑定地址前都必须先拿到 {@code userId}
     * 做归属校验。原来这三个入口只认 id，任何登录用户都能改删他人收货地址，
     * 或把订单挂到别人的地址上。
     */
    ShippingAddress getById(@Param("id") Integer id);
}
