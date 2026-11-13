package cn.kmbeast.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 就诊记录视图对象（2026-10-04）
 *
 * <p>在 {@code VisitRecord} 基础上补充医生姓名、职称与预约日期/时段，
 * 供医生端患者详情页展示历史就诊时间线。
 */
@Data
public class VisitRecordVO {

    private Integer id;
    private Integer appointmentId;
    private Integer patientId;
    private Integer doctorId;
    private String chiefComplaint;
    private String presentIllness;
    private String diagnosis;
    private String prescription;
    private String examinationResults;
    private String followUpPlan;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 就诊医生姓名 */
    private String doctorName;
    /** 就诊医生职称 */
    private String doctorTitle;

    /** 对应预约的日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;
    /** 对应预约的时段：morning / afternoon */
    private String timeSlot;
}
