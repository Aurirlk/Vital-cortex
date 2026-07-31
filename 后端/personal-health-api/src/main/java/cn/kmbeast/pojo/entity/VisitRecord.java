package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class VisitRecord {
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
}
