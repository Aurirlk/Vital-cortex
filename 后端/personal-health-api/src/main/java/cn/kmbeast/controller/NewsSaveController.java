package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.NewsSaveQueryDto;
import cn.kmbeast.pojo.entity.NewsSave;
import cn.kmbeast.pojo.vo.NewsSaveVO;
import cn.kmbeast.service.NewsSaveService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 资讯收藏的 Controller
 */
@RestController
@RequestMapping(value = "/news-save")
public class NewsSaveController {

    @Resource
    private NewsSaveService newsSaveService;

    /**
     * 资讯收藏或取消收藏
     */
    @Protector
    @PostMapping(value = "/operation")
    public Result<Void> operation(@RequestBody NewsSave newsSave) {
        return newsSaveService.operation(newsSave);
    }

    /**
     * 收藏或取消收藏操作
     */
    @Protector
    @PostMapping(value = "/save")
    public Result<Void> save(@RequestBody NewsSave newsSave) {
        return newsSaveService.save(newsSave);
    }

    /**
     * 资讯收藏删除
     */
    @Protector
    @PostMapping(value = "/batchDelete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        return newsSaveService.batchDelete(ids);
    }

    /**
     * 查询用户收藏的健康资讯
     */
    @Pager
    @Protector
    @PostMapping(value = "/queryUser")
    public Result<List<NewsSaveVO>> queryUser(@RequestBody NewsSaveQueryDto newsSaveQueryDto) {
        newsSaveQueryDto.setUserId(LocalThreadHolder.getUserId());
        return newsSaveService.query(newsSaveQueryDto);
    }

    /**
     * 资讯收藏查询（管理员）
     */
    @Pager
    @Protector(role = "管理员")
    @PostMapping(value = "/query")
    public Result<List<NewsSaveVO>> query(@RequestBody NewsSaveQueryDto newsSaveQueryDto) {
        return newsSaveService.query(newsSaveQueryDto);
    }
}
