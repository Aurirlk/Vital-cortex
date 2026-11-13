package cn.kmbeast.pojo.vo;

import cn.kmbeast.pojo.entity.HealthModelConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 健康模型VO类
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HealthModelConfigVO extends HealthModelConfig {
    /**
     * 用户名
     */
    private String userName;
}
