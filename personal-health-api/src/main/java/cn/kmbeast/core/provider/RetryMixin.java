package cn.kmbeast.core.provider;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * 重试机制
 * 提供可配置的重试策略
 */
@Slf4j
public class RetryMixin {

    /**
     * 带重试的执行
     */
    public static <T> T executeWithRetry(Supplier<T> action, int maxRetries, long delayMs) {
        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                lastException = e;
                log.warn("Retry attempt {}/{}: {}", attempt + 1, maxRetries + 1, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(delayMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                }
            }
        }
        throw new RuntimeException("Max retries exceeded", lastException);
    }
}
