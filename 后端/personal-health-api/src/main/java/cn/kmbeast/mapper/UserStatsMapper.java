package cn.kmbeast.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 个人中心统计查询（MyBatis 注解实现，无需 XML）
 *
 * <p>2026-10-04 表合并修正两处失效 SQL：
 * <ul>
 *   <li>{@code news_save} 已并入 {@code content_interaction}
 *       （action_type='FAVORITE'、target_type='NEWS'）</li>
 *   <li>{@code drug_subscription} <b>表根本不存在</b> —— 该查询每次调用必抛
 *       「表不存在」，属长期潜伏的线上故障。因无对应表也无替代数据源，
 *       改为返回 0 并在注释中说明。</li>
 * </ul>
 */
@Mapper
public interface UserStatsMapper {

    /** 资讯收藏数（news_save 已并入 content_interaction） */
    @Select("SELECT COUNT(*) FROM content_interaction "
            + "WHERE user_id = #{userId} AND action_type = 'FAVORITE' AND target_type = 'NEWS'")
    Integer countFavorites(@Param("userId") Integer userId);

    @Select("SELECT COUNT(*) FROM user_health WHERE user_id = #{userId}")
    Integer countHealthRecords(@Param("userId") Integer userId);

    @Select("SELECT COUNT(*) FROM ai_chat_record WHERE user_id = #{userId}")
    Integer countAiChats(@Param("userId") Integer userId);

    /**
     * 药品订阅数
     *
     * <p>⚠️ drug_subscription 表不存在，且无等价数据源。
     * 原实现直接查该表，个人中心每加载一次就抛一次 SQL 异常。
     * 现固定返回 0，待药品订阅功能真正落地（建表）后再改为真实查询。
     */
    @Select("SELECT 0")
    Integer countDrugSubscriptions(@Param("userId") Integer userId);

    @Select("SELECT COUNT(*) FROM appointment WHERE patient_id = #{userId}")
    Integer countAppointments(@Param("userId") Integer userId);

    @Select("SELECT COUNT(*) FROM mall_order WHERE user_id = #{userId}")
    Integer countOrders(@Param("userId") Integer userId);
}
