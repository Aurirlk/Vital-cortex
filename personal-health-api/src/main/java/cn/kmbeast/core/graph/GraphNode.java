package cn.kmbeast.core.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 图节点（含元数据）。
 *
 * <p>{@link Triple} 只表达"边"，而节点自身还有描述、别名、溯源等属性 ——
 * 尤其 {@code Disease} 的 {@code description} 是 GraphRAG 召回时
 * 唯一能直接喂给 LLM 的知识片段，必须有地方承载。
 *
 * <p>⚠️ {@code name} 是 {@link GraphRAG} 的硬契约（{@code WHERE n.name=$name}），
 * 必填且全局按 label 唯一。
 */
public class GraphNode {

    private final String name;
    private final GraphSchema.Label label;
    private final String description;
    private final List<String> alias;
    private final Map<String, Object> props;
    private final String source;

    public GraphNode(String name, GraphSchema.Label label, String description,
                     List<String> alias, Map<String, Object> props, String source) {
        this.name = name;
        this.label = label;
        this.description = description == null ? "" : description;
        this.alias = alias == null ? Collections.emptyList() : List.copyOf(alias);
        this.props = props == null || props.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(props));
        this.source = source;
    }

    public static GraphNode of(String name, GraphSchema.Label label, String source) {
        return new GraphNode(name, label, null, null, null, source);
    }

    public static GraphNode described(String name, GraphSchema.Label label,
                                      String description, String source) {
        return new GraphNode(name, label, description, null, null, source);
    }

    public String getName() {
        return name;
    }

    public GraphSchema.Label getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getAlias() {
        return alias;
    }

    public Map<String, Object> getProps() {
        return props;
    }

    public String getSource() {
        return source;
    }

    public boolean isValid() {
        return name != null && !name.trim().isEmpty() && label != null;
    }

    public String key() {
        return label.code() + "|" + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GraphNode other)) {
            return false;
        }
        return Objects.equals(name, other.name) && label == other.label;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, label);
    }

    @Override
    public String toString() {
        return "(" + label.code() + ":" + name + ")";
    }
}
