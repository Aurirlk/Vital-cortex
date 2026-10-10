package cn.kmbeast.crm.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 2026-10-07：启动后校验 ReAct 提示词与工具注册表的一致性。
 *
 * <p><b>为什么必须挂在 ApplicationReadyEvent 而不是 PostConstruct</b>：
 * {@code ToolRegistry} 用 {@code @PostConstruct} 注册工具，而
 * {@code ReactPromptLoader} 也用 {@code @PostConstruct} 加载提示词 ——
 * <b>两者的执行顺序 Spring 不作保证</b>。若在 loader 的 {@code @PostConstruct}
 * 里直接调校验，registry 可能还是空的，会<b>误报 8 个工具全部缺失</b>。
 * {@code ApplicationReadyEvent} 在所有 Bean 初始化完成后才发布，是安全的时机。</p>
 *
 * <p><b>为什么用 WARN 而不是启动失败</b>：这是开发期的防呆措施，不是安全边界。
 * 最常见的成因是「新增工具后忘了改 prompt」—— 这不该让整个系统起不来，
 * 但必须在启动日志里显眼地报出来，否则它会一直以
 * 「功能好像没生效」的形式潜伏着。</p>
 */
@Slf4j
@Component
public class ReactPromptConsistencyRunner {

    @Resource
    private ReactPromptLoader promptLoader;

    @EventListener(ApplicationReadyEvent.class)
    public void verifyOnStartup() {
        try {
            ReactPromptLoader.ConsistencyReport report = promptLoader.verifyToolConsistency();
            if (!report.missing.isEmpty()) {
                log.warn("┌──────────────────────────────────────────────────────────────┐");
                log.warn("│ [ReAct 提示词一致性] 提示词提到但【未注册】的工具：{} │",
                        report.missing);
                log.warn("│ 后果：模型永远不会调用这些工具，功能形同未上线 │");
                log.warn("│ 修法：同步 resources/ai-prompts/react/system.txt 与 ToolRegistry │");
                log.warn("└──────────────────────────────────────────────────────────────┘");
            }
            if (report.registryTools.isEmpty()) {
                log.warn("[ReAct 提示词一致性] ToolRegistry 为空（可能未装配），跳过比对");
            }
        } catch (Exception e) {
            // 校验失败绝不影响启动
            log.warn("[ReAct 提示词一致性] 校验异常（已忽略）: {}", e.getMessage());
        }
    }
}