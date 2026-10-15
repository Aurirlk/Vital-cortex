package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.tool.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T8：启动期提示词一致性校验（{@code ReactPromptConsistencyRunner}）。
 *
 * <p><b>为什么这类「启动钩子」也要测</b>：它没有返回值、没有断言点，
 * 最容易退化成<b>永远不执行</b>的死代码 —— 类还在、注解还在，
 * 但因为异常被 catch 吞掉、或时机不对而静默什么都不做。
 * 这类代码只能靠测试证明它真的跑得起来。</p>
 */
class ReactPromptConsistencyRunnerTest {

    @SuppressWarnings("unchecked")
    private static void register(ToolRegistry registry, String name) {
        try {
            Field f = ToolRegistry.class.getDeclaredField("tools");
            f.setAccessible(true);
            ((Map<String, Tool>) f.get(registry)).put(name, new Tool() {
                @Override public String getName() { return name; }
                @Override public String getDescription() { return "d"; }
                @Override public Map<String, Object> getParametersSchema() {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("type", "object");
                    s.put("properties", new LinkedHashMap<>());
                    return s;
                }
                @Override public cn.kmbeast.crm.agent.model.ToolResult execute(Map<String, Object> a) {
                    return null;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 反射注入 runner 的私有 loader 字段。 */
    private static void inject(ReactPromptConsistencyRunner runner, ReactPromptLoader loader) {
        try {
            Field f = ReactPromptConsistencyRunner.class.getDeclaredField("promptLoader");
            f.setAccessible(true);
            f.set(runner, loader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("T8.1 全部工具注册时启动校验不抛异常（正常路径可执行）")
    void startupCheckRunsCleanly() {
        ReactPromptLoader loader = new ReactPromptLoader();
        loader.load();
        ToolRegistry registry = new ToolRegistry();
        for (String t : ToolCatalog.ALL) {
            register(registry, t);
        }
        loader.setToolRegistry(registry);

        ReactPromptConsistencyRunner runner = new ReactPromptConsistencyRunner();
        inject(runner, loader);
        assertDoesNotThrow(runner::verifyOnStartup,
                "🔴 启动期校验抛异常说明钩子不可用 —— 它会退化成死代码");
    }

    @Test
    @DisplayName("T8.2 缺工具时启动校验仍不抛异常（只告警不让应用起不来）")
    void startupCheckToleratesMissing() {
        ReactPromptLoader loader = new ReactPromptLoader();
        loader.load();
        ToolRegistry registry = new ToolRegistry();
        register(registry, "search_drug"); // 故意只注册 1 个
        loader.setToolRegistry(registry);

        ReactPromptConsistencyRunner runner = new ReactPromptConsistencyRunner();
        inject(runner, loader);
        assertDoesNotThrow(runner::verifyOnStartup,
                "🔴 缺工具只应告警；若抛异常会导致应用无法启动（可用性事故）");
    }

    @Test
    @DisplayName("T8.3 registry 为空（未装配）时也不抛异常")
    void startupCheckHandlesEmptyRegistry() {
        ReactPromptLoader loader = new ReactPromptLoader();
        loader.load();
        loader.setToolRegistry(null);

        ReactPromptConsistencyRunner runner = new ReactPromptConsistencyRunner();
        inject(runner, loader);
        assertDoesNotThrow(runner::verifyOnStartup);
    }

    @Test
    @DisplayName("T8.4 loader 抛异常时被吞掉（校验绝不影响启动）")
    void startupCheckSwallowsException() {
        ReactPromptConsistencyRunner runner = new ReactPromptConsistencyRunner();
        inject(runner, new ReactPromptLoader() {
            @Override
            public ConsistencyReport verifyToolConsistency() {
                throw new IllegalStateException("模拟校验崩了");
            }
            @Override
            public void load() { /* no-op */ }
        });
        assertDoesNotThrow(runner::verifyOnStartup,
                "🔴 校验自身异常绝不能冒泡 —— 那会让整个应用启动失败");
    }

    @Test
    @DisplayName("T8.5 一致性校验：把工具全集全注册后不该报 missing")
    void consistencyCoversPreviouslyMissedTools() {
        ReactPromptLoader loader = new ReactPromptLoader();
        loader.load();
        ToolRegistry registry = new ToolRegistry();
        for (String t : ToolCatalog.ALL) {
            register(registry, t);
        }
        loader.setToolRegistry(registry);
        var report = loader.verifyToolConsistency();
        assertTrue(report.missing.isEmpty(),
                "提示词已覆盖工具全集（含此前漏掉的 web_search / execute_sql），不该有 missing: "
                        + report.missing);
    }
}