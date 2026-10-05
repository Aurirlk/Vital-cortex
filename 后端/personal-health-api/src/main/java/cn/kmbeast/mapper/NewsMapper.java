package cn.kmbeast.mapper;

import cn.kmbeast.pojo.dto.query.extend.NewsQueryDto;
import cn.kmbeast.pojo.dto.query.extend.PostContentQueryDto;
import cn.kmbeast.pojo.dto.query.extend.TagsQueryDto;
import cn.kmbeast.pojo.entity.News;
import cn.kmbeast.pojo.entity.Post;
import cn.kmbeast.pojo.entity.Tags;
import cn.kmbeast.pojo.vo.NewsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 标签持久化接口
 */
@Mapper
public interface NewsMapper {

    void save(News news);

    void update(News news);

    void batchDelete(@Param(value = "ids") List<Long> ids);

    List<NewsVO> query(NewsQueryDto newsQueryDto);

    Integer queryCount(NewsQueryDto newsQueryDto);

    /**
     * RAG知识库检索 — 按多关键词匹配标题和内容，返回TopN文章
     */
    List<NewsVO> ragSearch(@Param("keywords") List<String> keywords, @Param("limit") int limit);

    /**
     * RAG ingestion — 全量文章（用于向量库灌数，RAG-04）
     */
    List<NewsVO> selectAllForRag();

    /**
     * 按 ID 批量查资讯（2026-10-04 表合并新增）
     *
     * <p>服务于「我的收藏」列表：收藏关系已并入 {@code content_interaction}，
     * 需按 ID 批量取资讯标题/封面渲染列表，避免 N+1 查询。
     *
     * @param ids 资讯 ID 集合，调用方保证非空
     */
    List<NewsVO> selectByIds(@Param("ids") List<Integer> ids);

    // ==================== 帖子能力（2026-10-04 post 表并入 news） ====================
    // 帖子不再是独立表，而是 news 表中 content_type='POST' 的行。
    // 下列方法全部隐含 content_type='POST' 条件，防止与资讯混淆。

    /** 帖子列表查询 */
    List<NewsVO> queryPosts(@Param("dto") PostContentQueryDto dto);

    /** 帖子列表总数 */
    Integer countPosts(@Param("dto") PostContentQueryDto dto);

    /** 按 ID 查帖子（自动校验 content_type='POST'，非帖子返回 null） */
    NewsVO getPostById(@Param("id") Integer id);

    /** 插入帖子（自动写入 content_type='POST'） */
    int insertPost(@Param("post") Post post);

    /** 更新帖子（按 id 且限定 content_type='POST'，防止误改资讯） */
    int updatePost(@Param("post") Post post);

    /** 删除帖子（软删除：置 status=2 已下架） */
    int deletePost(@Param("ids") List<Long> ids);

    /** 浏览量 +1 */
    int incrementPostView(@Param("id") Integer id);

    /** 点赞数 ±1；delta=1 增，-1 减，且不允许减到负数 */
    int addPostLikeCount(@Param("id") Integer id, @Param("delta") int delta);

    /** 收藏数 ±1 */
    int addPostFavoriteCount(@Param("id") Integer id, @Param("delta") int delta);

    /** 评论数 +1 */
    int incrementPostCommentCount(@Param("id") Integer id);

    /** 更新热度分 */
    int updatePostHotScore(@Param("id") Integer id, @Param("hotScore") Object hotScore);

    /** 今日发帖数 */
    Integer countPostsToday();

    /** 热门帖子 TopN */
    List<Map<String, Object>> topPostsByHotScore(@Param("limit") int limit);

}
