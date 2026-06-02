package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.HealthModelConfigMapper;
import cn.kmbeast.mapper.UserHealthMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.base.QueryDto;
import cn.kmbeast.pojo.dto.query.extend.HealthModelConfigQueryDto;
import cn.kmbeast.pojo.dto.query.extend.UserHealthQueryDto;
import cn.kmbeast.pojo.dto.update.HealthImportDto;
import cn.kmbeast.pojo.em.IsReadEnum;
import cn.kmbeast.pojo.em.MessageType;
import cn.kmbeast.pojo.entity.HealthModelConfig;
import cn.kmbeast.pojo.entity.Message;
import cn.kmbeast.pojo.entity.UserHealth;
import cn.kmbeast.pojo.vo.ChartVO;
import cn.kmbeast.pojo.vo.HealthModelConfigVO;
import cn.kmbeast.pojo.vo.UserHealthVO;
import cn.kmbeast.service.MessageService;
import cn.kmbeast.service.UserHealthService;
import cn.kmbeast.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户健康记录业务逻辑实现
 */
@Slf4j
@Service
public class UserHealthServiceImpl implements UserHealthService {

    @Resource
    private UserHealthMapper userHealthMapper;
    @Resource
    private HealthModelConfigMapper healthModelConfigMapper;
    @Resource
    private MessageService messageService;

    /**
     * 用户健康记录新增
     *
     * @param userHealths 参数
     * @return Result<Void>
     */
    @Override
    public Result<Void> save(List<UserHealth> userHealths) {
        dealMessage(userHealths);
        dealRole(userHealths);
        userHealthMapper.batchSave(userHealths);
        return ApiResult.success();
    }

    public void dealRole(List<UserHealth> userHealths) {
        LocalDateTime nowTime = LocalDateTime.now();
        // 获取当前用户的ID
        Integer userId = LocalThreadHolder.getUserId();
        userHealths.forEach(userHealth -> {
            userHealth.setUserId(userId);
            userHealth.setCreateTime(nowTime);
        });
    }

    /**
     * 如果有异常指标情况，此方法做通知转发
     *
     * @param userHealths 用户健康记录集合
     */
    private void dealMessage(List<UserHealth> userHealths) {
        List<Message> messageList = new ArrayList<>();
        userHealths.forEach(userHealth -> {
            userHealth.setCreateTime(LocalDateTime.now());
            Integer healthModelConfigId = userHealth.getHealthModelConfigId();
            HealthModelConfigQueryDto queryDto = new HealthModelConfigQueryDto();
            queryDto.setId(healthModelConfigId);
            List<HealthModelConfigVO> healthModelConfigs = healthModelConfigMapper.query(queryDto);
            if (!CollectionUtils.isEmpty(healthModelConfigs)) {
                HealthModelConfig healthModelConfig = healthModelConfigs.get(0);
                // 值范围为：101,230
                String valueRange = healthModelConfig.getValueRange();
                String[] values = valueRange.split(",");
                // 最小值
                int mixValue = Integer.parseInt(values[0]);
                // 最大值
                int maxValue = Integer.parseInt(values[1]);
                // 如果用户输入的指标是超出正常范围的，需要通知用户处理
                double value = Double.parseDouble(String.valueOf(userHealth.getValue()));
                // 异常情况
                if (value < mixValue || value > maxValue) {
                    // 封装消息体
                    Message message = sendMessage(healthModelConfig, userHealth);
                    messageList.add(message);
                }
            }
        });
        if (!CollectionUtils.isEmpty(messageList)) {
            // 丢给消息业务逻辑处理
            messageService.dataWordSave(messageList);
        }
    }

    /**
     * 处理符合消息通知的用户健康记录
     *
     * @param userHealth 用户健康记录
     * @return List<Message>
     */
    private Message sendMessage(HealthModelConfig healthModelConfig, UserHealth userHealth) {
        Message message = new Message();
        // 指标提醒类通知
        message.setMessageType(MessageType.DATA_MESSAGE.getType());
        // 消息提醒时间
        message.setCreateTime(LocalDateTime.now());
        // 是否已读
        message.setIsRead(IsReadEnum.READ_NO.getStatus());
        // 接收者
        message.setReceiverId(LocalThreadHolder.getUserId());
        // 消息体
        message.setContent("你记录的【" + healthModelConfig.getName() + "】超标了，正常值范围:[" + healthModelConfig.getValueRange() + "]，请注意休息。必要时请就医!");
        return message;
    }

    /**
     * 用户健康记录删除
     *
     * @param ids 参数
     * @return Result<Void>
     */
    @Override
    public Result<Void> batchDelete(List<Long> ids) {
        userHealthMapper.batchDelete(ids);
        return ApiResult.success();
    }

    /**
     * 用户健康记录修改
     *
     * @param userHealth 参数
     * @return Result<Void>
     */
    @Override
    public Result<Void> update(UserHealth userHealth) {
        userHealthMapper.update(userHealth);
        return ApiResult.success();
    }

    /**
     * 用户健康记录查询
     *
     * @param userHealthQueryDto 查询参数
     * @return Result<List < UserHealthVO>>
     */
    @Override
    public Result<List<UserHealthVO>> query(UserHealthQueryDto userHealthQueryDto) {
        List<UserHealthVO> userHealthVOS = userHealthMapper.query(userHealthQueryDto);
        Integer totalCount = userHealthMapper.queryCount(userHealthQueryDto);
        return PageResult.success(userHealthVOS, totalCount);
    }

    /**
     * 统计模型存量数据
     *
     * @return Result<List < ChartVO>> 响应结果
     */
    @Override
    public Result<List<ChartVO>> daysQuery(Integer day) {
        QueryDto queryDto = DateUtil.startAndEndTime(day);
        UserHealthQueryDto userHealthQueryDto = new UserHealthQueryDto();
        userHealthQueryDto.setStartTime(queryDto.getStartTime());
        userHealthQueryDto.setEndTime(queryDto.getEndTime());
        List<UserHealthVO> userHealthVOS = userHealthMapper.query(userHealthQueryDto);
        List<LocalDateTime> localDateTimes = userHealthVOS.stream().map(UserHealthVO::getCreateTime).collect(Collectors.toList());
        List<ChartVO> chartVOS = DateUtil.countDatesWithinRange(day, localDateTimes);
        return ApiResult.success(chartVOS);
    }

    /**
     * 从JSON导入健康记录
     */
    @Override
    public Result<Map<String, Object>> importFromJson(Integer userId, HealthImportDto importDto) {
        if (importDto.getRecords() == null || importDto.getRecords().isEmpty()) {
            return ApiResult.error("导入数据为空");
        }

        // 获取所有健康模型配置，用于按名称匹配
        List<HealthModelConfig> allModels = healthModelConfigMapper.getGlobalModels();
        Map<String, HealthModelConfig> modelNameMap = new HashMap<>();
        for (HealthModelConfig model : allModels) {
            modelNameMap.put(model.getName(), model);
        }

        List<UserHealth> toSave = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        for (HealthImportDto.HealthRecordItem item : importDto.getRecords()) {
            try {
                Integer modelId = item.getHealthModelConfigId();
                
                // 如果没有ID，尝试按名称匹配
                if (modelId == null && item.getModelName() != null) {
                    HealthModelConfig matchedModel = modelNameMap.get(item.getModelName());
                    if (matchedModel != null) {
                        modelId = matchedModel.getId();
                    } else {
                        failCount++;
                        errors.add("未找到模型: " + item.getModelName());
                        continue;
                    }
                }

                if (modelId == null) {
                    failCount++;
                    errors.add("缺少模型ID或名称");
                    continue;
                }

                if (item.getValue() == null || item.getValue().isEmpty()) {
                    failCount++;
                    errors.add("记录值为空");
                    continue;
                }

                UserHealth record = new UserHealth();
                record.setUserId(userId);
                record.setHealthModelConfigId(modelId);
                record.setValue(item.getValue());
                
                // 解析时间
                if (item.getRecordTime() != null && !item.getRecordTime().isEmpty()) {
                    try {
                        record.setCreateTime(LocalDateTime.parse(item.getRecordTime().replace(" ", "T")));
                    } catch (Exception e) {
                        record.setCreateTime(LocalDateTime.now());
                    }
                } else {
                    record.setCreateTime(LocalDateTime.now());
                }

                toSave.add(record);
                successCount++;
            } catch (Exception e) {
                failCount++;
                errors.add("处理记录异常: " + e.getMessage());
            }
        }

        // 批量保存
        if (!toSave.isEmpty()) {
            userHealthMapper.batchSave(toSave);
            log.info("[健康导入] 用户{}成功导入{}条记录", userId, successCount);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", importDto.getRecords().size());
        result.put("success", successCount);
        result.put("fail", failCount);
        if (!errors.isEmpty()) {
            result.put("errors", errors.subList(0, Math.min(errors.size(), 10))); // 最多返回10条错误
        }

        return ApiResult.success(result);
    }

    /**
     * 导出健康记录为JSON
     */
    @Override
    public Result<List<Map<String, Object>>> exportToJson(Integer userId) {
        // 获取用户最近365天的健康记录
        QueryDto queryDto = DateUtil.startAndEndTime(365);
        UserHealthQueryDto query = new UserHealthQueryDto();
        query.setUserId(userId);
        query.setStartTime(queryDto.getStartTime());
        query.setEndTime(queryDto.getEndTime());
        
        List<UserHealthVO> records = userHealthMapper.query(query);
        
        // 获取所有健康模型配置
        List<HealthModelConfig> allModels = healthModelConfigMapper.getGlobalModels();
        Map<Integer, HealthModelConfig> modelMap = new HashMap<>();
        for (HealthModelConfig model : allModels) {
            modelMap.put(model.getId(), model);
        }

        List<Map<String, Object>> exportData = new ArrayList<>();
        for (UserHealthVO record : records) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("healthModelConfigId", record.getHealthModelConfigId());
            
            HealthModelConfig model = modelMap.get(record.getHealthModelConfigId());
            if (model != null) {
                item.put("modelName", model.getName());
                item.put("unit", model.getUnit());
                item.put("normalRange", model.getValueRange());
            }
            
            item.put("value", record.getValue());
            item.put("recordTime", record.getCreateTime() != null ? 
                record.getCreateTime().toString().replace("T", " ") : "");
            
            exportData.add(item);
        }

        log.info("[健康导出] 用户{}导出{}条记录", userId, exportData.size());
        return ApiResult.success(exportData);
    }

}
