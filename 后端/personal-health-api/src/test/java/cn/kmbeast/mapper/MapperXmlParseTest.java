package cn.kmbeast.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mapper XML 静态检查 —— 2026-10-10 新增。
 *
 * <p><b>为什么需要它</b>：本项目全部单元测试都是 Mockito 的纯对象测试，
 * <b>没有一个测试会启动 Spring 容器</b>，因此 MyBatis 的 Mapper XML
 * 从来没被真正解析过。结果就是：XML 里写错一个标签、少一个闭合符、
 * 写了个裸的 {@code <}，{@code mvn test} 全绿，直到有人真的把服务跑起来
 * 才在启动时报错 —— 属于「测试通过但系统起不来」的盲区。
 *
 * <p>MyBatis 解析 Mapper XML 只需要 {@link Configuration}，<b>不需要数据库连接</b>，
 * 所以这件事在单测里就能做。
 *
 * <p><b>覆盖范围</b>：XML 结构是否合法（含裸 {@code <}、标签未闭合等）、
 * {@code namespace} 能否对应到真实存在的 Mapper 接口、每条语句的 id 能否对应到
 * 该接口上的同名方法。
 * <b>不覆盖</b>：SQL 在 MySQL 上是否真的能跑（列名拼错、类型不匹配仍然要靠
 * 集成环境或人工核对）—— 测试名如实反映这一点，不夸大它的能力。
 *
 * <p>这条检查不是摆设：它第一次跑就把 {@code DepartmentMapper.xml} 里两个裸写的
 * {@code <>} 揪了出来（见该文件 {@code countDoctorByDepartment} 上方注释），
 * 那个文件会让整个服务启动失败。
 */
@DisplayName("Mapper XML 静态检查")
class MapperXmlParseTest {

    private static final Path MAPPER_DIR = Paths.get("src", "main", "resources", "mapper");

    /** 取 &lt;mapper namespace="..."&gt;；只用于静态检查，不参与业务逻辑 */
    private static final Pattern NAMESPACE = Pattern.compile("<mapper\\s[^>]*namespace\\s*=\\s*\"([^\"]+)\"");

    // ================================================================ 检查项

    @Test
    @DisplayName("全部 Mapper XML 都能被 MyBatis 解析（不需要数据库）")
    void allMapperXmlsShouldParse() throws Exception {
        List<Path> files = mapperFiles();
        // 41 个 Mapper 是 2026-10-10 的快照数；突然变成 0 说明路径写错了
        assertFalse(files.isEmpty(), "没有找到任何 Mapper XML，检查 " + MAPPER_DIR.toAbsolutePath());

        List<String> failures = new ArrayList<>();
        for (Path file : files) {
            try {
                parse(file);
            } catch (Exception e) {
                failures.add(file.getFileName() + " → " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
            }
        }

        assertTrue(failures.isEmpty(),
                "以下 Mapper XML 解析失败（服务启动时会直接报错）:\n" + String.join("\n", failures));
    }

    /**
     * namespace / 语句 id 与 Java 接口对不上。
     *
     * <p>这类错误<b>启动时不会报错</b>：MyBatis 照样把语句注册进去，只是没人调用得到；
     * 等真的调到才抛 {@code Invalid bound statement (not found)}。
     * 典型来源是「Java 方法改名了，XML 的 id 忘了改」或者反过来。
     */
    @Test
    @DisplayName("namespace 与语句 id 对得上真实存在的 Mapper 接口（防止改名漏改）")
    void statementsShouldMatchMapperInterfaces() throws Exception {
        List<String> problems = new ArrayList<>();
        int checked = 0;

        for (Path file : mapperFiles()) {
            Configuration configuration = parse(file);

            String namespace = namespaceOf(file);
            if (namespace == null) {
                problems.add(file.getFileName() + " → 没有 namespace 属性");
                continue;
            }
            Class<?> mapper;
            try {
                // initialize=false：只要类在 classpath 上就行，不需要触发静态初始化
                mapper = Class.forName(namespace, false, MapperXmlParseTest.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                problems.add(file.getFileName() + " → namespace 指向的接口不存在: " + namespace);
                continue;
            }
            if (!mapper.isInterface()) {
                problems.add(file.getFileName() + " → namespace 不是接口: " + namespace);
                continue;
            }

            for (String name : configuration.getMappedStatementNames()) {
                // MyBatis 的 StrictMap 里同时存了全名和短名，只认以 namespace 开头的全名
                if (!name.startsWith(namespace + ".")) {
                    continue;
                }
                String methodName = name.substring(namespace.length() + 1);
                if (methodName.contains(".")) {
                    continue;   // XML 里自己写了带点的 id，无法直接对应方法名，跳过
                }
                checked++;
                boolean exists = Arrays.stream(mapper.getMethods())
                        .anyMatch(m -> m.getName().equals(methodName));
                if (!exists) {
                    problems.add(file.getFileName() + " → 语句 id=" + methodName
                            + " 在 " + namespace + " 上没有同名方法"
                            + "（调到该语句时会报 Invalid bound statement）");
                }
            }
        }

        // 防止过滤条件写错导致「一条都没查」却静默通过
        assertTrue(checked > 0, "一条语句都没检查到，说明 namespace 过滤条件写错了");
        assertTrue(problems.isEmpty(),
                "Mapper 接口与 XML 对不上:\n" + String.join("\n", problems));
    }

    @Test
    @DisplayName("全部 XML 一起加载不冲突（更接近 Spring 的真实启动路径）")
    void allMapperXmlsShouldLoadIntoOneConfiguration() throws Exception {
        // 上面那个检查一个文件一个 Configuration，能定位到具体文件，
        // 但看不到「两个文件声明了同一个语句 id」这种跨文件冲突 ——
        // 那同样会让服务启动失败。这里按 Spring 的做法全部塞进同一个 Configuration，
        // 共用同一份 sqlFragments。
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        List<String> failures = new ArrayList<>();
        for (Path file : mapperFiles()) {
            try (InputStream in = Files.newInputStream(file)) {
                new XMLMapperBuilder(in, configuration, file.toString(),
                        configuration.getSqlFragments()).parse();
            } catch (Exception e) {
                failures.add(file.getFileName() + " → " + e.getMessage());
            }
        }
        assertTrue(failures.isEmpty(),
                "以下 Mapper XML 无法与其它 XML 共存于同一个 Configuration:\n"
                        + String.join("\n", failures));
    }

    @Test
    @DisplayName("本轮新增/改动的语句确实被注册（防止改了但没生效）")
    void newStatementsShouldBeRegistered() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        // 这三个 Mapper 在本轮（2026-10-10）为通知开关补了语句
        for (String name : List.of("AppointmentMapper", "NotificationMapper", "UserMapper")) {
            Path file = MAPPER_DIR.resolve(name + ".xml");
            try (InputStream in = Files.newInputStream(file)) {
                new XMLMapperBuilder(in, configuration, file.toString(),
                        configuration.getSqlFragments()).parse();
            } catch (Exception e) {
                fail(name + ".xml 解析失败: " + e.getMessage());
            }
        }

        String appointmentNs = AppointmentMapper.class.getName();
        assertTrue(configuration.hasStatement(appointmentNs + ".queryReminderTargets"),
                "queryReminderTargets 没注册 —— 就诊前一天提醒会查不到目标");
        assertTrue(configuration.hasStatement("cn.kmbeast.mapper.NotificationMapper.countBizNotification"),
                "countBizNotification 没注册 —— 通知去重失效，提醒会重复发");
        assertTrue(configuration.hasStatement("cn.kmbeast.mapper.UserMapper.getNotifyPreference"),
                "getNotifyPreference 没注册 —— 通知开关读不到");
    }

    // ================================================================ 工具

    private static List<Path> mapperFiles() throws Exception {
        assertTrue(Files.isDirectory(MAPPER_DIR),
                "找不到 Mapper 目录，测试工作目录不是模块根目录？实际: "
                        + MAPPER_DIR.toAbsolutePath());
        try (Stream<Path> stream = Files.list(MAPPER_DIR)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * 每个文件用一个全新的 Configuration：避免互相污染，
     * 也让「某个文件的 SQL 片段被另一个文件误引用」这类问题暴露出来。
     */
    private static Configuration parse(Path file) throws Exception {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream in = Files.newInputStream(file)) {
            new XMLMapperBuilder(in, configuration, file.toString(),
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static String namespaceOf(Path file) throws Exception {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Matcher matcher = NAMESPACE.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
