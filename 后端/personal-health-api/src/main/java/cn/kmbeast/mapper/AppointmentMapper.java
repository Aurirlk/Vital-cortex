package cn.kmbeast.mapper;

import cn.kmbeast.pojo.dto.query.extend.AppointmentQueryDto;
import cn.kmbeast.pojo.entity.Appointment;
import cn.kmbeast.pojo.vo.AppointmentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface AppointmentMapper {
    void save(Appointment appointment);
    void update(Appointment appointment);
    void batchDelete(@Param("ids") List<Long> ids);
    List<AppointmentVO> query(AppointmentQueryDto queryDto);
    Integer queryCount(AppointmentQueryDto queryDto);
    AppointmentVO getById(@Param("id") Integer id);
    Integer countByScheduleId(@Param("scheduleId") Integer scheduleId);
    List<Map<String, Object>> countByStatus();
    List<Map<String, Object>> countByDepartment();
    List<Map<String, Object>> countByDays(@Param("days") int days);
    List<Map<String, Object>> topDoctors(@Param("limit") int limit);

    // ==================== 医生端专用（2026-10-04） ====================

    /**
     * 查询某位医生在日期区间的预约（医生端「我的接诊」）
     *
     * <p>⚠️ {@code doctorId} 必须由服务端从登录 token 解析得到，
     * <b>绝不能</b>作为前端入参，否则医生可篡改参数查看其他医生的接诊记录。
     *
     * @param doctorId 医生 ID（来自 token）
     * @param fromDate  起始日期（含），null 表示不限
     * @param toDate    结束日期（含），null 表示不限
     * @param status    预约状态，null 表示全部
     * @return 按 日期 → 时段 → 序号 升序
     */
    List<AppointmentVO> queryByDoctorAndDateRange(@Param("doctorId") Integer doctorId,
                                                 @Param("fromDate") LocalDate fromDate,
                                                 @Param("toDate") LocalDate toDate,
                                                 @Param("status") Integer status);

    /**
     * 医生端概览统计（今日 / 本周 / 累计 / 待接诊 / 已完成）
     *
     * <p>无任何预约时 SUM 返回 null，Service 层需按 0 处理。
     *
     * @param doctorId 医生 ID（来自 token）
     */
    Map<String, Object> summaryByDoctor(@Param("doctorId") Integer doctorId);

    /**
     * 就诊确认：把预约置为「已完成」
     *
     * <p>SQL 内置两道防线：{@code doctor_id = #{doctorId}} 防越权，
     * {@code status IN (0,1)} 保证只从「待接诊/已预约」流转且天然幂等。
     *
     * @return 影响行数；0 表示「不属于该医生」或「状态已流转过」
     */
    int completeByDoctor(@Param("id") Integer id, @Param("doctorId") Integer doctorId);

    // ==================== 医患关系（防遍历 patientId 查全库档案） ====================

    /**
     * 校验医患关系：某患者与某医生是否存在预约关系
     *
     * <p><b>这是医生端「患者详情」的前置闸门。</b>
     * 没有它，医生可构造 {@code patientId=1..N} 遍历全库患者健康档案（身高体重、
     * 慢病、用药、血压血糖），构成严重的隐私泄露。
     *
     * <p>口径：存在<b>任意状态</b>的预约即视为有关系 —— 含历史已完成的，
     * 否则医生无法复诊老患者。
     *
     * @return 匹配行数，&gt;0 表示有医患关系
     */
    Integer countDoctorPatientRelation(@Param("doctorId") Integer doctorId,
                                       @Param("patientId") Integer patientId);

    /**
     * 该患者与我的最近一条预约（详情页用于定位到具体那一条）
     */
    AppointmentVO getLatestByDoctorAndPatient(@Param("doctorId") Integer doctorId,
                                             @Param("patientId") Integer patientId);

    // ==================== 就诊提醒（2026-10-10） ====================

    /**
     * 查询某个就诊日「需要发前一天提醒」的预约目标
     *
     * <p>供 {@code AppointmentReminderJob} 定时任务使用：扫某一天的预约，
     * 交给它逐条发「明天有就诊安排」的通知。
     *
     * <p><b>偏好过滤放在 SQL 里做</b>：这是批量路径，可能一次扫出几百条。
     * 若先全查出来再逐条调 {@code NotificationPreferenceService} 判断，
     * 就是 N 次查询；而且用户已经关掉开关时，这次查询本来就是多余的。
     * 因此这里直接 JOIN {@code user} 并带上 {@code notify_appointment} 条件，
     * 把「已关闭提醒」的人在数据库层就滤掉。
     *
     * <p>条件口径：
     * <ul>
     *   <li>{@code status IN (0,1)} —— 只提醒「待接诊 / 已预约」的；
     *       已取消(3)、已完成(2)的不该再打扰用户；</li>
     *   <li>{@code notify_appointment = 1 OR IS NULL} —— 与
     *       {@code NotificationPreferenceServiceImpl#enabled} 口径一致（NULL 视为开启）；</li>
     *   <li>不 JOIN 是因为要「不管有没有排班记录都能提醒」，用 LEFT JOIN 才不会漏。</li>
     * </ul>
     *
     * @param appointmentDate 就诊日（通常是「明天」）
     * @return 待提醒的预约（含患者、医生、科室、时段中文名）
     */
    List<AppointmentVO> queryReminderTargets(@Param("appointmentDate") LocalDate appointmentDate);
}
