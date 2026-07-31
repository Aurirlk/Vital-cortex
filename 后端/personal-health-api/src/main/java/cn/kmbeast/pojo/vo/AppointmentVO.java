package cn.kmbeast.pojo.vo;

import cn.kmbeast.pojo.entity.Appointment;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Data
public class AppointmentVO extends Appointment {
    private String patientName;
    private String patientPhone;
    private String doctorName;
    private String doctorTitle;
    private String departmentName;
    private LocalDate scheduleDate;
    private String timeSlotName;
}
