package cn.kmbeast.pojo.vo;

import cn.kmbeast.pojo.entity.HospitalDoctor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DoctorVO extends HospitalDoctor {
    private String departmentName;
    private Integer scheduleCount;
    private Integer appointmentCount;
}
