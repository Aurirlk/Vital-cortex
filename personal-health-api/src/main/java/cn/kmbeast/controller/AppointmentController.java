package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.AppointmentQueryDto;
import cn.kmbeast.pojo.entity.*;
import cn.kmbeast.pojo.vo.AppointmentVO;
import cn.kmbeast.pojo.vo.DepartmentVO;
import cn.kmbeast.pojo.vo.DoctorVO;
import cn.kmbeast.service.AppointmentService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
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

    @Protector(role = "管理员")
    @PostMapping("/department/save")
    public Result<Void> saveDepartment(@RequestBody Department department) {
        return appointmentService.saveDepartment(department);
    }

    @Protector(role = "管理员")
    @PutMapping("/department/update")
    public Result<Void> updateDepartment(@RequestBody Department department) {
        return appointmentService.updateDepartment(department);
    }

    @Protector(role = "管理员")
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

    /**
     * 医生分页列表（可扩展性改造）
     *
     * <p>原 {@code /doctors} 一次性返回全部医生，医生数量增长后前端下拉会卡死。
     * 本接口支持「姓名模糊 + 科室 + 职称 + 状态」筛选并后端分页，
     * 供管理端「科室树 + 医生列表」使用。
     */
    @Protector
    @GetMapping("/doctors/page")
    public Result<List<DoctorVO>> getDoctorsPage(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) String titleLevel,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return appointmentService.getDoctorsPage(
                name, departmentId, titleLevel, status, current, size);
    }

    /**
     * 医生远程搜索（供 {@code <DoctorSelect>} 组件的 remote-method 调用）
     *
     * <p>输入关键字即时检索，只返回启用中的医生，最多 limit 条。
     * 支持多科室医生（走 {@code dept_ids} 的 JSON_CONTAINS 匹配）。
     */
    @Protector
    @GetMapping("/doctors/search")
    public Result<List<DoctorVO>> searchDoctors(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(defaultValue = "20") Integer limit) {
        return appointmentService.searchDoctors(keyword, departmentId, limit);
    }

    /**
     * 科室树（科室支持多级层级后的导航结构）
     *
     * <p>返回带 {@code children} 与 {@code doctorCount} 的树形结构，
     * 供管理端左侧科室树与医生端导览使用。
     */
    @Protector
    @GetMapping("/departments/tree")
    public Result<List<DepartmentVO>> getDepartmentTree() {
        return appointmentService.getDepartmentTree();
    }

    @Protector
    @GetMapping("/doctor/{id}")
    public Result<DoctorVO> getDoctorById(@PathVariable Integer id) {
        return appointmentService.getDoctorById(id);
    }

    @Protector(role = "管理员")
    @PostMapping("/doctor/save")
    public Result<Void> saveDoctor(@RequestBody HospitalDoctor doctor) {
        return appointmentService.saveDoctor(doctor);
    }

    @Protector(role = "管理员")
    @PutMapping("/doctor/update")
    public Result<Void> updateDoctor(@RequestBody HospitalDoctor doctor) {
        return appointmentService.updateDoctor(doctor);
    }

    @Protector(role = "管理员")
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

    @Protector(role = "管理员")
    @PostMapping("/schedule/save")
    public Result<Void> saveSchedule(@RequestBody DoctorSchedule schedule) {
        return appointmentService.saveSchedule(schedule);
    }

    @Protector(role = "管理员")
    @PutMapping("/schedule/update")
    public Result<Void> updateSchedule(@RequestBody DoctorSchedule schedule) {
        return appointmentService.updateSchedule(schedule);
    }

    @Protector(role = "管理员")
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
