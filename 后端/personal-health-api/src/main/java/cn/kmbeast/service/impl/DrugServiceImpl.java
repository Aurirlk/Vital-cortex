package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.DrugMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.DrugQueryDto;
import cn.kmbeast.pojo.entity.Drug;
import cn.kmbeast.pojo.entity.DrugSubscription;
import cn.kmbeast.pojo.vo.DrugVO;
import cn.kmbeast.service.DrugService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DrugServiceImpl implements DrugService {

    @Resource
    private DrugMapper drugMapper;

    @Override
    public Result<Void> save(Drug drug) {
        drug.setCreateTime(LocalDateTime.now());
        drug.setStatus(true);
        drugMapper.save(drug);
        return ApiResult.success();
    }

    @Override
    public Result<Void> batchDelete(List<Long> ids) {
        drugMapper.batchDelete(ids);
        return ApiResult.success();
    }

    @Override
    public Result<Void> update(Drug drug) {
        drugMapper.update(drug);
        return ApiResult.success();
    }

    @Override
    public Result<List<DrugVO>> query(DrugQueryDto drugQueryDto) {
        List<DrugVO> drugs = drugMapper.query(drugQueryDto);
        Integer totalCount = drugMapper.queryCount(drugQueryDto);
        return PageResult.success(drugs, totalCount);
    }

    @Override
    public Result<DrugVO> getById(Integer id) {
        DrugVO drug = drugMapper.getById(id);
        if (drug == null) {
            return ApiResult.error("药品不存在");
        }
        return ApiResult.success(drug);
    }

    @Override
    public Result<Void> subscribe(Integer drugId, Integer quantity, Integer userId) {
        // 检查是否已订阅
        List<DrugVO> existing = drugMapper.getSubscribedDrugs(userId);
        for (DrugVO drug : existing) {
            if (drug.getId().equals(drugId)) {
                return ApiResult.error("您已订阅该药品");
            }
        }
        DrugSubscription subscription = new DrugSubscription();
        subscription.setUserId(userId);
        subscription.setDrugId(drugId);
        subscription.setQuantity(quantity != null ? quantity : 1);
        subscription.setCreateTime(LocalDateTime.now());
        drugMapper.subscribe(subscription);
        return ApiResult.success();
    }

    @Override
    public Result<Void> unsubscribe(Integer drugId, Integer userId) {
        drugMapper.unsubscribe(userId, drugId);
        return ApiResult.success();
    }

    @Override
    public Result<List<DrugVO>> getMySubscriptions(Integer userId) {
        List<DrugVO> drugs = drugMapper.getSubscribedDrugs(userId);
        return ApiResult.success(drugs);
    }

    @Override
    public Result<List<DrugVO>> search(String keyword) {
        List<DrugVO> drugs = drugMapper.searchByName(keyword, 20);
        return ApiResult.success(drugs);
    }
}
