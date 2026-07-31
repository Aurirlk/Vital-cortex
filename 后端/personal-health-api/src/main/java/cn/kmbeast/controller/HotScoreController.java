package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.service.HotScoreService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/hot")
public class HotScoreController {

    @Resource
    private HotScoreService hotScoreService;

    @Protector
    @GetMapping("/posts")
    public Result<List<Map<String, Object>>> getHotPosts(@RequestParam(required = false) Integer limit) {
        return ApiResult.success(hotScoreService.getHotPosts(limit));
    }

    @Protector(role = "管理员")
    @PostMapping("/update")
    public Result<Void> updateAllHotScores() {
        hotScoreService.updateAllHotScores();
        return ApiResult.success();
    }
}
