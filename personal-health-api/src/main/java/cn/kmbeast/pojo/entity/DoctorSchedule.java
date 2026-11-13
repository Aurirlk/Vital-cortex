package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DoctorSchedule {
    private Integer id;
    private Integer doctorId;
    private LocalDate scheduleDate;
    private String timeSlot;
    private Integer maxPatients;
    private Integer bookedCount;
    private Integer version;
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
