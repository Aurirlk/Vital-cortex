package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.SystemConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统配置Mapper接口
 */
@Mapper
public interface SystemConfigMapper {

    /**
     * 查询所有配置
     */
    List<SystemConfigEntity> findAll();

    /**
     * 根据分组查询配置
     */
    List<SystemConfigEntity> findByGroup(@Param("configGroup") String configGroup);

    /**
     * 根据key查询配置
     */
    SystemConfigEntity findByKey(@Param("configKey") String configKey);

    /**
     * 根据分组和key查询配置
     */
    SystemConfigEntity findByGroupAndKey(@Param("configGroup") String configGroup, @Param("configKey") String configKey);

    /**
     * 保存或更新配置
     */
    void saveOrUpdate(SystemConfigEntity config);

    /**
     * 批量保存或更新
     */
    void batchSaveOrUpdate(List<SystemConfigEntity> configs);

    /**
     * 删除配置
     */
    void deleteByKey(@Param("configKey") String configKey);

    /**
     * 根据分组删除配置
     */
    void deleteByGroup(@Param("configGroup") String configGroup);
}
