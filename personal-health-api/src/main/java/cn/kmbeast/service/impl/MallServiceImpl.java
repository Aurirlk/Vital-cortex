package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.core.notify.NotifyTemplates;
import cn.kmbeast.core.notify.UserNotifier;
import cn.kmbeast.mapper.*;
import cn.kmbeast.pojo.em.NotificationSource;
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
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import jakarta.annotation.Resource;
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
    @Resource private UserNotifier userNotifier;

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
        // 2026-10-10 修复：数量此前完全不校验。传 quantity=-5 会让购物车行变成负数，
        // 而结算总价是「单价 × 数量」，于是负数量等于倒贴钱下单
        // （totalAmount < 0，支付金额为负）。必须挡在入口。
        if (quantity == null || quantity <= 0) {
            return ApiResult.error("数量必须大于 0");
        }
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
        if (quantity == null || quantity <= 0) {
            return ApiResult.error("数量必须大于 0");
        }
        // 2026-10-10 修复（IDOR）：原先按 id 直接 UPDATE，任何登录用户改个 id
        // 就能篡改他人购物车数量。
        ShoppingCart owned = cartMapper.getById(id);
        if (owned == null) {
            return ApiResult.error("购物车项不存在");
        }
        if (!LocalThreadHolder.isSelfOrAdmin(owned.getUserId())) {
            log.warn("越权修改购物车被拒绝: cartId={}, ownerId={}, operatorId={}",
                    id, owned.getUserId(), LocalThreadHolder.getUserId());
            return ApiResult.error("无权操作该购物车项");
        }
        ShoppingCart cart = new ShoppingCart();
        cart.setId(id);
        cart.setQuantity(quantity);
        cartMapper.update(cart);
        return ApiResult.success();
    }

    @Override
    public Result<Void> removeFromCart(Integer id) {
        // 2026-10-10 修复（IDOR）：同上，原先按 id 直接 DELETE。
        ShoppingCart owned = cartMapper.getById(id);
        if (owned == null) {
            return ApiResult.error("购物车项不存在");
        }
        if (!LocalThreadHolder.isSelfOrAdmin(owned.getUserId())) {
            log.warn("越权删除购物车被拒绝: cartId={}, ownerId={}, operatorId={}",
                    id, owned.getUserId(), LocalThreadHolder.getUserId());
            return ApiResult.error("无权操作该购物车项");
        }
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
        // 2026-10-10 修复：addressId 原先直接落库、不校验归属。
        // 传别人的 addressId 即可把订单挂到他人收货地址上
        // （订单详情会带出对方的收货人姓名/电话/详细地址）。
        if (addressId != null) {
            ShippingAddress address = addressMapper.getById(addressId);
            if (address == null || !userId.equals(address.getUserId())) {
                return ApiResult.error("收货地址不存在");
            }
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ShoppingCartVO item : cartItems) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                return ApiResult.error("购物车中存在数量异常的商品，请重新添加");
            }
            if (item.getProductPrice() == null) {
                return ApiResult.error("商品「" + item.getProductName() + "」价格异常，请联系管理员");
            }
            totalAmount = totalAmount.add(item.getProductPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        // 2026-10-10 修复：先扣库存并检查影响行数，再建单。
        // 原实现把 decrementStock 的返回值丢掉，库存不足时 SQL 一行都没更新，
        // 订单却照样建成 —— 静默超卖（卖出多于库存）。
        // 库里库存不足 → 整个事务回滚，给用户一个明确提示。
        for (ShoppingCartVO item : cartItems) {
            Integer affected = productMapper.decrementStock(item.getProductId(), item.getQuantity());
            if (affected == null || affected == 0) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return ApiResult.error("商品「" + item.getProductName() + "」库存不足，请调整数量后重试");
            }
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
        }
        for (ShoppingCartVO item : cartItems) {
            cartMapper.delete(item.getId());
        }

        // 2026-10-10 新增：下单成功通知（待付款），受设置页「订单通知」开关控制。
        // 用 send 而非 sendOnce：一次重复提交会真的产生两笔订单，
        // 两笔都该各有一条通知；去重键是按订单ID的，本来也不会互相覆盖。
        // 放在最后：前面的库存校验失败会提前 return，不会通知一笔并不存在的订单。
        userNotifier.send(userId, NotificationSource.ORDER,
                NotifyTemplates.T_ORDER_CREATED,
                NotifyTemplates.orderCreated(order.getOrderNo(), order.getActualAmount()),
                order.getId() == null ? null : order.getId().longValue(),
                NotifyTemplates.LINK_MALL);
        return ApiResult.success(orderMapper.getById(order.getId()));
    }

    @Override
    @Transactional
    public Result<Void> payOrder(Integer orderId, String paymentMethod) {
        MallOrder order = orderMapper.getById(orderId);
        if (order == null) {
            return ApiResult.error("订单不存在");
        }
        // 2026-10-10 修复（最严重的一处越权写）：原先只校验「订单存在」，
        // 谁都能 POST /mall/order/pay/{id} 把他人未付款订单标记为已支付
        // （status=1 + 支付时间 + 刷销量）。对真实商城等于零元购。
        if (!LocalThreadHolder.isSelfOrAdmin(order.getUserId())) {
            log.warn("越权支付订单被拒绝: orderId={}, ownerId={}, operatorId={}, role={}",
                    orderId, order.getUserId(), LocalThreadHolder.getUserId(),
                    LocalThreadHolder.getRoleId());
            return ApiResult.error("无权操作该订单");
        }
        if (order.getStatus() != null && order.getStatus() != 0) {
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

        // 2026-10-10 新增：支付成功通知，受「订单通知」开关控制。
        // 收件人是 order.getUserId() 而不是当前登录人 —— 管理员代付时
        // 该通知仍要发给订单主人。上面的 status != 0 校验保证了同一笔订单
        // 只可能成功支付一次，因此不需要去重。
        userNotifier.send(order.getUserId(), NotificationSource.ORDER,
                NotifyTemplates.T_ORDER_PAID,
                NotifyTemplates.orderPaid(order.getOrderNo(), order.getActualAmount()),
                orderId == null ? null : orderId.longValue(),
                NotifyTemplates.LINK_MALL);
        return ApiResult.success();
    }

    @Override
    public Result<Void> updateOrderStatus(MallOrder order) {
        orderMapper.update(order);

        // 2026-10-10 新增：管理员侧状态流转（发货/完成/取消/退款）通知买家，
        // 受「订单通知」开关控制。只有 2/3/4/5 会发，0/1 返回 null 直接跳过。
        notifyOrderStatusChange(order);
        return ApiResult.success();
    }

    /**
     * 订单状态流转通知（2026-10-10 新增）。
     *
     * <p>入参是管理端传来的<b>局部更新对象</b>（只有 id + status），
     * 拿不到 orderNo / userId，所以命中可通知状态时再回查一次订单。
     * 判断放在回查之前：0/1 与未知状态一次数据库都不用打。
     *
     * <p>用 {@link UserNotifier#sendOnce}：管理员在管理端连点两下「发货」，
     * {@code orderMapper.update} 会照写两次（这条 update 没有状态前置条件），
     * 没有去重就会给买家堆两条一模一样的「订单已发货」。
     *
     * <p>通知失败不能影响状态流转本身 —— 管理员点发货，不该因为消息写不进去而失败。
     */
    private void notifyOrderStatusChange(MallOrder patch) {
        if (patch == null || patch.getId() == null) {
            return;
        }
        String title = NotifyTemplates.orderStatusTitle(patch.getStatus());
        if (title == null) {
            return;
        }
        try {
            MallOrder order = orderMapper.getById(patch.getId());
            if (order == null || order.getUserId() == null) {
                log.warn("订单状态通知：订单查不到，跳过: orderId={}", patch.getId());
                return;
            }
            userNotifier.sendOnce(order.getUserId(), NotificationSource.ORDER, title,
                    NotifyTemplates.orderStatusChanged(order.getOrderNo(),
                            NotifyTemplates.orderStatusLabel(patch.getStatus())),
                    patch.getId().longValue(),
                    NotifyTemplates.LINK_MALL);
        } catch (Exception e) {
            log.warn("订单状态通知发送失败，不影响状态流转: orderId={}, status={}",
                    patch.getId(), patch.getStatus(), e);
        }
    }

    @Override
    public Result<List<MallOrderVO>> getUserOrders(Integer userId) {
        return ApiResult.success(orderMapper.queryByUserId(userId));
    }

    @Override
    public Result<List<MallOrderVO>> getAllOrders() {
        return ApiResult.success(orderMapper.queryAll());
    }

    @Override
    public Result<MallOrderVO> getOrderById(Integer id) {
        MallOrderVO order = orderMapper.getById(id);
        // 2026-10-10 修复（IDOR）：接口注释写的是「管理员 / 本人」，
        // 实现却没做归属判断 —— 传 id 即可读走任意订单详情，
        // 含收货人姓名、电话、详细地址与下单商品。
        if (order != null && !LocalThreadHolder.isSelfOrAdmin(order.getUserId())) {
            log.warn("越权查看订单被拒绝: orderId={}, ownerId={}, operatorId={}",
                    id, order.getUserId(), LocalThreadHolder.getUserId());
            return ApiResult.error("无权查看该订单");
        }
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
        // 2026-10-10 修复（IDOR）：原先按 body 里的 id 直接 UPDATE，
        // 可改写任意用户的收货地址（把别人的地址改成自己的，或改成攻击者可控的地址）。
        ShippingAddress owned = (address == null || address.getId() == null)
                ? null : addressMapper.getById(address.getId());
        if (owned == null) {
            return ApiResult.error("收货地址不存在");
        }
        if (!LocalThreadHolder.isSelfOrAdmin(owned.getUserId())) {
            log.warn("越权修改收货地址被拒绝: addressId={}, ownerId={}, operatorId={}",
                    address.getId(), owned.getUserId(), LocalThreadHolder.getUserId());
            return ApiResult.error("无权操作该收货地址");
        }
        addressMapper.update(address);
        return ApiResult.success();
    }

    @Override
    public Result<Void> deleteAddress(Integer id) {
        // 2026-10-10 修复（IDOR）：同上，原先按 id 直接 DELETE。
        ShippingAddress owned = addressMapper.getById(id);
        if (owned == null) {
            return ApiResult.error("收货地址不存在");
        }
        if (!LocalThreadHolder.isSelfOrAdmin(owned.getUserId())) {
            log.warn("越权删除收货地址被拒绝: addressId={}, ownerId={}, operatorId={}",
                    id, owned.getUserId(), LocalThreadHolder.getUserId());
            return ApiResult.error("无权操作该收货地址");
        }
        addressMapper.delete(id);
        return ApiResult.success();
    }

    private String generateOrderNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", new Random().nextInt(10000));
    }
}
