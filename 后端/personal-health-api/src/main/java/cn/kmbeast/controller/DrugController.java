package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.DrugQueryDto;
import cn.kmbeast.pojo.entity.Drug;
import cn.kmbeast.pojo.vo.DrugVO;
import cn.kmbeast.service.DrugService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 药品订阅 Controller
 */
@RestController
@RequestMapping(value = "/drug")
public class DrugController {

    @Resource
    private DrugService drugService;

    /**
     * 药品新增（管理员）
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/save")
    public Result<Void> save(@RequestBody Drug drug) {
        return drugService.save(drug);
    }

    /**
     * 药品删除（管理员）
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/batchDelete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        return drugService.batchDelete(ids);
    }

    /**
     * 药品修改（管理员）
     */
    @Protector(role = "管理员")
    @PutMapping(value = "/update")
    public Result<Void> update(@RequestBody Drug drug) {
        return drugService.update(drug);
    }

    /**
     * 药品查询（分页，需登录）
     */
    @Pager
    @Protector
    @PostMapping(value = "/query")
    public Result<List<DrugVO>> query(@RequestBody DrugQueryDto drugQueryDto) {
        return drugService.query(drugQueryDto);
    }

    /**
     * 药品详情
     */
    @Protector
    @GetMapping(value = "/detail/{id}")
    public Result<DrugVO> getById(@PathVariable Integer id) {
        return drugService.getById(id);
    }

    /**
     * 订阅药品
     */
    @Protector
    @PostMapping(value = "/subscribe/{drugId}")
    public Result<Void> subscribe(@PathVariable Integer drugId,
                                   @RequestParam(required = false, defaultValue = "1") Integer quantity) {
        return drugService.subscribe(drugId, quantity, LocalThreadHolder.getUserId());
    }

    /**
     * 取消订阅
     */
    @Protector
    @PostMapping(value = "/unsubscribe/{drugId}")
    public Result<Void> unsubscribe(@PathVariable Integer drugId) {
        return drugService.unsubscribe(drugId, LocalThreadHolder.getUserId());
    }

    /**
     * 我的订阅列表
     */
    @Protector
    @GetMapping(value = "/my-subscriptions")
    public Result<List<DrugVO>> mySubscriptions() {
        return drugService.getMySubscriptions(LocalThreadHolder.getUserId());
    }

    /**
     * 药品搜索（无需管理员权限）
     */
    @Protector
    @GetMapping(value = "/search")
    public Result<List<DrugVO>> search(@RequestParam String keyword) {
        return drugService.search(keyword);
    }
}
