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
                userInfo.put("用户名", user.getUserName());
                userInfo.put("邮箱", user.getUserEmail());
                userInfo.put("注册时间", user.getCreateTime());
                profile.put("用户信息", userInfo);
            }

            // 2. 获取全局健康模型配置
            List<HealthModelConfig> globalModels = healthModelConfigMapper.getGlobalModels();

            // 3. 获取用户最近的健康记录
            List<UserHealth> recentRecords = userHealthMapper.getRecentByUserId(userId, 30);

            // 4. 构建健康指标摘要
            Map<String, Object> healthSummary = new LinkedHashMap<>();
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
                    indicator.put("最新值", latest.getValue() + " " + unit);
                    indicator.put("记录时间", latest.getCreateTime());
                    indicator.put("正常范围", valueRange);

                    // 判断是否异常
                    if (valueRange != null && valueRange.contains(",")) {
                        try {
                            String[] range = valueRange.split(",");
                            double min = Double.parseDouble(range[0]);
                            double max = Double.parseDouble(range[1]);
                            double value = Double.parseDouble(latest.getValue());
                            String status = (value >= min && value <= max) ? "正常" : "异常";
                            indicator.put("状态", status);
                        } catch (Exception e) {
                            indicator.put("状态", "无法判断");
                        }
                    }

                    // 计算统计信息
                    List<Double> values = records.stream()
                            .map(r -> {
                                try {
                                    return Double.parseDouble(r.getValue());
                                } catch (Exception e) {
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    if (!values.isEmpty()) {
                        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                        indicator.put("平均值", String.format("%.2f", avg));
                        indicator.put("最小值", String.format("%.2f", min));
                        indicator.put("最大值", String.format("%.2f", max));
                        indicator.put("记录次数", records.size());
                    }

                    healthSummary.put(modelName, indicator);
                }
            }

            profile.put("健康指标摘要", healthSummary);
            profile.put("记录总数", recentRecords != null ? recentRecords.size() : 0);

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
