package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.HealthModelConfigMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.HealthModelConfigQueryDto;
import cn.kmbeast.pojo.entity.HealthModelConfig;
import cn.kmbeast.pojo.vo.HealthModelConfigVO;
import cn.kmbeast.service.HealthModelConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 健康模型业务逻辑实现
 */
@Slf4j
@Service
public class HealthModelConfigServiceImpl implements HealthModelConfigService {

    @Resource
    private HealthModelConfigMapper healthModelConfigMapper;

    /**
     * 健康模型新增
     *
     * @param healthModelConfig 参数
     * @return Result<Void>
     */
    @Override
    public Result<Void> save(HealthModelConfig healthModelConfig) {
        // 将用户的ID设置上
        healthModelConfig.setUserId(LocalThreadHolder.getUserId());
        healthModelConfigMapper.save(healthModelConfig);
        return ApiResult.success();
    }

    /**
     * 健康模型删除
     *
     * @param ids 参数
     * @return Result<Void>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ApiResult.error("请选择要删除的模型配置");
        }
        // 2026-10-10 修复（IDOR + 越权删全局模型）：原先 DELETE ... WHERE id IN (...)
        // 不看归属，任何登录用户拿一串 id 就能删掉他人自建模型，甚至删掉全局模型
        // （全局模型被删后，所有用户的该指标记录都失去定义）。
        // 现把归属条件写进 SQL：非管理员只能删「自己创建的非全局模型」；
        // 删到的行数少于请求数即说明混入无权记录，整批回滚报错。
        Integer ownerScope = LocalThreadHolder.isAdmin() ? null : LocalThreadHolder.getUserId();
        Integer affectedRows = healthModelConfigMapper.batchDelete(ids, ownerScope);
        int affected = affectedRows == null ? 0 : affectedRows;
        if (affected < ids.size()) {
            log.warn("健康模型批量删除包含无权记录，已整批回滚: 请求={}, 实际可删={}, operatorId={}",
                    ids.size(), affected, LocalThreadHolder.getUserId());
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResult.error("存在无权删除的模型配置，已取消本次删除");
        }
        return ApiResult.success();
    }

    /**
     * 健康模型修改
     *
     * @param healthModelConfig 参数
     * @return Result<Void>
     */
    @Override
    public Result<Void> update(HealthModelConfig healthModelConfig) {
        if (healthModelConfig == null || healthModelConfig.getId() == null) {
            return ApiResult.error("缺少模型 ID");
        }
        // 2026-10-10 修复（IDOR + 越权改全局配置）：原先只按 id 更新，任何登录用户
        // 都能改他人自建模型，甚至能改管理员维护的全局模型——改掉名称/单位/
        // 正常值范围后，「指标超标通知」会依据被篡改的 value_range 误报，波及所有用户。
        HealthModelConfigVO exist = getConfigById(healthModelConfig.getId());
        if (exist == null) {
            return ApiResult.error("模型配置不存在");
        }
        if (!canModify(exist)) {
            log.warn("越权修改健康模型被拒绝: configId={}, ownerId={}, isGlobal={}, operatorId={}",
                    healthModelConfig.getId(), exist.getUserId(), exist.getIsGlobal(),
                    LocalThreadHolder.getUserId());
            return ApiResult.error("无权修改该模型配置");
        }
        healthModelConfigMapper.update(healthModelConfig);
        return ApiResult.success();
    }

    /** 按 ID 取单条模型配置；不存在返回 null */
    private HealthModelConfigVO getConfigById(Integer id) {
        HealthModelConfigQueryDto dto = new HealthModelConfigQueryDto();
        dto.setId(id);
        List<HealthModelConfigVO> list = healthModelConfigMapper.query(dto);
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    /** 全局模型仅管理员可改；用户自建模型仅本人或管理员可改 */
    private boolean canModify(HealthModelConfigVO config) {
        if (LocalThreadHolder.isAdmin()) {
            return true;
        }
        if (Boolean.TRUE.equals(config.getIsGlobal())) {
            return false;
        }
        return LocalThreadHolder.isSelfOrAdmin(config.getUserId());
    }

    /**
     * 查询用户自己配置的模型及全局模型
     *
     * @param healthModelConfigQueryDto 查询参数
     * @return Result<List < HealthModelConfigVO>>
     */
    @Override
    public Result<List<HealthModelConfigVO>> modelList() {
        HealthModelConfigQueryDto healthModelConfigQueryDto = new HealthModelConfigQueryDto();
        healthModelConfigQueryDto.setUserId(LocalThreadHolder.getUserId());
        List<HealthModelConfigVO> modelConfigs = healthModelConfigMapper.query(healthModelConfigQueryDto);
        healthModelConfigQueryDto.setUserId(null);
        healthModelConfigQueryDto.setIsGlobal(true);
        List<HealthModelConfigVO> modelConfigsGlobal = healthModelConfigMapper.query(healthModelConfigQueryDto);
        List<HealthModelConfigVO> modelAll = new ArrayList<>();
        modelAll.addAll(modelConfigs);
        modelAll.addAll(modelConfigsGlobal);
        return ApiResult.success(modelAll);
    }

    /**
     * 健康模型查询
     *
     * @param healthModelConfigQueryDto 查询参数
     * @return Result<List < HealthModelConfigVO>>
     */
    @Override
    public Result<List<HealthModelConfigVO>> query(HealthModelConfigQueryDto healthModelConfigQueryDto) {
        List<HealthModelConfigVO> modelConfigs = healthModelConfigMapper.query(healthModelConfigQueryDto);
        Integer totalCount = healthModelConfigMapper.queryCount(healthModelConfigQueryDto);
        return PageResult.success(modelConfigs, totalCount);
    }

}
