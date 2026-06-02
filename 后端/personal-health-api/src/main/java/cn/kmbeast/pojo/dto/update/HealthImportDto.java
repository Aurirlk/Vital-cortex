package cn.kmbeast.pojo.dto.update;

import java.util.List;

/**
 * 健康记录JSON导入请求DTO
 */
public class HealthImportDto {
    
    /**
     * 健康记录列表
     */
    private List<HealthRecordItem> records;

    public List<HealthRecordItem> getRecords() {
        return records;
    }

    public void setRecords(List<HealthRecordItem> records) {
        this.records = records;
    }
    
    public static class HealthRecordItem {
        /**
         * 健康模型ID（优先使用）
         */
        private Integer healthModelConfigId;
        
        /**
         * 健康模型名称（当没有ID时，按名称匹配）
         */
        private String modelName;
        
        /**
         * 记录值
         */
        private String value;
        
        /**
         * 记录时间（可选，默认当前时间）
         * 格式：yyyy-MM-dd HH:mm:ss
         */
        private String recordTime;

        public Integer getHealthModelConfigId() {
            return healthModelConfigId;
        }

        public void setHealthModelConfigId(Integer healthModelConfigId) {
            this.healthModelConfigId = healthModelConfigId;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getRecordTime() {
            return recordTime;
        }

        public void setRecordTime(String recordTime) {
            this.recordTime = recordTime;
        }
    }
}
