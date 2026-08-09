package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.SystemConfigMapper;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.dto.query.extend.UserQueryDto;
import cn.kmbeast.pojo.entity.SystemConfigEntity;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置服务实现（SystemConfigServiceImpl 重建）。
 *
 * <p>背景：SystemConfigService 接口长期存在但实现类缺失（历史遗留），
 * 导致启动时 {@code A component required a bean of type 'SystemConfigService'} 报错。
 * 本实现基于既有 SystemConfigMapper（findAll/findByGroup/findByGroupAndKey/
 * saveOrUpdate/batchSaveOrUpdate）补齐全部 7 个方法：
 * 分组读取（敏感值掩码）、密码保护的敏感配置读取、更新/批量更新、
 * 管理员密码验证（BCrypt 比对 user 表管理员账号）、默认配置种子。
 */
@Slf4j
@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    /** 敏感值掩码 */
    private static final String MASK = "******";

    @Resource
    private SystemConfigMapper systemConfigMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public Map<String, Object> getAllConfigs() {
        List<SystemConfigEntity> all = systemConfigMapper.findAll();
        // 结构：{ group: { key: value } }，敏感值统一掩码
        Map<String, Object> result = new LinkedHashMap<>();
        for (SystemConfigEntity cfg : all) {
            Map<String, Object> group = (Map<String, Object>) result.computeIfAbsent(
                    cfg.getConfigGroup(), k -> new LinkedHashMap<String, Object>());
            group.put(cfg.getConfigKey(), maskIfSensitive(cfg));
        }
        return result;
    }

    @Override
    public Map<String, Object> getConfigByGroup(String group) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (SystemConfigEntity cfg : systemConfigMapper.findByGroup(group)) {
            result.put(cfg.getConfigKey(), maskIfSensitive(cfg));
        }
        return result;
    }

    @Override
    public String getConfigValue(String group, String key, String password) {
        SystemConfigEntity cfg = systemConfigMapper.findByGroupAndKey(group, key);
        if (cfg == null) {
            return null;
        }
        // 敏感配置：需管理员密码验证才返回真实值
        if (Boolean.TRUE.equals(cfg.getSensitive())) {
            if (verifyPassword(password)) {
                return cfg.getConfigValue();
            }
            return MASK;
        }
        return cfg.getConfigValue();
    }

    @Override
    public void updateConfig(String group, String key, String value) {
        SystemConfigEntity cfg = new SystemConfigEntity();
        cfg.setConfigGroup(group);
        cfg.setConfigKey(key);
        cfg.setConfigValue(value);
        cfg.setUpdateTime(LocalDateTime.now());
        systemConfigMapper.saveOrUpdate(cfg);
    }

    @Override
    public void batchUpdateConfig(List<SystemConfigEntity> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        for (SystemConfigEntity cfg : configs) {
            cfg.setUpdateTime(LocalDateTime.now());
        }
        systemConfigMapper.batchSaveOrUpdate(configs);
    }

    @Override
    public boolean verifyPassword(String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        // 1) 查所有管理员账号（role=true），BCrypt 比对
        UserQueryDto dto = new UserQueryDto();
        dto.setRole(true);
        List<User> admins = userMapper.query(dto);
        if (admins != null) {
            for (User admin : admins) {
                if (admin.getUserPwd() != null
                        && passwordEncoder.matches(password, admin.getUserPwd())) {
                    return true;
                }
            }
        }
        // 2) 兜底：按用户名 admin 精确查
        User q = new User();
        q.setUserName("admin");
        User admin = userMapper.getByActive(q);
        return admin != null && admin.getUserPwd() != null
                && passwordEncoder.matches(password, admin.getUserPwd());
    }

    @Override
    public void initDefaultConfigs() {
        List<SystemConfigEntity> defaults = new ArrayList<>();
        defaults.add(build("basic", "site_name", "智康云健康管理系统",
                "站点名称", false, "string"));
        defaults.add(build("basic", "site_description", "AI 驱动的个人健康管理平台",
                "站点描述", false, "string"));
        defaults.add(build("ai", "provider", "deepseek",
                "AI 厂商", false, "string"));
        defaults.add(build("ai", "max_tokens", "4096",
                "最大输出 token", false, "number"));
        defaults.add(build("security", "ai_api_key", "",
                "AI 接口密钥（敏感，不在此展示）", true, "string"));
        defaults.add(build("security", "embedding_provider", "siliconflow",
                "向量嵌入服务厂商", false, "string"));
        systemConfigMapper.batchSaveOrUpdate(defaults);
        log.info("[SystemConfig] 已初始化默认配置 {} 项", defaults.size());
    }

    private SystemConfigEntity build(String group, String key, String value,
                                     String desc, boolean sensitive, String valueType) {
        SystemConfigEntity cfg = new SystemConfigEntity();
        cfg.setConfigGroup(group);
        cfg.setConfigKey(key);
        cfg.setConfigValue(value);
        cfg.setDescription(desc);
        cfg.setSensitive(sensitive);
        cfg.setValueType(valueType);
        cfg.setCreateTime(LocalDateTime.now());
        cfg.setUpdateTime(LocalDateTime.now());
        return cfg;
    }

    /** 敏感配置统一掩码 */
    private String maskIfSensitive(SystemConfigEntity cfg) {
        if (Boolean.TRUE.equals(cfg.getSensitive())) {
            return MASK;
        }
        return cfg.getConfigValue();
    }
}
