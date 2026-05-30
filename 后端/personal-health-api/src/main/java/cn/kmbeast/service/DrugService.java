package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.DrugQueryDto;
import cn.kmbeast.pojo.entity.Drug;
import cn.kmbeast.pojo.vo.DrugVO;

import java.util.List;

public interface DrugService {

    Result<Void> save(Drug drug);

    Result<Void> batchDelete(List<Long> ids);

    Result<Void> update(Drug drug);

    Result<List<DrugVO>> query(DrugQueryDto drugQueryDto);

    Result<DrugVO> getById(Integer id);

    Result<Void> subscribe(Integer drugId, Integer quantity, Integer userId);

    Result<Void> unsubscribe(Integer drugId, Integer userId);

    Result<List<DrugVO>> getMySubscriptions(Integer userId);

    Result<List<DrugVO>> search(String keyword);
}
