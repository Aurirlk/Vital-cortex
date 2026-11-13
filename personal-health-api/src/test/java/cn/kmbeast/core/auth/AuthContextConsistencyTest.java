package cn.kmbeast.core.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鉴权上下文获取方式的<b>防回归</b>测试。
 *
 * <p><b>背景（2026-10-07 审计发现）</b>：项目统一用 {@code LocalThreadHolder} 传递身份
 * （由 {@code JwtInterceptor} 写入、{@code afterCompletion} 清理）。但 4 个 Controller 共有
 * <b>13 个端点</b>用了 {@code @RequestAttribute("userId")} 取身份 ——
 * 而<b>全项目没有任何一处 {@code setAttribute("userId", ...)}</b>，
 * {@code JwtInterceptor} 只写 {@code LocalThreadHolder}。
 * 结果：这些端点必然抛 {@code MissingRequestAttributeException}
 * （被 {@code GlobalExceptionHandler} 的 {@code Exception} 分支兜住，不 500 但功能失效）。
 *
 * <p>实测受害面：{@code /notification/list}、{@code /notification/unread}、
 * {@code /notification/readAll}、{@code /quiz/start/{examId}}、{@code /quiz/records}
 * —— 前端 {@code NotificationBell.vue} 与 {@code Quiz.vue} 都在正常调用，
 * 即通知铃铛与答题记录在用户端<b>完全不可用</b>。</p>
 *
 * <p>本测试扫描 {@code target/classes} 下全部 Controller，防止将来再有人写回
 * {@code @RequestAttribute} 取身份 —— 那是一个编译期合法、运行期必然失败的写法，
 * 靠 code review 很难拦住。</p>
 */
class AuthContextConsistencyTest {

    @Test
    @DisplayName("全仓 Controller 不得用 @RequestAttribute(\"userId\") 取身份（应统一用 LocalThreadHolder）")
    void noControllerUsesRequestAttributeForUserId() throws Exception {
        Path root = Paths.get("target", "classes");
        assertTrue(Files.isDirectory(root), "target/classes 不存在，无法扫描");

        List<String> violations = new ArrayList<>();
        int scanned = 0;

        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> classFiles = stream
                    .filter(p -> p.toString().endsWith("Controller.class"))
                    .collect(java.util.stream.Collectors.toList());

            for (Path p : classFiles) {
                String className = root.relativize(p).toString()
                        .replace(File.separatorChar, '.')
                        .replaceAll("\\.class$", "");
                Class<?> clazz;
                try {
                    clazz = Class.forName(className);
                } catch (Throwable t) {
                    continue;   // 依赖缺失的类跳过，不影响本条断言
                }
                scanned++;
                for (Method m : clazz.getDeclaredMethods()) {
                    for (Parameter param : m.getParameters()) {
                        RequestAttribute ra = param.getAnnotation(RequestAttribute.class);
                        if (ra != null && "userId".equals(ra.value())) {
                            violations.add(clazz.getSimpleName() + "#" + m.getName()
                                    + "(参数 " + param.getName() + ")");
                        }
                    }
                }
            }
        }

        assertTrue(scanned > 0, "未扫描到任何 Controller，测试本身可能失效");
        assertTrue(violations.isEmpty(),
                "以下端点用 @RequestAttribute(\"userId\") 取身份，但项目无任何 setAttribute 注入，"
                        + "运行期必然抛 MissingRequestAttributeException：\n  " + String.join("\n  ", violations));
    }

    @Test
    @DisplayName("JwtInterceptor 与 InterceptorConfig 的白名单必须一致（防遗漏 /doctor/login）")
    void whiteListIsConsistent() throws Exception {
        // 医生端独立登录入口。此前它只写在 JwtInterceptor 内部，InterceptorConfig 里看不到，
        // 维护时极易误判「医生登录也需要登录态」而把它挪进拦截链。
        String interceptor = Files.readString(
                Paths.get("src", "main", "java", "cn", "kmbeast", "Interceptor", "JwtInterceptor.java"),
                java.nio.charset.StandardCharsets.UTF_8);
        String config = Files.readString(
                Paths.get("src", "main", "java", "cn", "kmbeast", "config", "InterceptorConfig.java"),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(interceptor.contains("/doctor/login"), "JwtInterceptor 应放行 /doctor/login");
        assertTrue(config.contains("/doctor/login"),
                "InterceptorConfig 的 excludePathPatterns 也必须列出 /doctor/login —— "
                        + "两处白名单必须同步，否则配置上看不到这个匿名入口");
    }

    /**
     * {@code /ai/config} URL 空间必须只由一个控制器声明。
     *
     * <p><b>背景（2026-10-07 审计发现并已修复）</b>：{@code /ai/config} 曾被两个控制器瓜分 ——
     * {@code AiConfigController}（类级前缀 {@code /ai/config}）与 {@code AiController}
     * （类级前缀 {@code /ai}，但方法上写了 {@code /config/xxx}）。路径虽不冲突，
     * 但管理员端点与普通用户端点混在同一 URL 空间、分散在两个类里，
     * 以后加端点极易漏配 {@code @Protector}。已全部迁入 {@code AiConfigController}。</p>
     */
    @Test
    @DisplayName("/ai/config URL 空间只允许 AiConfigController 一个类声明（防再次瓜分）")
    void aiConfigUrlSpaceOwnedBySingleController() throws Exception {
        Path root = Paths.get("target", "classes");
        java.util.Map<String, String> owners = new java.util.LinkedHashMap<>();

        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> classFiles = stream
                    .filter(p -> p.toString().endsWith("Controller.class"))
                    .collect(java.util.stream.Collectors.toList());
            for (Path p : classFiles) {
                String cn = root.relativize(p).toString()
                        .replace(File.separatorChar, '.')
                        .replaceAll("\\.class$", "");
                Class<?> clazz;
                try {
                    clazz = Class.forName(cn);
                } catch (Throwable t) {
                    continue;
                }
                RequestMapping rm = clazz.getAnnotation(RequestMapping.class);
                String base = (rm != null && rm.value().length > 0) ? rm.value()[0] : "";
                for (Method m : clazz.getDeclaredMethods()) {
                    String full = fullPath(m, base);
                    if (full.startsWith("/ai/config")) {
                        owners.put(full, clazz.getSimpleName());
                    }
                }
            }
        }

        assertFalse(owners.isEmpty(), "未扫描到任何 /ai/config 端点，测试本身可能失效");

        java.util.Set<String> distinct = new java.util.LinkedHashSet<>(owners.values());
        assertEquals(1, distinct.size(),
                "/ai/config/* 必须只由一个控制器声明，实际被这些类瓜分：" + distinct
                        + "（端点归属：" + owners + "）");
        assertEquals("AiConfigController", distinct.iterator().next());
    }

    /** 组合类级 @RequestMapping 前缀与方法级路径，得到完整 URL。 */
    private static String fullPath(Method m, String base) {
        GetMapping get = m.getAnnotation(GetMapping.class);
        PostMapping post = m.getAnnotation(PostMapping.class);
        PutMapping put = m.getAnnotation(PutMapping.class);
        DeleteMapping del = m.getAnnotation(DeleteMapping.class);
        String sub = null;
        if (get != null && get.value().length > 0) {
            sub = get.value()[0];
        } else if (post != null && post.value().length > 0) {
            sub = post.value()[0];
        } else if (put != null && put.value().length > 0) {
            sub = put.value()[0];
        } else if (del != null && del.value().length > 0) {
            sub = del.value()[0];
        }
        if (sub == null || sub.isEmpty()) {
            return base.replaceAll("/+$", "");
        }
        return (base + "/" + sub).replaceAll("/{2,}", "/").replaceAll("/+$", "");
    }
}
