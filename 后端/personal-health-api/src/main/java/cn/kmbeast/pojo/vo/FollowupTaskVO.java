package cn.kmbeast.pojo.vo;

import cn.kmbeast.pojo.entity.FollowupTask;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class FollowupTaskVO extends FollowupTask {
    private String patientName;
    private String doctorName;
}
