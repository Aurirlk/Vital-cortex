package cn.kmbeast.core.nl2sql;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据问答结果。
 */
@Data
public class DataAgentQaResult {

    /** 整体是否成功。 */
    private boolean success;

    /** 意图类别：CHITCHAT / DATA_QUERY。 */
    private String intent;

    /** 最终执行的 SQL（已做租户重写）。 */
    private String sql;

    /** 结果行。 */
    private List<Map<String, Object>> rows = new ArrayList<>();

    /** 行数。 */
    private int rowCount;

    /** 是否成功施加租户条件。 */
    private boolean tenantApplied;

    /** 租户列名。 */
    private String tenantColumn;

    /** Markdown 报告（可选）。 */
    private String report;

    /** 直接回答（闲聊时为模型回复；数据查询时为简短结论）。 */
    private String answer;

    /** 失败原因。 */
    private String message;

    /** 总耗时（毫秒）。 */
    private long elapsedMs;

    /** 步骤事件（同步链路回放用）。 */
    private List<AgentStepEvent> steps = new ArrayList<>();

    /** 会话 id。 */
    private String sessionId;

    public static DataAgentQaResult error(String message, long elapsedMs) {
        DataAgentQaResult r = new DataAgentQaResult();
        r.success = false;
        r.message = message;
        r.elapsedMs = elapsedMs;
        return r;
    }

    public Map<String, Object> summaryMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", success);
        m.put("intent", intent);
        m.put("rowCount", rowCount);
        m.put("tenantApplied", tenantApplied);
        m.put("tenantColumn", tenantColumn);
        m.put("elapsedMs", elapsedMs);
        return m;
    }
}
