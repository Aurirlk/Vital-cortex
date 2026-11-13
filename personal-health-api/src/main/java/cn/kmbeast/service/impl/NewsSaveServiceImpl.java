package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.ContentInteractionMapper;
import cn.kmbeast.mapper.NewsMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.NewsSaveQueryDto;
import cn.kmbeast.pojo.entity.NewsSave;
import cn.kmbeast.pojo.vo.NewsSaveVO;
import cn.kmbeast.pojo.vo.NewsVO;
import cn.kmbeast.service.NewsSaveService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康资讯收藏业务逻辑实现（2026-10-04 表合并后重写）
 *
 * <p><b>变更原因</b>：原 {@code news_save} 表已并入统一交互表
 * {@code content_interaction}（action_type='FAVORITE'，target_type='NEWS'）。
 * 旧表与 {@code post_favorite} 结构完全相同，属重复建设。
 *
 * <p><b>接口契约保持不变</b>：Controller 路径、DTO、VO 均未改动，
 * 前端（NewsDetail / 我的收藏等页面）零改动。
 */
@Service
public class NewsSaveServiceImpl implements NewsSaveService {

    private static final String TARGET_TYPE = "NEWS";
    private static final String ACTION_FAVORITE = "FAVORITE";

    @Resource
    private ContentInteractionMapper interactionMapper;

    @Resource
    private NewsMapper newsMapper;

    /**
     * 收藏新增
     *
     * <p>底层走 {@code INSERT ... ON DUPLICATE KEY UPDATE}，依赖
     * {@code uk_interaction} 唯一键做幂等 —— 重复收藏不会产生第二行。
     */
    @Override
    public Result<Void> save(NewsSave newsSave) {
        Integer userId = newsSave.getUserId() != null
                ? newsSave.getUserId()
                : LocalThreadHolder.getUserId();
        if (userId == null || newsSave.getNewsId() == null) {
            return ApiResult.error("缺少用户或资讯参数");
        }
        interactionMapper.insert(TARGET_TYPE, newsSave.getNewsId(), userId, ACTION_FAVORITE, null, 1);
        return ApiResult.success();
    }

    /**
     * 取消收藏
     *
     * <p>注意：按「用户 + 资讯」删，而不是按传入的 id 列表 ——
     * 旧表 id 已随合并失效，前端传来的 id 指向的是 content_interaction 的主键。
     */
    @Override
    public Result<Void> batchDelete(List<Long> ids) {
        Integer userId = LocalThreadHolder.getUserId();
        if (userId == null) {
            return ApiResult.error("未登录");
        }
        if (ids == null || ids.isEmpty()) {
            return ApiResult.success();
        }
        for (Long newsId : ids) {
            interactionMapper.delete(TARGET_TYPE, newsId.intValue(), userId, ACTION_FAVORITE);
        }
        return ApiResult.success();
    }

    /**
     * 收藏查询
     *
     * <p>保持原返回契约：分页 + 每条含资讯标题，供「我的收藏」列表渲染。
     *
     * <p>注意 {@code QueryDto.current} 是<b>偏移量</b>（由 {@code @Pager} 注解从
     * 页码换算而来），不是页码。
     */
    @Override
    public Result<List<NewsSaveVO>> query(NewsSaveQueryDto queryDto) {
        Integer userId = queryDto.getUserId() != null
                ? queryDto.getUserId()
                : LocalThreadHolder.getUserId();

        if (userId == null) {
            return PageResult.success(new ArrayList<>(), 0);
        }

        // 1) 取该用户收藏的资讯 ID（倒序，limit 上限防拖库）
        List<Integer> newsIds = collectFavoritedNewsIds(userId, queryDto);
        if (newsIds.isEmpty()) {
            return PageResult.success(new ArrayList<>(), 0);
        }

        // 2) 批量取资讯详情，一次 IN 查询避免 N+1
        List<NewsVO> newsList = newsMapper.selectByIds(newsIds);
        Map<Integer, NewsVO> newsMap = new HashMap<>();
        for (NewsVO n : newsList) {
            newsMap.put(n.getId(), n);
        }

        // 3) 内存分页（current 为偏移量）
        int offset = queryDto.getCurrent() != null && queryDto.getCurrent() > 0
                ? queryDto.getCurrent() : 0;
        int pageSize = queryDto.getSize() != null && queryDto.getSize() > 0
                ? queryDto.getSize() : 10;

        int from = Math.min(offset, newsIds.size());
        int to = Math.min(from + pageSize, newsIds.size());

        List<NewsSaveVO> result = new ArrayList<>(to - from);
        for (Integer newsId : newsIds.subList(from, to)) {
            NewsSaveVO vo = new NewsSaveVO();
            vo.setId(newsId);
            vo.setUserId(userId);
            vo.setNewsId(newsId);
            NewsVO n = newsMap.get(newsId);
            if (n != null) {
                vo.setName(n.getTitle() != null ? n.getTitle() : n.getName());
                vo.setContent(n.getContent());
                vo.setCover(n.getCover());
                vo.setNewsCreateTime(n.getCreateTime());
                vo.setTagName(n.getTagName());
            }
            result.add(vo);
        }
        return PageResult.success(result, newsIds.size());
    }

    /**
     * 收藏/取消收藏切换
     *
     * <p>先查再增删，语义比「直接调 delete」更明确：
     * 已收藏 → 取消；未收藏 → 收藏。
     */
    @Override
    public Result<Void> operation(NewsSave newsSave) {
        Integer userId = LocalThreadHolder.getUserId();
        if (userId == null) {
            return ApiResult.error("未登录");
        }
        if (newsSave.getNewsId() == null) {
            return ApiResult.error("缺少资讯ID");
        }

        Integer cnt = interactionMapper.countInteraction(
                TARGET_TYPE, newsSave.getNewsId(), userId, ACTION_FAVORITE);

        if (cnt != null && cnt > 0) {
            interactionMapper.delete(TARGET_TYPE, newsSave.getNewsId(), userId, ACTION_FAVORITE);
        } else {
            interactionMapper.insert(TARGET_TYPE, newsSave.getNewsId(), userId, ACTION_FAVORITE, null, 1);
        }
        return ApiResult.success();
    }

    /**
     * 组装候选资讯 ID 列表。
     *
     * <p>取当前用户收藏的全部资讯；若 DTO 带了 newsId 则只查那一条。
     */
    private List<Integer> collectFavoritedNewsIds(Integer userId, NewsSaveQueryDto queryDto) {
        List<Integer> ids = new ArrayList<>();
        if (queryDto.getNewsId() != null) {
            Integer cnt = interactionMapper.countInteraction(
                    TARGET_TYPE, queryDto.getNewsId(), userId, ACTION_FAVORITE);
            if (cnt != null && cnt > 0) {
                ids.add(queryDto.getNewsId());
            }
            return ids;
        }
        // 全量：借助 countByTarget 逐条排查代价太高，
        // 收藏量级小（个人维度），此处按分页上限兜底取前 200 条
        ids.addAll(interactionMapper.queryFavoritedNewsIds(userId, 200));
        return ids;
    }
}
