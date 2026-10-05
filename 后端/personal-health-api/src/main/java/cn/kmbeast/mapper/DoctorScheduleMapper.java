package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.DoctorSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DoctorScheduleMapper {
    void save(DoctorSchedule schedule);
    void update(DoctorSchedule schedule);
    void batchDelete(@Param("ids") List<Long> ids);
    List<DoctorSchedule> queryByDoctorAndDate(@Param("doctorId") Integer doctorId, @Param("date") LocalDate date);
    DoctorSchedule getById(@Param("id") Integer id);
    List<DoctorSchedule> queryAvailable(@Param("departmentId") Integer departmentId, @Param("date") LocalDate date);
    void incrementBookedCount(@Param("id") Integer id);
    void decrementBookedCount(@Param("id") Integer id);

    // ==================== 医生端专用（2026-10-04） ====================

    /**
     * 某位医生在日期区间的全部排班（含已停诊），用于「我的排班」
     *
     * <p>⚠️ {@code doctorId} 必须来自 token，不得由前端传入。
     */
    List<DoctorSchedule> queryByDoctorAndRange(@Param("doctorId") Integer doctorId,
                                               @Param("fromDate") LocalDate fromDate,
                                               @Param("toDate") LocalDate toDate);

    /**
     * 归属校验：该排班是否属于指定医生
     *
     * <p>医生修改/停用自己的排班前必须先过这一关，
     * 否则篡改 scheduleId 就能操作他人排班。
     *
     * @return 匹配行数，1 表示归属本人，0 表示不存在或属于他人
     */
    Integer countByIdAndDoctor(@Param("id") Integer id, @Param("doctorId") Integer doctorId);
}
