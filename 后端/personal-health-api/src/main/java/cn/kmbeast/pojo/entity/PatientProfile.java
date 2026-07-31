package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户画像实体类
 * 持久化存储用户的基本信息、疾病史、生活习惯和公共健康指标
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatientProfile {

    /**
     * 主键
     */
    private Integer id;

    /**
     * 用户ID
     */
    private Integer userId;

    // ========== 基本信息 ==========
    
    /**
     * 性别（male/female）
     */
    private String gender;

    /**
     * 年龄
     */
    private Integer age;

    /**
     * 出生日期
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    // ========== 身体指标 ==========
    
    /**
     * 身高（cm）
     */
    private Double height;

    /**
     * 体重（kg）
     */
    private Double weight;

    /**
     * BMI
     */
    private Double bmi;

    // ========== 疾病史（JSON数组） ==========
    
    /**
     * 基础疾病列表
     */
    private String chronicDiseases;

    /**
     * 过敏史
     */
    private String allergies;

    /**
     * 用药史
     */
    private String medications;

    /**
     * 手术史
     */
    private String surgeries;

    /**
     * 家族病史
     */
    private String familyHistory;

    // ========== 生活习惯（JSON对象） ==========
    
    /**
     * 生活习惯信息（JSON格式）
     * 包含：smoking, drinking, exerciseFrequency, dietPreference, sleepHours, sleepQuality
     */
    private String lifestyle;

    // ========== 健康目标 ==========
    
    /**
     * 健康目标列表（JSON数组）
     */
    private String healthGoals;

    // ========== 公共健康指标（从 health_model_config category=PUBLIC 同步） ==========
    
    /**
     * 空腹血糖（mmol/L）
     */
    private Double fastingBloodGlucose;

    /**
     * 餐后血糖（mmol/L）
     */
    private Double postprandialBloodGlucose;

    /**
     * 总胆固醇（mmol/L）
     */
    private Double totalCholesterol;

    /**
     * 甘油三酯（mmol/L）
     */
    private Double triglycerides;

    /**
     * 高密度脂蛋白（mmol/L）
     */
    private Double hdlCholesterol;

    /**
     * 低密度脂蛋白（mmol/L）
     */
    private Double ldlCholesterol;

    /**
     * 收缩压（mmHg）
     */
    private Integer systolicPressure;

    /**
     * 舒张压（mmHg）
     */
    private Integer diastolicPressure;

    /**
     * 静息心率（次/分）
     */
    private Integer restingHeartRate;

    // ========== 时间戳 ==========
    
    /**
     * 最后更新时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUpdateTime;

    /**
     * 创建时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 计算BMI
     */
    public void calculateBmi() {
        if (height != null && weight != null && height > 0) {
            double heightM = height / 100.0;
            this.bmi = weight / (heightM * heightM);
        }
    }

    /**
     * 根据健康模型配置ID和值更新对应的公共指标字段
     * 
     * @param symbol 健康模型符号（如 SBP, DBP, FPG 等）
     * @param value 健康指标值
     */
    public void updatePublicIndicator(String symbol, String value) {
        if (symbol == null || value == null) {
            return;
        }
        
        try {
            switch (symbol.toUpperCase()) {
                case "SBP":
                    this.systolicPressure = Integer.parseInt(value);
                    break;
                case "DBP":
                    this.diastolicPressure = Integer.parseInt(value);
                    break;
                case "FPG":
                    this.fastingBloodGlucose = Double.parseDouble(value);
                    break;
                case "PBG":
                    this.postprandialBloodGlucose = Double.parseDouble(value);
                    break;
                case "TC":
                    this.totalCholesterol = Double.parseDouble(value);
                    break;
                case "TG":
                    this.triglycerides = Double.parseDouble(value);
                    break;
                case "HDL":
                    this.hdlCholesterol = Double.parseDouble(value);
                    break;
                case "LDL":
                    this.ldlCholesterol = Double.parseDouble(value);
                    break;
                case "HR":
                    this.restingHeartRate = Integer.parseInt(value);
                    break;
                case "BMI":
                    this.bmi = Double.parseDouble(value);
                    break;
                default:
                    // 未知符号，忽略
                    break;
            }
        } catch (NumberFormatException e) {
            // 数值解析失败，忽略
        }
    }

    @Override
    public String toString() {
        return String.format("PatientProfile{userId=%d, gender=%s, age=%d, bmi=%.1f}", 
            userId, gender, age, bmi != null ? bmi : 0.0);
    }
}
