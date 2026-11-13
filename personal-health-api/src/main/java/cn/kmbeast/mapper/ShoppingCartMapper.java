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

    /**
     * 按主键取购物车行。
     *
     * <p>2026-10-10 新增：改数量 / 删除前必须先拿到 {@code userId} 做归属校验。
     * 原来这两个接口只认 id，任何登录用户改个 id 就能篡改、清空他人购物车。
     */
    ShoppingCart getById(@Param("id") Integer id);
}
