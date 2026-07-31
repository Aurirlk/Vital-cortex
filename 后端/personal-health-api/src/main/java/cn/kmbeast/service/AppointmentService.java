package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.AppointmentQueryDto;
import cn.kmbeast.pojo.entity.*;
import cn.kmbeast.pojo.vo.AppointmentVO;
import cn.kmbeast.pojo.vo.DoctorVO;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentService {
    // 科室
    Result<List<Department>> getDepartments();
    Result<Void> saveDepartment(Department department);
    Result<Void> updateDepartment(Department department);
    Result<Void> deleteDepartments(List<Long> ids);

    // 医生
    Result<List<DoctorVO>> getDoctors(Integer departmentId);
    Result<DoctorVO> getDoctorById(Integer id);
    Result<Void> saveDoctor(HospitalDoctor doctor);
    Result<Void> updateDoctor(HospitalDoctor doctor);
    Result<Void> deleteDoctors(List<Long> ids);

    // 排班
    Result<List<DoctorSchedule>> getSchedules(Integer doctorId, LocalDate date);
    Result<List<DoctorSchedule>> getAvailableSchedules(Integer departmentId, LocalDate date);
    Result<Void> saveSchedule(DoctorSchedule schedule);
    Result<Void> updateSchedule(DoctorSchedule schedule);
    Result<Void> deleteSchedules(List<Long> ids);

    // 预约
    Result<Void> bookAppointment(Appointment appointment);
    Result<Void> cancelAppointment(Integer id, String reason);
    Result<List<AppointmentVO>> queryAppointments(AppointmentQueryDto queryDto);
    Result<AppointmentVO> getAppointmentById(Integer id);

    // 就诊记录
    Result<Void> saveVisitRecord(VisitRecord record);
    Result<Void> updateVisitRecord(VisitRecord record);
    Result<VisitRecord> getVisitRecord(Integer appointmentId);
}
