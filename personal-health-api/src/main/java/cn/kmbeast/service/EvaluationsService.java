package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.base.QueryDto;
import cn.kmbeast.pojo.dto.query.extend.EvaluationsQueryDto;
import cn.kmbeast.pojo.entity.Evaluations;

import java.util.List;

/**
 * 评论服务接口
 */
public interface EvaluationsService {

    Result<Object> insert(Evaluations evaluations);

    Result<Object> list(Integer contentId, String contentType);

    Result<Object> query(EvaluationsQueryDto evaluationsQueryDto);

    Result<Object> batchDelete(List<Integer> ids);

    Result<String> delete(Integer id);

    Result<Void> update(Evaluations evaluations);

    /**
     * 点赞 / 取消点赞评论（2026-10-10 新增）。
     *
     * <p>点赞与评论作者无关，任何登录用户都可对任意评论操作；
     * 同一用户重复调用即取消。返回 {@code {upvoted, likeCount}}。
     */
    Result<Object> upvote(Integer id);

}
