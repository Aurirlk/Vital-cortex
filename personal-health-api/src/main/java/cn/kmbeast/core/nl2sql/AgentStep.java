package cn.kmbeast.core.nl2sql;

/**
 * 数据智能体（DataAgent）流水线步骤。
 *
 * <p>对齐 DataAgent 的 workflow 节点（IntentRecognition → SchemaRecall → TableRelation →
 * SqlGenerate → SemanticConsistency → SqlExecute → ReportGenerator），但<b>不引入</b>
 * Spring AI Alibaba Graph 依赖，用顺序编排 + 事件回调实现同等可观测性。</p>
 *
 * <p>每个步骤都对应一个「工具」，前端据此展示「工作流 + 工具调用」两栏视图。</p>
 */
public enum AgentStep {

    INTENT(1, "意图识别", "intent_recognizer",
            "判断问题是闲聊还是数据分析请求"),
    QUERY_ENHANCE(2, "查询增强", "query_enhancer",
            "结合上下文把问题改写为独立、可检索的规范查询"),
    SCHEMA_RECALL(3, "数据表召回", "schema_recaller",
            "从全库中召回与问题相关的候选数据表"),
    TABLE_RELATION(4, "表关系分析", "table_relation_analyzer",
            "分析候选表之间的外键关系，为 JOIN / 子查询提供依据"),
    SQL_GENERATE(5, "SQL 生成", "sql_generator",
            "依据 schema 与表关系生成只读 SQL"),
    SEMANTIC_CHECK(6, "语义一致性校验", "semantic_checker",
            "校验生成的 SQL 是否真正回答了用户问题"),
    SECURITY_GUARD(7, "安全守卫", "sql_guard",
            "词法校验：拒绝 DML/DDL/多语句/注释/系统库/黑名单表"),
    TENANT_REWRITE(8, "租户隔离重写", "tenant_rewriter",
            "删除模型写的租户谓词并无条件追加当前主体的租户条件"),
    EXECUTE(9, "执行查询", "sql_executor",
            "参数化执行 SQL，行数与时长为硬上限"),
    REPORT(10, "生成报告", "report_generator",
            "基于真实结果生成 Markdown 分析报告");

    private final int order;
    private final String title;
    private final String tool;
    private final String description;

    AgentStep(int order, String title, String tool, String description) {
        this.order = order;
        this.title = title;
        this.tool = tool;
        this.description = description;
    }

    public int getOrder() {
        return order;
    }

    public String getTitle() {
        return title;
    }

    public String getTool() {
        return tool;
    }

    public String getDescription() {
        return description;
    }
}
