package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.*;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.MallProductQueryDto;
import cn.kmbeast.pojo.entity.*;
import cn.kmbeast.pojo.vo.MallProductVO;
import cn.kmbeast.pojo.vo.MallOrderVO;
import cn.kmbeast.pojo.vo.ShoppingCartVO;
import cn.kmbeast.service.MallService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
public class MallServiceImpl implements MallService {

    @Resource private ProductCategoryMapper categoryMapper;
    @Resource private MallProductMapper productMapper;
    @Resource private ShoppingCartMapper cartMapper;
    @Resource private MallOrderMapper orderMapper;
    @Resource private OrderItemMapper orderItemMapper;
    @Resource private ShippingAddressMapper addressMapper;

    @Override
    public Result<List<ProductCategory>> getCategories() {
        return ApiResult.success(categoryMapper.queryAll());
    }

    @Override
    public Result<Void> saveCategory(ProductCategory category) {
        category.setCreateTime(LocalDateTime.now());
        categoryMapper.save(category);
        return ApiResult.success();
    }

    @Override
    public Result<Void> saveProduct(MallProduct product) {
        product.setSalesCount(0);
        product.setCreateTime(LocalDateTime.now());
        productMapper.save(product);
        return ApiResult.success();
    }

    @Override
    public Result<Void> updateProduct(MallProduct product) {
        productMapper.update(product);
        return ApiResult.success();
    }

    @Override
    public Result<Void> deleteProducts(List<Long> ids) {
        productMapper.batchDelete(ids);
        return ApiResult.success();
    }

    @Override
    public Result<List<MallProductVO>> queryProducts(MallProductQueryDto queryDto) {
        List<MallProductVO> list = productMapper.query(queryDto);
        Integer count = productMapper.queryCount(queryDto);
        return PageResult.success(list, count);
    }

    @Override
    public Result<MallProductVO> getProductById(Integer id) {
        return ApiResult.success(productMapper.getById(id));
    }

    @Override
    public Result<Void> addToCart(Integer userId, Integer productId, Integer quantity) {
        ShoppingCart existing = cartMapper.getByUserAndProduct(userId, productId);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
            cartMapper.update(existing);
        } else {
            ShoppingCart cart = new ShoppingCart();
            cart.setUserId(userId);
            cart.setProductId(productId);
            cart.setQuantity(quantity);
            cart.setCreateTime(LocalDateTime.now());
            cartMapper.save(cart);
        }
        return ApiResult.success();
    }

    @Override
    public Result<Void> updateCart(Integer id, Integer quantity) {
        ShoppingCart cart = new ShoppingCart();
        cart.setId(id);
        cart.setQuantity(quantity);
        cartMapper.update(cart);
        return ApiResult.success();
    }

    @Override
    public Result<Void> removeFromCart(Integer id) {
        cartMapper.delete(id);
        return ApiResult.success();
    }

    @Override
    public Result<List<ShoppingCartVO>> getCartItems(Integer userId) {
        return ApiResult.success(cartMapper.queryByUserId(userId));
    }

    @Override
    @Transactional
    public Result<MallOrderVO> createOrder(Integer userId, Integer addressId, String remark) {
        List<ShoppingCartVO> cartItems = cartMapper.queryByUserId(userId);
        if (cartItems.isEmpty()) {
            return ApiResult.error("购物车为空");
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ShoppingCartVO item : cartItems) {
            totalAmount = totalAmount.add(item.getProductPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        MallOrder order = new MallOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setActualAmount(totalAmount);
        order.setStatus(0);
        order.setShippingAddressId(addressId);
        order.setRemark(remark);
        order.setCreateTime(LocalDateTime.now());
        orderMapper.save(order);
        for (ShoppingCartVO item : cartItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setProductId(item.getProductId());
            orderItem.setProductName(item.getProductName());
            orderItem.setProductPrice(item.getProductPrice());
            orderItem.setProductCover(item.getProductCover());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setSubtotal(item.getProductPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            orderItem.setCreateTime(LocalDateTime.now());
            orderItemMapper.save(orderItem);
            productMapper.decrementStock(item.getProductId(), item.getQuantity());
        }
        for (ShoppingCartVO item : cartItems) {
            cartMapper.delete(item.getId());
        }
        return ApiResult.success(orderMapper.getById(order.getId()));
    }

    @Override
    @Transactional
    public Result<Void> payOrder(Integer orderId, String paymentMethod) {
        MallOrder order = orderMapper.getById(orderId);
        if (order == null) {
            return ApiResult.error("订单不存在");
        }
        if (order.getStatus() != 0) {
            return ApiResult.error("订单状态异常");
        }
        MallOrder update = new MallOrder();
        update.setId(orderId);
        update.setStatus(1);
        update.setPaymentMethod(paymentMethod);
        update.setPaymentTime(LocalDateTime.now());
        orderMapper.update(update);
        List<OrderItem> items = orderItemMapper.queryByOrderId(orderId);
        for (OrderItem item : items) {
            productMapper.incrementSalesCount(item.getProductId(), item.getQuantity());
        }
        return ApiResult.success();
    }

    @Override
    public Result<List<MallOrderVO>> getUserOrders(Integer userId) {
        return ApiResult.success(orderMapper.queryByUserId(userId));
    }

    @Override
    public Result<MallOrderVO> getOrderById(Integer id) {
        MallOrderVO order = orderMapper.getById(id);
        if (order != null) {
            order.setItems(orderItemMapper.queryByOrderId(id));
        }
        return ApiResult.success(order);
    }

    @Override
    public Result<List<ShippingAddress>> getAddresses(Integer userId) {
        return ApiResult.success(addressMapper.queryByUserId(userId));
    }

    @Override
    public Result<Void> saveAddress(ShippingAddress address) {
        address.setCreateTime(LocalDateTime.now());
        addressMapper.save(address);
        return ApiResult.success();
    }

    @Override
    public Result<Void> updateAddress(ShippingAddress address) {
        addressMapper.update(address);
        return ApiResult.success();
    }

    @Override
    public Result<Void> deleteAddress(Integer id) {
        addressMapper.delete(id);
        return ApiResult.success();
    }

    private String generateOrderNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", new Random().nextInt(10000));
    }
}
