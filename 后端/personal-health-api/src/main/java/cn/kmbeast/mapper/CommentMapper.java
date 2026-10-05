package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.Comment;
import cn.kmbeast.pojo.vo.CommentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 统一评论 Mapper（2026-10-03 合并 evaluations + post_reply）
 *
 * <p>多态设计：{@code targetType + targetId}。查询按目标聚合，支持树形（parentId）分页。
 */
@Mapper
public interface CommentMapper {

    void save(Comment comment);

    /** 更新内容/点赞数（不传的字段不覆盖） */
    void update(Comment comment);

    /** 软删除（status=0），保留树形结构不塌陷 */
    void softDelete(@Param("id") Integer id);

    /** 批量物理删除（谨慎使用，优先软删） */
    void batchDelete(@Param("ids") List<Long> ids);

    /** 按目标查询一级评论（parentId IS NULL），分页 */
    List<CommentVO> queryRoots(@Param("targetType") String targetType,
                               @Param("targetId") Integer targetId,
                               @Param("offset") Integer offset,
                               @Param("size") Integer size);

    /** 按目标统计一级评论数 */
    int countRoots(@Param("targetType") String targetType,
                   @Param("targetId") Integer targetId);

    /** 按父评论查子回复（最多 limit 条，用于「查看更多回复」） */
    List<CommentVO> queryChildren(@Param("parentId") Integer parentId,
                                  @Param("limit") Integer limit);

    /** 按父评论统计子回复数 */
    int countChildren(@Param("parentId") Integer parentId);

    CommentVO getById(@Param("id") Integer id);

    /** 点赞数 +1（原子更新） */
    int increaseLikeCount(@Param("id") Integer id);

    /** 点赞数 -1（原子更新，下限 0） */
    int decreaseLikeCount(@Param("id") Integer id);

    /** 更新点赞用户列表（整体覆盖，由 Service 先读后写） */
    void updateUpvoteList(@Param("id") Integer id, @Param("upvoteList") String upvoteList);

    /** 按用户查询其评论（个人中心用） */
    List<CommentVO> queryByUser(@Param("userId") Integer userId,
                                @Param("offset") Integer offset,
                                @Param("size") Integer size);

    int countByUser(@Param("userId") Integer userId);
}
