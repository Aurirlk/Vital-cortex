package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.*;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.AppointmentQueryDto;
import cn.kmbeast.pojo.entity.*;
import cn.kmbeast.pojo.vo.AppointmentVO;
import cn.kmbeast.pojo.vo.DoctorVO;
import cn.kmbeast.service.AppointmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class AppointmentServiceImpl implements AppointmentService {

    @Resource private DepartmentMapper departmentMapper;
    @Resource private HospitalDoctorMapper doctorMapper;
    @Resource private DoctorScheduleMapper scheduleMapper;
    @Resource private AppointmentMapper appointmentMapper;
    @Resource private VisitRecordMapper visitRecordMapper;

    @Override
    public Result<List<Department>> getDepartments() {
        return ApiResult.success(departmentMapper.queryAll());
    }

    @Override
    public Result<Void> saveDepartment(Department department) {
        department.setCreateTime(LocalDateTime.now());
        departmentMapper.save(department);
        return ApiResult.success();
    }

    @Override
    public Result<Void> updateDepartment(Department department) {
        departmentMapper.update(department);
        return ApiResult.success();
    }

    @Override
    public Result<Void> deleteDepartments(List<Long> ids) {
        departmentMapper.batchDelete(ids);
        return ApiResult.success();
    }

    @Override
    public Result<List<DoctorVO>> getDoctors(Integer departmentId) {
        return ApiResult.success(doctorMapper.queryByDepartment(departmentId));
    }

    @Override
    public Result<DoctorVO> getDoctorById(Integer id) {
        return ApiResult.success(doctorMapper.getById(id));
    }

    @Override
    public Result<Void> saveDoctor(HospitalDoctor doctor) {
        doctor.setCreateTime(LocalDateTime.now());
        doctorMapper.save(doctor);
        return ApiResult.success();
    }

    @Override
    public Result<Void> updateDoctor(HospitalDoctor doctor) {
        doctorMapper.update(doctor);
        return ApiResult.success();
    }

    @Override
    public Result<Void> deleteDoctors(List<Long> ids) {
        doctorMapper.batchDelete(ids);
        return ApiResult.success();
    }

    @Override
    public Result<List<DoctorSchedule>> getSchedules(Integer doctorId, LocalDate date) {
        return ApiResult.success(scheduleMapper.queryByDoctorAndDate(doctorId, date));
    }

    @Override
    public Result<List<DoctorSchedule>> getAvailableSchedules(Integer departmentId, LocalDate date) {
        return ApiResult.success(scheduleMapper.queryAvailable(departmentId, date));
    }

    @Override
    public Result<Void> saveSchedule(DoctorSchedule schedule) {
        schedule.setBookedCount(0);
        schedule.setVersion(0);
        schedule.setCreateTime(LocalDateTime.now());
        scheduleMapper.save(schedule);
        return ApiResult.success();
    }

    @Override
    public Result<Void> updateSchedule(DoctorSchedule schedule) {
        scheduleMapper.update(schedule);
        return ApiResult.success();
    }

    @Override
    public Result<Void> deleteSchedules(List<Long> ids) {
        scheduleMapper.batchDelete(ids);
        return ApiResult.success();
    }

    @Override
    @Transactional
    public Result<Void> bookAppointment(Appointment appointment) {
        DoctorSchedule schedule = scheduleMapper.getById(appointment.getScheduleId());
        if (schedule == null) {
            return ApiResult.error("排班不存?");
        }
        if (schedule.getBookedCount() >= schedule.getMaxPatients()) {
            return ApiResult.error("号源已满");
        }
        // 生成序号
        Integer currentCount = appointmentMapper.countByScheduleId(schedule.getId());
        appointment.setSerialNumber(currentCount + 1);
        appointment.setPatientId(appointment.getPatientId());
        appointment.setDoctorId(schedule.getDoctorId());
        appointment.setAppointmentDate(schedule.getScheduleDate());
        appointment.setTimeSlot(schedule.getTimeSlot());
        appointment.setStatus(0);
        appointment.setCreateTime(LocalDateTime.now());
        appointmentMapper.save(appointment);
        scheduleMapper.incrementBookedCount(schedule.getId());
        return ApiResult.success();
    }

    @Override
    @Transactional
    public Result<Void> cancelAppointment(Integer id, String reason) {
        Appointment appointment = appointmentMapper.getById(id);
        if (appointment == null) {
            return ApiResult.error("预约不存?");
        }
        if (appointment.getStatus() == 3) {
            return ApiResult.error("预约已取?");
        }
        Appointment update = new Appointment();
        update.setId(id);
        update.setStatus(3);
        update.setCancelReason(reason);
        appointmentMapper.update(update);
        scheduleMapper.decrementBookedCount(appointment.getScheduleId());
        return ApiResult.success();
    }

    @Override
    public Result<List<AppointmentVO>> queryAppointments(AppointmentQueryDto queryDto) {
        List<AppointmentVO> list = appointmentMapper.query(queryDto);
        Integer count = appointmentMapper.queryCount(queryDto);
        return PageResult.success(list, count);
    }

    @Override
    public Result<AppointmentVO> getAppointmentById(Integer id) {
        return ApiResult.success(appointmentMapper.getById(id));
    }

    @Override
    public Result<Void> saveVisitRecord(VisitRecord record) {
        record.setCreateTime(LocalDateTime.now());
        visitRecordMapper.save(record);
        return ApiResult.success();
    }

    @Override
    public Result<Void> updateVisitRecord(VisitRecord record) {
        visitRecordMapper.update(record);
        return ApiResult.success();
    }

    @Override
    public Result<VisitRecord> getVisitRecord(Integer appointmentId) {
        return ApiResult.success(visitRecordMapper.getByAppointmentId(appointmentId));
    }
}
