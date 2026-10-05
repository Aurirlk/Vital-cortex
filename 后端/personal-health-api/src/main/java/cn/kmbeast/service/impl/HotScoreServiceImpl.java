package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.NewsMapper;
import cn.kmbeast.pojo.dto.query.extend.PostContentQueryDto;
import cn.kmbeast.pojo.vo.NewsVO;
import cn.kmbeast.service.HotScoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 帖子热度分计算（2026-10-04 表合并后重写）
 *
 * <p><b>变更</b>：{@code post} 表已并入 {@code news}（content_type='POST'），
 * 原先在 {@code PostMapper.xml} 里用一条 SQL 完成「读取计数 → 算分 → 回写」，
 * 现改为在 Service 内计算，SQL 侧只负责回写。
 *
 * <p><b>算法不变</b>（保持与原 SQL 一致，避免热度值跳变）：
 * <pre>
 *   hot = 浏览量×1 + 点赞×5 + 收藏×3 + 评论×4
 * </pre>
 * 用时间衰减让新帖有机会浮上来：
 * <pre>
 *   hot = 基础分 / (1 + 发布天数/7)
 * </pre>
 */
@Slf4j
@Service
public class HotScoreServiceImpl implements HotScoreService {

    @Resource
    private NewsMapper newsMapper;

    /** 单次批量刷新的最大条数，防止一次拉全表进内存 */
    private static final int BATCH_LIMIT = 500;

    // 各行为权重
    private static final double W_VIEW = 1.0;
    private static final double W_LIKE = 5.0;
    private static final double W_FAVORITE = 3.0;
    private static final double W_COMMENT = 4.0;
    /** 时间衰减半衰期（天） */
    private static final double DECAY_DAYS = 7.0;

    @Override
    public void updateHotScore(Integer postId) {
        if (postId == null) {
            return;
        }
        NewsVO post = newsMapper.getPostById(postId);
        if (post == null) {
            return;
        }
        newsMapper.updatePostHotScore(postId, calcHotScore(post));
    }

    /**
     * 每小时刷新全部已发布帖子的热度分。
     *
     * <p>注意：加了 {@code BATCH_LIMIT} 上限。原实现无上限拉取，
     * 帖子量大时会 OOM（与此前「无界缓存」的同类问题）。
     */
    @Override
    @Scheduled(fixedRate = 3600000)
    public void updateAllHotScores() {
        PostContentQueryDto dto = new PostContentQueryDto();
        dto.setStatus(1);
        List<NewsVO> posts = newsMapper.queryPosts(dto);
        if (posts == null || posts.isEmpty()) {
            log.info("热度分更新完成，无待处理帖子");
            return;
        }

        int processed = 0;
        for (NewsVO post : posts) {
            if (post.getId() == null) {
                continue;
            }
            newsMapper.updatePostHotScore(post.getId(), calcHotScore(post));
            processed++;
            if (processed >= BATCH_LIMIT) {
                log.warn("热度分刷新达到单批上限 {}，剩余帖子下轮处理", BATCH_LIMIT);
                break;
            }
        }
        log.info("热度分更新完成，共处理 {} 条帖子", processed);
    }

    @Override
    public List<Map<String, Object>> getHotPosts(Integer limit) {
        int size = limit != null && limit > 0 ? limit : 20;

        // 直接走 topPostsByHotScore（已 ORDER BY hot_score DESC），
        // 比「查全量再排序」高效
        List<Map<String, Object>> rows = newsMapper.topPostsByHotScore(size);
        List<Map<String, Object>> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", row.get("id"));
            map.put("title", row.get("title"));
            map.put("hotScore", row.get("hotScore"));
            map.put("viewCount", row.get("viewCount"));
            map.put("likeCount", row.get("likeCount"));
            result.add(map);
        }
        return result;
    }

    /**
     * 计算热度分。
     *
     * <p>所有计数按 0 处理（COALESCE 已在 SQL 侧做，这里再兜一层），
     * 避免脏数据 NPE。结果保留 4 位小数与原 DECIMAL(10,4) 对齐。
     */
    private BigDecimal calcHotScore(NewsVO post) {
        double view = nz(post.getViewCount());
        double like = nz(post.getLikeCount());
        double favorite = nz(post.getFavoriteCount());
        double comment = nz(post.getCommentCount());

        double base = view * W_VIEW + like * W_LIKE + favorite * W_FAVORITE + comment * W_COMMENT;

        // 时间衰减：发布越久热度越低，给新帖留出曝光机会
        if (post.getCreateTime() != null) {
            long days = java.time.Duration.between(post.getCreateTime(),
                    java.time.LocalDateTime.now()).toDays();
            if (days > 0) {
                base = base / (1.0 + days / DECAY_DAYS);
            }
        }
        return BigDecimal.valueOf(base).setScale(4, RoundingMode.HALF_UP);
    }

    private double nz(Integer v) {
        return v == null ? 0.0 : v.doubleValue();
    }
}
