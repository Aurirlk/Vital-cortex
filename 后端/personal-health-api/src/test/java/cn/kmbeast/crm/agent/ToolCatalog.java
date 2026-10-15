package cn.kmbeast.crm.agent;

import java.util.List;

/**
 * 工具全集清单 —— <b>测试侧的唯一事实来源</b>。
 *
 * <p>存在的理由是一段真实的腐化史：这份清单此前在
 * {@code ToolSchemaComplianceTest} 和 {@code ReactPromptToolConsistencyTest}
 * 里各写了一份，都是 8 项。后来 {@code query_health_data}（{@code DataQueryTool}）
 * 被加进来，两处清单都没改 —— 于是「工具必须在 system prompt 里出现」这条唯一
 * 的自动化防线，对新工具彻底失效。一个没写进 prompt 的工具，模型<b>永远不会调用</b>，
 * 而所有测试都是绿的。</p>
 *
 * <p>所以清单必须只存一份。新增工具时改这里，所有引用它的断言（源码存在性、
 * schema 形态、prompt 覆盖）会同时生效于新工具 —— 漏改不再可能，
 * 因为你改的那一行就是唯一的那一行。</p>
 *
 * <p><b>它仍然不能自动发现「新工具没写进清单」</b>。补这道断层的是
 * {@code ToolRegistry} 启动时的 {@code verifyToolConsistency()}：它拿
 * prompt 里抽出的标识符与真实注册的工具求差集，注册了但没写进 prompt 会报
 * missing。两处一起看，才算闭环。</p>
 */
public final class ToolCatalog {

    private ToolCatalog() {
    }

    /** 全部 ReAct 工具的 {@code getName()} 返回值。 */
    public static final List<String> ALL = List.of(
            "search_drug",
            "search_knowledge",
            // 2026-10-13 阶段 2：管理员自建知识库的检索工具。
            // 它与 search_knowledge 查的不是同一批数据（内置文章 vs 管理员上传的资料），
            // 因此是两个工具而不是一个工具的两个参数。
            "search_knowledge_base",
            "query_knowledge_graph",
            "query_health_records",
            "get_health_data",
            "query_health_data",
            "get_chat_history",
            "web_search",
            "execute_sql",
            "call_agent");

    /** 工具数量。断言里写 {@code ToolCatalog.size()} 而不是字面量 8/10 —— 字面量正是上次漏改的东西。 */
    public static int size() {
        return ALL.size();
    }
}
