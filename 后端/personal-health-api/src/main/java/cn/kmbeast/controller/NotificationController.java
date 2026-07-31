package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.Notification;
import cn.kmbeast.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
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
    public Result<List<Notification>> getByUserId(@RequestAttribute("userId") Integer userId) {
        return notificationService.getByUserId(userId);
    }

    @Protector
    @PostMapping("/read/{id}")
    public Result<Void> markAsRead(@PathVariable Integer id) {
        return notificationService.markAsRead(id);
    }

    @Protector
    @PostMapping("/readAll")
    public Result<Void> markAllAsRead(@RequestAttribute("userId") Integer userId) {
        return notificationService.markAllAsRead(userId);
    }

    @Protector
    @GetMapping("/unread")
    public Result<Integer> countUnread(@RequestAttribute("userId") Integer userId) {
        return notificationService.countUnread(userId);
    }
}
