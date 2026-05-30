package cn.kmbeast.crm.controller;

import cn.kmbeast.crm.config.CrmConfig;
import cn.kmbeast.crm.dto.VectorSearchRequest;
import cn.kmbeast.crm.dto.VectorUpsertRequest;
import cn.kmbeast.crm.vectordb.LocalVectorStore;
import cn.kmbeast.crm.vectordb.VectorEntity;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping(value = "/crm/vectordb")
public class VectorDbController {

    @Resource
    private LocalVectorStore vectorStore;

    @Resource
    private CrmConfig crmConfig;

    private void checkApiKey(HttpServletRequest request) {
        String apiKey = request.getHeader("X-CRM-API-Key");
        if (apiKey == null || !apiKey.equals(crmConfig.getCrmApiKey())) {
            throw new ApiKeyException("未授权: API Key无效");
        }
    }

    @PostMapping(value = "/upsert")
    public Result<Map<String, Object>> upsert(@RequestBody VectorUpsertRequest request,
                                               HttpServletRequest httpRequest) {
        checkApiKey(httpRequest);

        String collection = request.getCollection();
        List<VectorUpsertRequest.DocumentItem> items = request.getDocuments();

        if (collection == null || collection.trim().isEmpty()) {
            return ApiResult.error("集合名称不能为空");
        }
        if (items == null || items.isEmpty()) {
            return ApiResult.error("文档列表不能为空");
        }

        try {
            if (!vectorStore.collectionExists(collection)) {
                vectorStore.createCollection(collection);
            }

            for (VectorUpsertRequest.DocumentItem item : items) {
                vectorStore.upsert(collection, null, item.getContent(), item.getMetadata());
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("count", items.size());
            result.put("collection", collection);
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("[VectorDbController] upsert失败", e);
            return ApiResult.error("向量更新失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/batch-upsert")
    public Result<Map<String, Object>> batchUpsert(@RequestBody VectorUpsertRequest request,
                                                    HttpServletRequest httpRequest) {
        checkApiKey(httpRequest);

        String collection = request.getCollection();
        List<VectorUpsertRequest.DocumentItem> items = request.getDocuments();

        if (collection == null || collection.trim().isEmpty()) {
            return ApiResult.error("集合名称不能为空");
        }
        if (items == null || items.isEmpty()) {
            return ApiResult.error("文档列表不能为空");
        }

        try {
            if (!vectorStore.collectionExists(collection)) {
                vectorStore.createCollection(collection);
            }

            List<VectorEntity> entities = items.stream()
                    .map(item -> VectorEntity.builder()
                            .content(item.getContent())
                            .metadata(item.getMetadata())
                            .build())
                    .collect(Collectors.toList());

            vectorStore.batchUpsert(collection, entities);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("count", items.size());
            result.put("collection", collection);
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("[VectorDbController] batchUpsert失败", e);
            return ApiResult.error("批量向量更新失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/search")
    public Result<Map<String, Object>> search(@RequestBody VectorSearchRequest request) {
        String collection = request.getCollection();
        String query = request.getQuery();
        int topK = request.getTopK();

        if (collection == null || query == null) {
            return ApiResult.error("collection和query不能为空");
        }

        try {
            if (!vectorStore.collectionExists(collection)) {
                return ApiResult.error("集合 '" + collection + "' 不存在");
            }

            List<LocalVectorStore.SearchResult> results = vectorStore.search(collection, query, topK);

            List<Map<String, Object>> formatted = new ArrayList<>();
            for (LocalVectorStore.SearchResult r : results) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", r.id);
                item.put("score", Math.round(r.score * 10000) / 10000.0);
                item.put("content", r.content);
                item.put("metadata", r.metadata);
                formatted.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("collection", collection);
            result.put("query", query);
            result.put("results", formatted);
            result.put("total", formatted.size());
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("[VectorDbController] search失败", e);
            return ApiResult.error("搜索失败: " + e.getMessage());
        }
    }

    @DeleteMapping(value = "/{collection}/{id}")
    public Result<Void> delete(@PathVariable("collection") String collection,
                                @PathVariable("id") int id,
                                HttpServletRequest httpRequest) {
        checkApiKey(httpRequest);

        try {
            if (!vectorStore.collectionExists(collection)) {
                return ApiResult.error("集合不存在");
            }
            vectorStore.delete(collection, id);
            return ApiResult.success("删除成功", (Void) null);
        } catch (Exception e) {
            log.error("[VectorDbController] delete失败", e);
            return ApiResult.error("删除失败: " + e.getMessage());
        }
    }

    @GetMapping(value = "/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = vectorStore.getStats();
        return ApiResult.success(stats);
    }

    @GetMapping(value = "/{collection}/stats")
    public Result<Map<String, Object>> getCollectionStats(@PathVariable("collection") String collection) {
        Map<String, Object> stats = vectorStore.getCollectionStats(collection);
        return ApiResult.success(stats);
    }

    @PostMapping(value = "/compact")
    public Result<Map<String, Object>> compact(HttpServletRequest httpRequest) {
        checkApiKey(httpRequest);
        vectorStore.compact();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("stats", vectorStore.getStats());
        return ApiResult.success(result);
    }

    @GetMapping(value = "/compact/count")
    public Result<Map<String, Object>> getCompactCount() {
        Map<String, Object> counts = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, Object> entry : vectorStore.getStats().entrySet()) {
            String collection = entry.getKey();
            if (!"total_collections".equals(collection) && !"total_documents".equals(collection)
                    && !"store_path".equals(collection)) {
                int count = vectorStore.getCompactCount(collection);
                if (count > 0) {
                    counts.put(collection, count);
                    total += count;
                }
            }
        }
        counts.put("total_deleted", total);
        return ApiResult.success(counts);
    }

    @PostMapping(value = "/{collection}")
    public Result<Void> createCollection(@PathVariable("collection") String collection,
                                          HttpServletRequest httpRequest) {
        checkApiKey(httpRequest);
        vectorStore.createCollection(collection);
        return ApiResult.success("集合创建成功", (Void) null);
    }
}
