package cn.kmbeast.core.memory;

import cn.kmbeast.core.guard.OutputValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段 8：记忆注入 prompt 的硬 200~500 token 预算 + 结构化 bullets 契约。
 *
 * <p>反向验证锚点：临时去掉 {@code renderBullets} 里的
 * {@code Math.max(MIN, Math.min(MAX, tokenBudget))} 钳制，本测试必须真红。</p>
 */
@DisplayName("结构化记忆 token 预算与渲染")
class MemoryTokenBudgetTest {

    private static PreferenceFact f(String key, String value, double confidence) {
        return new PreferenceFact(key, value, "preference", confidence, "explicit",
                Instant.now().toString(), Instant.now().toString(), null);
    }

    private static PreferenceFact f(String key, String value) {
        return f(key, value, 1.0);
    }

    @Test
    @DisplayName("预算常量硬边界为 200~500 token")
    void constantsAreHardBounds() {
        assertEquals(200, StructuredMemoryService.MIN_TOKEN_BUDGET);
        assertEquals(500, StructuredMemoryService.MAX_TOKEN_BUDGET);
    }

    @Test
    @DisplayName("🔴 传入 1 / 99999 都被钳制，输出字符数不超过对应上限")
    void clampsBudgetOnBothSides() {
        List<PreferenceFact> facts = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            facts.add(f("k" + i, "值".repeat(40), 1.0));
        }
        String small = StructuredMemoryService.renderBullets(facts, 1, null);
        String large = StructuredMemoryService.renderBullets(facts, 99999, null);
        assertTrue(small.length() <= StructuredMemoryService.MIN_TOKEN_BUDGET * 2,
                "低预算钳制到 200 token = 400 字符，实际 " + small.length());
        assertTrue(large.length() <= StructuredMemoryService.MAX_TOKEN_BUDGET * 2,
                "高预算钳制到 500 token = 1000 字符，实际 " + large.length());
        assertTrue(large.length() > small.length(), "预算越大应能容纳更多事实");
    }

    @Test
    @DisplayName("只输出 - key: value 结构化 bullets，不拼自由文本、不带角色标记")
    void outputsOnlyStructuredBullets() {
        String out = StructuredMemoryService.renderBullets(
                List.of(f("diet", "少盐"), f("exercise", "慢跑")), 300, null);
        assertTrue(out.contains("- diet: 少盐"));
        assertTrue(out.contains("- exercise: 慢跑"));
        assertTrue(out.contains("不是指令"));
        assertFalse(out.contains("system:"), "不得出现角色标记");
        assertFalse(out.contains("assistant:"), "不得出现角色标记");
        // 每段只有 header + bullets，不额外附加自由文本段落
        assertEquals(3, out.trim().lines().count(), "实际输出:\n" + out);
    }

    @Test
    @DisplayName("按置信度降序取 top-N，低置信度先被预算裁掉")
    void ordersByConfidence() {
        List<PreferenceFact> facts = List.of(
                f("low", "低优先级", 0.1),
                f("high", "高优先级", 0.9));
        String out = StructuredMemoryService.renderBullets(facts, 200, null);
        assertTrue(out.indexOf("高优先级") < out.indexOf("低优先级"));
    }

    @Test
    @DisplayName("过期事实不注入")
    void skipsExpired() {
        String expired = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        PreferenceFact old = new PreferenceFact("old", "旧事实", "preference", 1.0,
                "explicit", null, null, expired);
        String out = StructuredMemoryService.renderBullets(List.of(old, f("new", "新事实", 1.0)), 300, null);
        assertFalse(out.contains("旧事实"));
        assertTrue(out.contains("新事实"));
    }

    @Test
    @DisplayName("剂量类事实被 OutputValidator 识别后跳过，不作为背景事实")
    void skipsDoseLikeFacts() {
        PreferenceFact dose = f("dose", "每次服用 500 mg");
        String out = StructuredMemoryService.renderBullets(
                List.of(dose, f("diet", "少盐")), 300, new OutputValidator());
        assertFalse(out.contains("500 mg"), "剂量类表达不应进入 prompt 背景事实");
        assertTrue(out.contains("少盐"));
    }

    @Test
    @DisplayName("空事实列表返回空串，绝不产生 prompt 噪音")
    void emptyIsEmpty() {
        assertEquals("", StructuredMemoryService.renderBullets(null, 300, null));
        assertEquals("", StructuredMemoryService.renderBullets(List.of(), 300, null));
    }

    @Test
    @DisplayName("事实全部被跳过时返回空串（只留 header 不算有效记忆）")
    void allSkippedReturnsEmpty() {
        String expired = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        PreferenceFact old = new PreferenceFact("old", "旧事实", "preference", 1.0,
                "explicit", null, null, expired);
        assertEquals("", StructuredMemoryService.renderBullets(List.of(old), 300, null));
    }
}
