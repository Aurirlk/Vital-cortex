package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.mapper.*;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.NewsQueryDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Resource
    private UserMapper userMapper;
    @Resource
    private NewsMapper newsMapper;

    @Protector
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("userCount", userMapper.queryCount(new cn.kmbeast.pojo.dto.query.extend.UserQueryDto()));
        stats.put("newsCount", newsMapper.queryCount(new NewsQueryDto()));
        return ApiResult.success(stats);
    }
}
