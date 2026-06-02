package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.UserHealthMapper;
import cn.kmbeast.mapper.HealthModelConfigMapper;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.pojo.entity.UserHealth;
import cn.kmbeast.pojo.entity.HealthModelConfig;
import cn.kmbeast.service.AiHealthDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI健康数据服务实现
 * 从MySQL读取用户健康数据，供AI分析使用
 */
@Service
@Slf4j
public class AiHealthDataServiceImpl implements AiHealthDataService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserHealthMapper userHealthMapper;

    @Resource
    private HealthModelConfigMapper healthModelConfigMapper;

    @Override
    public Result<Map<String, Object>> getUserHealthProfile(Integer userId) {
        try {
            Map<String, Object> profile = new LinkedHashMap<>();

            // 1. 获取用户基本信息
            User user = userMapper.getUserById(userId);
            if (user != null) {
                Map<String, Object> userInfo = new LinkedHashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("name", user.getUserName());
                userInfo.put("email", user.getUserEmail());
                profile.put("userInfo", userInfo);
            }

            // 2. 获取全局健康模型配置
            List<HealthModelConfig> globalModels = healthModelConfigMapper.getGlobalModels();

            // 3. 获取用户最近的健康记录
            List<UserHealth> recentRecords = userHealthMapper.getRecentByUserId(userId, 30);

            // 4. 构建健康指标列表（标准JSON格式）
            List<Map<String, Object>> healthIndicators = new ArrayList<>();
            List<Map<String, Object>> abnormalIndicators = new ArrayList<>();
            
            if (recentRecords != null && !recentRecords.isEmpty()) {
                // 按健康模型分组
                Map<Integer, List<UserHealth>> grouped = recentRecords.stream()
                        .collect(Collectors.groupingBy(UserHealth::getHealthModelConfigId));

                for (Map.Entry<Integer, List<UserHealth>> entry : grouped.entrySet()) {
                    Integer modelId = entry.getKey();
                    List<UserHealth> records = entry.getValue();

                    // 找到对应的模型配置
                    String modelName = "未知指标";
                    String unit = "";
                    String valueRange = "";
                    for (HealthModelConfig model : globalModels) {
                        if (model.getId().equals(modelId)) {
                            modelName = model.getName();
                            unit = model.getUnit();
                            valueRange = model.getValueRange();
                            break;
                        }
                    }

                    // 获取最新值
                    UserHealth latest = records.get(0);
                    Map<String, Object> indicator = new LinkedHashMap<>();
                    indicator.put("name", modelName);
                    indicator.put("value", latest.getValue());
                    indicator.put("unit", unit);
                    indicator.put("normalRange", valueRange);
                    indicator.put("recordTime", latest.getCreateTime() != null ? 
                        latest.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");

                    // 判断是否异常
                    boolean isAbnormal = false;
                    if (valueRange != null && valueRange.contains(",")) {
                        try {
                            String[] range = valueRange.split(",");
                            double min = Double.parseDouble(range[0].trim());
                            double max = Double.parseDouble(range[1].trim());
                            double value = Double.parseDouble(latest.getValue());
                            indicator.put("status", (value >= min && value <= max) ? "normal" : "abnormal");
                            isAbnormal = value < min || value > max;
                        } catch (Exception e) {
                            indicator.put("status", "unknown");
                        }
                    } else {
                        indicator.put("status", "unknown");
                    }

                    healthIndicators.add(indicator);
                    
                    if (isAbnormal) {
                        Map<String, Object> abnormal = new LinkedHashMap<>(indicator);
                        abnormal.put("status", "abnormal");
                        abnormalIndicators.add(abnormal);
                    }
                }
            }

            profile.put("healthIndicators", healthIndicators);
            profile.put("abnormalIndicators", abnormalIndicators);
            profile.put("totalRecords", recentRecords != null ? recentRecords.size() : 0);
            profile.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            return ApiResult.success(profile);

        } catch (Exception e) {
            log.error("获取用户健康档案异常: userId={}", userId, e);
            return ApiResult.error("获取健康档案失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Map<String, Object>> getRecentHealthRecords(Integer userId, Integer days) {
        try {
            List<UserHealth> records = userHealthMapper.getRecentByUserId(userId, days);
            List<HealthModelConfig> globalModels = healthModelConfigMapper.getGlobalModels();

            // 构建模型ID到名称的映射
            Map<Integer, HealthModelConfig> modelMap = new HashMap<>();
            for (HealthModelConfig model : globalModels) {
                modelMap.put(model.getId(), model);
            }

            // 格式化记录
            List<Map<String, Object>> formattedRecords = new ArrayList<>();
            for (UserHealth record : records) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("时间", record.getCreateTime());

                HealthModelConfig model = modelMap.get(record.getHealthModelConfigId());
                if (model != null) {
                    item.put("指标", model.getName());
                    item.put("值", record.getValue() + " " + model.getUnit());
                    item.put("正常范围", model.getValueRange());
                } else {
                    item.put("指标", "ID-" + record.getHealthModelConfigId());
                    item.put("值", record.getValue());
                }

                formattedRecords.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("天数", days);
            result.put("记录数", formattedRecords.size());
            result.put("记录列表", formattedRecords);

            return ApiResult.success(result);

        } catch (Exception e) {
            log.error("获取用户最近健康记录异常: userId={}, days={}", userId, days, e);
            return ApiResult.error("获取健康记录失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Map<String, Object>> getAbnormalIndicators(Integer userId) {
        try {
            List<UserHealth> records = userHealthMapper.getRecentByUserId(userId, 30);
            List<HealthModelConfig> globalModels = healthModelConfigMapper.getGlobalModels();

            Map<Integer, HealthModelConfig> modelMap = new HashMap<>();
            for (HealthModelConfig model : globalModels) {
                modelMap.put(model.getId(), model);
            }

            List<Map<String, Object>> abnormalList = new ArrayList<>();

            // 按健康模型分组，找出异常指标
            Map<Integer, List<UserHealth>> grouped = records.stream()
                    .collect(Collectors.groupingBy(UserHealth::getHealthModelConfigId));

            for (Map.Entry<Integer, List<UserHealth>> entry : grouped.entrySet()) {
                Integer modelId = entry.getKey();
                List<UserHealth> modelRecords = entry.getValue();

                HealthModelConfig model = modelMap.get(modelId);
                if (model == null || model.getValueRange() == null) continue;

                try {
                    String[] range = model.getValueRange().split(",");
                    double min = Double.parseDouble(range[0]);
                    double max = Double.parseDouble(range[1]);

                    // 检查最近的记录是否异常
                    UserHealth latest = modelRecords.get(0);
                    double latestValue = Double.parseDouble(latest.getValue());

                    if (latestValue < min || latestValue > max) {
                        Map<String, Object> abnormal = new LinkedHashMap<>();
                        abnormal.put("指标", model.getName());
                        abnormal.put("最新值", latestValue + " " + model.getUnit());
                        abnormal.put("正常范围", model.getValueRange());
                        abnormal.put("状态", latestValue < min ? "偏低" : "偏高");
                        abnormal.put("记录时间", latest.getCreateTime());
                        abnormalList.add(abnormal);
                    }
                } catch (Exception e) {
                    // 跳过无法解析的记录
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("异常指标数", abnormalList.size());
            result.put("异常指标列表", abnormalList);

            return ApiResult.success(result);

        } catch (Exception e) {
            log.error("获取用户异常指标异常: userId={}", userId, e);
            return ApiResult.error("获取异常指标失败: " + e.getMessage());
        }
    }
}
