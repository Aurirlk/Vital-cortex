package cn.kmbeast.core.context;

import cn.kmbeast.config.AiConfig;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 上下文窗口预算管理（Phase A，设计见 docs/multimodal-design.md §4.3）。
 *
 * <p>职责：估算组装好的 messages（含图片）的 token 用量，与「配置预算 ∩ 模型上下文上限」
 * 比对，超限抛业务可读异常。Phase A 暂不区分 VIP，统一使用 {@code ai.context.normal}；
 * VIP 512K 分级窗口（roadmap §1.2 Phase B）在此类上扩展。
 */
@Slf4j
@Component
public class TokenBudgetManager {

    @Resource
    private AiConfig aiConfig;

    /**
     * 文本 token 粗估：中文为主场景，1 字符 ≈ 0.6~1.2 token，取 0.6 中值。
     */
    public int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() * 0.6));
    }

    /**
     * 估算 messages 数组总 token（含图片部分，按 tokens-per-image 计数）。
     */
    public int estimateMessagesTokens(JSONArray messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < messages.size(); i++) {
            JSONObject msg = messages.getJSONObject(i);
            if (msg == null) {
                continue;
            }
            Object content = msg.get("content");
            if (content instanceof String) {
                total += estimateTextTokens((String) content);
            } else if (content instanceof JSONArray) {
                // 多模态 content：[{type:text},{type:image_url},...]
                JSONArray parts = (JSONArray) content;
                for (int j = 0; j < parts.size(); j++) {
                    JSONObject part = parts.getJSONObject(j);
                    if (part == null) {
                        continue;
                    }
                    if ("text".equals(part.getString("type"))) {
                        total += estimateTextTokens(part.getString("text"));
                    } else if ("image_url".equals(part.getString("type"))) {
                        total += aiConfig.getTokensPerImage();
                    }
                }
            }
        }
        return total;
    }

    /**
     * 预算校验：超限抛 IllegalArgumentException（chat 与 chatStream 均作为业务错误回传）。
     * Phase B（roadmap §1.2）：预算 = min(用户档位预算, 模型上下文上限)，VIP 512K / 非 VIP 128K。
     *
     * @param messages  已组装的 messages（含图片）
     * @param scene     场景名（用于日志，如 chat / chatStream）
     * @param vip       是否 VIP（决定 128K / 512K 档；VIP 档仅对声明 ≥512K 的模型生效）
     */
    public void assertWithinBudget(JSONArray messages, String scene, boolean vip) {
        int modelMax = aiConfig.getModelMaxContext();
        int userBudget = vip ? aiConfig.getContextVip() : aiConfig.getContextNormal();
        int budget = Math.min(userBudget, modelMax);
        int estimated = estimateMessagesTokens(messages);
        if (estimated > budget) {
            throw new IllegalArgumentException(
                    "上下文过长（约 " + estimated + " token，当前上限 " + budget
                            + "），请开启新会话或减少图片数量");
        }
        if (log.isDebugEnabled()) {
            log.debug("[TokenBudget] {} 估算 {} token / 预算 {}（{}）",
                    scene, estimated, budget, vip ? "VIP" : "普通");
        }
    }
}
