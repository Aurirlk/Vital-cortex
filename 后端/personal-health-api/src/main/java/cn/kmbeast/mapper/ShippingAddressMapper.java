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
}
