package cn.kmbeast.core.voice;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 语音合成专用线程池（2026-10-03 新增）
 *
 * <p><b>解决什么问题</b>：{@code TTSFactory} / {@code EdgeTTSProvider} / {@code VoiceServiceImpl}
 * 三处 {@code CompletableFuture.supplyAsync(...)} 未传 Executor，会落到
 * {@link java.util.concurrent.ForkJoinPool#commonPool()}。而任务体是<b>阻塞式</b>的
 * OkHttp 同步网络调用（合成一段语音通常数秒），一旦并��上来就会：
 * <ul>
 *   <li>占满 commonPool，拖垮<b>其他</b>所有使用 ForkJoin 的业务</li>
 *   <li>commonPool 并行度 = CPU核数-1，容器里可能只有 1~2 个线程</li>
 * </ul>
 *
 * <p><b>本线程池的设计</b>：
 * <ul>
 *   <li>有界队列 + 明确的拒绝策略，避免任务无限堆积导致 OOM</li>
 *   <li>拒绝时回退到调用线程同步执行（降级不丢任务），
 *       宁可让这一次请求变慢，也不能让用户的语音请求直接失败</li>
 *   <li>{@link PreDestroy} 关闭，防止线程泄漏</li>
 * </ul>
 */
@Slf4j
public final class VoiceSynthesisExecutor {

    /** 核心线程数：语音合成以网络 I/O 为主，可适当大于 CPU 核数 */
    private static final int CORE_POOL_SIZE = 8;

    /** 最大线程数 */
    private static final int MAX_POOL_SIZE = 16;

    /** 队列容量：超出后触发拒绝策略（降级为调用线程执行） */
    private static final int QUEUE_CAPACITY = 64;

    /** 空闲线程存活时间 */
    private static final long KEEP_ALIVE_SECONDS = 60L;

    private static volatile ThreadPoolExecutor instance;

    private VoiceSynthesisExecutor() {
    }

    /**
     * 获取全局单例线程池（懒加载 + 双重检查锁）
     */
    public static ThreadPoolExecutor getInstance() {
        if (instance == null) {
            synchronized (VoiceSynthesisExecutor.class) {
                if (instance == null) {
                    instance = build();
                }
            }
        }
        return instance;
    }

    private static ThreadPoolExecutor build() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "voice-tts-" + counter.getAndIncrement());
                // 守护线程：不阻止 JVM 退出
                t.setDaemon(true);
                return t;
            }
        };

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                factory,
                // 关键：队列满时由调用线程自己执行（CallerRunsPolicy），
                // 保证语音合成请求不会因为池满而直接失败，只是延迟返回。
                new ThreadPoolExecutor.CallerRunsPolicy());

        pool.allowCoreThreadTimeOut(false);
        log.info("[Voice] 语音合成线程池已初始化: core={}, max={}, queue={}, 拒绝策略=CallerRuns",
                CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY);
        return pool;
    }

    /**
     * 预热：提前创建核心线程，避免首次调用因建线程而延迟。
     *
     * <p>⚠️ 本类是 final + 静态方法的工具类，<b>不是 Spring Bean</b>，
     * {@code @PostConstruct} 不会生效，因此由 {@link VoiceExecutorLifecycle} 在启动时调用。
     */
    public static void warmUp() {
        getInstance();
    }

    /**
     * 应用关闭时优雅停机。
     *
     * <p>同上，由 {@link VoiceExecutorLifecycle} 的 {@code @PreDestroy} 调用。
     */
    public static void shutdown() {
        ThreadPoolExecutor pool = instance;
        if (pool == null || pool.isShutdown()) {
            return;
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                log.warn("[Voice] 语音合成线程池未在 10s 内关闭，强制中断");
            } else {
                log.info("[Voice] 语音合成线程池已关闭");
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
