package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.AppointmentQueryDto;
import cn.kmbeast.pojo.entity.*;
import cn.kmbeast.pojo.vo.AppointmentVO;
import cn.kmbeast.pojo.vo.DepartmentVO;
import cn.kmbeast.pojo.vo.DoctorVO;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentService {
    // 科室
    Result<List<Department>> getDepartments();
    Result<Void> saveDepartment(Department department);
    Result<Void> updateDepartment(Department department);
    Result<Void> deleteDepartments(List<Long> ids);

    /**
     * 科室树（2026-10-03 科室支持多级层级后的导航结构）
     *
     * @return 带 children 与 doctorCount 的树形结构
     */
    Result<List<DepartmentVO>> getDepartmentTree();

    // 医生
    Result<List<DoctorVO>> getDoctors(Integer departmentId);
    Result<DoctorVO> getDoctorById(Integer id);
    Result<Void> saveDoctor(HospitalDoctor doctor);
    Result<Void> updateDoctor(HospitalDoctor doctor);
    Result<Void> deleteDoctors(List<Long> ids);

    /**
     * 医生分页列表 —— 医生/科室数量增长后的可扩展性改造
     *
     * @param name          姓名关键字（可空）
     * @param departmentId  科室ID（可空，支持多科室医生）
     * @param titleLevel    职称（可空）
     * @param status        状态（可空）
     */
    Result<List<DoctorVO>> getDoctorsPage(String name, Integer departmentId,
                                          String titleLevel, Integer status,
                                          Integer current, Integer size);

    /**
     * 医生远程搜索 —— 供 {@code <DoctorSelect>} 组件调用
     *
     * @param keyword      姓名/账号/职称关键字
     * @param departmentId 科室ID（可空）
     * @param limit        返回条数上限
     */
    Result<List<DoctorVO>> searchDoctors(String keyword, Integer departmentId, Integer limit);

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
