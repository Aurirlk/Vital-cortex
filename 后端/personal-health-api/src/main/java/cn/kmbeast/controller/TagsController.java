package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.TagsQueryDto;
import cn.kmbeast.pojo.entity.Tags;
import cn.kmbeast.service.TagsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 标签的 Controller
 */
@RestController
@RequestMapping(value = "/tags")
public class TagsController {

    @Resource
    private TagsService tagsService;

    /**
     * 标签新增（管理员）
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/save")
    public Result<Void> save(@RequestBody Tags tags) {
        return tagsService.save(tags);
    }

    /**
     * 标签删除（管理员）
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/batchDelete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        return tagsService.batchDelete(ids);
    }

    /**
     * 标签修改（管理员）
     */
    @Protector(role = "管理员")
    @PutMapping(value = "/update")
    public Result<Void> update(@RequestBody Tags tags) {
        return tagsService.update(tags);
    }

    /**
     * 标签查询
     */
    @Pager
    @Protector
    @PostMapping(value = "/query")
    public Result<List<Tags>> query(@RequestBody TagsQueryDto tagsQueryDto) {
        return tagsService.query(tagsQueryDto);
    }
}
