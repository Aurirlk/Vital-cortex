package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.MallProductQueryDto;
import cn.kmbeast.pojo.entity.MallOrder;
import cn.kmbeast.pojo.entity.MallProduct;
import cn.kmbeast.pojo.entity.ProductCategory;
import cn.kmbeast.pojo.entity.ShippingAddress;
import cn.kmbeast.pojo.vo.MallOrderVO;
import cn.kmbeast.pojo.vo.MallProductVO;
import cn.kmbeast.pojo.vo.ShoppingCartVO;
import cn.kmbeast.service.MallService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 健康商城 Controller（药品 / 医疗器械 / 保健品）
 * <p>MallService / Mapper 层早已实现，本 Controller 补齐前后端通路（SEC 修复：此前 /mall/* 全部 404）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/mall")
public class MallController {

    @Resource
    private MallService mallService;

    // ==================== 商品分类 ====================

    /** 分类列表（用户端商城筛选 + 管理端） */
    @Protector
    @GetMapping("/categories")
    public Result<List<ProductCategory>> categories() {
        return mallService.getCategories();
    }

    /** 新增分类（管理员） */
    @Protector(role = "管理员")
    @PostMapping("/category/save")
    public Result<Void> saveCategory(@RequestBody ProductCategory category) {
        return mallService.saveCategory(category);
    }

    // ==================== 商品 ====================

    /** 商品列表（支持分页/分类/关键词/类型筛选） */
    @Protector
    @PostMapping("/product/query")
    public Result<List<MallProductVO>> queryProducts(@RequestBody MallProductQueryDto queryDto) {
        return mallService.queryProducts(queryDto);
    }

    /** 商品详情 */
    @Protector
    @GetMapping("/product/{id}")
    public Result<MallProductVO> productDetail(@PathVariable Integer id) {
        return mallService.getProductById(id);
    }

    /** 新增商品（管理员） */
    @Protector(role = "管理员")
    @PostMapping("/product/save")
    public Result<Void> saveProduct(@RequestBody MallProduct product) {
        return mallService.saveProduct(product);
    }

    /** 修改商品（管理员） */
    @Protector(role = "管理员")
    @PutMapping("/product/update")
    public Result<Void> updateProduct(@RequestBody MallProduct product) {
        return mallService.updateProduct(product);
    }

    /** 批量删除商品（管理员） */
    @Protector(role = "管理员")
    @PostMapping("/product/batchDelete")
    public Result<Void> deleteProducts(@RequestBody List<Long> ids) {
        return mallService.deleteProducts(ids);
    }

    // ==================== 购物车 ====================

    /** 我的购物车 */
    @Protector
    @GetMapping("/cart/list")
    public Result<List<ShoppingCartVO>> cartList() {
        return mallService.getCartItems(LocalThreadHolder.getUserId());
    }

    /** 加入购物车（productId, quantity） */
    @Protector
    @PostMapping("/cart/add")
    public Result<Void> addCart(@RequestParam Integer productId,
                                @RequestParam(defaultValue = "1") Integer quantity) {
        return mallService.addToCart(LocalThreadHolder.getUserId(), productId, quantity);
    }

    /** 修改购物车数量（id, quantity） */
    @Protector
    @PutMapping("/cart/update")
    public Result<Void> updateCart(@RequestParam Integer id,
                                   @RequestParam Integer quantity) {
        return mallService.updateCart(id, quantity);
    }

    /** 移除购物车项 */
    @Protector
    @DeleteMapping("/cart/{id}")
    public Result<Void> deleteCart(@PathVariable Integer id) {
        return mallService.removeFromCart(id);
    }

    // ==================== 订单 / 支付 ====================

    /** 创建订单（从购物车生成，addressId 可选） */
    @Protector
    @PostMapping("/order/create")
    public Result<MallOrderVO> createOrder(@RequestParam(required = false) Integer addressId,
                                           @RequestParam(required = false) String remark) {
        return mallService.createOrder(LocalThreadHolder.getUserId(), addressId, remark);
    }

    /** 支付订单 */
    @Protector
    @PostMapping("/order/pay/{id}")
    public Result<Void> payOrder(@PathVariable Integer id,
                                 @RequestParam(required = false) String paymentMethod) {
        return mallService.payOrder(id, paymentMethod == null ? "在线支付" : paymentMethod);
    }

    /** 我的订单（用户端） */
    @Protector
    @GetMapping("/order/my")
    public Result<List<MallOrderVO>> myOrders() {
        return mallService.getUserOrders(LocalThreadHolder.getUserId());
    }

    /** 全部订单（管理员：管理端订单管理） */
    @Protector(role = "管理员")
    @GetMapping("/order/list")
    public Result<List<MallOrderVO>> orderList() {
        return mallService.getAllOrders();
    }

    /** 管理员更新订单状态（发货/取消等） */
    @Protector(role = "管理员")
    @PutMapping("/order/status/{id}")
    public Result<Void> updateOrderStatus(@PathVariable Integer id,
                                          @RequestParam Integer status) {
        MallOrder update = new MallOrder();
        update.setId(id);
        update.setStatus(status);
        return mallService.updateOrderStatus(update);
    }

    /** 订单详情（管理员 / 本人） */
    @Protector
    @GetMapping("/order/{id}")
    public Result<MallOrderVO> orderDetail(@PathVariable Integer id) {
        return mallService.getOrderById(id);
    }

    // ==================== 收货地址 ====================

    @Protector
    @GetMapping("/address/list")
    public Result<List<ShippingAddress>> addressList() {
        return mallService.getAddresses(LocalThreadHolder.getUserId());
    }

    @Protector
    @PostMapping("/address/save")
    public Result<Void> saveAddress(@RequestBody ShippingAddress address) {
        address.setUserId(LocalThreadHolder.getUserId());
        return mallService.saveAddress(address);
    }

    @Protector
    @PutMapping("/address/update")
    public Result<Void> updateAddress(@RequestBody ShippingAddress address) {
        return mallService.updateAddress(address);
    }

    @Protector
    @DeleteMapping("/address/{id}")
    public Result<Void> deleteAddress(@PathVariable Integer id) {
        return mallService.deleteAddress(id);
    }
}
