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

    /**
     * 原子的「占号」：仅当排班可用且未满时才 +1。
     *
     * <p>2026-10-10 新增，用于修复超卖。原先 {@code bookAppointment} 的写法是
     * 「先 SELECT 判断 bookedCount &lt; maxPatients，再 UPDATE +1」——
     * 检查与自增之间存在竞态窗口，N 个并发请求会同时读到「还有 1 个号」，
     * 然后各自 +1，最终超卖 N-1 个号。
     *
     * <p>把条件写进 UPDATE 的 WHERE，由数据库行锁保证串行：
     * 排在后面的事务会阻塞到前一个提交，重新读到已自增的 booked_count，
     * 条件不再成立 → 影响 0 行 → 上层据此返回「号源已满」。
     *
     * <p>同时兼作「排班有效性」校验（{@code status = 1}），
     * 已停诊/不存在的排班一律占号失败。
     *
     * @return 影响行数：1 = 占号成功，0 = 不存在 / 已停诊 / 已满
     */
    Integer reserveSlot(@Param("id") Integer id);

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
