import doctorRequest from "@/utils/doctorRequest.js";

/**
 * 医生端 API（2026-10-04）
 *
 * 注意：这里**没有任何接口接收 doctorId**。
 * 医生身份由后端从 token 中解析，前端无法指定要查哪个医生 —— 这是刻意的。
 */

/** 首页概览：身份 + 今日/本周/累计统计 */
export function getDoctorOverview() {
  return doctorRequest.get("/doctor/overview");
}

/**
 * 我的接诊列表
 * @param {{fromDate?: string, toDate?: string, status?: number}} params
 *        日期格式 yyyy-MM-dd；不传则后端默认最近 30 天
 */
export function getMyAppointments(params) {
  return doctorRequest.get("/doctor/appointments", { params });
}

/** 我的排班 */
export function getMySchedules(params) {
  return doctorRequest.get("/doctor/schedules", { params });
}

/** 就诊确认 */
export function completeAppointment(id) {
  return doctorRequest.post(`/doctor/appointment/${id}/complete`);
}

/** 停用排班 */
export function disableSchedule(id) {
  return doctorRequest.post(`/doctor/schedule/${id}/disable`);
}

/**
 * 患者档案详情（医生接诊一屏视图）
 *
 * 后端会先校验医患关系，无预约关系的患者一律返回「无权查看该患者档案」，
 * 因此这里不需要（也无法）传 doctorId。
 *
 * @param {number} patientId
 * @param {number} [trendDays] 趋势天数，默认 90，上限 365
 */
export function getPatientDetail(patientId, trendDays) {
  return doctorRequest.get(`/doctor/patient/${patientId}`, {
    params: trendDays ? { trendDays } : {},
  });
}

/**
 * 开始接诊：保存就诊记录并把预约置为已完成
 *
 * @param {number} appointmentId
 * @param {{chiefComplaint?: string, presentIllness?: string, diagnosis?: string,
 *          prescription?: string, examinationResults?: string, followUpPlan?: string}} record
 *        diagnosis 为必填（后端强校验）
 */
export function startConsultation(appointmentId, record) {
  return doctorRequest.post(`/doctor/appointment/${appointmentId}/consult`, record);
}
