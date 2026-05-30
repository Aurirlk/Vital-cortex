package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.NewsQueryDto;
import cn.kmbeast.pojo.entity.News;
import cn.kmbeast.pojo.vo.NewsVO;
import cn.kmbeast.service.NewsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 健康资讯的 Controller
 */
@RestController
@RequestMapping(value = "/news")
public class NewsController {

    @Resource
    private NewsService newsService;

    /**
     * 健康资讯新增（管理员）
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/save")
    public Result<Void> save(@RequestBody News news) {
        return newsService.save(news);
    }

    /**
     * 健康资讯删除（管理员）
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/batchDelete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        return newsService.batchDelete(ids);
    }

    /**
     * 健康资讯修改（管理员）
     */
    @Protector(role = "管理员")
    @PutMapping(value = "/update")
    public Result<Void> update(@RequestBody News news) {
        return newsService.update(news);
    }

    /**
     * 健康资讯查询
     */
    @Pager
    @Protector
    @PostMapping(value = "/query")
    public Result<List<NewsVO>> query(@RequestBody NewsQueryDto NewsQueryDto) {
        return newsService.query(NewsQueryDto);
    }
}
