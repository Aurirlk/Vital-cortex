package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Data
public class AppointmentQueryDto extends QueryDto {
    private Integer patientId;
    private Integer doctorId;
    private Integer departmentId;
    private LocalDate appointmentDate;
    private Integer status;
    private String doctorName;
    private String departmentName;
}
