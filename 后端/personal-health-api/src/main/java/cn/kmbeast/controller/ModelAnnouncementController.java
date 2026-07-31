package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.ModelAnnouncement;
import cn.kmbeast.service.ModelAnnouncementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * 模型公告/横幅通知管理接口
 */
@Slf4j
@RestController
@RequestMapping(value = "/ai/announcement")
public class ModelAnnouncementController {

    @Resource
    private ModelAnnouncementService modelAnnouncementService;

    /**
     * C端获取当前展示的横幅（无需鉴权）
     */
    @GetMapping("/active")
    public Result<ModelAnnouncement> getActiveAnnouncement() {
        ModelAnnouncement announcement = modelAnnouncementService.findActiveAnnouncement();
        return ApiResult.success(announcement);
    }

    /**
     * 管理员获取所有横幅列表
     */
    @Protector(role = "管理员")
    @GetMapping("/list")
    public Result<List<ModelAnnouncement>> list() {
        List<ModelAnnouncement> list = modelAnnouncementService.findAll();
        return ApiResult.success(list);
    }

    /**
     * 管理员保存/更新横幅
     */
    @Protector(role = "管理员")
    @PostMapping("/save")
    public Result<Void> save(@RequestBody ModelAnnouncement announcement) {
        boolean success = modelAnnouncementService.saveOrUpdate(announcement);
        if (success) {
            log.info("[横幅管理] 保存横幅成功: {}", announcement.getTitle());
            return ApiResult.success("保存成功");
        }
        return ApiResult.error("保存失败");
    }

    /**
     * 管理员删除横幅
     */
    @Protector(role = "管理员")
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody Map<String, Integer> request) {
        Integer id = request.get("id");
        if (id == null) {
            return ApiResult.error("缺少横幅ID");
        }
        boolean success = modelAnnouncementService.deleteById(id);
        if (success) {
            log.info("[横幅管理] 删除横幅成功，ID: {}", id);
            return ApiResult.success("删除成功");
        }
        return ApiResult.error("删除失败");
    }

    /**
     * 管理员批量删除横幅
     */
    @Protector(role = "管理员")
    @PostMapping("/batchDelete")
    public Result<Void> batchDelete(@RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ApiResult.error("缺少横幅ID列表");
        }
        boolean success = modelAnnouncementService.batchDelete(ids);
        if (success) {
            log.info("[横幅管理] 批量删除横幅成功，数量: {}", ids.size());
            return ApiResult.success("批量删除成功");
        }
        return ApiResult.error("批量删除失败");
    }

    /**
     * 管理员设置当前展示横幅
     */
    @Protector(role = "管理员")
    @PostMapping("/setActive")
    public Result<Void> setActive(@RequestBody Map<String, Integer> request) {
        Integer id = request.get("id");
        if (id == null) {
            return ApiResult.error("缺少横幅ID");
        }
        boolean success = modelAnnouncementService.setActive(id);
        if (success) {
            log.info("[横幅管理] 设置当前展示横幅成功，ID: {}", id);
            return ApiResult.success("设置成功");
        }
        return ApiResult.error("设置失败");
    }
}
