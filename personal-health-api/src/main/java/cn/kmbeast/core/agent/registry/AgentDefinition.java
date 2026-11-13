package cn.kmbeast.core.agent.registry;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Agent 的运行时视图：把 {@code agent_config} 一行 + 它的工具绑定 + 它的委派关系
 * 合成一个执行期不可变的定义对象。
 *
 * <p>刻意与 entity {@code AgentConfig} 分开：entity 是「表的一行」，
 * 本类是「路由与执行真正需要的东西」。两者的差异不是冗余，而是边界——
 * 比如 {@link #toolNames} 的三态语义在 entity 里根本表达不了。</p>
 *
 * <p><b>两个字段用 null 表达「不干预」，而不是空集</b>：</p>
 * <ul>
 *   <li>{@link #toolNames} 为 null = 该 Agent 没有任何绑定行，下发全量工具；
 *       为空集 = 绑了但全被停用，一个工具都不给。前者是向后兼容，后者是真实收窄。
 *       用空集兼表两义会让「升级后所有 Agent 突然失去工具」这种故障无法避免。</li>
 *   <li>{@link #systemPrompt} 等覆盖字段为 null = 沿用既有链路
 *       （ReactPromptLoader / ROLE_INSTRUCTIONS / AiPromptConfig）。
 *       这些链路各有权威，本类再抄一份必然与它们发散。</li>
 * </ul>
 */
@Data
@Builder
public class AgentDefinition {

    /** agent_config.id；内置兜底定义没有 id（未落库），为 null。 */
    private Integer id;

    private String agentKey;
    private String name;
    private String description;
    private String avatar;

    /** null = 不覆盖既有提示词链路。 */
    private String systemPrompt;
    /** null = 沿用全局默认模型。 */
    private String model;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;

    /** user / doctor / admin / all。 */
    private String audience;
    private int maxIterations;

    /** 路由关键词。 */
    private List<String> routingKeywords;

    private boolean enabled;
    private boolean isDefault;
    private int sortOrder;

    /** 工具白名单；null = 全量（无绑定行），空集 = 一个都不给。 */
    private Set<String> toolNames;

    /** 允许委派到的目标 agentKey；空集 = 不允许委派。 */
    private Set<String> handoffTargets;

    /** true = 来自 DB；false = 内置兜底（表未建 / 无数据时的降级）。 */
    private boolean fromDb;

    /** 供调用方免判空遍历。 */
    public List<String> keywordsOrEmpty() {
        return routingKeywords == null ? Collections.emptyList() : routingKeywords;
    }

    public Set<String> handoffTargetsOrEmpty() {
        return handoffTargets == null ? Collections.emptySet() : handoffTargets;
    }

    /**
     * 该 Agent 是否被明确收窄过工具集。
     * {@code false} 时调用方应下发全量工具（见 {@link #toolNames} 的三态说明）。
     */
    public boolean hasToolWhitelist() {
        return toolNames != null;
    }
}
