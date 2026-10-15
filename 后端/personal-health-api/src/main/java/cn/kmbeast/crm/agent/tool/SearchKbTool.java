package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.crm.rag.KbSearchService;
import cn.kmbeast.crm.rag.RagRetrievalEvalService;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索工具：让智能体检索管理员维护的知识库（PDF / Word / Excel / 粘贴文本）。
 *
 * <h3>与 {@link SearchKnowledgeTool} 的区别</h3>
 * <p>{@code SearchKnowledgeTool} 查的是<b>内置的三个固定向量集合</b>
 * （health_knowledge / report_templates / nutrition_knowledge），集合名写死、
 * 内容由 {@code KnowledgeIngestionService} 从新闻文章单向灌入 ——
 * 管理员无法往里加自己的资料。</p>
 * <p>本工具查的是<b>管理员自建的知识库</b>：可以新建任意多个、上传各类文档、
 * 设置受众与召回条数、随时停用。检索走完整的两路融合链路，
 * 结果会进 {@code retrieval_log}，因此「智能体到底查到了什么」是可追溯的。</p>
 *
 * <h3>为什么必须截断正文</h3>
 * <p>一个块默认 200 字，但单篇块上限可配到 4000 字。若模型一次取 10 条、
 * 每条都塞 4000 字，光这一条工具结果就吃掉 4 万字符的上下文 ——
 * 足以把整轮对话挤爆，而模型看到的还是「一堆半截文档」。
 * 这里的 400 字是「够模型判断相关性并引用」与「不挤爆上下文」之间的取舍点。</p>
 */
@Slf4j
@Component
public class SearchKbTool implements Tool {

    /** 单条结果的正文截断长度。 */
    private static final int CONTENT_MAX = 400;

    /** 单次返回条数上限，防止模型传个 50 把上下文灌满。 */
    private static final int TOP_K_MAX = 10;

    @Resource
    private KbSearchService kbSearchService;

    @Override
    public String getName() {
        return "search_knowledge_base";
    }

    @Override
    public String getDescription() {
        return "检索管理员维护的知识库（收录了药品说明书、诊疗规范、健康科普等资料）。"
                + "适用于需要权威依据的问题：用法用量、禁忌、指标参考范围、流程规范。"
                + "返回结果的 source 字段标明由哪条检索路径召回。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "检索查询文本，建议直接用用户的原问题");
        properties.put("query", query);

        Map<String, Object> kbIds = new LinkedHashMap<>();
        kbIds.put("type", "string");
        kbIds.put("description", "可选：限定检索的知识库 id，多个用英文逗号分隔（如 \"1,3\"）。"
                + "留空表示检索当前受众下全部启用的知识库。");
        properties.put("kb_ids", kbIds);

        Map<String, Object> audience = new LinkedHashMap<>();
        audience.put("type", "string");
        audience.put("description", "可选：受众范围，doctor（医生）/ user（普通用户）/ all（全部），默认 all");
        audience.put("enum", List.of("doctor", "user", "all"));
        properties.put("audience", audience);

        Map<String, Object> topK = new LinkedHashMap<>();
        topK.put("type", "integer");
        topK.put("description", "返回条数，默认 5，最大 " + TOP_K_MAX);
        topK.put("default", 5);
        // 声明上下界：模型读中文描述感知不到边界，写死在这里才能被本地校验拦住，
        // 而不是等它传个 50 把上下文灌满才发现（ToolSchemaComplianceTest T2.3 的同一理由）。
        topK.put("minimum", 1);
        topK.put("maximum", TOP_K_MAX);
        properties.put("top_k", topK);

        schema.put("properties", properties);
        schema.put("required", List.of("query"));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (arguments == null) {
            return ToolResult.error("缺少参数");
        }
        String query = str(arguments.get("query"));
        if (query == null || query.isEmpty()) {
            return ToolResult.error("缺少必填参数 query");
        }
        int topK = intOf(arguments.get("top_k"), 5);
        topK = Math.max(1, Math.min(topK, TOP_K_MAX));
        List<Integer> kbIds = RagRetrievalEvalService.parseKbIds(str(arguments.get("kb_ids")));
        String audience = str(arguments.get("audience"));

        try {
            KbSearchService.SearchOutcome outcome = kbSearchService.search(
                    query, null, kbIds, audience, null, topK);
            if (outcome.hits().isEmpty()) {
                // 明确告诉模型「没查到」，它才会用自己的知识回答而不是硬编
                return ToolResult.ok("知识库中没有检索到与该问题相关的内容，请基于你自己的知识回答，"
                        + "并说明这一结论未在知识库中找到依据。");
            }

            List<Map<String, Object>> formatted = new ArrayList<>(outcome.hits().size());
            for (KbSearchService.KbHit hit : outcome.hits()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("chunk_id", hit.getChunkId());
                item.put("doc_id", hit.getDocId());
                item.put("knowledge_base", hit.getKbName());
                if (hit.getTitle() != null && !hit.getTitle().isEmpty()) {
                    item.put("title", hit.getTitle());
                }
                item.put("source", hit.getSource());
                item.put("score", Math.round(hit.getScore() * 10000) / 10000.0);
                item.put("content", truncate(hit.getContent(), CONTENT_MAX));
                formatted.add(item);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("retrievalPath", outcome.path());
            payload.put("items", formatted);
            return ToolResult.ok(JSON.toJSONString(payload));
        } catch (Exception e) {
            log.error("[SearchKbTool] 知识库检索失败", e);
            return ToolResult.error("知识库检索异常: " + e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…（已截断）";
    }

    private static String str(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static int intOf(Object v, int fallback) {
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return Integer.parseInt(((String) v).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
