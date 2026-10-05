package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.CommentMapper;
import cn.kmbeast.mapper.ContentInteractionMapper;
import cn.kmbeast.mapper.NewsMapper;
import cn.kmbeast.mapper.UserFollowMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.PostContentQueryDto;
import cn.kmbeast.pojo.dto.query.extend.PostQueryDto;
import cn.kmbeast.pojo.entity.Comment;
import cn.kmbeast.pojo.entity.Post;
import cn.kmbeast.pojo.entity.PostReply;
import cn.kmbeast.pojo.entity.PostReport;
import cn.kmbeast.pojo.entity.UserFollow;
import cn.kmbeast.pojo.vo.CommentVO;
import cn.kmbeast.pojo.vo.PostReplyVO;
import cn.kmbeast.pojo.vo.PostVO;
import cn.kmbeast.service.PostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 社区帖子服务实现（2026-10-04 表合并后重写）
 *
 * <p><b>表合并背景</b>：
 * <ul>
 *   <li>{@code post} → {@code news}（帖子即 {@code news.content_type='POST'}）</li>
 *   <li>{@code post_like} + {@code post_favorite} + {@code post_report}
 *       → {@code content_interaction}（用 action_type 区分行为）</li>
 * </ul>
 *
 * <p><b>接口契约完全不变</b>：{@code PostService} 的 20 个方法签名、返回结构
 * 与前端调用方式均未改动，前端零改动。
 *
 * <p><b>关键设计</b>：
 * <ul>
 *   <li>所有 SQL 都带 {@code content_type='POST'} 条件，杜绝与资讯混淆</li>
 *   <li>点赞/收藏计数与交互记录<b>分开维护</b>：交互表用唯一键保证幂等，
 *       计数字段用 {@code GREATEST(...,0)} 防止减成负数</li>
 *   <li>删除帖子用软删除（status=2），保留数据可回溯</li>
 * </ul>
 */
@Slf4j
@Service
public class PostServiceImpl implements PostService {

    private static final String TARGET_TYPE = "POST";
    private static final String ACTION_LIKE = "LIKE";
    private static final String ACTION_FAVORITE = "FAVORITE";
    private static final String ACTION_REPORT = "REPORT";

    @Resource
    private NewsMapper newsMapper;

    @Resource
    private ContentInteractionMapper interactionMapper;

    @Resource
    private CommentMapper commentMapper;

    /** 关注关系仍用独立表（user_follow 未合并，属不同业务域） */
    @Resource
    private UserFollowMapper userFollowMapper;

    // ================================================================ 帖子 CRUD

    @Override
    public Result<Void> save(Post post) {
        if (post.getUserId() == null) {
            post.setUserId(LocalThreadHolder.getUserId());
        }
        if (post.getUserId() == null) {
            return ApiResult.error("未登录");
        }
        if (post.getCreateTime() == null) {
            post.setCreateTime(LocalDateTime.now());
        }
        post.setUpdateTime(LocalDateTime.now());
        if (post.getStatus() == null) {
            post.setStatus(1);
        }
        if (post.getIsTop() == null) {
            post.setIsTop(false);
        }
        newsMapper.insertPost(post);
        return ApiResult.success();
    }

    @Override
    public Result<Void> batchDelete(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            newsMapper.deletePost(ids);
        }
        return ApiResult.success();
    }

    @Override
    public Result<Void> update(Post post) {
        post.setUpdateTime(LocalDateTime.now());
        newsMapper.updatePost(post);
        return ApiResult.success();
    }

    /**
     * 帖子列表查询
     *
     * <p>沿用原 {@code PostQueryDto}（接口签名不变），内部转成
     * {@code PostContentQueryDto} 交给 news 表查询。
     */
    @Override
    public Result<List<PostVO>> query(PostQueryDto queryDto) {
        PostContentQueryDto dto = toContentDto(queryDto);
        List<PostVO> list = toPostVOs(newsMapper.queryPosts(dto));
        fillLikeFavoriteState(list);
        return ApiResult.success(list);
    }

    @Override
    public Result<PostVO> getById(Integer id) {
        PostVO vo = toPostVO(newsMapper.getPostById(id));
        if (vo == null) {
            return ApiResult.error("帖子不存在");
        }
        // 浏览量 +1（详情页访问）
        newsMapper.incrementPostView(id);

        Integer userId = LocalThreadHolder.getUserId();
        if (userId != null) {
            vo.setLiked(interactionMapper.countInteraction(
                    TARGET_TYPE, id, userId, ACTION_LIKE) > 0);
            vo.setFavorited(interactionMapper.countInteraction(
                    TARGET_TYPE, id, userId, ACTION_FAVORITE) > 0);
        }
        return ApiResult.success(vo);
    }

    // ================================================================ 点赞

    @Override
    public Result<Void> like(Integer userId, Integer postId) {
        if (userId == null || postId == null) {
            return ApiResult.error("参数不完整");
        }
        Integer cnt = interactionMapper.countInteraction(TARGET_TYPE, postId, userId, ACTION_LIKE);
        if (cnt != null && cnt > 0) {
            return ApiResult.success(); // 幂等：已点赞直接返回，不重复计数
        }
        interactionMapper.insert(TARGET_TYPE, postId, userId, ACTION_LIKE, null, 1);
        newsMapper.addPostLikeCount(postId, 1);
        return ApiResult.success();
    }

    @Override
    public Result<Void> unlike(Integer userId, Integer postId) {
        if (userId == null || postId == null) {
            return ApiResult.error("参数不完整");
        }
        Integer cnt = interactionMapper.countInteraction(TARGET_TYPE, postId, userId, ACTION_LIKE);
        if (cnt == null || cnt == 0) {
            return ApiResult.success(); // 本来就没点赞，幂等返回
        }
        int affected = interactionMapper.delete(TARGET_TYPE, postId, userId, ACTION_LIKE);
        if (affected > 0) {
            // 只有真正删掉了交互记录才减计数，否则会出现「计数变负」
            newsMapper.addPostLikeCount(postId, -1);
        }
        return ApiResult.success();
    }

    // ================================================================ 收藏

    @Override
    public Result<Void> favorite(Integer userId, Integer postId) {
        if (userId == null || postId == null) {
            return ApiResult.error("参数不完整");
        }
        Integer cnt = interactionMapper.countInteraction(TARGET_TYPE, postId, userId, ACTION_FAVORITE);
        if (cnt != null && cnt > 0) {
            return ApiResult.success();
        }
        interactionMapper.insert(TARGET_TYPE, postId, userId, ACTION_FAVORITE, null, 1);
        newsMapper.addPostFavoriteCount(postId, 1);
        return ApiResult.success();
    }

    @Override
    public Result<Void> unfavorite(Integer userId, Integer postId) {
        if (userId == null || postId == null) {
            return ApiResult.error("参数不完整");
        }
        Integer cnt = interactionMapper.countInteraction(TARGET_TYPE, postId, userId, ACTION_FAVORITE);
        if (cnt == null || cnt == 0) {
            return ApiResult.success();
        }
        int affected = interactionMapper.delete(TARGET_TYPE, postId, userId, ACTION_FAVORITE);
        if (affected > 0) {
            newsMapper.addPostFavoriteCount(postId, -1);
        }
        return ApiResult.success();
    }

    // ================================================================ 回复（走 comment 表）

    /**
     * 保存帖子回复。
     *
     * <p>{@code post_reply} 表已并入 {@code comment}（target_type='POST'），
     * 此处做字段映射后交给统一评论表，接口契约不变。
     */
    @Override
    public Result<Void> saveReply(PostReply postReply) {
        if (postReply.getUserId() == null) {
            postReply.setUserId(LocalThreadHolder.getUserId());
        }
        if (postReply.getCreateTime() == null) {
            postReply.setCreateTime(LocalDateTime.now());
        }

        Comment comment = new Comment();
        comment.setTargetType(TARGET_TYPE);
        comment.setTargetId(postReply.getPostId());
        comment.setUserId(postReply.getUserId());
        comment.setParentId(postReply.getParentId());
        comment.setContent(postReply.getContent());
        comment.setLikeCount(postReply.getLikeCount() != null ? postReply.getLikeCount() : 0);
        comment.setStatus(1);
        comment.setCreateTime(postReply.getCreateTime());
        commentMapper.save(comment);

        if (postReply.getPostId() != null) {
            newsMapper.incrementPostCommentCount(postReply.getPostId());
        }
        return ApiResult.success();
    }

    /**
     * 查询帖子的回复列表。
     *
     * <p>从统一评论表按 {@code target_type='POST'} 取根评论。
     *
     * <p>注：{@code PostReplyVO} 没有 children 字段（它继承自扁平的 PostReply），
     * 故只返回一级回复。子回复通过 {@code childCount} 语义由
     * {@code /comment/children/{parentId}} 单独查询。
     */
    @Override
    public Result<List<PostReplyVO>> getReplies(Integer postId) {
        if (postId == null) {
            return ApiResult.success(new ArrayList<>());
        }
        List<PostReplyVO> result = new ArrayList<>();
        List<CommentVO> roots = commentMapper.queryRoots(TARGET_TYPE, postId, 0, 100);
        if (roots == null) {
            return ApiResult.success(result);
        }
        for (CommentVO root : roots) {
            PostReplyVO vo = new PostReplyVO();
            vo.setId(root.getId());
            vo.setPostId(root.getTargetId());
            vo.setUserId(root.getUserId());
            vo.setParentId(root.getParentId());
            vo.setContent(root.getContent());
            vo.setLikeCount(root.getLikeCount());
            vo.setCreateTime(root.getCreateTime());
            vo.setUserName(root.getUserName());
            vo.setUserAvatar(root.getUserAvatar());
            result.add(vo);
        }
        return ApiResult.success(result);
    }

    // ================================================================ 关注

    @Override
    public Result<Void> follow(Integer followerId, Integer followeeId) {
        if (followerId == null || followeeId == null) {
            return ApiResult.error("参数不完整");
        }
        if (followerId.equals(followeeId)) {
            return ApiResult.error("不能关注自己");
        }
        UserFollow follow = new UserFollow();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        follow.setCreateTime(LocalDateTime.now());
        userFollowMapper.save(follow);
        return ApiResult.success();
    }

    @Override
    public Result<Void> unfollow(Integer followerId, Integer followeeId) {
        userFollowMapper.delete(followerId, followeeId);
        return ApiResult.success();
    }

    @Override
    public Result<Boolean> isFollowing(Integer followerId, Integer followeeId) {
        return ApiResult.success(
                userFollowMapper.getByFollowerAndFollowee(followerId, followeeId) != null);
    }

    // ================================================================ 热门与搜索

    @Override
    public Result<List<PostVO>> getHotList(Integer limit) {
        List<Map<String, Object>> rows = newsMapper.topPostsByHotScore(
                limit != null && limit > 0 ? limit : 10);
        List<PostVO> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            PostVO vo = new PostVO();
            vo.setId(toInt(row.get("id")));
            vo.setTitle(row.get("title") != null ? String.valueOf(row.get("title")) : "");
            vo.setHotScore(toDouble(row.get("hotScore")));
            vo.setViewCount(toInt(row.get("viewCount")));
            vo.setLikeCount(toInt(row.get("likeCount")));
            list.add(vo);
        }
        return ApiResult.success(list);
    }

    @Override
    public Result<List<PostVO>> search(String keyword) {
        PostContentQueryDto dto = new PostContentQueryDto();
        dto.setKeyword(keyword);
        List<PostVO> list = toPostVOs(newsMapper.queryPosts(dto));
        fillLikeFavoriteState(list);
        return ApiResult.success(list);
    }

    // ================================================================ 举报

    @Override
    public Result<Void> report(PostReport postReport) {
        if (postReport.getUserId() == null) {
            postReport.setUserId(LocalThreadHolder.getUserId());
        }
        if (postReport.getUserId() == null || postReport.getPostId() == null) {
            return ApiResult.error("参数不完整");
        }
        // status=0 待处理，与合并前 post_report.status 语义一致
        interactionMapper.insert(TARGET_TYPE, postReport.getPostId(), postReport.getUserId(),
                ACTION_REPORT, postReport.getReason(), 0);
        return ApiResult.success();
    }

    /**
     * 待处理举报列表
     *
     * <p>返回 {@code PostReport} 列表以保持前端 AuditManage.vue 的消费方式不变，
     * 内部由 content_interaction 的行转换而来。
     */
    @Override
    public Result<List<PostReport>> getPendingReports() {
        List<Map<String, Object>> rows = interactionMapper.queryPendingReports(0, 100);
        List<PostReport> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            PostReport r = new PostReport();
            r.setId(toInt(row.get("id")));
            r.setUserId(toInt(row.get("user_id")));
            r.setPostId(toInt(row.get("post_id")));
            r.setReason(row.get("reason") != null ? String.valueOf(row.get("reason")) : null);
            r.setStatus(toInt(row.get("status")));
            list.add(r);
        }
        return ApiResult.success(list);
    }

    @Override
    public Result<Void> handleReport(Integer id, Integer status) {
        interactionMapper.handleReport(id, status);
        return ApiResult.success();
    }

    // ================================================================ 内部工具

    /** PostQueryDto → PostContentQueryDto（保留接口签名，同时适配 news 表查询） */
    private PostContentQueryDto toContentDto(PostQueryDto src) {
        PostContentQueryDto dto = new PostContentQueryDto();
        if (src == null) {
            return dto;
        }
        dto.setTitle(src.getTitle());
        dto.setTagId(src.getTagId());
        dto.setUserId(src.getUserId());
        dto.setStatus(src.getStatus());
        dto.setIsTop(src.getIsTop());
        dto.setKeyword(src.getKeyword());
        dto.setOrderBy(src.getOrderBy());
        // QueryDto 继承来的分页与时间范围
        dto.setCurrent(src.getCurrent());
        dto.setSize(src.getSize());
        dto.setStartTime(src.getStartTime());
        dto.setEndTime(src.getEndTime());
        return dto;
    }

    /**
     * 批量填充列表的 liked/favorited 状态。
     *
     * <p>逐条查询：帖子列表通常 10~20 条，每条 2 次 count 查询。
     * 若后续出现性能问题，可加批量接口
     * {@code SELECT target_id, action_type FROM content_interaction
     *  WHERE user_id=? AND target_type='POST' AND target_id IN (...)}。
     */
    private void fillLikeFavoriteState(List<PostVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Integer userId = LocalThreadHolder.getUserId();
        if (userId == null) {
            return;
        }
        for (PostVO vo : list) {
            if (vo.getId() == null) {
                continue;
            }
            vo.setLiked(interactionMapper.countInteraction(
                    TARGET_TYPE, vo.getId(), userId, ACTION_LIKE) > 0);
            vo.setFavorited(interactionMapper.countInteraction(
                    TARGET_TYPE, vo.getId(), userId, ACTION_FAVORITE) > 0);
        }
    }

    /** NewsVO → PostVO（帖子与资讯在 news 表中共用行，按帖子视角投影） */
    private PostVO toPostVO(cn.kmbeast.pojo.vo.NewsVO n) {
        if (n == null) {
            return null;
        }
        PostVO vo = new PostVO();
        vo.setId(n.getId());
        vo.setUserId(n.getUserId());
        vo.setTitle(n.getTitle() != null ? n.getTitle() : n.getName());
        vo.setContent(n.getContent());
        vo.setCover(n.getCover());
        vo.setTagId(n.getTagId());
        vo.setViewCount(n.getViewCount());
        vo.setLikeCount(n.getLikeCount());
        vo.setFavoriteCount(n.getFavoriteCount());
        vo.setCommentCount(n.getCommentCount());
        vo.setShareCount(n.getShareCount());
        vo.setHotScore(n.getHotScore() != null ? n.getHotScore().doubleValue() : null);
        vo.setStatus(n.getStatus());
        vo.setIsTop(n.getIsTop());
        vo.setCreateTime(n.getCreateTime());
        vo.setUpdateTime(n.getUpdatedAt() != null ? n.getUpdatedAt() : n.getCreateTime());
        vo.setTagName(n.getTagName());
        return vo;
    }

    private List<PostVO> toPostVOs(List<cn.kmbeast.pojo.vo.NewsVO> newsList) {
        List<PostVO> list = new ArrayList<>();
        if (newsList == null) {
            return list;
        }
        for (cn.kmbeast.pojo.vo.NewsVO n : newsList) {
            PostVO vo = toPostVO(n);
            if (vo != null) {
                list.add(vo);
            }
        }
        return list;
    }

    private Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double toDouble(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
