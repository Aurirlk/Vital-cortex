package cn.kmbeast.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 内容交互统一 Mapper（2026-10-04 表合并）
 *
 * <p>合并前是 {@code post_like} / {@code post_favorite} / {@code post_report} 三张
 * 结构高度重合的表，现统一为 {@code content_interaction} 一张，
 * 用 {@code action_type} 区分行为（LIKE / FAVORITE / REPORT / FOLLOW）。
 *
 * <p><b>兼容策略</b>：本表上线后，旧接口路径（{@code /post/reports/*} 等）
 * 保持不变，由 Service 内部转调本 Mapper，前端零改动。
 */
@Mapper
public interface ContentInteractionMapper {

    /** 插入一条交互（依赖 uk_interaction 唯一键做幂等） */
    int insert(@Param("targetType") String targetType,
               @Param("targetId") Integer targetId,
               @Param("userId") Integer userId,
               @Param("actionType") String actionType,
               @Param("reason") String reason,
               @Param("status") Integer status);

    /**
     * 取消交互（点赞取消、收藏取消）
     *
     * @return 影响行数，0 表示本来就没交互过（幂等）
     */
    int delete(@Param("targetType") String targetType,
               @Param("targetId") Integer targetId,
               @Param("userId") Integer userId,
               @Param("actionType") String actionType);

    /**
     * 查询某用户对某内容的交互状态
     *
     * @return 记录数，&gt;0 表示已交互
     */
    Integer countInteraction(@Param("targetType") String targetType,
                             @Param("targetId") Integer targetId,
                             @Param("userId") Integer userId,
                             @Param("actionType") String actionType);

    /**
     * 某内容的交互计数（按行为分组）
     *
     * @return [{actionType, cnt}, ...]
     */
    List<Map<String, Object>> countByTarget(@Param("targetType") String targetType,
                                            @Param("targetId") Integer targetId);

    /**
     * 查某用户收藏的全部资讯 ID（倒序）
     *
     * <p>供「我的收藏」列表使用。{@code limit} 由 Service 封顶防拖库。
     */
    List<Integer> queryFavoritedNewsIds(@Param("userId") Integer userId,
                                        @Param("limit") Integer limit);

    /**
     * 待处理的举报列表（管理端审核用）
     *
     * <p>对应合并前的 {@code post_report} 表，供 {@code AuditManage.vue} 使用。
     * 返回结构需保持与旧 PostReport 实体兼容，故在 Service 层做转换。
     */
    List<Map<String, Object>> queryPendingReports(@Param("status") Integer status,
                                                   @Param("limit") Integer limit);

    /**
     * 处理举报：更新状态
     *
     * @return 影响行数
     */
    int handleReport(@Param("id") Integer id, @Param("status") Integer status);
}
