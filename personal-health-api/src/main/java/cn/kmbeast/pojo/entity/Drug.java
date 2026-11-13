package cn.kmbeast.pojo.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药品实体类
 */
@Data
public class Drug {
    private Integer id;
    private String name;
    private String genericName;
    private String category;
    private String description;
    private BigDecimal price;
    private String unit;
    private String specification;
    private String manufacturer;
    private String cover;
    private Boolean isOtc;
    private Integer stock;
    private Boolean status;
    private LocalDateTime createTime;
}
