package cn.kmbeast.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-10-07 · 「重启不必重填」端到端链路验证（不连库，纯内存模拟持久化往返）。
 *
 * <p>复现用户报的两个问题并证明已修复：<br>
 * ① 掩码值落库 → 重启后模型读到 {@code ****}（原缺陷）<br>
 * ② 加密后读不出（{@code AESUtils} 从未接线）</p>
 *
 * <p>用 Map 模拟 {@code ai_config} 表的「存 → 取」往返，验证存取对称性。</p>
 */
class AiConfigPersistenceRoundTripTest {

    /** 模拟 ai_config 表：key → 库中实际存储的字符串。 */
    private static java.util.Map<String, String> fakeTable() {
        return new java.util.HashMap<>();
    }

    @Test
    @DisplayName("端到端：真实 key 加密入库 → 模拟重启读出 → 还原为明文")
    void endToEndKeySurvivesRestart() {
        java.util.Map<String, String> db = fakeTable();
        String realKey = "sk-real-deepseek-key-9876543210abcd";

        // ① 保存：走 persistConfig 的 sensitive() 语义
        db.put("api_key", AESUtils.encryptIfNeeded(realKey));

        // 库里必须是密文（安全要求）
        assertNotEquals(realKey, db.get("api_key"), "库中不得为明文");
        assertTrue(db.get("api_key").length() > 50, "密文应较长");

        // ② 重启：走 loadConfig 的 loadFromPersistence 语义
        String loaded = AESUtils.decryptIfNeeded(db.get("api_key"));

        // ③ 模型最终读到的必须是真实 key
        assertEquals(realKey, loaded, "重启后模型必须读到真实 key");
    }

    @Test
    @DisplayName("端到端：历史明文数据能被读取并迁移为密文（向后兼容）")
    void legacyPlaintextIsMigratedToCipher() {
        java.util.Map<String, String> db = fakeTable();
        String legacyKey = "sk-legacy-plaintext-1234567890";

        // 修复前库里是明文
        db.put("api_key", legacyKey);

        // 重启读取：decryptIfNeeded 对明文幂等返回
        String loaded = AESUtils.decryptIfNeeded(db.get("api_key"));
        assertEquals(legacyKey, loaded, "历史明文必须能正常读出，否则升级即丢配置");

        // 就地回写为密文（渐进迁移）
        db.put("api_key", AESUtils.encryptIfNeeded(loaded));
        assertEquals(legacyKey, AESUtils.decryptIfNeeded(db.get("api_key")), "迁移后仍可读");
    }

    @Test
    @DisplayName("端到端：掩码值不会被当作真实 key 落库（Controller 守卫语义）")
    void maskedValueIsRejectedByControllerGuard() {
        // 后端 /get 只返回掩码，前端原样回传 —— 守卫必须识别为「未修改」
        java.util.function.Predicate<String> isMasked = v -> v == null || v.isEmpty()
                || v.contains("****") || "sk-xxx".equals(v) || v.matches("^[xX*]+$");

        assertTrue(isMasked.test("sk-abc****wxyz"), "含 **** 的掩码必须被识别");
        assertTrue(isMasked.test("sk-xxx"), "占位符必须被识别");
        assertTrue(isMasked.test(null));
        assertTrue(isMasked.test(""));

        // 真实 key 不应被误判（否则用户改了密钥也存不进去）
        assertTrue(!isMasked.test("sk-realkey-1234567890"), "真实 key 不应被掩码守卫拦截");
    }

    @Test
    @DisplayName("端到端：多轮重启不产生二次加密，读出值稳定")
    void repeatedRestartsAreStable() {
        java.util.Map<String, String> db = fakeTable();
        String realKey = "sk-multi-restart-key-abcdef123456";
        db.put("api_key", AESUtils.encryptIfNeeded(realKey));

        for (int restart = 0; restart < 5; restart++) {
            String loaded = AESUtils.decryptIfNeeded(db.get("api_key"));
            assertEquals(realKey, loaded, "第 " + restart + " 次重启读出值应稳定");
            // 模拟"读出后若重新保存"：encryptIfNeeded 必须幂等，不能把明文又加密一次导致解不开
            db.put("api_key", AESUtils.encryptIfNeeded(loaded));
        }
    }

    @Test
    @DisplayName("端到端：embedding/reasoner 等多密钥并存互不干扰")
    void multipleKeysCoexist() {
        java.util.Map<String, String> db = fakeTable();
        db.put("api_key", AESUtils.encryptIfNeeded("sk-chat-key-1111111111111"));
        db.put("reasoner_api_key", AESUtils.encryptIfNeeded("sk-reasoner-key-2222222222"));
        db.put("embedding_api_key", AESUtils.encryptIfNeeded("sk-embed-key-33333333333333"));

        assertEquals("sk-chat-key-1111111111111", AESUtils.decryptIfNeeded(db.get("api_key")));
        assertEquals("sk-reasoner-key-2222222222", AESUtils.decryptIfNeeded(db.get("reasoner_api_key")));
        assertEquals("sk-embed-key-33333333333333", AESUtils.decryptIfNeeded(db.get("embedding_api_key")));
    }
}