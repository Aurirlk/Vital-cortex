package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.PatientProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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

    /**
     * 全量档案（阶段 0-B 知识图谱抽取用）。
     *
     * <p>知识图谱需要遍历全部 patient_profile 建「患者-疾病/过敏/用药」边，
     * 而此前只有 {@link #getByUserId} 单条查询。
     *
     * <p>⚠️ 仅供离线导入端点调用（管理员），<b>不得</b>暴露到用户端接口 ——
     * 本表含慢病史、家族史等敏感信息。
     */
    List<PatientProfile> listAll();
}
