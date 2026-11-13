package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.vo.MallProductVO;
import java.util.List;

public interface DrugRecommendService {
    List<MallProductVO> recommendByHealthData(Integer userId);
}
