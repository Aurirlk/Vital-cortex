package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.vo.MallProductVO;
import cn.kmbeast.service.DrugRecommendService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/drug-recommend")
public class DrugRecommendController {

    @Resource
    private DrugRecommendService drugRecommendService;

    @Protector
    @GetMapping("/by-health")
    public Result<List<MallProductVO>> recommendByHealth(@RequestAttribute("userId") Integer userId) {
        return ApiResult.success(drugRecommendService.recommendByHealthData(userId));
    }
}
