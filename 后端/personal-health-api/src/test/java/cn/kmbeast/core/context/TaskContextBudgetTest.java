package cn.kmbeast.core.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 阶段 9：每任务上下文预算（硬红线 ≤ 模型窗口 50%）。 */
@DisplayName("每任务上下文预算")
class TaskContextBudgetTest {

    @Test
    @DisplayName("默认预算守住窗口 50% 红线")
    void defaultsWithinWindowHalf() {
        TaskContextBudget b = TaskContextBudget.defaults();
        assertTrue(b.withinWindowHalf());
        assertTrue(b.getCeiling() <= b.getModelWindow() / 2);
    }

    @Test
    @DisplayName("四个任务按系数递减，且都不超过 ceiling")
    void taskFactorsDescend() {
        TaskContextBudget b = new TaskContextBudget(100000, 30000, true);
        int chat = b.forTask(TaskContextBudget.Task.CHAT);
        int react = b.forTask(TaskContextBudget.Task.REACT);
        int nl2sql = b.forTask(TaskContextBudget.Task.NL2SQL);
        int report = b.forTask(TaskContextBudget.Task.REPORT);
        assertTrue(chat >= react && react >= nl2sql && nl2sql >= report);
        assertEquals(b.getCeiling(), chat);
        assertTrue(report > 0);
    }

    @Test
    @DisplayName("clamp 把调用方放大的请求值收回预算内")
    void clampRejectsAmplification() {
        TaskContextBudget b = new TaskContextBudget(100000, 30000, true);
        assertEquals(b.forTask(TaskContextBudget.Task.REPORT),
                b.clamp(Integer.MAX_VALUE, TaskContextBudget.Task.REPORT));
        assertTrue(b.clamp(999, TaskContextBudget.Task.CHAT) <= b.getCeiling());
    }

    @Test
    @DisplayName("位置门禁开关可透传")
    void positionGateFlag() {
        assertTrue(new TaskContextBudget(100000, 30000, true).isPositionGateEnabled());
        assertFalse(new TaskContextBudget(100000, 30000, false).isPositionGateEnabled());
    }

    /**
     * 反向验证专用：唯一断言「管理员配置超过窗口 50% 时必须被钳制」的用例。
     * 探针去掉钳制时，本类应恰好 1 例真红。
     */
    @Test
    @DisplayName("🔴 配置值不得突破模型窗口 50% 红线")
    void configuredValueCannotExceedWindowHalf() {
        TaskContextBudget b = new TaskContextBudget(1000, 999999, true);
        assertEquals(500, b.getCeiling(), "模型窗口 1000 → 硬上限 500，配置 999999 只能收窄不能放大");
        assertTrue(b.withinWindowHalf());
    }
}
