package cn.kmbeast.controller;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.vo.MallProductVO;
import cn.kmbeast.service.DrugRecommendService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

@RestController

    // 2026-10-07 审计修复：原先用 @RequestAttribute("userId") 取身份，
    // 但全项目没有任何 setAttribute("userId", ...) —— JwtInterceptor 只写 LocalThreadHolder，
    // 故该参数必然抛 MissingRequestAttributeException。改用项目统一的 LocalThreadHolder。@RequestMapping("/drug-recommend")
public class DrugRecommendController {

    @Resource
    private DrugRecommendService drugRecommendService;

    @Protector
    @GetMapping("/by-health")
    public Result<List<MallProductVO>> recommendByHealth() {
        Integer userId = LocalThreadHolder.getUserId();
        return ApiResult.success(drugRecommendService.recommendByHealthData(userId));
    }
}
