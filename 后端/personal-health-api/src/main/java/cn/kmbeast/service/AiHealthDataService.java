package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;

import java.util.Map;

/**
 * AI健康数据服务接口
 * 提供用户健康数据查询，供AI分析使用
 */
public interface AiHealthDataService {

    /**
     * 获取用户健康档案摘要
     * 包含：基本信息、最近健康记录、异常指标等
     *
     * @param userId 用户ID
     * @return 健康档案摘要文本
     */
    Result<Map<String, Object>> getUserHealthProfile(Integer userId);

    /**
     * 获取用户最近N天的健康记录
     *
     * @param userId 用户ID
     * @param days   天数
     * @return 健康记录列表
     */
    Result<Map<String, Object>> getRecentHealthRecords(Integer userId, Integer days);

    /**
     * 获取用户异常指标列表
     *
     * @param userId 用户ID
     * @return 异常指标列表
     */
    Result<Map<String, Object>> getAbnormalIndicators(Integer userId);
}
