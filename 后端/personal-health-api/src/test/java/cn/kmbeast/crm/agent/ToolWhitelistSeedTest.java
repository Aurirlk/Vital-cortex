package cn.kmbeast.crm.agent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code Data/02_迁移脚本/10_agent_architecture.sql} 的 {@code agent_tool_binding}
 * 白名单种子与 {@link ToolCatalog} 的<b>双向一致性</b>。
 *
 * <p><b>为什么值得为一段 SQL 写单测</b>：这是全库唯一一处「用枚举表达权限」的地方 ——
 * 有绑定行 = 白名单生效。因此 {@code ToolRegistry} 每加一个工具，就必须回到这份 SQL
 * 里补行，而漏补<b>不报错、不告警、所有测试全绿</b>，只表现为「新工具对 5 个具名
 * Agent 永远不可用」。它已经真实发生过两次：{@code query_health_data} 上线时漏过一次，
 * 阶段 2 的 {@code search_knowledge_base} 又漏了一次（{@code ToolCatalog} 与
 * system prompt 都补了，唯独这里没补）。</p>
 *
 * <p>断言的是「注册的工具集」与「种子绑定的工具集」互为对方的子集（差一个显式的豁免表），
 * 而不是把 SQL 里那几行抄进 Java —— 抄一遍只能证明两处文本相同，
 * 证明不了新工具真的被覆盖到。</p>
 *
 * <p>脚本是使用者手动执行的，所以本测试只读文件、不连库；找不到文件时
 * <b>跳过而不是失败</b>（在只检出后端模块的目录里跑测试是合法用法）。</p>
 */
@DisplayName("工具白名单种子与 ToolCatalog 的双向一致性")
class ToolWhitelistSeedTest {

    /**
     * 刻意<b>不</b>绑定给这 5 个具名 Agent 的工具。
     *
     * <p>它们不是「漏了」，而是收紧本身：{@code general_assistant} 没有任何绑定行
     * （= 全量工具），而它只在无人命中关键词时兜底 —— 于是这些工具不会被
     * 「问了一句含『血压』的话」顺带带出来。</p>
     *
     * <ul>
     *   <li>{@code execute_sql} —— 它的实际能力常被注释高估：跑的是本地 SQLite 的
     *       {@code chat_history}，且由 SqlGuard 强制校验手机号归属
     *       （见 {@code ExecuteSqlTool}），并不是直达业务库的通道；
     *       真正的高权工具是 {@code query_health_data}。</li>
     *   <li>{@code call_agent} —— 委派能力只给 {@code general_assistant}：
     *       {@code agent_handoff} 种子里也只有它指向 5 个专科 Agent。
     *       给具名 Agent 绑定它，运行时也会被 {@code isHandoffAllowed} 拒掉
     *       （没有对应的 handoff 行），绑了等于写了一行不会生效的谎言。</li>
     * </ul>
     */
    private static final Set<String> INTENTIONALLY_UNSEEDED = Set.of("execute_sql", "call_agent");

    /** 未命中关键词时的兜底 Agent：必须保持「零绑定行 = 全量」，否则升级即故障。 */
    private static final String FALLBACK_AGENT = "general_assistant";

    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

    private static Map<String, Set<String>> bindings;

    @BeforeAll
    static void loadSeed() {
        Path seed = locateSeed();
        assumeTrue(seed != null, "未找到 10_agent_architecture.sql，跳过（只检出后端模块时属正常）");
        bindings = parseBindings(seed);
    }

    @Test
    @DisplayName("T1 种子里出现的每个工具名都仍然是已注册的工具（防拼错/防已删工具留下的死绑定）")
    void everySeededToolNameIsStillRegistered() {
        Set<String> unknown = new TreeSet<>();
        for (Set<String> tools : bindings.values()) {
            for (String t : tools) {
                if (!ToolCatalog.ALL.contains(t)) {
                    unknown.add(t);
                }
            }
        }
        assertEquals(Set.of(), unknown,
                "这些工具名出现在白名单种子里，但 ToolRegistry 里已经没有了："
                        + "绑定一个不存在的工具不会报错，只会让该 Agent 的白名单少一个能力");
    }

    @Test
    @DisplayName("T2 每个已注册工具都必须在种子里被处理：要么绑定，要么进豁免表")
    void everyRegisteredToolIsEitherSeededOrExplicitlyExempt() {
        Set<String> seeded = new LinkedHashSet<>();
        bindings.values().forEach(seeded::addAll);

        Set<String> unhandled = new TreeSet<>(ToolCatalog.ALL);
        unhandled.removeAll(seeded);
        // 豁免表是「明知不绑」的显式声明；不在表里的未绑定工具就是漏补
        unhandled.removeAll(INTENTIONALLY_UNSEEDED);

        assertEquals(Set.of(), unhandled,
                "新增工具后没有更新 Data/02_迁移脚本/10_agent_architecture.sql："
                        + "这些工具不会出现在任何具名 Agent 的工具集里，而模型永远不会抱怨"
                        + "它没被提供的工具（要么补绑定行，要么加入 INTENTIONALLY_UNSEEDED 并写明理由）");

        // 反向：豁免表里不该留着已经补上绑定的工具（否则豁免表会变成过期的噪音）
        Set<String> staleExempt = new TreeSet<>(INTENTIONALLY_UNSEEDED);
        staleExempt.retainAll(seeded);
        assertEquals(Set.of(), staleExempt,
                "这些工具既在豁免表里、又在种子中被绑定，豁免表已过期");
    }

    @Test
    @DisplayName("T3 兜底 Agent 保持零绑定行（它的全部能力正来自「没有白名单」）")
    void fallbackAgentKeepsZeroBindings() {
        assertFalse(bindings.containsKey(FALLBACK_AGENT),
                "给 general_assistant 加了绑定行 = 白名单生效，它的工具集会从「全量」"
                        + "收窄成那几行。它是所有路由失败时的落点，必须保留全部能力。");
    }

    // ------------------------------------------------------------------
    // 解析
    // ------------------------------------------------------------------

    /** 从 cwd 逐级向上找脚本。测试的工作目录是模块目录，脚本在仓库根的 Data/ 下。 */
    private static Path locateSeed() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("Data/02_迁移脚本/10_agent_architecture.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /**
     * 解析 {@code INSERT IGNORE INTO agent_tool_binding ... UNION ALL SELECT 'a', 'tool', n}
     * 这一块。每行两个单引号token，依次是 agent_key 与 tool_name。
     */
    private static Map<String, Set<String>> parseBindings(Path seed) {
        List<String> lines;
        try {
            lines = Files.readAllLines(seed, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("白名单种子读取失败: " + seed, e);
        }

        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("INSERT IGNORE INTO `agent_tool_binding`")) {
                start = i;
                break;
            }
        }
        assertTrue(start >= 0, "脚本里找不到 agent_tool_binding 的 INSERT —— 结构变了，请同步更新本测试");

        Map<String, Set<String>> result = new LinkedHashMap<>();
        boolean closed = false;
        for (int i = start + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains(") m ON")) {
                closed = true;
                break;
            }
            if (!line.contains("SELECT")) {
                continue;
            }
            Matcher m = QUOTED.matcher(line);
            List<String> tokens = new java.util.ArrayList<>();
            while (m.find()) {
                tokens.add(m.group(1));
            }
            if (tokens.isEmpty()) {
                // 块首的 `SELECT a.id, m.tool_name, m.sort_order` 表头行，没有字面量
                continue;
            }
            // 整数 sort_order 没有引号，所以正常一行恰好两个 token；
            // 行尾注释里若出现引号会多出来，这里显式拒绝而不是猜。
            assertTrue(tokens.size() == 2,
                    "解析绑定行失败（期望 agent_key + tool_name 两个字符串）: " + line);
            result.computeIfAbsent(tokens.get(0), k -> new LinkedHashSet<>()).add(tokens.get(1));
        }
        assertTrue(closed, "找不到绑定块的结束行 ') m ON ...' —— 脚本结构变了，请同步更新本测试");
        assertFalse(result.isEmpty(), "解析结果为空，说明格式已变，本测试正在假装通过");
        return result;
    }
}
