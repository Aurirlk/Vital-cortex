package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.PostQueryDto;
import cn.kmbeast.pojo.entity.Post;
import cn.kmbeast.pojo.entity.PostReply;
import cn.kmbeast.pojo.entity.PostReport;
import cn.kmbeast.pojo.vo.PostVO;
import cn.kmbeast.pojo.vo.PostReplyVO;
import cn.kmbeast.service.PostService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/post")
public class PostController {

    @Resource
    private PostService postService;

    /** 发帖 */
    @Protector
    @PostMapping("/save")
    public Result<Void> save(@RequestBody Post post) {
        return postService.save(post);
    }

    /** 删帖（管理员员?*/
    @Protector(role = "管理员")
    @PostMapping("/batchDelete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        return postService.batchDelete(ids);
    }

    /** 编辑帖子 */
    @Protector
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Post post) {
        return postService.update(post);
    }

    /** 查询帖子列表 */
    @Pager
    @Protector
    @PostMapping("/query")
    public Result<List<PostVO>> query(@RequestBody PostQueryDto queryDto) {
        return postService.query(queryDto);
    }

    /** 获取帖子详情 */
    @Protector
    @GetMapping("/getById/{id}")
    public Result<PostVO> getById(@PathVariable Integer id) {
        return postService.getById(id);
    }

    /** 点赞 */
    @Protector
    @PostMapping("/like/{postId}")
    public Result<Void> like(@PathVariable Integer postId, @RequestAttribute("userId") Integer userId) {
        return postService.like(userId, postId);
    }

    /** 取消点赞 */
    @Protector
    @PostMapping("/unlike/{postId}")
    public Result<Void> unlike(@PathVariable Integer postId, @RequestAttribute("userId") Integer userId) {
        return postService.unlike(userId, postId);
    }

    /** 收藏 */
    @Protector
    @PostMapping("/favorite/{postId}")
    public Result<Void> favorite(@PathVariable Integer postId, @RequestAttribute("userId") Integer userId) {
        return postService.favorite(userId, postId);
    }

    /** 取消收藏 */
    @Protector
    @PostMapping("/unfavorite/{postId}")
    public Result<Void> unfavorite(@PathVariable Integer postId, @RequestAttribute("userId") Integer userId) {
        return postService.unfavorite(userId, postId);
    }

    /** 回复帖子 */
    @Protector
    @PostMapping("/reply")
    public Result<Void> saveReply(@RequestBody PostReply postReply) {
        return postService.saveReply(postReply);
    }

    /** 获取帖子回复 */
    @Protector
    @GetMapping("/replies/{postId}")
    public Result<List<PostReplyVO>> getReplies(@PathVariable Integer postId) {
        return postService.getReplies(postId);
    }

    /** 关注用户 */
    @Protector
    @PostMapping("/follow/{followeeId}")
    public Result<Void> follow(@PathVariable Integer followeeId, @RequestAttribute("userId") Integer userId) {
        return postService.follow(userId, followeeId);
    }

    /** 取消关注 */
    @Protector
    @PostMapping("/unfollow/{followeeId}")
    public Result<Void> unfollow(@PathVariable Integer followeeId, @RequestAttribute("userId") Integer userId) {
        return postService.unfollow(userId, followeeId);
    }

    /** 是否已关?*/
    @Protector
    @GetMapping("/isFollowing/{followeeId}")
    public Result<Boolean> isFollowing(@PathVariable Integer followeeId, @RequestAttribute("userId") Integer userId) {
        return postService.isFollowing(userId, followeeId);
    }

    /** 热榜 */
    @Protector
    @GetMapping("/hotList")
    public Result<List<PostVO>> getHotList(@RequestParam(required = false) Integer limit) {
        return postService.getHotList(limit);
    }

    /** 搜索帖子 */
    @Protector
    @GetMapping("/search")
    public Result<List<PostVO>> search(@RequestParam String keyword) {
        return postService.search(keyword);
    }

    /** 举报 */
    @Protector
    @PostMapping("/report")
    public Result<Void> report(@RequestBody PostReport postReport) {
        return postService.report(postReport);
    }

    /** 获取待处理举报（管理员员） */
    @Protector(role = "管理员")
    @GetMapping("/reports/pending")
    public Result<List<PostReport>> getPendingReports() {
        return postService.getPendingReports();
    }

    /** 处理举报（管理员员?*/
    @Protector(role = "管理员")
    @PostMapping("/reports/handle")
    public Result<Void> handleReport(@RequestParam Integer id, @RequestParam Integer status) {
        return postService.handleReport(id, status);
    }
}
