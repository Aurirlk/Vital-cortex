package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.AppointmentMapper;
import cn.kmbeast.mapper.DoctorScheduleMapper;
import cn.kmbeast.mapper.HospitalDoctorMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.DoctorSchedule;
import cn.kmbeast.pojo.vo.AppointmentVO;
import cn.kmbeast.pojo.vo.DoctorVO;
import cn.kmbeast.service.DoctorWorkService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 医生端工作台实现（2026-10-04）
 *
 * <p>全部方法的 doctorId 都取自 {@link LocalThreadHolder}，
 * 该值由 {@code JwtInterceptor} 从已验签的 JWT 中解析并写入 ThreadLocal，
 * 前端无法干预 —— 这是本服务全部防越权能力的基石。
 */
@Slf4j
@Service
public class DoctorWorkServiceImpl implements DoctorWorkService {

    /** 单次查询允许的最大跨度（天），防止前端传 fromDate=1900-01-01 拖库 */
    private static final int MAX_RANGE_DAYS = 366;

    @Resource
    private AppointmentMapper appointmentMapper;

    @Resource
    private DoctorScheduleMapper scheduleMapper;

    @Resource
    private HospitalDoctorMapper doctorMapper;

    /**
     * 取当前登录医生 ID。
     *
     * @return 医生 ID
     * @throws IllegalStateException 未登录（理论上被拦截器挡住，这里是兜底）
     */
    private Integer currentDoctorId() {
        Integer id = LocalThreadHolder.getUserId();
        if (id == null) {
            throw new IllegalStateException("未获取到当前医生身份");
        }
        return id;
    }

    /**
     * 归一化日期区间：null 视为不限，但跨度不得超过 {@link #MAX_RANGE_DAYS}。
     *
     * @return {fromDate, toDate}
     */
    private LocalDate[] normalizeRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate from = fromDate;
        LocalDate to = toDate;
        if (from == null && to == null) {
            // 默认给最近 30 天，医生端最常用视图
            to = LocalDate.now();
            from = to.minusDays(29);
        } else if (from == null) {
            from = to.minusDays(MAX_RANGE_DAYS - 1L);
        } else if (to == null) {
            to = from.plusDays(MAX_RANGE_DAYS - 1L);
        }
        // 区间倒置时交换，避免返回空列表让人以为没数据
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        // 硬上限保护
        if (from.plusDays(MAX_RANGE_DAYS - 1L).isBefore(to)) {
            to = from.plusDays(MAX_RANGE_DAYS - 1L);
            log.warn("[DoctorWork] 请求区间超过 {} 天，已截断: doctorId={}", MAX_RANGE_DAYS, currentDoctorId());
        }
        return new LocalDate[]{from, to};
    }

    @Override
    public Result<Map<String, Object>> overview() {
        Integer doctorId = currentDoctorId();

        Map<String, Object> data = new HashMap<>();
        data.put("doctorId", doctorId);

        // 身份信息：查不到不阻断概览，只是名字显示为「未知」
        try {
            DoctorVO vo = doctorMapper.getById(doctorId);
            if (vo != null) {
                data.put("name", vo.getName());
                data.put("title", vo.getTitle());
                data.put("titleLevel", vo.getTitleLevel());
                data.put("departmentId", vo.getDepartmentId());
                data.put("departmentName", vo.getDepartmentName());
            } else {
                data.put("name", "未知医生");
            }
        } catch (Exception e) {
            // 概览页不能因为附属信息查不到就整个失败
            log.warn("[DoctorWork] 读取医生档案失败，仅返回统计: doctorId={}, err={}", doctorId, e.getMessage());
            data.put("name", "未知医生");
        }

        // 统计：无预约时 SUM 返回 null，必须按 0 处理
        Map<String, Object> sum;
        try {
            sum = appointmentMapper.summaryByDoctor(doctorId);
        } catch (Exception e) {
            log.error("[DoctorWork] 统计查询失败: doctorId={}", doctorId, e);
            sum = new HashMap<>();
        }
        data.put("todayCount", toInt(sum.get("todayCount")));
        data.put("weekCount", toInt(sum.get("weekCount")));
        data.put("totalCount", toInt(sum.get("totalCount")));
        data.put("pendingCount", toInt(sum.get("pendingCount")));
        data.put("completedCount", toInt(sum.get("completedCount")));
        data.put("today", LocalDate.now().toString());

        return ApiResult.success(data);
    }

    @Override
    public Result<List<AppointmentVO>> myAppointments(LocalDate fromDate,
                                                     LocalDate toDate,
                                                     Integer status) {
        Integer doctorId = currentDoctorId();
        LocalDate[] range = normalizeRange(fromDate, toDate);

        List<AppointmentVO> list = appointmentMapper.queryByDoctorAndDateRange(
                doctorId, range[0], range[1], status);
        return ApiResult.success(list == null ? List.of() : list);
    }

    @Override
    public Result<List<DoctorSchedule>> mySchedules(LocalDate fromDate, LocalDate toDate) {
        Integer doctorId = currentDoctorId();
        LocalDate[] range = normalizeRange(fromDate, toDate);

        List<DoctorSchedule> list = scheduleMapper.queryByDoctorAndRange(
                doctorId, range[0], range[1]);
        return ApiResult.success(list == null ? List.of() : list);
    }

    @Override
    public Result<Void> completeAppointment(Integer appointmentId) {
        Integer doctorId = currentDoctorId();
        if (appointmentId == null) {
            return ApiResult.error("缺少预约ID");
        }

        // SQL 内已带 doctor_id 与 status 双重条件：
        // 影响 0 行 = 「不是我的预约」或「状态已流转过」，二者都无法据此区分，
        // 因此对外统一提示为「预约不存在或状态已变更」，不泄露他人预约是否存在。
        int affected = appointmentMapper.completeByDoctor(appointmentId, doctorId);
        if (affected == 0) {
            return ApiResult.error("预约不存在或状态已变更");
        }
        log.info("[DoctorWork] 医生 {} 完成接诊: appointmentId={}", doctorId, appointmentId);
        return ApiResult.success();
    }

    @Override
    public Result<Void> disableSchedule(Integer scheduleId) {
        Integer doctorId = currentDoctorId();
        if (scheduleId == null) {
            return ApiResult.error("缺少排班ID");
        }

        // 归属校验：不在自己名下就拒绝，这是防「改 id 停他人排班」的关键
        Integer owned = scheduleMapper.countByIdAndDoctor(scheduleId, doctorId);
        if (owned == null || owned == 0) {
            log.warn("[DoctorWork] 拒绝停用非本人排班: doctorId={}, scheduleId={}", doctorId, scheduleId);
            return ApiResult.error("排班不存在");
        }

        DoctorSchedule patch = new DoctorSchedule();
        patch.setId(scheduleId);
        patch.setStatus(0);          // 0 = 停诊
        scheduleMapper.update(patch);
        log.info("[DoctorWork] 医生 {} 停用排班: scheduleId={}", doctorId, scheduleId);
        return ApiResult.success("排班已停用");
    }

    /** Map 里的值可能是 null / BigDecimal / Long，统一安全转 int */
    private int toInt(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
