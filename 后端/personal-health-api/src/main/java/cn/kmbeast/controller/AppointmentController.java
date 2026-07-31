package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.AppointmentQueryDto;
import cn.kmbeast.pojo.entity.*;
import cn.kmbeast.pojo.vo.AppointmentVO;
import cn.kmbeast.pojo.vo.DoctorVO;
import cn.kmbeast.service.AppointmentService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/appointment")
public class AppointmentController {

    @Resource
    private AppointmentService appointmentService;

    // ========== 科室 ==========

    @Protector
    @GetMapping("/departments")
    public Result<List<Department>> getDepartments() {
        return appointmentService.getDepartments();
    }

    @Protector(role = "admin")
    @PostMapping("/department/save")
    public Result<Void> saveDepartment(@RequestBody Department department) {
        return appointmentService.saveDepartment(department);
    }

    @Protector(role = "admin")
    @PutMapping("/department/update")
    public Result<Void> updateDepartment(@RequestBody Department department) {
        return appointmentService.updateDepartment(department);
    }

    @Protector(role = "admin")
    @PostMapping("/department/batchDelete")
    public Result<Void> deleteDepartments(@RequestBody List<Long> ids) {
        return appointmentService.deleteDepartments(ids);
    }

    // ========== 医生 ==========

    @Protector
    @GetMapping("/doctors")
    public Result<List<DoctorVO>> getDoctors(@RequestParam(required = false) Integer departmentId) {
        return appointmentService.getDoctors(departmentId);
    }

    @Protector
    @GetMapping("/doctor/{id}")
    public Result<DoctorVO> getDoctorById(@PathVariable Integer id) {
        return appointmentService.getDoctorById(id);
    }

    @Protector(role = "admin")
    @PostMapping("/doctor/save")
    public Result<Void> saveDoctor(@RequestBody HospitalDoctor doctor) {
        return appointmentService.saveDoctor(doctor);
    }

    @Protector(role = "admin")
    @PutMapping("/doctor/update")
    public Result<Void> updateDoctor(@RequestBody HospitalDoctor doctor) {
        return appointmentService.updateDoctor(doctor);
    }

    @Protector(role = "admin")
    @PostMapping("/doctor/batchDelete")
    public Result<Void> deleteDoctors(@RequestBody List<Long> ids) {
        return appointmentService.deleteDoctors(ids);
    }

    // ========== 排班 ==========

    @Protector
    @GetMapping("/schedules")
    public Result<List<DoctorSchedule>> getSchedules(
            @RequestParam Integer doctorId,
            @RequestParam LocalDate date) {
        return appointmentService.getSchedules(doctorId, date);
    }

    @Protector
    @GetMapping("/schedules/available")
    public Result<List<DoctorSchedule>> getAvailableSchedules(
            @RequestParam Integer departmentId,
            @RequestParam LocalDate date) {
        return appointmentService.getAvailableSchedules(departmentId, date);
    }

    @Protector(role = "admin")
    @PostMapping("/schedule/save")
    public Result<Void> saveSchedule(@RequestBody DoctorSchedule schedule) {
        return appointmentService.saveSchedule(schedule);
    }

    @Protector(role = "admin")
    @PutMapping("/schedule/update")
    public Result<Void> updateSchedule(@RequestBody DoctorSchedule schedule) {
        return appointmentService.updateSchedule(schedule);
    }

    @Protector(role = "admin")
    @PostMapping("/schedule/batchDelete")
    public Result<Void> deleteSchedules(@RequestBody List<Long> ids) {
        return appointmentService.deleteSchedules(ids);
    }

    // ========== 预约 ==========

    @Protector
    @PostMapping("/book")
    public Result<Void> bookAppointment(@RequestBody Appointment appointment) {
        return appointmentService.bookAppointment(appointment);
    }

    @Protector
    @PostMapping("/cancel/{id}")
    public Result<Void> cancelAppointment(@PathVariable Integer id, @RequestParam(required = false) String reason) {
        return appointmentService.cancelAppointment(id, reason);
    }

    @Pager
    @Protector
    @PostMapping("/query")
    public Result<List<AppointmentVO>> queryAppointments(@RequestBody AppointmentQueryDto queryDto) {
        return appointmentService.queryAppointments(queryDto);
    }

    @Protector
    @GetMapping("/getById/{id}")
    public Result<AppointmentVO> getAppointmentById(@PathVariable Integer id) {
        return appointmentService.getAppointmentById(id);
    }

    // ========== 就诊记录 ==========

    @Protector
    @PostMapping("/visitRecord/save")
    public Result<Void> saveVisitRecord(@RequestBody VisitRecord record) {
        return appointmentService.saveVisitRecord(record);
    }

    @Protector
    @PutMapping("/visitRecord/update")
    public Result<Void> updateVisitRecord(@RequestBody VisitRecord record) {
        return appointmentService.updateVisitRecord(record);
    }

    @Protector
    @GetMapping("/visitRecord/{appointmentId}")
    public Result<VisitRecord> getVisitRecord(@PathVariable Integer appointmentId) {
        return appointmentService.getVisitRecord(appointmentId);
    }
}
