package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.AiConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI配置Mapper接口
 */
@Mapper
public interface AiConfigMapper {

    /**
     * 查询所有配置
     */
    List<AiConfigEntity> findAll();

    /**
     * 根据key查询配置
     */
    AiConfigEntity findByKey(@Param("configKey") String configKey);

    /**
     * 保存或更新配置（存在则更新，不存在则插入）
     */
    void saveOrUpdate(AiConfigEntity config);

    /**
     * 批量保存或更新
     */
    void batchSaveOrUpdate(List<AiConfigEntity> configs);
}
