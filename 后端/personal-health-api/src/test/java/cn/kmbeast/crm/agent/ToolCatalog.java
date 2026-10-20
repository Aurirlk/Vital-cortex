package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.tool.Tool;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具全集 —— <b>测试侧的唯一事实来源</b>，从类路径扫描得到。
 *
 * <p>这份清单经历过两次形态：最早在两个测试里各手写一份 8 项；后来收敛成
 * 这里的单个手写常量。手写常量解决了「两处不同步」，但没解决根因 ——
 * <b>新增工具时仍然没人记得改这一处</b>。{@code query_health_data} 就是这么
 * 漏掉的：工具已经注册、模型却永远看不到它（system prompt 里没写），
 * 而所有测试都是绿的。</p>
 *
 * <p>现在改为扫描 {@code cn.kmbeast.crm.agent.tool} 下所有
 * {@link Tool} 的非抽象实现，无参实例化后取 {@code getName()}：
 * 新增一个 {@code @Component} 工具类，清单自动包含它，
 * 「工具没写进 prompt」这类断言立刻覆盖到新工具。</p>
 *
 * <p>之所以不直接注入 Spring 的 {@code List<Tool>}：使用本清单的测试大多是
 * 纯 JUnit（不起容器）。工具类都是 {@code @Component} + 无参构造 + 字段
 * {@code @Resource}，而 {@code getName()} 只返回字面量，直接实例化即可。
 * 若将来有工具在构造期依赖容器，{@link #scan()} 会抛出明确异常而不是静默漏掉。</p>
 */
public final class ToolCatalog {

    private static final String TOOL_PACKAGE = "cn.kmbeast.crm.agent.tool";

    /** 扫描到的工具实例（按名称排序，可直接注册进 stub 容器）。 */
    private static final List<Tool> INSTANCES = scan();

    /** 全部 ReAct 工具的 {@code getName()} 返回值，按名称排序。 */
    public static final List<String> ALL = INSTANCES.stream().map(Tool::getName).toList();

    private ToolCatalog() {
    }

    /** 工具实例，顺序与 {@link #ALL} 一致。 */
    public static List<Tool> instances() {
        return INSTANCES;
    }

    /** 工具数量。断言里写 {@code ToolCatalog.size()} 而不是字面量，避免再次出现「清单比真相少一项」。 */
    public static int size() {
        return ALL.size();
    }

    private static List<Tool> scan() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Tool.class));

        List<Tool> tools = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(TOOL_PACKAGE)) {
            String className = bd.getBeanClassName();
            if (className == null) {
                continue;
            }
            Class<?> clazz;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("扫描到无法加载的工具类: " + className, e);
            }
            if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }
            try {
                tools.add((Tool) clazz.getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "工具类无法无参实例化: " + className
                                + "（新增工具须保持 @Component + 无参构造，get 方法不依赖容器）", e);
            }
        }
        if (tools.isEmpty()) {
            throw new IllegalStateException("未扫描到任何 Tool 实现，测试类路径可能未包含主代码");
        }

        // 同名工具在 ToolRegistry 里会互相覆盖（后注册的胜出），
        // 那是运行期静默丢工具；在测试侧直接失败，暴露命名冲突。
        Map<String, String> byName = new LinkedHashMap<>();
        for (Tool tool : tools) {
            String previous = byName.put(tool.getName(), tool.getClass().getName());
            if (previous != null) {
                throw new IllegalStateException("工具名重复: " + tool.getName()
                        + "（" + previous + " 与 " + tool.getClass().getName() + "）");
            }
        }

        tools.sort(Comparator.comparing(Tool::getName));
        return List.copyOf(tools);
    }
}
