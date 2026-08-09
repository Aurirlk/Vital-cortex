package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.PostFavoriteMapper;
import cn.kmbeast.mapper.PostLikeMapper;
import cn.kmbeast.mapper.PostMapper;
import cn.kmbeast.mapper.PostReplyMapper;
import cn.kmbeast.mapper.PostReportMapper;
import cn.kmbeast.mapper.UserFollowMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.PostQueryDto;
import cn.kmbeast.pojo.entity.Post;
import cn.kmbeast.pojo.entity.PostFavorite;
import cn.kmbeast.pojo.entity.PostLike;
import cn.kmbeast.pojo.entity.PostReply;
import cn.kmbeast.pojo.entity.PostReport;
import cn.kmbeast.pojo.entity.UserFollow;
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
 * 社区帖子服务实现（PostServiceImpl 重建）。
 *
 * <p>背景：PostService 接口长期存在但实现类缺失（历史遗留），导致启动时
 * {@code A component required a bean of type 'PostService'} 报错。
 * 本实现补齐接口全部 20 个方法：发帖/改删/查询/详情 + 点赞/收藏/回复/关注 +
 * 举报处理 + 热门/搜索，全部基于既有 Mapper，无新增表结构。
 */
@Slf4j
@Service
public class PostServiceImpl implements PostService {

    @Resource
    private PostMapper postMapper;

    @Resource
    private PostReplyMapper postReplyMapper;

    @Resource
    private PostLikeMapper postLikeMapper;

    @Resource
    private PostFavoriteMapper postFavoriteMapper;

    @Resource
    private PostReportMapper postReportMapper;

    @Resource
    private UserFollowMapper userFollowMapper;

    @Override
    public Result<Void> save(Post post) {
        if (post.getUserId() == null) {
            post.setUserId(LocalThreadHolder.getUserId());
        }
        if (post.getCreateTime() == null) {
            post.setCreateTime(LocalDateTime.now());
        }
        if (post.getStatus() == null) {
            post.setStatus(1);
        }
        postMapper.save(post);
        return ApiResult.success();
    }

    @Override
    public Result<Void> batchDelete(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            postMapper.batchDelete(ids);
        }
        return ApiResult.success();
    }

    @Override
    public Result<Void> update(Post post) {
        post.setUpdateTime(LocalDateTime.now());
        postMapper.update(post);
        return ApiResult.success();
    }

    @Override
    public Result<List<PostVO>> query(PostQueryDto queryDto) {
        List<PostVO> list = postMapper.query(queryDto);
        fillLikeFavoriteState(list);
        return ApiResult.success(list);
    }

    @Override
    public Result<PostVO> getById(Integer id) {
        PostVO vo = postMapper.getById(id);
        if (vo == null) {
            return ApiResult.error("帖子不存在");
        }
        // 浏览量 +1（详情页访问）
        postMapper.incrementViewCount(id);
        // 填充当前用户是否已点赞/收藏
        Integer userId = LocalThreadHolder.getUserId();
        if (userId != null) {
            vo.setLiked(postLikeMapper.getByUserAndPost(userId, id) != null);
            vo.setFavorited(postFavoriteMapper.getByUserAndPost(userId, id) != null);
        }
        return ApiResult.success(vo);
    }

    @Override
    public Result<Void> like(Integer userId, Integer postId) {
        if (postLikeMapper.getByUserAndPost(userId, postId) != null) {
            return ApiResult.success(); // 幂等：已点赞直接成功
        }
        PostLike like = new PostLike();
        like.setUserId(userId);
        like.setPostId(postId);
        like.setCreateTime(LocalDateTime.now());
        postLikeMapper.save(like);
        postMapper.incrementLikeCount(postId);
        return ApiResult.success();
    }

    @Override
    public Result<Void> unlike(Integer userId, Integer postId) {
        postLikeMapper.delete(userId, postId);
        postMapper.decrementLikeCount(postId);
        return ApiResult.success();
    }

    @Override
    public Result<Void> favorite(Integer userId, Integer postId) {
        if (postFavoriteMapper.getByUserAndPost(userId, postId) != null) {
            return ApiResult.success(); // 幂等：已收藏直接成功
        }
        PostFavorite favorite = new PostFavorite();
        favorite.setUserId(userId);
        favorite.setPostId(postId);
        favorite.setCreateTime(LocalDateTime.now());
        postFavoriteMapper.save(favorite);
        postMapper.incrementFavoriteCount(postId);
        return ApiResult.success();
    }

    @Override
    public Result<Void> unfavorite(Integer userId, Integer postId) {
        postFavoriteMapper.delete(userId, postId);
        postMapper.decrementFavoriteCount(postId);
        return ApiResult.success();
    }

    @Override
    public Result<Void> saveReply(PostReply postReply) {
        if (postReply.getUserId() == null) {
            postReply.setUserId(LocalThreadHolder.getUserId());
        }
        if (postReply.getCreateTime() == null) {
            postReply.setCreateTime(LocalDateTime.now());
        }
        postReplyMapper.save(postReply);
        if (postReply.getPostId() != null) {
            postMapper.incrementCommentCount(postReply.getPostId());
        }
        return ApiResult.success();
    }

    @Override
    public Result<List<PostReplyVO>> getReplies(Integer postId) {
        return ApiResult.success(postReplyMapper.queryByPostId(postId));
    }

    @Override
    public Result<Void> follow(Integer followerId, Integer followeeId) {
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

    @Override
    public Result<List<PostVO>> getHotList(Integer limit) {
        List<Map<String, Object>> rows = postMapper.topByHotScore(
                limit != null && limit > 0 ? limit : 10);
        List<PostVO> list = new ArrayList<>();
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
        PostQueryDto dto = new PostQueryDto();
        dto.setKeyword(keyword);
        List<PostVO> list = postMapper.query(dto);
        fillLikeFavoriteState(list);
        return ApiResult.success(list);
    }

    @Override
    public Result<Void> report(PostReport postReport) {
        if (postReport.getUserId() == null) {
            postReport.setUserId(LocalThreadHolder.getUserId());
        }
        if (postReport.getStatus() == null) {
            postReport.setStatus(0); // 待处理
        }
        if (postReport.getCreateTime() == null) {
            postReport.setCreateTime(LocalDateTime.now());
        }
        postReportMapper.save(postReport);
        return ApiResult.success();
    }

    @Override
    public Result<List<PostReport>> getPendingReports() {
        return ApiResult.success(postReportMapper.queryPending());
    }

    @Override
    public Result<Void> handleReport(Integer id, Integer status) {
        postReportMapper.updateStatus(id, status);
        return ApiResult.success();
    }

    /**
     * 批量填充列表的 liked/favorited 状态（当前用户已登录时）。
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
            vo.setLiked(postLikeMapper.getByUserAndPost(userId, vo.getId()) != null);
            vo.setFavorited(postFavoriteMapper.getByUserAndPost(userId, vo.getId()) != null);
        }
    }

    /** Map 值安全转 Integer（MySQL 聚合/别名可能返回 Long/BigDecimal） */
    private Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        return ((Number) v).intValue();
    }

    /** Map 值安全转 Double */
    private Double toDouble(Object v) {
        if (v == null) {
            return null;
        }
        return ((Number) v).doubleValue();
    }
}
