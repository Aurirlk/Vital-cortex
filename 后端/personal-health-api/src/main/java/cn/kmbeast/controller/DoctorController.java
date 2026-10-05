package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.request.DoctorLoginDTO;
import cn.kmbeast.pojo.entity.DoctorSchedule;
import cn.kmbeast.pojo.vo.AppointmentVO;
import cn.kmbeast.service.DoctorAuthService;
import cn.kmbeast.service.DoctorWorkService;
import cn.kmbeast.service.PatientDetailService;
import cn.kmbeast.service.impl.DoctorAuthServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 医生端控制器（2026-10-03 医生与 user 解耦）
 *
 * <p><b>设计要点</b>：
 * <ul>
 *   <li>医生有<b>独立登录入口</b> {@code /doctor/login}，不复用 {@code /user/login}，
 *       避免与普通用户账号体系混淆</li>
 *   <li>医生端接口统一用 {@code @Protector(role = "医生")} 保护；
 *       由于医生在 JWT 中 role=3，而 user 表只有 1/2，
 *       医生<b>无法</b>通过 {@code @Protector(role = "管理员")} 或普通用户端点</li>
 *   <li>普通状态（普通用户 / 管理员）只能进用户端与管理端，<b>不能</b>访问医生端</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/doctor")
public class DoctorController {

    @Resource
    private DoctorAuthService doctorAuthService;

    @Resource
    private DoctorWorkService doctorWorkService;

    @Resource
    private PatientDetailService patientDetailService;

    /**
     * 医生登录
     *
     * <p>首次登录（账号由管理员创建后未设密）时：先不传 password 调用，返回
     * {@code needInitPassword=true}；前端引导输入新密码后，携带 {@code newPassword} 再次调用即可完成登录。
     *
     * @param dto 登录请求
     */
    @PostMapping("/login")
    public Result<Object> login(@RequestBody DoctorLoginDTO dto) {
        return doctorAuthService.login(dto);
    }

    /**
     * 管理员重置医生密码
     *
     * <p>医生忘记密码、或批量初始化账号时由管理员调用。重置后该医生已签发的 token 全部失效。
     *
     * @param request {doctorId, password}
     */
    @Protector(role = "管理员")
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@RequestBody Map<String, Object> request) {
        Object id = request.get("doctorId");
        if (id == null) {
            return ApiResult.error("缺少 doctorId");
        }
        Integer doctorId;
        try {
            doctorId = Integer.valueOf(String.valueOf(id));
        } catch (NumberFormatException e) {
            return ApiResult.error("doctorId 格式不正确");
        }
        return doctorAuthService.resetPassword(doctorId, (String) request.get("password"));
    }

    /**
     * 医生端首页问候信息
     *
     * <p>仅医生可访问（{@code @Protector(role = "医生")}），用于验证医生身份与前端路由守卫是否生效。
     */
    @Protector(role = "医生")
    @GetMapping("/home")
    public Result<Map<String, Object>> home() {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "医生端已就绪");
        data.put("role", DoctorAuthServiceImpl.ROLE_DOCTOR);
        data.put("doctorId", LocalThreadHolder.getUserId());
        return ApiResult.success(data);
    }

    // ==================== 医生端工作台（2026-10-04） ====================
    // 全部 @Protector(role = "医生")，配合 JwtInterceptor 的 DoctorIsolation，
    // 医生端与用户端形成双向隔离：医生进不了 /user/**，用户也进不了 /doctor/**。
    //
    // ⚠️ 下方接口<b>一律不接受 doctorId 参数</b> —— 医生身份由服务端从 token 取。

    /** 医生端首页概览：身份 + 今日/本周/累计统计 */
    @Protector(role = "医生")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return doctorWorkService.overview();
    }

    /**
     * 我的接诊列表
     *
     * @param fromDate 起始日期 yyyy-MM-dd，可空（默认最近 30 天）
     * @param toDate   结束日期 yyyy-MM-dd，可空
     * @param status   状态：0待接诊 1已预约 2已完成 3已取消，可空表示全部
     */
    @Protector(role = "医生")
    @GetMapping("/appointments")
    public Result<List<AppointmentVO>> myAppointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Integer status) {
        return doctorWorkService.myAppointments(fromDate, toDate, status);
    }

    /** 我的排班 */
    @Protector(role = "医生")
    @GetMapping("/schedules")
    public Result<List<DoctorSchedule>> mySchedules(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return doctorWorkService.mySchedules(fromDate, toDate);
    }

    /** 就诊确认：把预约置为「已完成」 */
    @Protector(role = "医生")
    @PostMapping("/appointment/{id}/complete")
    public Result<Void> completeAppointment(@PathVariable Integer id) {
        return doctorWorkService.completeAppointment(id);
    }

    /** 停用我的排班 */
    @Protector(role = "医生")
    @PostMapping("/schedule/{id}/disable")
    public Result<Void> disableSchedule(@PathVariable Integer id) {
        return doctorWorkService.disableSchedule(id);
    }

    // ==================== 患者档案（医生接诊一屏视图） ====================

    /**
     * 患者档案详情（医生视角聚合）
     *
     * <p>聚合：基本信息 / 体征生化 / 慢病·过敏·用药 / 生活方式 /
     * 近 N 天健康趋势 / 历史就诊 / 随访任务 / 与我的预约关系。
     *
     * <p>⚠️ 内部会校验<b>医患关系</b>：无预约关系的患者一律拒绝，
     * 防止医生构造 patientId 遍历全库患者档案。
     *
     * @param patientId 患者 ID
     * @param trendDays 趋势天数，可空（默认 90，上限 365）
     */
    @Protector(role = "医生")
    @GetMapping("/patient/{patientId}")
    public Result<Map<String, Object>> patientDetail(
            @PathVariable Integer patientId,
            @RequestParam(required = false) Integer trendDays) {
        return patientDetailService.detail(patientId, trendDays);
    }

    /**
     * 开始接诊：保存就诊记录并把预约置为已完成
     *
     * <p>同样带归属与重复校验：预约必须属于当前医生，且该预约未建过就诊记录。
     *
     * @param appointmentId 预约 ID
     * @param record  {chiefComplaint, presentIllness, diagnosis,
     *                prescription, examinationResults, followUpPlan}
     */
    @Protector(role = "医生")
    @PostMapping("/appointment/{appointmentId}/consult")
    public Result<Void> startConsultation(@PathVariable Integer appointmentId,
                                           @RequestBody Map<String, Object> record) {
        return patientDetailService.startConsultation(appointmentId, record);
    }
}
