package cn.kmbeast.mapper;

import cn.kmbeast.pojo.dto.query.extend.HealthModelConfigQueryDto;
import cn.kmbeast.pojo.entity.HealthModelConfig;
import cn.kmbeast.pojo.vo.HealthModelConfigVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HealthModelConfigMapper {

    void save(HealthModelConfig healthModelConfig);

    void update(HealthModelConfig healthModelConfig);

    void batchDelete(@Param(value = "ids") List<Long> ids);

    List<HealthModelConfigVO> query(HealthModelConfigQueryDto healthModelConfigQueryDto);

    Integer queryCount(HealthModelConfigQueryDto healthModelConfigQueryDto);

    /**
     * 获取所有全局健康模型配置
     *
     * @return 全局模型列表
     */
    List<HealthModelConfig> getGlobalModels();

}
