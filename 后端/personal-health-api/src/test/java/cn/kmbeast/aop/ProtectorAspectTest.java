package cn.kmbeast.aop;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;

/**
 * 鉴权切面单元测试（2026-10-04 构建）
 *
 * <p>{@code ProtectorAspect} 是三端（管理员 / 用户 / 医生）隔离的唯一执行点。
 * 一旦它的角色比对失效，越权即可发生，因此本测试覆盖：
 * <ul>
 *   <li>无 token / 非法 token → 拒绝，且不执行业务方法</li>
 *   <li>{@code @Protector}（无角色）→ 仅校验登录</li>
 *   <li>{@code @Protector(role="管理员")} → 用户/医生不得越权</li>
 *   <li>{@code @Protector(role="医生")} → 管理员/用户不得越权</li>
 *   <li>token 缺 role 声明时返回可读的鉴权失败，而不是抛 500</li>
 *   <li>ThreadLocal 必须在 finally 中清理（否则线程池复用会串号）</li>
 * </ul>
 *
 * <p>注意：这里不打 Spring 容器，直接 mock 切面依赖，验证纯逻辑分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("鉴权切面（三端隔离）")
class ProtectorAspectTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private ProtectorAspect aspect;

    private MockHttpServletRequest request;

    // 三个被切面读取的目标方法
    private static Object noRoleTarget() { return null; }
    @Protector
    private void endpointNoRole() { }

    @Protector(role = "管理员")
    private void endpointAdmin() { }

    @Protector(role = "医生")
    private void endpointDoctor() { }

    @BeforeEach
    void setUp() throws Throwable {
        aspect = new ProtectorAspect();
        // 注入 mock 的 JwtUtil（字段是 @Resource 私有注入，测试里直接反射写入）
        java.lang.reflect.Field f = ProtectorAspect.class.getDeclaredField("jwtUtil");
        f.setAccessible(true);
        f.set(aspect, jwtUtil);

        request = new MockHttpServletRequest();
        request.setRequestURI("/api/personal-health/v1.0/test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(signature);
        // proceed() 声明抛 checked Throwable，用 doReturn().when() 而非 when().thenReturn()：
        // 后者在 proceed() 抛受检异常的语境下会留下未完成的 stubbing，
        // 导致后续所有 when() 报 UnfinishedStubbingException。
        doReturn("PROCEEDED").when(joinPoint).proceed();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        LocalThreadHolder.clear();
    }

    /** 让切面从目标方法上读到指定的 @Protector 声明 */
    private void bindAnnotationTo(String methodName) throws Exception {
        Method m = findMethod(methodName);
        when(signature.getMethod()).thenReturn(m);
    }

    private Method findMethod(String name) throws NoSuchMethodException {
        for (Method m : ProtectorAspectTest.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) return m;
        }
        throw new NoSuchMethodException(name);
    }

    /** 造一个 claims 解析结果 */
    private Claims claimsOf(Integer id, Integer role) {
        Claims c = mock(Claims.class);
        when(c.get("id", Integer.class)).thenReturn(id);
        when(c.get("role", Integer.class)).thenReturn(role);
        return c;
    }

    private void withToken(String token, Integer id, Integer role) {
        request.addHeader("token", token);
        // ⚠️ 必须先构造好 claims 并完成其全部 stubbing，再 stub fromToken。
        // 反过来写（when(fromToken).thenReturn(claimsOf(...))）会在外层 when 未完成时
        // 触发内层 when，Mockito 抛 UnfinishedStubbingException。
        Claims c = claimsOf(id, role);
        when(jwtUtil.fromToken(token)).thenReturn(c);
    }

    // ---------------------------------------------------------------- 未登录

    @Test
    @DisplayName("无 token：拒绝访问且不执行业务方法")
    void noTokenShouldReject() throws Throwable {
        bindAnnotationTo("endpointAdmin");

        Object r = aspect.auth(joinPoint);

        assertTrue(r instanceof Result, "鉴权失败应返回 Result 而不是抛异常");
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("非法/被篡改 token：jwtUtil 返回 null 时拒绝")
    void invalidTokenShouldReject() throws Throwable {
        request.addHeader("token", "garbage.token.value");
        when(jwtUtil.fromToken("garbage.token.value")).thenReturn(null);
        bindAnnotationTo("endpointAdmin");

        Object r = aspect.auth(joinPoint);

        assertTrue(r instanceof Result);
        verify(joinPoint, never()).proceed();
    }

    // ---------------------------------------------------------------- 仅需登录

    @Test
    @DisplayName("@Protector（无角色）：任意已登录角色放行，并把身份写入 ThreadLocal")
    void anyLoggedInRoleShouldPassWhenNoRoleRequired() throws Throwable {
        withToken("t-user", 7, 2);
        bindAnnotationTo("endpointNoRole");

        // 关键：切面在 finally 里会清 ThreadLocal，所以必须在「业务方法执行期间」取值，
        // auth() 返回后再读必然已被清理（那正是我们想要的语义）。
        final Integer[] captured = new Integer[2];
        doAnswer(inv -> {
            captured[0] = LocalThreadHolder.getUserId();
            captured[1] = LocalThreadHolder.getRoleId();
            return "PROCEEDED";
        }).when(joinPoint).proceed();

        Object r = aspect.auth(joinPoint);

        assertEquals("PROCEEDED", r, "无角色声明的端点只校验登录态");
        assertEquals(7, captured[0], "业务方法执行期间应能读到用户 ID");
        assertEquals(2, captured[1], "业务方法执行期间应能读到角色");
        // auth 返回后 finally 已清理
        assertNull(LocalThreadHolder.getUserId(), "切面返回后必须已清理 ThreadLocal");
    }

    // ---------------------------------------------------------------- 角色隔离

    @Test
    @DisplayName("@Protector(role=管理员)：普通用户越权被拒")
    void normalUserCannotAccessAdminEndpoint() throws Throwable {
        withToken("t-user", 7, 2);
        bindAnnotationTo("endpointAdmin");

        Object r = aspect.auth(joinPoint);

        assertTrue(r instanceof Result, "用户访问管理员端点必须被拒");
        Result<?> res = (Result<?>) r;
        assertTrue(res.getMsg().contains("无操作权限"),
                "应为角色不匹配提示，实际: " + res.getMsg());
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("@Protector(role=管理员)：医生越权被拒（医生不等于管理员）")
    void doctorCannotAccessAdminEndpoint() throws Throwable {
        withToken("t-doc", 3001, 3);
        bindAnnotationTo("endpointAdmin");

        Object r = aspect.auth(joinPoint);

        assertTrue(r instanceof Result);
        assertTrue(((Result<?>) r).getMsg().contains("无操作权限"));
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("@Protector(role=医生)：管理员不能冒充医生")
    void adminCannotAccessDoctorEndpoint() throws Throwable {
        withToken("t-admin", 1, 1);
        bindAnnotationTo("endpointDoctor");

        Object r = aspect.auth(joinPoint);

        assertTrue(r instanceof Result);
        assertTrue(((Result<?>) r).getMsg().contains("无操作权限"));
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("@Protector(role=医生)：医生本人正常放行")
    void doctorCanAccessDoctorEndpoint() throws Throwable {
        withToken("t-doc", 3001, 3);
        bindAnnotationTo("endpointDoctor");

        final Integer[] captured = new Integer[1];
        doAnswer(inv -> {
            captured[0] = LocalThreadHolder.getRoleId();
            return "PROCEEDED";
        }).when(joinPoint).proceed();

        Object r = aspect.auth(joinPoint);

        assertEquals("PROCEEDED", r);
        assertEquals(3, captured[0], "医生端点内应读到 role=3");
    }

    @Test
    @DisplayName("@Protector(role=管理员)：管理员本人正常放行")
    void adminCanAccessAdminEndpoint() throws Throwable {
        withToken("t-admin", 1, 1);
        bindAnnotationTo("endpointAdmin");

        assertEquals("PROCEEDED", aspect.auth(joinPoint));
    }

    // ---------------------------------------------------------------- 健壮性

    @Test
    @DisplayName("token 缺 role 声明：返回可读鉴权失败，而不是抛 NPE 变成 500")
    void missingRoleClaimShouldNotThrow() throws Throwable {
        request.addHeader("token", "t-norole");
        Claims c = claimsOf(7, null);
        doReturn(c).when(jwtUtil).fromToken("t-norole");
        bindAnnotationTo("endpointAdmin");

        Object r = aspect.auth(joinPoint);
        // 关键：不能抛异常
        assertTrue(r instanceof Result);
        assertTrue(((Result<?>) r).getMsg().contains("重新登录"),
                "应提示重新登录，实际: " + ((Result<?>) r).getMsg());
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("非法角色编码（如 role=99）：拒绝放行，不得因枚举缺失而 NPE")
    void unknownRoleCodeShouldBeRejected() throws Throwable {
        withToken("t-weird", 7, 99);
        bindAnnotationTo("endpointAdmin");

        Object r = aspect.auth(joinPoint);

        assertTrue(r instanceof Result);
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("业务方法抛异常时，ThreadLocal 仍被清理（finally 生效）")
    void threadLocalMustBeClearedOnException() throws Throwable {
        withToken("t-user", 7, 2);
        bindAnnotationTo("endpointNoRole");
        doThrow(new IllegalStateException("业务异常")).when(joinPoint).proceed();

        assertThrows(IllegalStateException.class, () -> aspect.auth(joinPoint));
        // 线程复用时若残留身份，会导致下一个请求被当作已登录 —— 必须为空
        assertNull(LocalThreadHolder.getUserId(), "异常路径必须清理 ThreadLocal");
    }

    @Test
    @DisplayName("鉴权失败时 ThreadLocal 不应残留")
    void threadLocalNotSetOnRejection() throws Throwable {
        withToken("t-user", 7, 2);
        bindAnnotationTo("endpointAdmin");

        aspect.auth(joinPoint);

        assertNull(LocalThreadHolder.getUserId(), "被拒绝的请求不得留下身份");
    }
}
