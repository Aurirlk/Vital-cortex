package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.VisitRecord;
import cn.kmbeast.pojo.vo.VisitRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VisitRecordMapper {
    void save(VisitRecord record);
    void update(VisitRecord record);
    VisitRecord getByAppointmentId(@Param("appointmentId") Integer appointmentId);

    // ==================== 医生端患者详情（2026-10-04） ====================

    /**
     * 某患者的历史就诊记录（倒序）
     *
     * @param limit 返回条数上限，由 Service 封顶（防拖库）
     */
    List<VisitRecordVO> queryByPatientId(@Param("patientId") Integer patientId,
                                         @Param("limit") Integer limit);

    /**
     * 该预约是否已有就诊记录
     *
     * <p>{@code visit_record.appointment_id} 上有 UNIQUE 约束，
     * 重复插入会直接抛 SQL 异常（前端表现为 500），
     * 因此 Service 需先查后插，给出可读提示。
     */
    Integer countByAppointmentId(@Param("appointmentId") Integer appointmentId);
}
