package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.DoctorSchedule;
import cn.kmbeast.pojo.vo.AppointmentVO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 医生端工作台服务（2026-10-04）
 *
 * <p><b>安全约定（重要）</b>：本接口族的所有方法<b>都不接受 doctorId 入参</b>。
 * 医生身份一律由实现类从 {@code LocalThreadHolder} 读取 —— 该值由
 * {@code JwtInterceptor} 从 JWT 解析并写入，无法被前端伪造。
 *
 * <p>若未来有人在此接口加上 {@code Long doctorId} 参数，
 * 医生即可篡改请求体查看其他医生的接诊记录，属于越权。
 * 已有单测 {@code DoctorWorkServiceIsolationTest} 锁定这一行为。
 */
public interface DoctorWorkService {

    /**
     * 医生端首页概览：身份信息 + 今日/本周/累计统计
     *
     * @return 医生姓名、职称、科室，以及 todayCount/weekCount/totalCount/
     *         pendingCount/completedCount
     */
    Result<Map<String, Object>> overview();

    /**
     * 我的接诊列表（按日期区间）
     *
     * @param fromDate 起始日期（含），null 表示不限
     * @param toDate   结束日期（含），null 表示不限
     * @param status   预约状态，null 表示全部
     */
    Result<List<AppointmentVO>> myAppointments(LocalDate fromDate,
                                              LocalDate toDate,
                                              Integer status);

    /**
     * 我的排班（按日期区间）
     */
    Result<List<DoctorSchedule>> mySchedules(LocalDate fromDate, LocalDate toDate);

    /**
     * 就诊确认：把某条预约置为「已完成」
     *
     * @param appointmentId 预约 ID
     * @return 成功 / 越权或已流转过
     */
    Result<Void> completeAppointment(Integer appointmentId);

    /**
     * 停用我的某个排班
     *
     * <p>仅允许停用自己的排班 —— 实现内需先做归属校验。
     */
    Result<Void> disableSchedule(Integer scheduleId);
}
