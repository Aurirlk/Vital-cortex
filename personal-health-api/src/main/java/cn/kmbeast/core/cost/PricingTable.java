package cn.kmbeast.core.cost;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 定价表
 * 各 AI 厂商的 Token 定价
 */
@Component
public class PricingTable {

    private static final Map<String, Map<String, Double>> PRICING = new HashMap<>();

    static {
        // DeepSeek
        Map<String, Double> deepseek = new HashMap<>();
        deepseek.put("input", 0.001);
        deepseek.put("output", 0.002);
        PRICING.put("deepseek", deepseek);

        // 通义千问
        Map<String, Double> qwen = new HashMap<>();
        qwen.put("input", 0.002);
        qwen.put("output", 0.006);
        PRICING.put("qwen", qwen);

        // 默认
        Map<String, Double> defaultPricing = new HashMap<>();
        defaultPricing.put("input", 0.001);
        defaultPricing.put("output", 0.002);
        PRICING.put("default", defaultPricing);
    }

    public double calculateCost(String provider, long inputTokens, long outputTokens) {
        Map<String, Double> pricing = PRICING.getOrDefault(provider, PRICING.get("default"));
        return (inputTokens * pricing.get("input") + outputTokens * pricing.get("output")) / 1000.0;
    }
}
