package cn.kmbeast.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI token 用量记录（ENG-10）
 */
@Data
public class AiUsage {

    private Long id;

    private Integer userId;

    private String scene;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private LocalDateTime createTime;
}
