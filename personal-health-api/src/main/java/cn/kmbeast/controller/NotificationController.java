package cn.kmbeast.controller;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.Notification;
import cn.kmbeast.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

@RestController

    // 2026-10-07 审计修复：原先用 @RequestAttribute("userId") 取身份，
    // 但全项目没有任何 setAttribute("userId", ...) —— JwtInterceptor 只写 LocalThreadHolder，
    // 故该参数必然抛 MissingRequestAttributeException。改用项目统一的 LocalThreadHolder。
@RequestMapping("/notification")
public class NotificationController {

    @Resource
    private NotificationService notificationService;

    @Protector(role = "管理员")
    @PostMapping("/save")
    public Result<Void> save(@RequestBody Notification notification) {
        return notificationService.save(notification);
    }

    @Protector
    @GetMapping("/list")
    public Result<List<Notification>> getByUserId() {
        Integer userId = LocalThreadHolder.getUserId();
        return notificationService.getByUserId(userId);
    }

    @Protector
    @PostMapping("/read/{id}")
    public Result<Void> markAsRead(@PathVariable Integer id) {
        return notificationService.markAsRead(id);
    }

    @Protector
    @PostMapping("/readAll")
    public Result<Void> markAllAsRead() {
        Integer userId = LocalThreadHolder.getUserId();
        return notificationService.markAllAsRead(userId);
    }

    @Protector
    @GetMapping("/unread")
    public Result<Integer> countUnread() {
        Integer userId = LocalThreadHolder.getUserId();
        return notificationService.countUnread(userId);
    }
}
