package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.HealthModelConfigQueryDto;
import cn.kmbeast.pojo.entity.HealthModelConfig;
import cn.kmbeast.pojo.vo.HealthModelConfigVO;
import cn.kmbeast.service.HealthModelConfigService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 健康模型的 Controller
 */
@RestController
@RequestMapping(value = "/health-model-config")
public class HealthModelConfigController {

    @Resource
    private HealthModelConfigService healthModelConfigService;

    /**
     * 健康模型新增（用户）
     */
    @Protector
    @PostMapping(value = "/save")
    public Result<Void> save(@RequestBody HealthModelConfig healthModelConfig) {
        healthModelConfig.setIsGlobal(false);
        return healthModelConfigService.save(healthModelConfig);
    }

    /**
     * 健康模型新增（管理员 - 全局）
     */
    @Protector(role = "管理员")
    @PostMapping(value = "config/save")
    public Result<Void> configSave(@RequestBody HealthModelConfig healthModelConfig) {
        healthModelConfig.setIsGlobal(true);
        return healthModelConfigService.save(healthModelConfig);
    }

    /**
     * 健康模型删除
     */
    @Protector
    @PostMapping(value = "/batchDelete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        return healthModelConfigService.batchDelete(ids);
    }

    /**
     * 健康模型修改
     */
    @Protector
    @PutMapping(value = "/update")
    public Result<Void> update(@RequestBody HealthModelConfig healthModelConfig) {
        return healthModelConfigService.update(healthModelConfig);
    }

    /**
     * 查询用户自己配置的模型及全局模型
     */
    @Pager
    @Protector
    @PostMapping(value = "/modelList")
    public Result<List<HealthModelConfigVO>> modelList() {
        return healthModelConfigService.modelList();
    }

    /**
     * 健康模型查询（管理员）
     */
    @Pager
    @Protector(role = "管理员")
    @PostMapping(value = "/query")
    public Result<List<HealthModelConfigVO>> query(@RequestBody HealthModelConfigQueryDto healthModelConfigQueryDto) {
        return healthModelConfigService.query(healthModelConfigQueryDto);
    }
}
