package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class Appointment {
    private Integer id;
    private Integer patientId;
    private Integer doctorId;
    private Integer scheduleId;
    private Integer departmentId;
    private LocalDate appointmentDate;
    private String timeSlot;
    private Integer serialNumber;
    private String symptomDescription;
    private String cancelReason;
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
