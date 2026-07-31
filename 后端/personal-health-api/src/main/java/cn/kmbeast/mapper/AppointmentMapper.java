package cn.kmbeast.mapper;

import cn.kmbeast.pojo.dto.query.extend.AppointmentQueryDto;
import cn.kmbeast.pojo.entity.Appointment;
import cn.kmbeast.pojo.vo.AppointmentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface AppointmentMapper {
    void save(Appointment appointment);
    void update(Appointment appointment);
    void batchDelete(@Param("ids") List<Long> ids);
    List<AppointmentVO> query(AppointmentQueryDto queryDto);
    Integer queryCount(AppointmentQueryDto queryDto);
    AppointmentVO getById(@Param("id") Integer id);
    Integer countByScheduleId(@Param("scheduleId") Integer scheduleId);
    List<Map<String, Object>> countByStatus();
    List<Map<String, Object>> countByDepartment();
    List<Map<String, Object>> countByDays(@Param("days") int days);
    List<Map<String, Object>> topDoctors(@Param("limit") int limit);
}
