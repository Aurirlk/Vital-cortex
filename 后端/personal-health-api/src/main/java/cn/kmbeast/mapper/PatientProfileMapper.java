package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.PatientProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户画像持久化接口
 */
@Mapper
public interface PatientProfileMapper {

    /**
     * 根据用户ID获取画像
     *
     * @param userId 用户ID
     * @return 用户画像
     */
    PatientProfile getByUserId(@Param("userId") Integer userId);

    /**
     * 插入用户画像
     *
     * @param patientProfile 用户画像
     */
    void insert(PatientProfile patientProfile);

    /**
     * 更新用户画像
     *
     * @param patientProfile 用户画像
     */
    void update(PatientProfile patientProfile);

    /**
     * 插入或更新用户画像（Upsert）
     *
     * @param patientProfile 用户画像
     */
    void insertOrUpdate(PatientProfile patientProfile);

    /**
     * 删除用户画像
     *
     * @param userId 用户ID
     */
    void deleteByUserId(@Param("userId") Integer userId);
}
