package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.PostQueryDto;
import cn.kmbeast.pojo.entity.Post;
import cn.kmbeast.pojo.entity.PostReply;
import cn.kmbeast.pojo.entity.PostReport;
import cn.kmbeast.pojo.vo.PostVO;
import cn.kmbeast.pojo.vo.PostReplyVO;

import java.util.List;

public interface PostService {

    Result<Void> save(Post post);

    Result<Void> batchDelete(List<Long> ids);

    Result<Void> update(Post post);

    Result<List<PostVO>> query(PostQueryDto queryDto);

    Result<PostVO> getById(Integer id);

    // 点赞
    Result<Void> like(Integer userId, Integer postId);

    Result<Void> unlike(Integer userId, Integer postId);

    // 收藏
    Result<Void> favorite(Integer userId, Integer postId);

    Result<Void> unfavorite(Integer userId, Integer postId);

    // 回复
    Result<Void> saveReply(PostReply postReply);

    Result<List<PostReplyVO>> getReplies(Integer postId);

    // 关注
    Result<Void> follow(Integer followerId, Integer followeeId);

    Result<Void> unfollow(Integer followerId, Integer followeeId);

    Result<Boolean> isFollowing(Integer followerId, Integer followeeId);

    // 热榜
    Result<List<PostVO>> getHotList(Integer limit);

    // 搜索
    Result<List<PostVO>> search(String keyword);

    // 举报
    Result<Void> report(PostReport postReport);

    Result<List<PostReport>> getPendingReports();

    Result<Void> handleReport(Integer id, Integer status);
}
