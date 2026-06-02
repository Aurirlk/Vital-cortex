package cn.kmbeast.service;

import java.util.Map;

/**
 * 数据导出服务
 * 将MySQL数据导出为JSON文件，供AI工具读取
 */
public interface DataExportService {

    /**
     * 导出所有药品数据到JSON文件
     * @return 导出结果
     */
    Map<String, Object> exportDrugsToJson();

    /**
     * 导出指定用户的健康指标数据到JSON文件
     * @param userId 用户ID
     * @return 导出结果
     */
    Map<String, Object> exportHealthDataToJson(Integer userId);

    /**
     * 导出所有用户的健康指标数据到JSON文件
     * @return 导出结果
     */
    Map<String, Object> exportAllHealthDataToJson();

    /**
     * 导出所有数据（药品+健康指标）
     * @return 导出结果
     */
    Map<String, Object> exportAll();
}
