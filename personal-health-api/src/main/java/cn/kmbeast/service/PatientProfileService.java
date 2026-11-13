package cn.kmbeast.service;

import cn.kmbeast.pojo.entity.PatientProfile;

/**
 * 用户画像服务接口
 */
public interface PatientProfileService {

    /**
     * 根据用户ID获取画像
     *
     * @param userId 用户ID
     * @return 用户画像
     */
    PatientProfile getByUserId(Integer userId);

    /**
     * 保存或更新用户画像
     *
     * @param patientProfile 用户画像
     */
    void saveOrUpdate(PatientProfile patientProfile);

    /**
     * 更新用户基本信息
     *
     * @param userId 用户ID
     * @param gender 性别
     * @param age 年龄
     * @param birthDate 出生日期
     */
    void updateBasicInfo(Integer userId, String gender, Integer age, String birthDate);

    /**
     * 更新身体指标
     *
     * @param userId 用户ID
     * @param height 身高
     * @param weight 体重
     */
    void updateBodyMetrics(Integer userId, Double height, Double weight);

    /**
     * 更新疾病史
     *
     * @param userId 用户ID
     * @param chronicDiseases 基础疾病（JSON数组）
     * @param allergies 过敏史（JSON数组）
     * @param medications 用药史（JSON数组）
     */
    void updateMedicalHistory(Integer userId, String chronicDiseases, String allergies, String medications);

    /**
     * 更新公共健康指标
     * 从 user_health 表同步 category=PUBLIC 的指标到画像
     *
     * @param userId 用户ID
     * @param symbol 健康模型符号（如 SBP, DBP, FPG 等）
     * @param value 健康指标值
     */
    void updatePublicIndicator(Integer userId, String symbol, String value);

    /**
     * 删除用户画像
     *
     * @param userId 用户ID
     */
    void delete(Integer userId);
}
