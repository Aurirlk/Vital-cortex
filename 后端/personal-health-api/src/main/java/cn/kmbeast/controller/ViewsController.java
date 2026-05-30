package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.vo.ChartVO;
import cn.kmbeast.service.ViewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 可视化接口
 */
@RestController
@RequestMapping(value = "/views")
public class ViewsController {

    @Resource
    private ViewsService viewsService;

    /**
     * 统计系统基础数据（管理员）
     */
    @Protector(role = "管理员")
    @GetMapping("/staticControls")
    public Result<List<ChartVO>> staticControls() {
        return viewsService.staticControls();
    }
}
