package cn.kmbeast.controller;





import cn.kmbeast.aop.Protector;


import cn.kmbeast.pojo.api.ApiResult;


import cn.kmbeast.pojo.api.Result;


import cn.kmbeast.pojo.entity.SystemConfigEntity;


import cn.kmbeast.service.SystemConfigService;


import lombok.Data;


import lombok.extern.slf4j.Slf4j;


import org.springframework.web.bind.annotation.*;





import javax.annotation.Resource;


import java.util.List;


import java.util.Map;





/**


 * 系统配置管理员接口（仅管理员员）


 * 包含MySQL、OTA、WebSocket、SQLite等系统级配置


 */


@Slf4j


@RestController


@RequestMapping(value = "/system/config")


public class SystemConfigController {





    @Resource


    private SystemConfigService systemConfigService;





    /**


     * 验证管理员密码


     */


    @Protector(role = "管理员")


    @PostMapping("/verify-password")


    public Result<Boolean> verifyPassword(@RequestBody PasswordRequest request) {


        boolean valid = systemConfigService.verifyPassword(request.getPassword());


        if (valid) {


            return ApiResult.success(true);


        } else {


            return ApiResult.error("密码错误");


        }


    }





    /**


     * 获取所有配置（敏感信息脱敏）


     */


    @Protector(role = "管理员")


    @GetMapping("/all")


    public Result<Map<String, Object>> getAllConfigs() {


        Map<String, Object> configs = systemConfigService.getAllConfigs();


        return ApiResult.success(configs);


    }





    /**


     * 获取指定分组的配置     */


    @Protector(role = "管理员")


    @GetMapping("/group/{group}")


    public Result<Map<String, Object>> getConfigByGroup(@PathVariable String group) {


        Map<String, Object> configs = systemConfigService.getConfigByGroup(group);


        return ApiResult.success(configs);


    }





    /**


     * 获取指定配置的值（敏感配置需要密码验证）


     */


    @Protector(role = "管理员")


    @PostMapping("/value")


    public Result<String> getConfigValue(@RequestBody ConfigValueRequest request) {


        try {


            String value = systemConfigService.getConfigValue(


                    request.getGroup(),


                    request.getKey(),


                    request.getPassword()


            );


            return ApiResult.success(value);


        } catch (Exception e) {


            return ApiResult.error(e.getMessage());


        }


    }





    /**


     * 更新单个配置


     */


    @Protector(role = "管理员")


    @PostMapping("/update")


    public Result<Void> updateConfig(@RequestBody ConfigUpdateRequest request) {


        try {


            systemConfigService.updateConfig(


                    request.getGroup(),


                    request.getKey(),


                    request.getValue()


            );


            return ApiResult.success("配置更新成功");


        } catch (Exception e) {


            return ApiResult.error("配置更新失败: " + e.getMessage());


        }


    }





    /**


     * 批量更新配置


     */


    @Protector(role = "管理员")


    @PostMapping("/batch-update")


    public Result<Void> batchUpdateConfig(@RequestBody BatchUpdateRequest request) {


        try {


            systemConfigService.batchUpdateConfig(request.getConfigs());


            return ApiResult.success("批量更新成功");


        } catch (Exception e) {


            return ApiResult.error("批量更新失败: " + e.getMessage());


        }


    }





    /**


     * 重置指定分组的配置为默认?     */


    @Protector(role = "管理员")


    @PostMapping("/reset/{group}")


    public Result<Void> resetConfig(@PathVariable String group) {


        try {


            systemConfigService.initDefaultConfigs();


            return ApiResult.success("配置已重置为默认值");


        } catch (Exception e) {


            return ApiResult.error("重置失败: " + e.getMessage());


        }


    }





    // ==================== DTO?====================





    @Data


    public static class PasswordRequest {


        private String password;


    }





    @Data


    public static class ConfigValueRequest {


        private String group;


        private String key;


        private String password;


    }





    @Data


    public static class ConfigUpdateRequest {


        private String group;


        private String key;


        private String value;


    }





    @Data


    public static class BatchUpdateRequest {


        private List<SystemConfigEntity> configs;


    }


}


