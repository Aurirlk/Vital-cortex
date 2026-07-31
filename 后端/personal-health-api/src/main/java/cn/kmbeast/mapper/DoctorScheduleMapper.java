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
}
