package cn.kmbeast.core.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * RAG 管理器
 * 协调知识库检索和上下文构建
 */
@Slf4j
@Component
public class RAGManager {

    /**
     * 构建 RAG 上下文
     */
    public String buildContext(String query, List<String> keywords) {
        StringBuilder context = new StringBuilder();
        // 关键词提取后的知识库检索结果
        if (keywords != null && !keywords.isEmpty()) {
            context.append("相关知识：\n");
            for (String keyword : keywords) {
                context.append("- ").append(keyword).append("\n");
            }
        }
        return context.toString();
    }

    /**
     * 合并多个上下文
     */
    public String mergeContexts(String... contexts) {
        StringBuilder merged = new StringBuilder();
        for (String ctx : contexts) {
            if (ctx != null && !ctx.isEmpty()) {
                merged.append(ctx).append("\n\n");
            }
        }
        return merged.toString().trim();
    }
}
