package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.CommentMapper;
import cn.kmbeast.mapper.ContentInteractionMapper;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.EvaluationsQueryDto;
import cn.kmbeast.pojo.entity.Comment;
import cn.kmbeast.pojo.entity.Evaluations;
import cn.kmbeast.pojo.entity.Message;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.pojo.vo.CommentChildVO;
import cn.kmbeast.pojo.vo.CommentParentVO;
import cn.kmbeast.pojo.vo.CommentVO;
import cn.kmbeast.pojo.vo.EvaluationsVO;
import cn.kmbeast.service.EvaluationsService;
import cn.kmbeast.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
 *
 * <p><b>2026-10-10 关于 upvoteList 的说明</b>：上面这条映射已不再是点赞的真实来源。
 * {@code comment.upvote_list} 是个逗号串，用它记「谁赞过」要么整体覆盖（并发丢赞）
 * 要么整串读取（越赞越长）。点赞关系现统一落在 {@code content_interaction}
 * （targetType=COMMENT、actionType=LIKE），靠它的唯一键保证幂等；
 * 回显时批量查一次「我赞过哪些」。该列保留仅为兼容历史数据，不再读也不再写。
 */
@Slf4j
@Service
public class EvaluationsServiceImpl implements EvaluationsService {

    /** 单页最多取的子回复数，与原实现一致 */
    private static final int CHILD_LIMIT = 50;

    /** 评论点赞在统一交互表里的两个枚举值 */
    private static final String INTERACTION_TARGET_COMMENT = "COMMENT";
    private static final String INTERACTION_LIKE = "LIKE";

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ContentInteractionMapper interactionMapper;

    @Resource
    private MessageService messageService;

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
        // 2026-10-10 修复（点赞数可伪造）：原先直接把请求体里的 upvoteList 落库，
        // 而点赞数与「是否已赞」都由该字段在服务端推导（countVotes / isUserUpvote）——
        // 攻击者发评论时附带 upvoteList="1,2,...,1000" 即可凭空造出上千赞。
        // 评论的点赞本应随用户真实点赞逐步累积，新建评论一律从空开始，不采信前端。
        comment.setUpvoteList(null);
        comment.setLikeCount(0);
        comment.setStatus(1);
        comment.setCreateTime(LocalDateTime.now());
        commentMapper.save(comment);

        // 2026-10-10 补：回复别人的评论时给对方发互动通知。
        // replyToId 是「被回复的人」（由前端 replierId 传入），parentId 非空即为回复。
        if (comment.getParentId() != null) {
            notifyInteraction(comment.getReplyToId(), userId,
                    NotificationKind.REPLY, comment.getId());
        }

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
                // 点赞数取自 comment.like_count（由 upvote() 原子自增/自减维护），
                // 不再由 upvote_list 的逗号个数推导
                parent.setUpvoteCount(root.getLikeCount() != null ? root.getLikeCount() : 0);
                parent.setCreateTime(root.getCreateTime());

                // 子回复
                List<CommentVO> children = commentMapper.queryChildren(root.getId(), CHILD_LIMIT);
                int childTotal = commentMapper.countChildren(root.getId());
                parent.setChildTotal(childTotal);
                parent.setCommentChildVOS(toChildVOs(children));

                parents.add(parent);
            }
        }

        applyUpvoteState(parents);
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
        // 2026-10-10 修复（IDOR）：原先直接按 id 软删，任何登录用户传一个 id
        // 就能删掉他人评论。现要求评论作者本人或管理员。
        CommentVO comment = commentMapper.getById(id);
        if (comment == null) {
            return ApiResult.error("评论不存在");
        }
        if (!LocalThreadHolder.isSelfOrAdmin(comment.getUserId())) {
            log.warn("越权删除评论被拒绝: commentId={}, ownerId={}, operatorId={}",
                    id, comment.getUserId(), LocalThreadHolder.getUserId());
            return ApiResult.error("无权删除该评论");
        }
        commentMapper.softDelete(id);
        return ApiResult.success();
    }

    @Override
    public Result<Void> update(Evaluations evaluations) {
        if (evaluations == null || evaluations.getId() == null) {
            return ApiResult.error("缺少评论ID");
        }
        // 2026-10-10 修复（IDOR）：原先按 id 直接改 content，任何登录用户可篡改他人评论。
        CommentVO comment = commentMapper.getById(evaluations.getId());
        if (comment == null) {
            return ApiResult.error("评论不存在");
        }
        if (!LocalThreadHolder.isSelfOrAdmin(comment.getUserId())) {
            log.warn("越权修改评论被拒绝: commentId={}, ownerId={}, operatorId={}",
                    evaluations.getId(), comment.getUserId(), LocalThreadHolder.getUserId());
            return ApiResult.error("无权修改该评论");
        }
        Comment patch = new Comment();
        patch.setId(evaluations.getId());
        patch.setContent(evaluations.getContent());
        commentMapper.update(patch);
        return ApiResult.success();
    }

    // ================================================================ 评论点赞

    /**
     * 点赞 / 取消点赞（同一接口来回切换）—— 2026-10-10 新增。
     *
     * <p><b>为什么必须新增这个接口</b>：此前前端点「顶」走的是
     * {@code PUT /evaluations/update} 并附带 {@code upvoteList}，但后端 {@code update}
     * 只写 {@code content}/{@code status}（{@code CommentMapper.update} 的
     * {@code <set>} 里根本没有 upvote_list），并且它还要求「评论作者本人或管理员」。
     * 两重阻断叠加的结果是：<b>给别人的评论点赞必然失败，给自己的评论点赞也只落进
     * 一个没人读的字段</b>。而失败时前端连提示都没有 —— 业务错误码走的是 HTTP 200，
     * 前端只在 {@code .catch} 里处理真正的 HTTP 错误。CommentMapper 里那三个
     * {@code increaseLikeCount}/{@code decreaseLikeCount}/{@code updateUpvoteList}
     * 因此从未被调用过，是彻底的死 SQL。
     *
     * <p><b>点赞与「评论作者是谁」无关</b>，所以这里只要求登录（{@code @Protector}），
     * 不做作者校验 —— 那正是原实现错的地方。
     *
     * <p><b>幂等性来源</b>：{@code content_interaction} 上的
     * {@code uk_interaction(target_type, target_id, user_id, action_type)} 唯一键。
     * 加赞时只有 {@code insert} 真正插入了行（影响行数 &gt; 0，重复插入因
     * ON DUPLICATE KEY UPDATE 且值未变化而返回 0）才自增计数；取消时只有
     * {@code delete} 真的删掉了行才自减，避免并发双击把计数刷歪。
     *
     * @return 切换后的状态：{@code {upvoted: 布尔, likeCount: 数字}}
     */
    @Override
    public Result<Object> upvote(Integer id) {
        Integer userId = LocalThreadHolder.getUserId();
        if (userId == null) {
            return ApiResult.error("未登录");
        }
        if (id == null) {
            return ApiResult.error("缺少评论ID");
        }
        CommentVO comment = commentMapper.getById(id);
        // 已软删的评论不给点赞：否则删掉的内容仍会被「顶」上热门
        if (comment == null || !Integer.valueOf(1).equals(comment.getStatus())) {
            return ApiResult.error("评论不存在");
        }

        int existing = interactionMapper.countInteraction(
                INTERACTION_TARGET_COMMENT, id, userId, INTERACTION_LIKE);
        boolean upvoted;
        if (existing > 0) {
            int deleted = interactionMapper.delete(
                    INTERACTION_TARGET_COMMENT, id, userId, INTERACTION_LIKE);
            if (deleted > 0) {
                commentMapper.decreaseLikeCount(id);
            }
            upvoted = false;
        } else {
            int inserted = interactionMapper.insert(
                    INTERACTION_TARGET_COMMENT, id, userId, INTERACTION_LIKE, null, 1);
            if (inserted > 0) {
                commentMapper.increaseLikeCount(id);
                // 2026-10-10 补：给评论作者发「被点赞」通知。
                // 只在真的新增了点赞时发 —— 取消点赞（上面的 delete 分支）不发，
                // 重复点赞因唯一键返回 0 行也不会重复发。
                notifyInteraction(comment.getUserId(), userId,
                        NotificationKind.UPVOTE, id);
            }
            upvoted = true;
        }

        // 回读真实计数而非本地加减：并发下本地推算的数和库里会对不上
        CommentVO latest = commentMapper.getById(id);
        int likeCount = latest != null && latest.getLikeCount() != null ? latest.getLikeCount() : 0;
        Map<String, Object> data = new HashMap<>(4);
        data.put("upvoted", upvoted);
        data.put("likeCount", likeCount);
        return ApiResult.success(data);
    }

    // ================================================================ 内部工具

    /** 互动通知的两类，对应 MessageType.EVALUATIONS_BY_REPLY / EVALUATIONS_BY_UPVOTE */
    private enum NotificationKind {
        REPLY, UPVOTE
    }

    /**
     * 发送一条互动通知（2026-10-10 新增）。
     *
     * <p>补这个调用点的原因：{@code MessageService} 里的
     * {@code evaluationsReplySave} / {@code evaluationsUpvoteSave} 两个方法
     * 写好了却<b>零调用</b> —— 消息中心把「评论」「点赞」两类都列给了前端
     * （{@code /message/type} 返回 {@code MessageType.values()}），
     * 但这两类永远不会有任何内容，用户看到的评论回复/点赞没有任何提示。
     *
     * <p>三条纪律：
     * <ul>
     *   <li>自己的评论自己回复/点赞不发通知，否则会出现「你回复了你」；</li>
     *   <li>通知失败绝不能让评论或点赞失败 —— 通知是附带的，外面整段兜异常；</li>
     *   <li>内容文案由 {@code MessageServiceImpl} 统一填，
     *       这里只传收件人/发件人/关联评论，避免同一种通知两种措辞。</li>
     * </ul>
     *
     * @param receiverId 收件人（评论作者 / 被回复者）
     * @param senderId   发件人（当前操作者）
     * @param kind       互动类型
     * @param commentId  关联的评论ID，落进 notification.related_id 供前端跳转
     */
    private void notifyInteraction(Integer receiverId, Integer senderId,
                                   NotificationKind kind, Integer commentId) {
        if (receiverId == null || Objects.equals(receiverId, senderId)) {
            return;
        }
        try {
            Message message = new Message();
            message.setReceiverId(receiverId);
            message.setSenderId(senderId);
            message.setContentId(commentId);
            if (kind == NotificationKind.REPLY) {
                messageService.evaluationsReplySave(message);
            } else {
                messageService.evaluationsUpvoteSave(message);
            }
        } catch (RuntimeException e) {
            log.warn("互动通知发送失败，不影响主流程: kind={}, receiverId={}, senderId={}, commentId={}",
                    kind, receiverId, senderId, commentId, e);
        }
    }

    /**
     * 回显「当前用户赞过哪些评论」。
     *
     * <p>2026-10-10 重写：原先从 {@code upvote_list} 逗号串里找自己的 userId
     * （{@code isUserUpvote}），而那个字段根本不会有数据写入；点赞数也由该串长度
     * 推导，因此列表里永远是 0 赞。现改为一次批量查 {@code content_interaction}，
     * 避免逐条评论各查一次（N+1）。
     */
    private void applyUpvoteState(List<CommentParentVO> parentComments) {
        if (parentComments == null || parentComments.isEmpty()) {
            return;
        }
        Integer currentUserId = LocalThreadHolder.getUserId();
        Set<Integer> upvotedIds = currentUserId == null
                ? Collections.emptySet()
                : queryUpvotedCommentIds(currentUserId, parentComments);

        parentComments.forEach(parent -> {
            parent.setUpvoteFlag(upvotedIds.contains(parent.getId()));
            Optional.ofNullable(parent.getCommentChildVOS())
                    .orElse(Collections.emptyList())
                    .forEach(child -> child.setUpvoteFlag(upvotedIds.contains(child.getId())));
        });
    }

    /** 收集父子评论 ID 后一次查回「已赞集合」 */
    private Set<Integer> queryUpvotedCommentIds(Integer userId, List<CommentParentVO> parents) {
        List<Integer> ids = new ArrayList<>();
        for (CommentParentVO parent : parents) {
            if (parent.getId() != null) {
                ids.add(parent.getId());
            }
            for (CommentChildVO child : Optional.ofNullable(parent.getCommentChildVOS())
                    .orElse(Collections.emptyList())) {
                if (child.getId() != null) {
                    ids.add(child.getId());
                }
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }
        List<Integer> upvoted = interactionMapper.queryUpvotedTargetIds(
                userId, INTERACTION_TARGET_COMMENT, ids);
        return upvoted == null ? Collections.emptySet() : new HashSet<>(upvoted);
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
