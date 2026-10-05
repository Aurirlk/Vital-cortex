package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.*;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.AppointmentQueryDto;
import cn.kmbeast.pojo.entity.*;
import cn.kmbeast.pojo.vo.AppointmentVO;
import cn.kmbeast.pojo.vo.DepartmentVO;
import cn.kmbeast.pojo.vo.DoctorVO;
import cn.kmbeast.service.AppointmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public Result<List<DepartmentVO>> getDepartmentTree() {
        List<Department> all = departmentMapper.queryAll();
        Map<Integer, DepartmentVO> nodeMap = new LinkedHashMap<>();
        Map<Integer, Integer> doctorCount = new HashMap<>();

        // Mapper 返回的是「每行一个科室」的聚合结果，这里转成 科室ID → 医生数
        for (Map<String, Object> row : departmentMapper.countDoctorByDepartment()) {
            Object deptId = row.get("deptId");
            Object cnt = row.get("cnt");
            if (deptId == null) {
                continue;
            }
            int id = ((Number) deptId).intValue();
            int c = cnt == null ? 0 : ((Number) cnt).intValue();
            // 同一科室可能在 UNION 两路都命中，取较大值避免重复计数
            doctorCount.merge(id, c, Math::max);
        }

        // 先建节点（继承 Department 全部字段，含 parentId / level / code）
        for (Department d : all) {
            DepartmentVO vo = new DepartmentVO();
            BeanUtils.copyProperties(d, vo);
            vo.setDoctorCount(doctorCount.getOrDefault(d.getId(), 0));
            vo.setChildren(new ArrayList<>());
            nodeMap.put(d.getId(), vo);
        }

        // 再挂树；parentId 为 0/空或找不到父节点时视为顶级
        List<DepartmentVO> roots = new ArrayList<>();
        for (DepartmentVO vo : nodeMap.values()) {
            Integer parentId = vo.getParentId();
            DepartmentVO parent = (parentId == null || parentId == 0) ? null : nodeMap.get(parentId);
            if (parent == null || parent == vo) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }
        // 父节点医生数累加子科室，便于树节点显示「内科 (12)」
        accumulateDoctorCount(roots);
        return ApiResult.success(roots);
    }

    /** 递归累加：父节点 doctorCount = 自身 + 所有子节点 */
    private void accumulateDoctorCount(List<DepartmentVO> nodes) {
        for (DepartmentVO node : nodes) {
            if (node.getChildren() == null || node.getChildren().isEmpty()) {
                continue;
            }
            accumulateDoctorCount(node.getChildren());
            int sum = node.getDoctorCount() == null ? 0 : node.getDoctorCount();
            for (DepartmentVO child : node.getChildren()) {
                sum += child.getDoctorCount() == null ? 0 : child.getDoctorCount();
            }
            node.setDoctorCount(sum);
        }
    }

    @Override
    public Result<List<DoctorVO>> getDoctorsPage(String name, Integer departmentId,
                                                 String titleLevel, Integer status,
                                                 Integer current, Integer size) {
        int pageSize = (size == null || size <= 0) ? 20 : Math.min(size, 100);
        int page = (current == null || current <= 0) ? 1 : current;
        int offset = (page - 1) * pageSize;
        List<DoctorVO> list = doctorMapper.queryDoctorPage(
                name, departmentId, titleLevel, status, offset, pageSize);
        int total = doctorMapper.countDoctorPage(name, departmentId, titleLevel, status);
        return ApiResult.success(list, total);
    }

    @Override
    public Result<List<DoctorVO>> searchDoctors(String keyword, Integer departmentId, Integer limit) {
        int max = (limit == null || limit <= 0) ? 20 : Math.min(limit, 50);
        return ApiResult.success(doctorMapper.searchForSelect(keyword, departmentId, max));
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
