package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.ShoppingCart;
import cn.kmbeast.pojo.vo.ShoppingCartVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ShoppingCartMapper {
    void save(ShoppingCart cart);
    void update(ShoppingCart cart);
    void delete(@Param("id") Integer id);
    void deleteByUserAndProduct(@Param("userId") Integer userId, @Param("productId") Integer productId);
    List<ShoppingCartVO> queryByUserId(@Param("userId") Integer userId);
    ShoppingCart getByUserAndProduct(@Param("userId") Integer userId, @Param("productId") Integer productId);
}
