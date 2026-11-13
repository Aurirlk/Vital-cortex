package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MallProduct {
    private Integer id;
    private Integer categoryId;
    /** 商品类型：drug=药品, device=医疗器械, health=保健品 */
    private String productType;
    private String name;
    private String description;
    private String cover;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private Integer salesCount;
    private String unit;
    private Integer status;
    private Integer isHot;
    private Integer isNew;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
