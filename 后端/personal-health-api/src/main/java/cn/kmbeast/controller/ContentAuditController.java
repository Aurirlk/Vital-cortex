package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.SensitiveWord;
import cn.kmbeast.service.ContentAuditService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/audit")
public class ContentAuditController {

    @Resource
    private ContentAuditService contentAuditService;

    @Protector(role = "管理员")
    @PostMapping("/sensitive-word/save")
    public Result<Void> addSensitiveWord(@RequestBody SensitiveWord word) {
        return contentAuditService.addSensitiveWord(word);
    }

    @Protector(role = "管理员")
    @PostMapping("/sensitive-word/batchDelete")
    public Result<Void> deleteSensitiveWord(@RequestBody List<Long> ids) {
        return contentAuditService.deleteSensitiveWord(ids);
    }

    @Protector(role = "管理员")
    @GetMapping("/sensitive-word/list")
    public Result<List<SensitiveWord>> getSensitiveWords() {
        return contentAuditService.getSensitiveWords();
    }

    @Protector
    @PostMapping("/check")
    public Result<Boolean> checkContent(@RequestBody String content) {
        return cn.kmbeast.pojo.api.ApiResult.success(contentAuditService.containsSensitiveWord(content));
    }
}
