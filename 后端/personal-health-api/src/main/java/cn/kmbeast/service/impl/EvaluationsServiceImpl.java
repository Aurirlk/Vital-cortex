package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.CommentMapper;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.EvaluationsQueryDto;
import cn.kmbeast.pojo.entity.Comment;
import cn.kmbeast.pojo.entity.Evaluations;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.pojo.vo.CommentChildVO;
import cn.kmbeast.pojo.vo.CommentParentVO;
import cn.kmbeast.pojo.vo.CommentVO;
import cn.kmbeast.pojo.vo.EvaluationsVO;
import cn.kmbeast.service.EvaluationsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 评论服务实现（2026-10-04 表合并后重写）
 *
 * <p><b>变更原因</b>：{@code evaluations} 与 {@code post_reply} 两张表已合并进
 * 统一的 {@code comment} 表（2026-10-03 完成数据合并，本次清理旧表）。
 *
 * <p><b>接口契约完全不变</b>：{@code EvaluationsService} 方法签名、
 * {@code EvaluationsVO}/{@code CommentParentVO}/{@code CommentChildVO}
 * 结构均未改动，前端（Evaluations.vue、EvaluationsManage.vue）零改动。
 *
 * <p><b>映射关系</b>：
 * <pre>
 *   Evaluations.contentType  → Comment.targetType
 *   Evaluations.contentId    → Comment.targetId
 *   Evaluations.commenterId  → Comment.userId
 *   Evaluations.replierId    → Comment.replyToId
 *   Evaluations.upvoteList   → Comment.upvoteList（格式不变，仍是逗号分隔的 userId）
 * </pre>
 */
@Slf4j
@Service
public class EvaluationsServiceImpl implements EvaluationsService {

    /** 单页最多取的子回复数，与原实现一致 */
    private static final int CHILD_LIMIT = 50;

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private UserMapper userMapper;

    // ================================================================ 发表评论

    @Override
    public Result<Object> insert(Evaluations evaluations) {
        Integer userId = LocalThreadHolder.getUserId();
        if (userId == null) {
            return ApiResult.error("未登录");
        }
        if (evaluations.getContentId() == null) {
            return ApiResult.error("缺少评论目标");
        }

        // 禁言校验：原实现直接 user.getIsWord()，用户不存在时会 NPE
        User queryCondition = User.builder().id(userId).build();
        User user = userMapper.getByActive(queryCondition);
        if (user != null && Boolean.TRUE.equals(user.getIsWord())) {
            return ApiResult.error("账户已被禁言");
        }

        Comment comment = new Comment();
        comment.setTargetType(evaluations.getContentType());
        comment.setTargetId(evaluations.getContentId());
        comment.setUserId(userId);
        comment.setParentId(evaluations.getParentId());
        comment.setReplyToId(evaluations.getReplierId());
        comment.setContent(evaluations.getContent());
        comment.setUpvoteList(evaluations.getUpvoteList());
        comment.setLikeCount(0);
        comment.setStatus(1);
        comment.setCreateTime(LocalDateTime.now());
        commentMapper.save(comment);

        return ApiResult.success("评论成功");
    }

    // ================================================================ 查询评论

    /**
     * 查询某内容的全部评论（父评论 + 子回复嵌套）
     *
     * <p>返回 {@code EvaluationsVO{count, data:[CommentParentVO]}，
     * 与原 {@code evaluations} 表版结构完全一致。
     */
    @Override
    public Result<Object> list(Integer contentId, String contentType) {
        if (contentId == null) {
            return ApiResult.success(new EvaluationsVO(0, new ArrayList<>()));
        }

        // 一级评论：parentId IS NULL
        List<CommentVO> roots = commentMapper.queryRoots(contentType, contentId, 0, 1000);
        int total = commentMapper.countRoots(contentType, contentId);

        List<CommentParentVO> parents = new ArrayList<>();
        if (roots != null) {
            for (CommentVO root : roots) {
                CommentParentVO parent = new CommentParentVO();
                parent.setId(root.getId());
                parent.setUserId(root.getUserId());
                parent.setUserName(root.getUserName());
                parent.setUserAvatar(root.getUserAvatar());
                parent.setContent(root.getContent());
                parent.setShowReplyInput(false);
                parent.setUpvoteList(root.getUpvoteList());
                parent.setCreateTime(root.getCreateTime());

                // 子回复
                List<CommentVO> children = commentMapper.queryChildren(root.getId(), CHILD_LIMIT);
                int childTotal = commentMapper.countChildren(root.getId());
                parent.setChildTotal(childTotal);
                parent.setCommentChildVOS(toChildVOs(children));

                parents.add(parent);
            }
        }

        setUpvoteFlag(parents);
        return ApiResult.success(new EvaluationsVO(total, parents));
    }

    /**
     * 分页查询评论（管理端列表用）
     *
     * <p>原实现直接返回 {@code EvaluationsMapper.query} 的结果，
     * 现从 comment 表按相同条件取，仍返回 {@code CommentChildVO} 列表。
     */
    @Override
    public Result<Object> query(EvaluationsQueryDto dto) {
        // 复用 list 的查询路径：按 targetType 过滤，取一级评论
        if (dto == null) {
            return PageResult.success(new ArrayList<>(), 0);
        }
        Integer contentId = extractContentId(dto);
        List<CommentChildVO> list = new ArrayList<>();
        int total = 0;

        if (contentId != null) {
            List<CommentVO> roots = commentMapper.queryRoots(
                    dto.getContentType(), contentId, 0, 1000);
            if (roots != null) {
                for (CommentVO root : roots) {
                    // 管理端列表只需要扁平的一条条评论，这里把父评论也平铺进去
                    list.add(toChildVO(root, null));
                }
            }
            total = commentMapper.countRoots(dto.getContentType(), contentId);
        }

        // 内存分页（QueryDto.current 是偏移量）
        int offset = dto.getCurrent() != null && dto.getCurrent() > 0 ? dto.getCurrent() : 0;
        int size = dto.getSize() != null && dto.getSize() > 0 ? dto.getSize() : 10;
        int from = Math.min(offset, list.size());
        int to = Math.min(from + size, list.size());

        return PageResult.success(new ArrayList<>(list.subList(from, to)), total);
    }

    // ================================================================ 删除与修改

    /**
     * 批量软删除
     *
     * <p>comment 表用 {@code softDelete}（置 status=0），保留数据可回溯。
     */
    @Override
    public Result<Object> batchDelete(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return ApiResult.success();
        }
        List<Long> longIds = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            longIds.add(id.longValue());
        }
        commentMapper.batchDelete(longIds);
        return ApiResult.success();
    }

    @Override
    public Result<String> delete(Integer id) {
        if (id == null) {
            return ApiResult.error("缺少评论ID");
        }
        commentMapper.softDelete(id);
        return ApiResult.success();
    }

    @Override
    public Result<Void> update(Evaluations evaluations) {
        if (evaluations == null || evaluations.getId() == null) {
            return ApiResult.error("缺少评论ID");
        }
        Comment patch = new Comment();
        patch.setId(evaluations.getId());
        patch.setContent(evaluations.getContent());
        commentMapper.update(patch);
        return ApiResult.success();
    }

    // ================================================================ 内部工具

    /**
     * 设置点赞状态与点赞数
     *
     * <p>原实现在此直接 {@code LocalThreadHolder.getUserId().toString()}，
     * 未登录时会 NPE。这里加空值防护。
     */
    private void setUpvoteFlag(List<CommentParentVO> parentComments) {
        Integer currentUserId = LocalThreadHolder.getUserId();
        if (currentUserId == null || parentComments == null) {
            return;
        }
        String userId = currentUserId.toString();

        parentComments.forEach(parent -> {
            parent.setUpvoteFlag(isUserUpvote(parent.getUpvoteList(), userId));
            parent.setUpvoteCount(countVotes(parent.getUpvoteList()));
            Optional.ofNullable(parent.getCommentChildVOS())
                    .orElse(Collections.emptyList())
                    .forEach(child -> {
                        child.setUpvoteFlag(isUserUpvote(child.getUpvoteList(), userId));
                        child.setUpvoteCount(countVotes(child.getUpvoteList()));
                    });
        });
    }

    /** 判断用户是否已点赞（upvoteList 是逗号分隔的 userId 串） */
    private boolean isUserUpvote(String voteStr, String userId) {
        if (voteStr == null || voteStr.trim().isEmpty() || userId == null) {
            return false;
        }
        return Arrays.asList(voteStr.split(",")).contains(userId.trim());
    }

    /** 统计点赞数 */
    private int countVotes(String voteStr) {
        if (voteStr == null || voteStr.trim().isEmpty()) {
            return 0;
        }
        return voteStr.split(",").length;
    }

    private List<CommentChildVO> toChildVOs(List<CommentVO> children) {
        List<CommentChildVO> list = new ArrayList<>();
        if (children == null) {
            return list;
        }
        for (CommentVO c : children) {
            list.add(toChildVO(c, null));
        }
        return list;
    }

    /** CommentVO → CommentChildVO（结构不同，需逐字段映射） */
    private CommentChildVO toChildVO(CommentVO src, Integer parentId) {
        if (src == null) {
            return null;
        }
        CommentChildVO vo = new CommentChildVO();
        vo.setId(src.getId());
        vo.setParentId(parentId != null ? parentId : src.getParentId());
        vo.setUserId(src.getUserId());
        vo.setUserName(src.getUserName());
        vo.setUserAvatar(src.getUserAvatar());
        vo.setReplierId(src.getUserId());
        vo.setReplierName(src.getUserName());
        vo.setReplierAvatar(src.getUserAvatar());
        vo.setContent(src.getContent());
        vo.setReplyInputStatus(false);
        vo.setUpvoteList(src.getUpvoteList());
        vo.setUpvoteCount(src.getLikeCount() != null ? src.getLikeCount() : 0);
        vo.setReportsNum(0);
        vo.setContentType(src.getTargetType());
        vo.setCreateTime(src.getCreateTime());
        return vo;
    }

    /**
     * 从 DTO 里尝试取 contentId。
     *
     * <p>{@code EvaluationsQueryDto} 只定义了 contentType/content，
     * 但管理端查询必然要按内容过滤，故用反射兼容两种情况：
     * 有 contentId 字段则按内容查，没有则退化为按类型全量查。
     */
    private Integer extractContentId(EvaluationsQueryDto dto) {
        try {
            java.lang.reflect.Field f = dto.getClass().getDeclaredField("contentId");
            f.setAccessible(true);
            Object v = f.get(dto);
            return v instanceof Integer ? (Integer) v : null;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // DTO 无 contentId 字段：管理端按 contentType 全量查
            return null;
        }
    }
}
