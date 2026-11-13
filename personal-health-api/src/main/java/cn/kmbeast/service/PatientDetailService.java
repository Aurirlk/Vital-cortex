package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;

import java.util.Map;

/**
 * 医生端患者档案服务（2026-10-04）
 *
 * <p>解决医生接诊时的核心断点：接诊列表里点进一个患者，必须一屏看到
 * 全部决策所需信息，而不是跳到患者自己填的健康档案去猜。
 *
 * <p><b>安全约定</b>：{@code patientId} 由前端传入（医生从自己的接诊列表点进来），
 * 因此<b>必须</b>先校验医患关系，否则医生可遍历 patientId 查全库患者档案。
 * 校验逻辑封装在实现类内，调用方无需重复判断。
 *
 * <p><b>医生身份</b>同 {@link DoctorWorkService}：一律从 {@code LocalThreadHolder} 取，
 * 绝不接受外部传入。
 */
public interface PatientDetailService {

    /**
     * 患者档案详情（医生视角聚合）
     *
     * <p>聚合内容：基本信息 / 体征与生化指标 / 慢病·过敏·用药史 /
     * 生活方式 / 近 N 天健康趋势 / 历史就诊记录 / 随访任务 / 与我的预约关系。
     *
     * <p>患者未建档时<b>不报错</b>，返回 {@code profileExists=false} 与基础信息，
     * 由前端引导「该患者尚未建立健康档案」。
     *
     * @param patientId 患者（user 表主键）
     * @param trendDays 趋势天数，默认 90，上限 365
     */
    Result<Map<String, Object>> detail(Integer patientId, Integer trendDays);

    /**
     * 开始接诊：基于指定预约创建就诊记录
     *
     * @param appointmentId 预约 ID（须属于当前医生）
     * @param record        就诊内容：chiefComplaint / presentIllness /
     *                      diagnosis / prescription / examinationResults / followUpPlan
     */
    Result<Void> startConsultation(Integer appointmentId, Map<String, Object> record);
}
