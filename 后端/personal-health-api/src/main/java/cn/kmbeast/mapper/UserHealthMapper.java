package cn.kmbeast.mapper;

import cn.kmbeast.pojo.dto.query.extend.UserHealthQueryDto;
import cn.kmbeast.pojo.entity.Tags;
import cn.kmbeast.pojo.entity.UserHealth;
import cn.kmbeast.pojo.vo.UserHealthVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 用户健康记录持久化接口
 */
@Mapper
public interface UserHealthMapper {

    void batchSave(List<UserHealth> userHealths);

    void update(UserHealth userHealth);

    void batchDelete(@Param(value = "ids") List<Long> ids);

    List<UserHealthVO> query(UserHealthQueryDto userHealthQueryDto);

    Integer queryCount(UserHealthQueryDto userHealthQueryDto);

    /**
     * 获取用户最近N天的健康记录
     *
     * @param userId 用户ID
     * @param days   天数
     * @return 健康记录列表
     */
    List<UserHealth> getRecentByUserId(@Param("userId") Integer userId, @Param("days") Integer days);

    /**
     * 阶段 2（双轨参数化查询）：按指标规范名 + 用户 + 时间窗查询健康记录。
     *
     * <p>完全参数化：指标名来自 {@code HealthMetric} 枚举白名单（不是 LLM 原始字符串），
     * 其余均为 MyBatis 占位符，<b>不存在 SQL 注入面</b>。
     *
     * @param userId     用户ID（来自 ToolContext，用户端强制本人）
     * @param metricName 指标规范名（health_model_config.name）
     * @param days       时间窗（天），调用方已钳制
     * @param limit      最大返回条数，调用方已钳制
     * @return 每行含 value / createTime / unit / valueRange
     */
    List<Map<String, Object>> queryByMetric(@Param("userId") Integer userId,
                                            @Param("metricName") String metricName,
                                            @Param("days") Integer days,
                                            @Param("limit") Integer limit);

    /**
     * 阶段 2（医生双轨）：校验医生对患者是否存在接诊/就诊关系。
     *
     * <p>授权模型：医生只能查与自己在 {@code appointment} 或 {@code visit_record} 中有过
     * 关联的患者。两表取并集，任一存在即视为已授权。
     *
     * @return 关联记录数（0 = 无授权）
     */
    Integer countDoctorPatientAccess(@Param("doctorId") Integer doctorId,
                                     @Param("patientId") Integer patientId);

}
