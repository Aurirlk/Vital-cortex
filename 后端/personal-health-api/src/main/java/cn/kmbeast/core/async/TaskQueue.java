package cn.kmbeast.core.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 任务队列
 * 异步任务管理
 */
@Slf4j
@Component
public class TaskQueue {

    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private final Map<String, Future<?>> taskFutures = new ConcurrentHashMap<>();

    /**
     * 提交异步任务
     */
    public String submitTask(String taskId, Runnable task) {
        Future<?> future = executor.submit(() -> {
            try {
                task.run();
                log.info("Task completed: {}", taskId);
            } catch (Exception e) {
                log.error("Task failed: {}", taskId, e);
            }
        });
        taskFutures.put(taskId, future);
        return taskId;
    }

    /**
     * 检查任务状态
     */
    public boolean isTaskDone(String taskId) {
        Future<?> future = taskFutures.get(taskId);
        return future == null || future.isDone();
    }

    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId) {
        Future<?> future = taskFutures.get(taskId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
            taskFutures.remove(taskId);
            return true;
        }
        return false;
    }
}
