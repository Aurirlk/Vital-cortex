package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.mapper.AppointmentMapper;
import cn.kmbeast.mapper.NewsMapper;
import cn.kmbeast.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 管理员看板统计接口测试（2026-10-24，bug.md §7.2-4）
 *
 * <p>两条不变量：
 * <ol>
 *   <li><b>角色必须是管理员</b>。这里曾是「登录即可」，任何注册用户都能读平台用户总数；
 *       注解被人改回 {@code @Protector} 是不会有人报错的，故用反射把要求固化下来。</li>
 *   <li><b>「待确认」只能来自 status=0 那一桶</b>。统计走的是
 *       {@code GROUP BY status}，JDBC 回来的 status/count 具体是 Integer 还是 Long
 *       由驱动决定，按字符串或按「第一行」取数都会算错管理员看到的积压量。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("管理员看板统计（/dashboard/stats）")
class DashboardControllerStatsTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private NewsMapper newsMapper;

    @Mock
    private AppointmentMapper appointmentMapper;

    @InjectMocks
    private DashboardController controller;

    /** 造一行 GROUP BY 结果：key 名必须与 AppointmentMapper.xml 的列别名一致 */
    private static Map<String, Object> row(Object status, Object count) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("count", count);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> appointmentOf(Map<String, Object> stats) {
        return (Map<String, Object>) stats.get("appointment");
    }

    // ================================================================ 角色门槛

    @Test
    @DisplayName("端点必须限管理员角色，不能退回「登录即可」")
    void statsEndpointMustRequireAdminRole() throws NoSuchMethodException {
        Method m = DashboardController.class.getMethod("getStats");
        Protector protector = m.getAnnotation(Protector.class);

        assertNotNull(protector, "丢了 @Protector 等于任何请求都能读平台统计");
        assertEquals("管理员", protector.role(),
                "平台级统计只应服务管理员看板；写成空串就是登录即可");
    }

    // ================================================================ 预约积压

    @Test
    @DisplayName("待确认只取 status=0 那一桶，总数是各桶相加")
    void pendingComesOnlyFromStatusZeroBucket() {
        when(appointmentMapper.countByStatus()).thenReturn(List.of(
                row(0, 7L), row(1, 5L), row(4, 2L)));

        Map<String, Object> appointment = appointmentOf(controller.getStats().getData());

        assertEquals(7, appointment.get("pending"), "只有待确认(0)算积压");
        assertEquals(14, appointment.get("total"));
    }

    @Test
    @DisplayName("status 为 NULL 的脏数据不计入待确认，但仍计入总数")
    void nullStatusMustNotBeCountedAsPending() {
        when(appointmentMapper.countByStatus()).thenReturn(List.of(
                row(null, 3L), row(0, 1L)));

        Map<String, Object> appointment = appointmentOf(controller.getStats().getData());

        assertEquals(1, appointment.get("pending"),
                "NULL 状态被当成 0 会让管理员看到根本不存在的积压");
        assertEquals(4, appointment.get("total"));
    }

    @Test
    @DisplayName("驱动返回 Byte / Integer / Long 都要认（按类型取数，不按字符串）")
    void anyNumericJdbcTypeShouldBeHandled() {
        when(appointmentMapper.countByStatus()).thenReturn(List.of(
                row((byte) 0, 4), row(1L, 6L)));

        Map<String, Object> appointment = appointmentOf(controller.getStats().getData());

        assertEquals(4, appointment.get("pending"));
        assertEquals(10, appointment.get("total"));
    }

    @Test
    @DisplayName("空表：两个数都是 0，不是 null（前端据此显示 0 而不是「—」）")
    void emptyTableShouldYieldZeros() {
        when(appointmentMapper.countByStatus()).thenReturn(List.of());

        Map<String, Object> appointment = appointmentOf(controller.getStats().getData());

        assertEquals(0, appointment.get("pending"));
        assertEquals(0, appointment.get("total"));
    }

    // ================================================================ 存量计数

    @Test
    @DisplayName("用户数与资讯数照常返回")
    void platformCountsShouldBePresent() {
        when(userMapper.queryCount(any())).thenReturn(128);
        when(newsMapper.queryCount(any())).thenReturn(36);
        when(appointmentMapper.countByStatus()).thenReturn(List.of(row(0, 2L)));

        Map<String, Object> stats = controller.getStats().getData();

        assertEquals(128, stats.get("userCount"));
        assertEquals(36, stats.get("newsCount"));
    }
}
