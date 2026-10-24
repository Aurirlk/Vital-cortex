package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2026-10-07 P0-6：ReAct 提示词外置加载器。
 *
 * <p><b>为什么要外置</b>：此前 {@code DEFAULT_SYSTEM_PROMPT}（约 40 行）与
 * {@code ROLE_INSTRUCTIONS}（6 段）全部硬编码在 {@code BaseReActAgent} 里。后果有三：
 * <ol>
 *   <li>调 prompt 词必须改 Java → 重新编译 → 重新部署，业务同学根本碰不了；</li>
 *   <li>无法做灰度——想对比两版 prompt 的效果只能改代码切分支；</li>
 *   <li>工具增减后 prompt 不会自动更新，<b>模型永远不知道新工具存在</b>。
 *       这是已经发生的真实缺陷：8 个工具里的 {@code execute_sql} 与 {@code web_search}
 *       在 prompt 里<b>一个字都没提</b>，模型永远不会调它们。</li>
 * </ol>
 *
 * <p><b>读取优先级</b>（前者覆盖后者）：
 * <ol>
 *   <li>{@code crm.react.prompt}（yml / 环境变量）—— 现有配置，行为不变</li>
 *   <li>{@code ./config/react-prompts.json} —— 可热改的外部文件，运维改完重启即生效</li>
 *   <li>classpath {@code ai-prompts/react/system.txt} —— 随 jar 打包的默认版本</li>
 *   <li>Java 常量 —— 最后兜底，保证任何环境下都有可用 prompt</li>
 * </ol>
 *
 * <p><b>降级策略是「静默回落」而非抛异常</b>：单测环境没有 resources、
 * 生产环境可能换了工作目录，都不应该让应用起不来。读不到就用 Java 常量，
 * 行为与改造前完全一致。</p>
 */
@Slf4j
@Component
public class ReactPromptLoader {

    /** classpath 中的提示词目录。 */
    private static final String CLASSPATH_DIR = "ai-prompts/react/";
    private static final String SYSTEM_FILE = "system.txt";
    private static final String ROLE_FILE_PREFIX = "role-";
    private static final String ROLE_FILE_SUFFIX = ".txt";

    /** 外部热改文件（相对工作目录）。 */
    private static final String EXTERNAL_FILE = "./config/react-prompts.json";

    /**
     * 工具名识别正则：<b>仅匹配带下划线的小写标识符</b>。
     *
     * <p>为什么要限得这么死：一致性校验是拿 prompt 文本里抽出的标识符去和
     * {@code toolRegistry} 求差集。若把中文词或大写词也抽进来（例如从「AI 健康分析」
     * 抽出 {@code AI}），会产生大量假阳性告警，久了就没人看了。</p>
     *
     * <p>工具名的实际形态：{@code search_drug} / {@code execute_sql} / {@code get_chat_history}。
     * 要求「至少一个下划线」正好把普通英文单词排除在外。</p>
     */
    private static final Pattern TOOL_NAME_PATTERN =
            Pattern.compile("\\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\\b");

    /**
     * 下划线标识符白名单（这些词不是工具名，校验时排除）。
     *
     * <p>{@code agent_key} 是 {@code call_agent} 的<b>参数名</b>，但形态上完全符合
     * 工具名正则。prompt 里必须写它（否则模型不知道该传什么），于是每次启动都会
     * 抽出一个「提到但未注册的工具」并打进 WARN 日志。这类假阳性最危险的地方不是
     * 日志难看，而是它会训练运维「这条 WARN 可以忽略」—— 等真的漏注册工具时，
     * 没人再会去看它。所以发现一个就补一个。</p>
     */
    private static final Set<String> NON_TOOL_IDENTIFIERS = Set.of(
            "prompt_cache", "tool_choice", "tool_call_id", "max_tokens", "top_p",
            "agent_key");

    /**
     * @PostConstruct 在 {@code ToolRegistry.init()} 之前还是之后执行<b>不确定</b>，
     * 因此一致性校验不在这里做（那时 registry 可能还是空的），改由
     * {@link #verifyToolConsistency} 显式调用。
     */
    @Autowired(required = false)
    private ToolRegistry toolRegistry;

    /** classpath 读到的 system prompt（null 表示读不到）。 */
    private String classpathSystemPrompt;

    /** classpath 读到的角色指令：roleKey → 文本。 */
    private final Map<String, String> classpathRolePrompts = new LinkedHashMap<>();

    /** 外部 JSON 文件读到的内容（可能为 null）。 */
    private Map<String, Object> externalConfig;

    @PostConstruct
    public void load() {
        loadFromClasspath();
        loadFromExternalFile();
        log.info("[ReactPrompt] 提示词加载完成：system={}（来源 {}），角色指令 {} 条（来源 {}）",
                classpathSystemPrompt != null ? "外部/配置" : "Java常量",
                resolveSourceLabel(),
                classpathRolePrompts.size(),
                classpathRolePrompts.isEmpty() ? "Java常量" : "外部/配置");
    }

    private void loadFromClasspath() {
        classpathSystemPrompt = readClasspath(SYSTEM_FILE);
        for (String role : List.of("doctor", "nutritionist", "psychologist",
                "analyst", "consultant", "general_assistant")) {
            String text = readClasspath(ROLE_FILE_PREFIX + role + ROLE_FILE_SUFFIX);
            if (text != null) {
                classpathRolePrompts.put(role, text);
            }
        }
    }

    private void loadFromExternalFile() {
        try {
            Path p = Paths.get(EXTERNAL_FILE);
            if (!Files.exists(p)) {
                return;
            }
            String raw = Files.readString(p, StandardCharsets.UTF_8);
            Object parsed = com.alibaba.fastjson2.JSON.parse(raw);
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) parsed;
                externalConfig = map;
                log.info("[ReactPrompt] 已加载外部提示词文件: {}", EXTERNAL_FILE);
            }
        } catch (Exception e) {
            // 外部文件是「可选增强」，读不了不能影响启动
            log.warn("[ReactPrompt] 外部提示词文件读取失败（忽略，使用内置版本）: {}", e.getMessage());
        }
    }

    /**
     * 取 system prompt 的外部覆盖文本（可能为 null）。
     *
     * <p>优先级：yml 的 {@code crm.react.prompt} 由 {@code BaseReActAgent.getSystemPrompt()}
     * 先判（它读的是 {@code CrmConfig}），这里只提供 yml 之外的两级。</p>
     */
    public String externalSystemPrompt() {
        if (externalConfig == null) {
            return null;
        }
        Object v = externalConfig.get("system");
        if (v instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    /**
     * 取角色指令的外部覆盖文本（可能为 null）。
     *
     * <p>外部文件里以 {@code roles} 对象承载：{@code {"roles":{"doctor":"..."}}}。</p>
     */
    public String externalRolePrompt(String roleKey) {
        if (externalConfig == null || roleKey == null) {
            return null;
        }
        Object rolesObj = externalConfig.get("roles");
        if (!(rolesObj instanceof Map)) {
            return null;
        }
        Object v = ((Map<?, ?>) rolesObj).get(roleKey);
        if (v instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    /** classpath 版 system prompt（无则 null，供测试断言与一致性校验使用）。 */
    public String classpathSystemPrompt() {
        return classpathSystemPrompt;
    }

    /** classpath 版角色指令表（供测试与一致性校验使用）。 */
    public Map<String, String> classpathRolePrompts() {
        return classpathRolePrompts;
    }

    private String resolveSourceLabel() {
        if (externalConfig != null) {
            return EXTERNAL_FILE;
        }
        return "classpath:" + CLASSPATH_DIR;
    }

    private String readClasspath(String fileName) {
        // ① 直接用 classloader（jar 内外都能读）
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream(CLASSPATH_DIR + fileName)) {
            if (in != null) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        } catch (Exception e) {
            log.debug("[ReactPrompt] classloader 读取 {} 失败: {}", fileName, e.getMessage());
        }
        // ② 兜底：源码运行（IDEA 直接 Run target/classes）时 classpath 可能不含 resources
        try {
            Path devPath = Paths.get("src/main/resources", CLASSPATH_DIR + fileName);
            if (Files.exists(devPath)) {
                String text = Files.readString(devPath, StandardCharsets.UTF_8).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        } catch (Exception e) {
            log.debug("[ReactPrompt] 文件系统读取 {} 失败: {}", fileName, e.getMessage());
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 工具名一致性校验
    // ------------------------------------------------------------------

    /**
     * 2026-10-07：校验提示词里提到的工具名是否都在 {@code ToolRegistry} 里注册了。
     *
     * <p><b>为什么只 warn 不抛</b>：这是开发期的防呆措施，不是运行期安全边界。
     * prompt 与工具不一致最常见的原因是<b>新增工具后忘了更新 prompt</b>（最常见）
     * 或 prompt 写了尚未启用的工具（灰度中），这两种情况都不该让应用起不来。
     * 但必须在启动日志里显眼地报出来 —— 因为它的后果是「模型永远不会调那个工具」，
     * 表现为「功能好像没生效」，极难排查。</p>
     *
     * @return 校验结果，供管理端界面展示（{@code declaredTools} / {@code registryTools} /
     *         {@code missing}（prompt 有、registry 无）与 {@code unmentioned}（registry 有、prompt 无））
     */
    public ConsistencyReport verifyToolConsistency() {
        ConsistencyReport report = new ConsistencyReport();
        Set<String> declared = extractToolNames();
        report.declaredTools = new LinkedHashSet<>(declared);

        if (toolRegistry != null) {
            for (Tool t : toolRegistry.getAll()) {
                if (t != null && t.getName() != null) {
                    report.registryTools.add(t.getName());
                }
            }
        }

        for (String name : declared) {
            if (!report.registryTools.contains(name)) {
                report.missing.add(name);
            }
        }
        // 反向差集（2026-10-20 补，台账 P1-8）：注册了但提示词里一个字都没提。
        // 这一侧才是已经真实发生过的缺陷 —— execute_sql / web_search 上线时 prompt 没写，
        // 于是模型永远不会调用它们，表现为「功能好像上线了但从来没人用过」，不报错也不告警。
        // 原先只算 prompt→registry，等于只防「加了工具忘了写 prompt」的反方向。
        for (String name : report.registryTools) {
            if (!report.declaredTools.contains(name)) {
                report.unmentioned.add(name);
            }
        }

        if (!report.missing.isEmpty()) {
            log.warn("[ReactPrompt] ⚠️ 提示词提到但未注册的工具: {} —— "
                            + "这些工具模型永远不会被调用，请检查 prompt 是否与 ToolRegistry 同步",
                    report.missing);
        }
        if (!report.unmentioned.isEmpty()) {
            log.warn("[ReactPrompt] ⚠️ 已注册但提示词未提及的工具: {} —— "
                            + "模型看不到它们，除非是有意灰度，否则等于功能没上线",
                    report.unmentioned);
        }
        log.info("[ReactPrompt] 工具名一致性校验：prompt 声明 {} 个，registry 注册 {} 个，缺失 {}，未提及 {}",
                report.declaredTools.size(), report.registryTools.size(),
                report.missing, report.unmentioned);
        return report;
    }

    /**
     * 从已加载的提示词文本中抽取疑似工具名。
     *
     * <p>抽取范围：classpath 版 system prompt + 外部覆盖版（若存在）+
     * 全部角色指令。抽不到任何东西时返回空集（不抛异常）。</p>
     */
    public Set<String> extractToolNames() {
        Set<String> found = new LinkedHashSet<>();
        collectFrom(classpathSystemPrompt, found);
        collectFrom(externalSystemPrompt(), found);
        for (String text : classpathRolePrompts.values()) {
            collectFrom(text, found);
        }
        for (Object v : classpathRolePrompts.keySet()) {
            collectFrom(externalRolePrompt(String.valueOf(v)), found);
        }
        return found;
    }

    private void collectFrom(String text, Set<String> sink) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher m = TOOL_NAME_PATTERN.matcher(text);
        while (m.find()) {
            String token = m.group();
            if (!NON_TOOL_IDENTIFIERS.contains(token)) {
                sink.add(token);
            }
        }
    }

    /** 一致性校验结果（供日志与未来的管理端接口复用）。 */
    public static class ConsistencyReport {
        /** 提示词中出现的工具名 */
        public Set<String> declaredTools = new LinkedHashSet<>();
        /** ToolRegistry 实际注册的工具名 */
        public Set<String> registryTools = new LinkedHashSet<>();
        /** 提示词提到但未注册的（需要修 prompt） */
        public Set<String> missing = new LinkedHashSet<>();
        /** 已注册但提示词从未提及的（模型看不到 = 功能没上线，需要修 prompt 或有意灰度） */
        public Set<String> unmentioned = new LinkedHashSet<>();

        @Override
        public String toString() {
            return "ConsistencyReport{declared=" + declaredTools
                    + ", registry=" + registryTools
                    + ", missing=" + missing
                    + ", unmentioned=" + unmentioned + "}";
        }
    }

    /** 便于测试注入的构造方式。 */
    public void setToolRegistry(ToolRegistry registry) {
        this.toolRegistry = registry;
    }

    /** 全部已知的角色 key（供管理端下拉与测试使用）。 */
    public static List<String> knownRoles() {
        return Arrays.asList("doctor", "nutritionist", "psychologist",
                "analyst", "consultant", "general_assistant");
    }
}