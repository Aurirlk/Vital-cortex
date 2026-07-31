package cn.kmbeast.core.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 混合搜索器
 * 结合关键词搜索和语义搜索
 */
@Slf4j
@Component
public class HybridSearcher {

    /**
     * 混合搜索
     */
    public List<String> search(String query, List<String> keywordResults, List<String> semanticResults) {
        List<String> merged = new ArrayList<>();
        // 关键词结果优先
        if (keywordResults != null) {
            merged.addAll(keywordResults);
        }
        // 语义结果补充
        if (semanticResults != null) {
            for (String result : semanticResults) {
                if (!merged.contains(result)) {
                    merged.add(result);
                }
            }
        }
        return merged;
    }
}
