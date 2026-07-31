package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.MallProductQueryDto;
import cn.kmbeast.pojo.entity.*;
import cn.kmbeast.pojo.vo.MallProductVO;
import cn.kmbeast.pojo.vo.MallOrderVO;
import cn.kmbeast.pojo.vo.ShoppingCartVO;

import java.util.List;

public interface MallService {
    // 商品分类
    Result<List<ProductCategory>> getCategories();
    Result<Void> saveCategory(ProductCategory category);

    // 商品
    Result<Void> saveProduct(MallProduct product);
    Result<Void> updateProduct(MallProduct product);
    Result<Void> deleteProducts(List<Long> ids);
    Result<List<MallProductVO>> queryProducts(MallProductQueryDto queryDto);
    Result<MallProductVO> getProductById(Integer id);

    // 购物车
    Result<Void> addToCart(Integer userId, Integer productId, Integer quantity);
    Result<Void> updateCart(Integer id, Integer quantity);
    Result<Void> removeFromCart(Integer id);
    Result<List<ShoppingCartVO>> getCartItems(Integer userId);

    // 订单
    Result<MallOrderVO> createOrder(Integer userId, Integer addressId, String remark);
    Result<Void> payOrder(Integer orderId, String paymentMethod);
    Result<List<MallOrderVO>> getUserOrders(Integer userId);
    Result<MallOrderVO> getOrderById(Integer id);

    // 收货地址
    Result<List<ShippingAddress>> getAddresses(Integer userId);
    Result<Void> saveAddress(ShippingAddress address);
    Result<Void> updateAddress(ShippingAddress address);
    Result<Void> deleteAddress(Integer id);
}
