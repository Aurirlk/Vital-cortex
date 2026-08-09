package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.HealthModelConfigMapper;
import cn.kmbeast.mapper.NewsMapper;
import cn.kmbeast.mapper.UserHealthMapper;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.HealthModelConfigQueryDto;
import cn.kmbeast.pojo.dto.query.extend.NewsQueryDto;
import cn.kmbeast.pojo.dto.query.extend.UserHealthQueryDto;
import cn.kmbeast.pojo.dto.query.extend.UserQueryDto;
import cn.kmbeast.pojo.vo.ChartVO;
import cn.kmbeast.service.ViewsService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页可视化
 */
@Service
public class ViewsServiceImpl implements ViewsService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private NewsMapper newsMapper;
    @Resource
    private HealthModelConfigMapper healthModelConfigMapper;
    @Resource
    private UserHealthMapper userHealthMapper;

    /**
     * 统计一些系统的基础数据
     *
     * @return Result<List < ChartVO>>
     */
    @Override
    public Result<List<ChartVO>> staticControls() {
        List<ChartVO> chartVOS = new ArrayList<>();
        // 1. 用户数
        UserQueryDto userQueryDto = new UserQueryDto();
        int userCount = userMapper.queryCount(userQueryDto);
        change(userCount, "存量用户", chartVOS);
        // 2. 资讯数
        NewsQueryDto newsQueryDto = new NewsQueryDto();
        int newsCount = newsMapper.queryCount(newsQueryDto);
        change(newsCount, "收录资讯", chartVOS);
        // 3. 健康模型数
        HealthModelConfigQueryDto queryDto = new HealthModelConfigQueryDto();
        int modelCount = healthModelConfigMapper.queryCount(queryDto);
        change(modelCount, "收录模型", chartVOS);
        // 4. 健康数据
        UserHealthQueryDto dto = new UserHealthQueryDto();
        int healthCount = userHealthMapper.queryCount(dto);
        change(healthCount, "健康数据", chartVOS);
        return ApiResult.success(chartVOS);
    }

    /**
     * 参数处理
     *
     * @param count    总数目
     * @param name     统计项
     * @param chartVOS 装它们的集合
     */
    private void change(Integer count, String name, List<ChartVO> chartVOS) {
        ChartVO chartVO = new ChartVO(name, count);
        chartVOS.add(chartVO);
    }


}
