package cn.kmbeast.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户药品订阅记录实体类
 */
@Data
public class DrugSubscription {
    private Integer id;
    private Integer userId;
    private Integer drugId;
    private Integer quantity;
    private LocalDateTime createTime;
}
