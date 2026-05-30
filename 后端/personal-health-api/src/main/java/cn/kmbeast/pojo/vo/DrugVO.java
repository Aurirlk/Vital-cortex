package cn.kmbeast.pojo.vo;

import cn.kmbeast.pojo.entity.Drug;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 药品VO类
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DrugVO extends Drug {
    /**
     * 当前用户是否已订阅
     */
    private Boolean subscribed;
    /**
     * 订阅数量
     */
    private Integer subscriptionCount;
}
