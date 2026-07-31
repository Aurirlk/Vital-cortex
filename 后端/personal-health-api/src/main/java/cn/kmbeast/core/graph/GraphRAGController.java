package cn.kmbeast.core.graph;

import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱控制器
 */
@Slf4j
@RestController
@RequestMapping("/graph")
public class GraphRAGController {

    @Resource
    private KnowledgeGraphService knowledgeGraphService;

    @Resource
    private GraphRAG graphRAG;

    @GetMapping("/entity")
    public Result<List<Map<String, Object>>> queryEntity(@RequestParam String name) {
        return ApiResult.success(knowledgeGraphService.queryEntityRelations(name));
    }

    @PostMapping("/context")
    public Result<String> getContext(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        return ApiResult.success(knowledgeGraphService.getRelatedContext(text));
    }
}
