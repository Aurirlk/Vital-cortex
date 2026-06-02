package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.DrugMapper;
import cn.kmbeast.mapper.HealthModelConfigMapper;
import cn.kmbeast.mapper.UserHealthMapper;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.dto.query.extend.UserQueryDto;
import cn.kmbeast.pojo.entity.HealthModelConfig;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.pojo.entity.UserHealth;
import cn.kmbeast.pojo.vo.DrugVO;
import cn.kmbeast.service.DataExportService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 数据导出服务实现
 * 将MySQL数据导出为JSON文件，供AI工具读取
 */
@Slf4j
@Service
public class DataExportServiceImpl implements DataExportService {

    @Resource
    private DrugMapper drugMapper;

    @Resource
    private UserHealthMapper userHealthMapper;

    @Resource
    private HealthModelConfigMapper healthModelConfigMapper;

    @Resource
    private UserMapper userMapper;

    @Value("${vector-cache-dir:./vector_cache}")
    private String vectorCacheDir;

    private String exportDir;

    private String getExportDir() {
        if (exportDir == null) {
            exportDir = vectorCacheDir.replace("vector_cache", "ai_data");
            File dir = new File(exportDir);
            if (!dir.exists()) dir.mkdirs();
        }
        return exportDir;
    }

    @Override
    public Map<String, Object> exportDrugsToJson() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // 查询所有药品
            List<DrugVO> drugs = drugMapper.searchByName("", 1000);
            
            JSONArray drugArray = new JSONArray();
            for (DrugVO drug : drugs) {
                JSONObject obj = new JSONObject();
                obj.put("id", drug.getId());
                obj.put("name", drug.getName());
                obj.put("genericName", drug.getGenericName());
                obj.put("category", drug.getCategory());
                obj.put("description", drug.getDescription());
                obj.put("price", drug.getPrice());
                obj.put("unit", drug.getUnit());
                obj.put("specification", drug.getSpecification());
                obj.put("manufacturer", drug.getManufacturer());
                obj.put("isOtc", drug.getIsOtc());
                obj.put("stock", drug.getStock());
                drugArray.add(obj);
            }

            JSONObject root = new JSONObject();
            root.put("exportTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            root.put("total", drugs.size());
            root.put("drugs", drugArray);

            Path filePath = Paths.get(getExportDir(), "drugs.json");
            Files.write(filePath, JSON.toJSONString(root, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat)
                    .getBytes(StandardCharsets.UTF_8));

            result.put("success", true);
            result.put("count", drugs.size());
            result.put("path", filePath.toString());
            log.info("[Export] 药品数据导出成功: {} 条", drugs.size());
        } catch (Exception e) {
            log.error("[Export] 药品数据导出失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> exportHealthDataToJson(Integer userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            User user = userMapper.getUserById(userId);
            String userName = user != null ? user.getUserName() : "未知用户";

            // 查询用户的健康记录
            List<UserHealth> records = userHealthMapper.getRecentByUserId(userId, 365);
            List<HealthModelConfig> models = healthModelConfigMapper.getGlobalModels();

            Map<Integer, HealthModelConfig> modelMap = new HashMap<>();
            for (HealthModelConfig model : models) {
                modelMap.put(model.getId(), model);
            }

            JSONArray recordArray = new JSONArray();
            for (UserHealth record : records) {
                HealthModelConfig model = modelMap.get(record.getHealthModelConfigId());
                JSONObject obj = new JSONObject();
                obj.put("id", record.getId());
                obj.put("healthModelConfigId", record.getHealthModelConfigId());
                obj.put("modelName", model != null ? model.getName() : "未知");
                obj.put("unit", model != null ? model.getUnit() : "");
                obj.put("normalRange", model != null ? model.getValueRange() : "");
                obj.put("value", record.getValue());
                obj.put("recordTime", record.getCreateTime() != null ?
                        record.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
                recordArray.add(obj);
            }

            JSONObject root = new JSONObject();
            root.put("exportTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            root.put("userId", userId);
            root.put("userName", userName);
            root.put("total", records.size());
            root.put("records", recordArray);

            // 按用户ID存储
            String userDir = getExportDir() + File.separator + "health";
            new File(userDir).mkdirs();
            Path filePath = Paths.get(userDir, "user_" + userId + ".json");
            Files.write(filePath, JSON.toJSONString(root, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat)
                    .getBytes(StandardCharsets.UTF_8));

            result.put("success", true);
            result.put("userId", userId);
            result.put("userName", userName);
            result.put("count", records.size());
            result.put("path", filePath.toString());
            log.info("[Export] 用户{}健康数据导出成功: {} 条", userName, records.size());
        } catch (Exception e) {
            log.error("[Export] 健康数据导出失败: userId={}", userId, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> exportAllHealthDataToJson() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // 查询所有用户
            UserQueryDto queryDto = new UserQueryDto();
            List<User> users = userMapper.query(queryDto);
            int successCount = 0;
            int failCount = 0;

            for (User user : users) {
                Map<String, Object> userResult = exportHealthDataToJson(user.getId());
                if (Boolean.TRUE.equals(userResult.get("success"))) {
                    successCount++;
                } else {
                    failCount++;
                }
            }

            result.put("success", true);
            result.put("totalUsers", users.size());
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            log.info("[Export] 所有用户健康数据导出完成: 成功={}, 失败={}", successCount, failCount);
        } catch (Exception e) {
            log.error("[Export] 批量导出健康数据失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> exportAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("drugs", exportDrugsToJson());
        result.put("healthData", exportAllHealthDataToJson());
        return result;
    }
}
