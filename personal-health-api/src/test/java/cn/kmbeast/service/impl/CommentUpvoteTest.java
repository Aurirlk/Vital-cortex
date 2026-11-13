package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.CommentMapper;
import cn.kmbeast.mapper.ContentInteractionMapper;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.Evaluations;
import cn.kmbeast.pojo.entity.Message;
import cn.kmbeast.pojo.vo.CommentChildVO;
import cn.kmbeast.pojo.vo.CommentParentVO;
import cn.kmbeast.pojo.vo.CommentVO;
import cn.kmbeast.pojo.vo.EvaluationsVO;
import cn.kmbeast.service.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 评论点赞测试 —— 2026-10-10 新增。
 *
 * <p>这条链路此前是<b>纯伪实现</b>：前端点「顶」发的是
 * {@code PUT /evaluations/update {id, upvoteList}}，而后端 update 只写
 * content/status，且要求「作者本人或管理员」。于是：
 * <ul>
 *   <li>给<b>别人</b>的评论点赞 → 400「无权修改该评论」，前端还不弹提示
 *       （业务错误码是 HTTP 200，前端只在 .catch 里处理真 HTTP 错误）；</li>
 *   <li>给<b>自己</b>的评论点赞 → 返回成功，但 upvoteList 从未落库，刷新即丢；</li>
 *   <li>{@code CommentMapper} 里的 increaseLikeCount / decreaseLikeCount /
 *       updateUpvoteList 三个方法全仓零调用者。</li>
 * </ul>
 * 所以这里的每一条用例都在钉「真的落库了吗」，而不是「接口返回 200 了吗」——
 * 后者正是原实现骗过所有人的地方。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("评论点赞（落库与幂等）")
class CommentUpvoteTest {

    private static final Integer ME = 1001;
    private static final Integer AUTHOR = 1002;
    private static final Integer COMMENT_ID = 5001;
    private static final String TARGET_COMMENT = "COMMENT";
    private static final String ACTION_LIKE = "LIKE";

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ContentInteractionMapper interactionMapper;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private EvaluationsServiceImpl service;

    @AfterEach
    void tearDown() {
        LocalThreadHolder.clear();
    }

    private static Integer okCode() {
        return ApiResult.success().getCode();
    }

    private CommentVO comment(Integer id, Integer ownerId, int status, int likeCount) {
        CommentVO vo = new CommentVO();
        vo.setId(id);
        vo.setUserId(ownerId);
        vo.setStatus(status);
        vo.setLikeCount(likeCount);
        return vo;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(Result<Object> result) {
        return (Map<String, Object>) result.getData();
    }

    // ==================== 加赞 ====================

    @Test
    @DisplayName("首次点赞：写入交互记录并自增计数，返回 upvoted=true 与最新赞数")
    void shouldInsertInteractionAndIncreaseCount() {
        LocalThreadHolder.setUserId(ME, 2);
        when(commentMapper.getById(COMMENT_ID))
                .thenReturn(comment(COMMENT_ID, AUTHOR, 1, 0))   // 首次读取
                .thenReturn(comment(COMMENT_ID, AUTHOR, 1, 1));  // 更新后回读
        when(interactionMapper.countInteraction(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE))
                .thenReturn(0);
        when(interactionMapper.insert(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE, null, 1))
                .thenReturn(1);

        Result<Object> r = service.upvote(COMMENT_ID);

        assertEquals(okCode(), r.getCode());
        verify(interactionMapper).insert(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE, null, 1);
        verify(commentMapper).increaseLikeCount(COMMENT_ID);
        verify(commentMapper, never()).decreaseLikeCount(anyInt());
        assertEquals(Boolean.TRUE, dataOf(r).get("upvoted"));
        assertEquals(1, dataOf(r).get("likeCount"), "赞数应回读库里的值，而不是本地推算");
    }

    @Test
    @DisplayName("给别人的评论也能点赞（原实现在这里返回「无权修改该评论」）")
    void shouldAllowUpvotingOthersComment() {
        LocalThreadHolder.setUserId(ME, 2);
        when(commentMapper.getById(COMMENT_ID)).thenReturn(comment(COMMENT_ID, AUTHOR, 1, 7));
        when(interactionMapper.countInteraction(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE))
                .thenReturn(0);
        when(interactionMapper.insert(anyString(), anyInt(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(1);

        Result<Object> r = service.upvote(COMMENT_ID);

        assertEquals(okCode(), r.getCode(), "点赞与评论作者是谁无关");
        verify(commentMapper).increaseLikeCount(COMMENT_ID);
    }

    @Test
    @DisplayName("重复点赞（并发/双击）不重复自增：insert 影响行数为 0 时不加计数")
    void shouldNotDoubleCountWhenInsertAffectsNoRow() {
        LocalThreadHolder.setUserId(ME, 2);
        when(commentMapper.getById(COMMENT_ID)).thenReturn(comment(COMMENT_ID, AUTHOR, 1, 3));
        when(interactionMapper.countInteraction(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE))
                .thenReturn(0);
        // ON DUPLICATE KEY UPDATE 且值未变化 → MySQL 返回 0
        when(interactionMapper.insert(anyString(), anyInt(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(0);

        service.upvote(COMMENT_ID);

        verify(commentMapper, never()).increaseLikeCount(anyInt());
    }

    // ==================== 取消赞 ====================

    @Test
    @DisplayName("已赞再点：删除交互记录并自减计数，返回 upvoted=false")
    void shouldDeleteInteractionAndDecreaseCount() {
        LocalThreadHolder.setUserId(ME, 2);
        when(commentMapper.getById(COMMENT_ID))
                .thenReturn(comment(COMMENT_ID, AUTHOR, 1, 4))
                .thenReturn(comment(COMMENT_ID, AUTHOR, 1, 3));
        when(interactionMapper.countInteraction(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE))
                .thenReturn(1);
        when(interactionMapper.delete(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE)).thenReturn(1);

        Result<Object> r = service.upvote(COMMENT_ID);

        verify(interactionMapper).delete(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE);
        verify(commentMapper).decreaseLikeCount(COMMENT_ID);
        verify(commentMapper, never()).increaseLikeCount(anyInt());
        assertEquals(Boolean.FALSE, dataOf(r).get("upvoted"));
        assertEquals(3, dataOf(r).get("likeCount"));
    }

    @Test
    @DisplayName("并发取消时 delete 影响行数为 0：不再自减，避免把赞数刷成负数")
    void shouldNotDecreaseWhenNothingDeleted() {
        LocalThreadHolder.setUserId(ME, 2);
        when(commentMapper.getById(COMMENT_ID)).thenReturn(comment(COMMENT_ID, AUTHOR, 1, 1));
        when(interactionMapper.countInteraction(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE))
                .thenReturn(1);
        when(interactionMapper.delete(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE)).thenReturn(0);

        service.upvote(COMMENT_ID);

        verify(commentMapper, never()).decreaseLikeCount(anyInt());
    }

    // ==================== 拒绝路径 ====================

    @Test
    @DisplayName("未登录：拒绝，且不触碰任何表")
    void shouldRejectAnonymous() {
        LocalThreadHolder.clear();

        Result<Object> r = service.upvote(COMMENT_ID);

        assertNotEquals(okCode(), r.getCode());
        verify(interactionMapper, never()).insert(any(), any(), any(), any(), any(), any());
        verify(commentMapper, never()).increaseLikeCount(anyInt());
    }

    @Test
    @DisplayName("评论不存在：拒绝")
    void shouldRejectMissingComment() {
        LocalThreadHolder.setUserId(ME, 2);
        when(commentMapper.getById(COMMENT_ID)).thenReturn(null);

        assertNotEquals(okCode(), service.upvote(COMMENT_ID).getCode());
        verify(commentMapper, never()).increaseLikeCount(anyInt());
    }

    @Test
    @DisplayName("已软删（status=0）的评论不给点赞，否则删掉的内容还能被顶上热门")
    void shouldRejectDeletedComment() {
        LocalThreadHolder.setUserId(ME, 2);
        when(commentMapper.getById(COMMENT_ID)).thenReturn(comment(COMMENT_ID, AUTHOR, 0, 2));

        assertNotEquals(okCode(), service.upvote(COMMENT_ID).getCode());
        verify(commentMapper, never()).increaseLikeCount(anyInt());
    }

    @Test
    @DisplayName("缺少评论ID：拒绝")
    void shouldRejectNullId() {
        LocalThreadHolder.setUserId(ME, 2);

        assertNotEquals(okCode(), service.upvote(null).getCode());
    }

    // ==================== 互动通知 ====================

    @Test
    @DisplayName("点赞别人的评论：给作者发「被点赞」通知，带上发起人与评论ID")
    void shouldNotifyAuthorOnUpvote() {
        LocalThreadHolder.setUserId(ME, 2);
        when(commentMapper.getById(COMMENT_ID)).thenReturn(comment(COMMENT_ID, AUTHOR, 1, 0));
        when(interactionMapper.countInteraction(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE))
                .thenReturn(0);
        when(interactionMapper.insert(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE, null, 1))
                .thenReturn(1);

        service.upvote(COMMENT_ID);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageService).evaluationsUpvoteSave(captor.capture());
        Message m = captor.getValue();
        assertEquals(AUTHOR, m.getReceiverId(), "收件人应是评论作者");
        assertEquals(ME, m.getSenderId());
        assertEquals(COMMENT_ID, m.getContentId(), "关联评论ID要带上，前端靠它跳转");
    }

    @Test
    @DisplayName("给自己的评论点赞：不发通知（否则会收到「你点赞了你」）")
    void shouldNotNotifyWhenUpvotingOwnComment() {
        LocalThreadHolder.setUserId(ME, 2);
        when(commentMapper.getById(COMMENT_ID)).thenReturn(comment(COMMENT_ID, ME, 1, 0));
        when(interactionMapper.countInteraction(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE))
                .thenReturn(0);
        when(interactionMapper.insert(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE, null, 1))
                .thenReturn(1);

        service.upvote(COMMENT_ID);

        verify(messageService, never()).evaluationsUpvoteSave(any());
    }

    @Test
    @DisplayName("取消点赞 / 重复点赞：都不发通知（取消不是「被点赞」，重复会被唯一键挡回 0 行）")
    void shouldNotNotifyOnUnvoteOrDuplicate() {
        LocalThreadHolder.setUserId(ME, 2);
        when(commentMapper.getById(COMMENT_ID)).thenReturn(comment(COMMENT_ID, AUTHOR, 1, 3));
        when(interactionMapper.countInteraction(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE))
                .thenReturn(1);
        when(interactionMapper.delete(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE)).thenReturn(1);

        service.upvote(COMMENT_ID);   // 取消赞

        when(interactionMapper.countInteraction(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE))
                .thenReturn(0);
        when(interactionMapper.insert(anyString(), anyInt(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(0);
        service.upvote(COMMENT_ID);   // 重复赞，未真正写入

        verify(messageService, never()).evaluationsUpvoteSave(any());
    }

    @Test
    @DisplayName("通知发送抛异常：点赞本身仍然成功（通知只是附带的，不能连累主流程）")
    void notificationFailureShouldNotBreakUpvote() {
        LocalThreadHolder.setUserId(ME, 2);
        when(commentMapper.getById(COMMENT_ID)).thenReturn(comment(COMMENT_ID, AUTHOR, 1, 0));
        when(interactionMapper.countInteraction(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE))
                .thenReturn(0);
        when(interactionMapper.insert(TARGET_COMMENT, COMMENT_ID, ME, ACTION_LIKE, null, 1))
                .thenReturn(1);
        doThrow(new IllegalStateException("通知服务不可用"))
                .when(messageService).evaluationsUpvoteSave(any());

        Result<Object> r = service.upvote(COMMENT_ID);

        assertEquals(okCode(), r.getCode());
        verify(commentMapper).increaseLikeCount(COMMENT_ID);
    }

    // ==================== 回复通知 ====================

    private static Evaluations reply(Integer parentId, Integer replierId, Integer contentId) {
        Evaluations e = new Evaluations();
        e.setContentType("ARTICLE");
        e.setContentId(contentId);
        e.setParentId(parentId);
        e.setReplierId(replierId);
        e.setContent("同意");
        return e;
    }

    @Test
    @DisplayName("回复别人的评论：通知被回复者（原先发通知的方法写好了却零调用）")
    void shouldNotifyRepliedUser() {
        LocalThreadHolder.setUserId(ME, 2);

        assertEquals(okCode(), service.insert(reply(10, AUTHOR, 1)).getCode());

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageService).evaluationsReplySave(captor.capture());
        assertEquals(AUTHOR, captor.getValue().getReceiverId(), "收件人应是被回复的人");
        assertEquals(ME, captor.getValue().getSenderId());
    }

    @Test
    @DisplayName("顶层评论没有回复对象：不发通知；回复自己也不发")
    void shouldNotNotifyForRootCommentOrSelfReply() {
        LocalThreadHolder.setUserId(ME, 2);

        assertEquals(okCode(), service.insert(reply(null, null, 1)).getCode());
        assertEquals(okCode(), service.insert(reply(10, ME, 1)).getCode());

        verify(messageService, never()).evaluationsReplySave(any());
    }

    // ==================== 列表回显 ====================

    @Test
    @DisplayName("评论列表：赞数来自 like_count，已赞标记来自 content_interaction（一次批量查，不逐条查）")
    void listShouldReadCountFromLikeCountAndFlagFromInteraction() {
        LocalThreadHolder.setUserId(ME, 2);
        CommentVO root = comment(10, AUTHOR, 1, 8);
        when(commentMapper.queryRoots("ARTICLE", 1, 0, 1000)).thenReturn(new ArrayList<>(List.of(root)));
        when(commentMapper.countRoots("ARTICLE", 1)).thenReturn(1);
        CommentVO child = comment(11, AUTHOR, 1, 2);
        when(commentMapper.queryChildren(10, 50)).thenReturn(new ArrayList<>(List.of(child)));
        when(commentMapper.countChildren(10)).thenReturn(1);
        // 我只赞过父评论 10
        when(interactionMapper.queryUpvotedTargetIds(eq(ME), eq(TARGET_COMMENT), anyList()))
                .thenReturn(List.of(10));

        Result<Object> r = service.list(1, "ARTICLE");

        assertEquals(okCode(), r.getCode());
        EvaluationsVO vo = (EvaluationsVO) r.getData();
        List<CommentParentVO> parents = vo.getData();
        CommentParentVO parent = parents.get(0);

        assertEquals(8, parent.getUpvoteCount().intValue(), "赞数应等于 comment.like_count");
        assertEquals(Boolean.TRUE, parent.getUpvoteFlag());
        CommentChildVO childVO = parent.getCommentChildVOS().get(0);
        assertEquals(2, childVO.getUpvoteCount().intValue());
        assertEquals(Boolean.FALSE, childVO.getUpvoteFlag(), "没赞过的子回复不能显示为已赞");

        // 关键：父子评论的已赞状态必须一次查回，不能逐条查（N+1）
        verify(interactionMapper, times(1))
                .queryUpvotedTargetIds(eq(ME), eq(TARGET_COMMENT), anyList());
    }

    @Test
    @DisplayName("未登录看列表：不查交互表，全部按未赞处理（不得 NPE）")
    void listWithoutLoginShouldNotQueryInteraction() {
        LocalThreadHolder.clear();
        when(commentMapper.queryRoots("ARTICLE", 1, 0, 1000))
                .thenReturn(new ArrayList<>(List.of(comment(10, AUTHOR, 1, 5))));
        when(commentMapper.queryChildren(10, 50)).thenReturn(new ArrayList<>());

        Result<Object> r = service.list(1, "ARTICLE");

        assertEquals(okCode(), r.getCode());
        verify(interactionMapper, never()).queryUpvotedTargetIds(any(), any(), anyList());
    }
}
