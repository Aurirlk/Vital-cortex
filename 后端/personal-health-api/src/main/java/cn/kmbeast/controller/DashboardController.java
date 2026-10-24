package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.mapper.*;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.NewsQueryDto;
import cn.kmbeast.pojo.dto.query.extend.UserQueryDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理员看板的平台概览统计。
 *
 * <p>2026-10-24（bug.md §7.2-4）：这里原先只有 {@code @Protector}（=「登录即可」），
 * 任何注册用户都能读到平台用户总数；并且前端没有任何调用点 —— 是个挂着注解、
 * 等着被误当成「谁都能调」的死接口。现在补上 {@code role = "管理员"}
 * （口径同 {@code ViewsController.staticControls}），并让它真正服务于管理端看板：
 * 新增「待确认预约数」，这是管理员每天要处理的积压，此前的看板只有存量总数、
 * 看不出「有多少单子在等人处理」。
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    /** 预约状态 0 = 待确认（新建预约由 AppointmentServiceImpl 写入 0，前端状态表同口径） */
    private static final int APPOINTMENT_PENDING = 0;

    @Resource
    private UserMapper userMapper;
    @Resource
    private NewsMapper newsMapper;
    @Resource
    private AppointmentMapper appointmentMapper;

    @Protector(role = "管理员")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("userCount", userMapper.queryCount(new UserQueryDto()));
        stats.put("newsCount", newsMapper.queryCount(new NewsQueryDto()));

        // 一次 GROUP BY 同时拿到总数与「待确认」数，比按状态各发一条 COUNT 少一趟库。
        Map<String, Integer> appointment = new LinkedHashMap<>();
        long total = 0;
        long pending = 0;
        for (Map<String, Object> row : appointmentMapper.countByStatus()) {
            long count = row.get("count") instanceof Number n ? n.longValue() : 0L;
            total += count;
            // status 只认数字：NULL 状态的单子不算「待确认」，免得把脏数据当成待办
            if (row.get("status") instanceof Number s && s.intValue() == APPOINTMENT_PENDING) {
                pending += count;
            }
        }
        appointment.put("total", (int) total);
        appointment.put("pending", (int) pending);
        stats.put("appointment", appointment);

        return ApiResult.success(stats);
    }
}
