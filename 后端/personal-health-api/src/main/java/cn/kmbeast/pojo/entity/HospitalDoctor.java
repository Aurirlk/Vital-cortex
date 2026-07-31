package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class HospitalDoctor {
    private Integer id;
    private Integer userId;
    private String name;
    private String avatar;
    private String title;
    private Integer departmentId;
    private String introduction;
    private String expertise;
    private String qualifications;
    private Integer isOnline;
    private Integer sortOrder;
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
