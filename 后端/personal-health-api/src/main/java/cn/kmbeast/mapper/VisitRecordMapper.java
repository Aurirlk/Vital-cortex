package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.VisitRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VisitRecordMapper {
    void save(VisitRecord record);
    void update(VisitRecord record);
    VisitRecord getByAppointmentId(@Param("appointmentId") Integer appointmentId);
}
