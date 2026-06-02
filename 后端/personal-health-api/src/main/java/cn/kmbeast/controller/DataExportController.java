package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.service.DataExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 数据导出接口
 * 将MySQL数据导出为JSON文件供AI工具读取
 */
@Slf4j
@RestController
@RequestMapping("/data-export")
public class DataExportController {

    @Resource
    private DataExportService dataExportService;

    /**
     * 导出所有药品数据到JSON
     */
    @Protector(role = "管理员")
    @PostMapping("/drugs")
    public Result<Map<String, Object>> exportDrugs() {
        return ApiResult.success(dataExportService.exportDrugsToJson());
    }

    /**
     * 导出指定用户健康数据到JSON
     */
    @Protector(role = "管理员")
    @PostMapping("/health/{userId}")
    public Result<Map<String, Object>> exportHealthData(@PathVariable Integer userId) {
        return ApiResult.success(dataExportService.exportHealthDataToJson(userId));
    }

    /**
     * 导出所有用户健康数据到JSON
     */
    @Protector(role = "管理员")
    @PostMapping("/health/all")
    public Result<Map<String, Object>> exportAllHealthData() {
        return ApiResult.success(dataExportService.exportAllHealthDataToJson());
    }

    /**
     * 导出所有数据
     */
    @Protector(role = "管理员")
    @PostMapping("/all")
    public Result<Map<String, Object>> exportAll() {
        return ApiResult.success(dataExportService.exportAll());
    }
}
