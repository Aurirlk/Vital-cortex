package cn.kmbeast.core.cost;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 成本追踪器
 * 记录 AI 调用的成本
 */
@Slf4j
@Component
public class CostTracker {

    private final Map<String, AtomicLong> tokenUsage = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> callCount = new ConcurrentHashMap<>();

    public void recordUsage(String provider, long tokens) {
        tokenUsage.computeIfAbsent(provider, k -> new AtomicLong(0)).addAndGet(tokens);
        callCount.computeIfAbsent(provider, k -> new AtomicLong(0)).incrementAndGet();
    }

    public long getTotalTokens(String provider) {
        return tokenUsage.getOrDefault(provider, new AtomicLong(0)).get();
    }

    public long getTotalCalls(String provider) {
        return callCount.getOrDefault(provider, new AtomicLong(0)).get();
    }

    public Map<String, Long> getSummary() {
        Map<String, Long> summary = new java.util.HashMap<>();
        tokenUsage.forEach((k, v) -> summary.put(k + "_tokens", v.get()));
        callCount.forEach((k, v) -> summary.put(k + "_calls", v.get()));
        return summary;
    }
}
