package cn.kmbeast.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「Spring 映射注解被行注释吞掉」的防回归测试。
 *
 * <h3>要防的是什么</h3>
 * <p>2026-10-12 实测事故：{@code 9baa4ea} 提交把类级注解黏到了注释行尾，形成
 * <pre>// …改用项目统一的 LocalThreadHolder。&#64;RequestMapping("/notification")</pre>
 * 注解随注释一起被编译器忽略，4 个控制器的路由前缀<b>整体消失</b>，端点上浮到根路径并产生
 * {@code POST /save} 等同名冲突 —— Spring 在 {@code RequestMappingHandlerMapping}
 * 初始化阶段会抛 {@code Ambiguous mapping}，<b>容器直接启动失败</b>。
 *
 * <p>这类缺陷的特性：<b>编译通过、684 个单测全绿、只有真启动才炸</b>。
 * 故本用例用纯文本扫描把不变量固化下来，不依赖 Spring 上下文，跑得极快。
 *
 * <h3>判据</h3>
 * <p>任何映射注解（{@code @RequestMapping} / {@code @GetMapping} / …）<b>不得出现
 * 在同一行的 {@code //} 之后</b> —— 那等于它永远不会生效。
 */
class MappingAnnotationCommentGuardTest {

    /** 会被注释吞掉且后果严重的 Spring 映射注解。 */
    private static final Pattern MAPPING_ANNOTATION =
            Pattern.compile("@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\\b");

    /** 行注释起始 —— 不考虑字符串字面量里的 "//"（本仓库控制器里没有这种写法）。 */
    private static final Pattern LINE_COMMENT = Pattern.compile("//");

    @Test
    @DisplayName("任何映射注解都不得落在行注释之后（否则路由前缀会静默消失）")
    void mappingAnnotationsMustNotBeSwallowedByLineComment() throws IOException {
        Path srcRoot = Paths.get("src", "main", "java");
        assertThat(Files.isDirectory(srcRoot))
                .as("测试需在模块根目录下运行（%s 不存在）", srcRoot.toAbsolutePath())
                .isTrue();

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(srcRoot)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    Matcher ann = MAPPING_ANNOTATION.matcher(line);
                    if (!ann.find()) {
                        continue;
                    }
                    Matcher cmt = LINE_COMMENT.matcher(line);
                    if (cmt.find() && cmt.start() < ann.start()) {
                        offenders.add(srcRoot.relativize(p) + ":" + (i + 1) + " → " + line.trim());
                    }
                }
            }
        }

        assertThat(offenders)
                .as("以下映射注解被行注释吞掉，运行时不会生效。请把注解拆到独立一行：\n%s",
                        String.join("\n", offenders))
                .isEmpty();
    }
}
