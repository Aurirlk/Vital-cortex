package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.crm.rag.KnowledgeIngestionService;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.NewsQueryDto;
import cn.kmbeast.pojo.entity.News;
import cn.kmbeast.pojo.vo.NewsVO;
import cn.kmbeast.service.NewsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康资讯的 Controller
 */
@RestController
@RequestMapping(value = "/news")
public class NewsController {

    @Resource
    private NewsService newsService;

    @Resource
    private KnowledgeIngestionService knowledgeIngestionService;

    /**
     * 健康资讯新增（管理员）
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/save")
    public Result<Void> save(@RequestBody News news) {
        Result<Void> result = newsService.save(news);
        // RAG-19：文章新增 → 向量库增量同步
        if (result != null && news.getId() != null && news.getContent() != null) {
            knowledgeIngestionService.ingestArticle(
                    news.getId(), news.getName(), news.getContent(), null);
        }
        return result;
    }

    /**
     * 健康资讯删除（管理员）
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/batchDelete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        Result<Void> result = newsService.batchDelete(ids);
        // RAG-19：文章删除 → 向量库联动清理
        if (result != null && ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    knowledgeIngestionService.deleteArticle(id.intValue());
                }
            }
        }
        return result;
    }

    /**
     * 健康资讯修改（管理员）
     */
    @Protector(role = "管理员")
    @PutMapping(value = "/update")
    public Result<Void> update(@RequestBody News news) {
        Result<Void> result = newsService.update(news);
        // RAG-19：文章更新 → 重建该文章向量
        if (result != null && news.getId() != null && news.getContent() != null) {
            knowledgeIngestionService.ingestArticle(
                    news.getId(), news.getName(), news.getContent(), null);
        }
        return result;
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

    /**
     * RAG-04：知识向量库全量重建（管理员维护用）
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/rag/rebuild")
    public Result<Map<String, Object>> rebuildRag() {
        knowledgeIngestionService.rebuildAll();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("status", "OK");
        stats.put("collection", KnowledgeIngestionService.COLLECTION);
        stats.put("stats", knowledgeIngestionService.getVectorStoreStats());
        return ApiResult.success(stats);
    }

    /**
     * RAG-04：查看向量库状态
     */
    @Protector(role = "管理员")
    @GetMapping(value = "/rag/stats")
    public Result<Map<String, Object>> ragStats() {
        return ApiResult.success(knowledgeIngestionService.getVectorStoreStats());
    }
}
