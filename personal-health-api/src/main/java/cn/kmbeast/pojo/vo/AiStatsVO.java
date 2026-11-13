package cn.kmbeast.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI使用统计VO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiStatsVO {

    /**
     * 总对话数
     */
    private Integer totalChats;

    /**
     * 今日对话数
     */
    private Integer todayChats;

    /**
     * 使用用户数
     */
    private Integer userCount;

    /**
     * 人均对话数
     */
    private Double avgPerUser;
}
