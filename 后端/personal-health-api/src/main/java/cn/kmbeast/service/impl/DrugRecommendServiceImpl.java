package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.MallProductMapper;
import cn.kmbeast.pojo.dto.query.extend.MallProductQueryDto;
import cn.kmbeast.pojo.vo.MallProductVO;
import cn.kmbeast.service.DrugRecommendService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class DrugRecommendServiceImpl implements DrugRecommendService {

    @Resource
    private MallProductMapper mallProductMapper;

    @Override
    public List<MallProductVO> recommendByHealthData(Integer userId) {
        // 默认推荐热销商品
        MallProductQueryDto queryDto = new MallProductQueryDto();
        queryDto.setIsHot(1);
        return mallProductMapper.query(queryDto);
    }
}
