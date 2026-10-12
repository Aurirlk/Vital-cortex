package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.core.notify.NotifyTemplates;
import cn.kmbeast.core.notify.UserNotifier;
import cn.kmbeast.mapper.*;
import cn.kmbeast.pojo.em.NotificationSource;
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
    @Resource private UserNotifier userNotifier;

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
        if (appointment == null || appointment.getScheduleId() == null) {
            return ApiResult.error("请选择就诊号源");
        }
        DoctorSchedule schedule = scheduleMapper.getById(appointment.getScheduleId());
        if (schedule == null) {
            return ApiResult.error("排班不存在");
        }

        // 2026-10-10 修复（越权 + 挂号功能整体不可用）：
        // 原代码是 appointment.setPatientId(appointment.getPatientId()) —— 自赋值，
        // 等于原样采信前端传来的 patientId。而前端（user/Appointment.vue）
        // 只提交 {scheduleId, symptomDescription}，patientId 恒为 null；
        // appointment.patient_id 在 DDL 里是 NOT NULL，于是 INSERT 直接失败——
        // 也就是说「预约挂号」这条主流程在生产库上根本走不通。
        // 同时，自赋值等于允许攻击者塞任意 patientId 冒充他人挂号。
        // 现在改为服务端从 token 取身份；管理员保留代客下单能力。
        if (LocalThreadHolder.isAdmin()) {
            if (appointment.getPatientId() == null) {
                return ApiResult.error("请指定就诊人");
            }
        } else {
            appointment.setPatientId(LocalThreadHolder.getUserId());
        }

        // doctor_id / department_id / 日期 / 时段一律以排班的真实数据为准，
        // 不采信前端传值——否则可以挂 A 医生的号、写 B 医生的 appointment_date，
        // 绕开号源与接诊安排。
        HospitalDoctor doctor = doctorMapper.getById(schedule.getDoctorId());
        if (doctor == null) {
            return ApiResult.error("医生不存在或已停诊");
        }
        // department_id 同样是 NOT NULL，而排班表没有冗余它，只能由医生反查。
        appointment.setDoctorId(schedule.getDoctorId());
        appointment.setDepartmentId(doctor.getDepartmentId());
        appointment.setAppointmentDate(schedule.getScheduleDate());
        appointment.setTimeSlot(schedule.getTimeSlot());
        appointment.setStatus(0);
        appointment.setCreateTime(LocalDateTime.now());

        // 原子占号必须排在构造实体之前：它既校验号源，又锁住该排班行，
        // 使下面的号序计算在并发下不会重号（见 DoctorScheduleMapper.reserveSlot 注释）。
        Integer reserved = scheduleMapper.reserveSlot(schedule.getId());
        if (reserved == null || reserved == 0) {
            return ApiResult.error("号源已满或该排班已停诊");
        }

        // 生成序号（此时持有排班行锁，count 结果稳定）
        Integer currentCount = appointmentMapper.countByScheduleId(schedule.getId());
        appointment.setSerialNumber((currentCount == null ? 0 : currentCount) + 1);

        appointmentMapper.save(appointment);

        // 2026-10-10 新增：挂号成功通知，受设置页「预约提醒」开关控制。
        // 放在 save 之后：此时 appointment.getId() 已由 useGeneratedKeys 回填，
        // 通知的 biz_id 才拿得到，链接也能落到具体那一条。
        Department department = departmentMapper.getById(appointment.getDepartmentId());
        userNotifier.send(appointment.getPatientId(), NotificationSource.APPOINTMENT,
                NotifyTemplates.T_APPOINTMENT_BOOKED,
                NotifyTemplates.appointmentBooked(doctor.getName(),
                        department == null ? null : department.getName(),
                        appointment.getAppointmentDate(),
                        NotifyTemplates.timeSlotName(appointment.getTimeSlot()),
                        appointment.getSerialNumber()),
                appointment.getId() == null ? null : appointment.getId().longValue(),
                NotifyTemplates.LINK_APPOINTMENT);
        return ApiResult.success();
    }

    @Override
    @Transactional
    public Result<Void> cancelAppointment(Integer id, String reason) {
        // 这里用 AppointmentVO 接：Mapper 的 getById 返回的就是 VO（带 doctor_name），
        // 下面「被别人取消要通知患者」要用到医生名。
        AppointmentVO appointment = appointmentMapper.getById(id);
        if (appointment == null) {
            return ApiResult.error("预约不存在");
        }
        // 2026-10-10 修复：原先只判「不存在」，任何登录用户传一个 id 就能取消
        // 他人的预约（IDOR）。改为必须本人或管理员。
        if (!LocalThreadHolder.isSelfOrAdmin(appointment.getPatientId())) {
            log.warn("越权取消预约被拒绝: appointmentId={}, ownerId={}, operatorId={}, role={}",
                    id, appointment.getPatientId(), LocalThreadHolder.getUserId(),
                    LocalThreadHolder.getRoleId());
            return ApiResult.error("无权操作该预约");
        }
        if (appointment.getStatus() != null && appointment.getStatus() == 3) {
            return ApiResult.error("该预约已取消");
        }
        Appointment update = new Appointment();
        update.setId(id);
        update.setStatus(3);
        update.setCancelReason(reason);
        appointmentMapper.update(update);
        // 只有「已占用号源」的状态才归还号源：2=已完成时座位早已消耗过，
        // 再减会凭空放出号源。
        Integer status = appointment.getStatus();
        if (status != null && (status == 0 || status == 1)) {
            scheduleMapper.decrementBookedCount(appointment.getScheduleId());
        }

        // 2026-10-10 新增：预约取消通知，受「预约提醒」开关控制。
        // 只在「不是本人操作」时发 —— 患者自己点的取消，再给他推一条「你的预约已取消」
        // 是纯噪音（他刚做完这个动作）；真正需要告知的是「医生/管理员替他取消了」。
        if (!LocalThreadHolder.isSelf(appointment.getPatientId())) {
            userNotifier.send(appointment.getPatientId(), NotificationSource.APPOINTMENT,
                    NotifyTemplates.T_APPOINTMENT_CANCELLED,
                    NotifyTemplates.appointmentCancelled(appointment.getAppointmentDate(),
                            NotifyTemplates.timeSlotName(appointment.getTimeSlot()),
                            appointment.getDoctorName()),
                    id == null ? null : id.longValue(),
                    NotifyTemplates.LINK_APPOINTMENT);
        }
        return ApiResult.success();
    }

    @Override
    public Result<List<AppointmentVO>> queryAppointments(AppointmentQueryDto queryDto) {
        if (queryDto == null) {
            queryDto = new AppointmentQueryDto();
        }
        // 2026-10-10 修复（最严重的一处 IDOR）：本接口挂在 /appointment 下，
        // 属于「公共端点」，医生与普通用户都能调用，而 @Protector 未声明角色。
        // 前端 user/Appointment.vue 的「我的预约」恰恰是 POST appointment/query {}
        // ——查询条件为空，Mapper 的 <where> 全部不生效，于是返回全库预约：
        // 每条都带 patient_name / patient_phone / symptom_description，
        // 即任意注册用户可翻页拉走全部患者的姓名、手机号与病情自述。
        // 现在非管理员一律强制按自己过滤，忽略前端传来的 patientId。
        if (!LocalThreadHolder.isAdmin()) {
            queryDto.setPatientId(LocalThreadHolder.getUserId());
        }
        List<AppointmentVO> list = appointmentMapper.query(queryDto);
        Integer count = appointmentMapper.queryCount(queryDto);
        return PageResult.success(list, count);
    }

    @Override
    public Result<AppointmentVO> getAppointmentById(Integer id) {
        AppointmentVO vo = appointmentMapper.getById(id);
        // 2026-10-10 修复：原先不看归属直接返回，传 id 即可遍历他人预约详情
        // （含患者姓名、手机号、病情自述）。医生经 /doctor/** 有自己的入口，
        // 这里只放行「本人或管理员」。
        if (vo != null && !LocalThreadHolder.isSelfOrAdmin(vo.getPatientId())) {
            log.warn("越权查看预约被拒绝: appointmentId={}, ownerId={}, operatorId={}",
                    id, vo.getPatientId(), LocalThreadHolder.getUserId());
            return ApiResult.error("无权查看该预约");
        }
        return ApiResult.success(vo);
    }

    @Override
    public Result<Void> saveVisitRecord(VisitRecord record) {
        // 2026-10-10 修复：就诊记录是医疗文书，只有医生（写自己的接诊）和管理员可写。
        // 原先只要求登录，任何普通用户都能给任意 appointmentId 伪造一份就诊记录。
        if (!isDoctorOrAdmin()) {
            return ApiResult.error("无权录入就诊记录");
        }
        record.setCreateTime(LocalDateTime.now());
        visitRecordMapper.save(record);
        return ApiResult.success();
    }

    @Override
    public Result<Void> updateVisitRecord(VisitRecord record) {
        if (!isDoctorOrAdmin()) {
            return ApiResult.error("无权修改就诊记录");
        }
        visitRecordMapper.update(record);
        return ApiResult.success();
    }

    @Override
    public Result<VisitRecord> getVisitRecord(Integer appointmentId) {
        Appointment appointment = appointmentMapper.getById(appointmentId);
        if (appointment == null) {
            return ApiResult.error("预约不存在");
        }
        // 患者本人可查看自己的就诊记录；医生/管理员照常。
        if (!LocalThreadHolder.isSelfOrAdmin(appointment.getPatientId()) && !isDoctorOrAdmin()) {
            return ApiResult.error("无权查看该就诊记录");
        }
        return ApiResult.success(visitRecordMapper.getByAppointmentId(appointmentId));
    }

    /** 当前请求者是否为医生或管理员 */
    private boolean isDoctorOrAdmin() {
        if (LocalThreadHolder.isAdmin()) {
            return true;
        }
        return Integer.valueOf(cn.kmbeast.pojo.em.RoleEnum.DOCTOR.getRole())
                .equals(LocalThreadHolder.getRoleId());
    }
}
