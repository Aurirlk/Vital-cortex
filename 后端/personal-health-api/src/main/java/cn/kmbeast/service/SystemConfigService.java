package cn.kmbeast.service;

import cn.kmbeast.pojo.entity.SystemConfigEntity;

import java.util.List;
import java.util.Map;

/**
 * 系统配置服务接口
 */
public interface SystemConfigService {

    /**
     * 获取所有配置（隐藏敏感信息）
     */
    Map<String, Object> getAllConfigs();

    /**
     * 获取指定分组的配置（隐藏敏感信息）
     */
    Map<String, Object> getConfigByGroup(String group);

    /**
     * 获取指定配置的值（需要密码验证才能查看敏感配置）
     */
    String getConfigValue(String group, String key, String password);

    /**
     * 更新配置
     */
    void updateConfig(String group, String key, String value);

    /**
     * 批量更新配置
     */
    void batchUpdateConfig(List<SystemConfigEntity> configs);

    /**
     * 验证管理员密码
     */
    boolean verifyPassword(String password);

    /**
     * 初始化默认配置
     */
    void initDefaultConfigs();
}
