package cn.kmbeast.core.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 重排序器
 * 对检索结果进行重排序
 */
@Slf4j
@Component
/**
 * @deprecated RAG-12: dead code - zero references in the codebase, not wired into business.
 * Do not present this as existing RAG capability in reviews. Decide to activate or delete
 * when the ingestion pipeline lands. Confirm zero references before deleting.
 */
@Deprecated
public class Reranker {

    /**
     * 根据相关性重排序
     */
    public List<String> rerank(String query, List<String> documents) {
        // 简单实现：按关键词匹配度排序
        documents.sort((a, b) -> {
            int scoreA = calculateRelevance(query, a);
            int scoreB = calculateRelevance(query, b);
            return Integer.compare(scoreB, scoreA);
        });
        return documents;
    }

    private int calculateRelevance(String query, String document) {
        if (query == null || document == null) return 0;
        String[] queryWords = query.split("\\s+");
        int score = 0;
        for (String word : queryWords) {
            if (document.contains(word)) score++;
        }
        return score;
    }
}
