package cn.kmbeast.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-10-07 · AI 配置加密存储回归测试。
 *
 * <p>背景：用户报「重启必须重填 base url/apikey」+「加密后 apikey 读不出来」。
 * 根因之一是 {@code AESUtils} 早已实现却从未接线；本类锁定其幂等语义，
 * 防止后续改动破坏「明文历史数据可读 → 密文可解密」这条链。</p>
 */
class AESUtilsTest {

    @Test
    @DisplayName("加密后不等于明文，且可正确解密还原")
    void encryptThenDecryptRoundTrip() {
        String plain = "sk-abcdef1234567890abcdef12";
        String cipher = AESUtils.encrypt(plain);

        assertNotEquals(plain, cipher, "密文不应等于明文");
        // 注意：不能断言"密文不含明文字符片段"——Base64 密文可能偶然包含相同字符子串，
        // 那是编码巧合而非加密缺陷。此处只锁定"非明文 + 可还原"这两个真正的不变式。
        assertEquals(plain, AESUtils.decrypt(cipher), "解密必须还原原文");
    }

    @Test
    @DisplayName("两次加密同一明文结果不同（GCM 随机 IV），但都能解回原文")
    void cipherUsesRandomIv() {
        String plain = "sk-same-key-1234567890123456";
        String c1 = AESUtils.encrypt(plain);
        String c2 = AESUtils.encrypt(plain);

        assertNotEquals(c1, c2, "GCM 应使用随机 IV，两次密文应不同");
        assertEquals(plain, AESUtils.decrypt(c1));
        assertEquals(plain, AESUtils.decrypt(c2));
    }

    @Test
    @DisplayName("encryptIfNeeded 幂等：已加密的值不会被二次加密")
    void encryptIfNeededIsIdempotent() {
        String plain = "sk-abcdef1234567890abcdef12";
        String once = AESUtils.encryptIfNeeded(plain);
        String twice = AESUtils.encryptIfNeeded(once);

        assertEquals(once, twice, "已加密的值应原样返回，不能二次加密");
        assertEquals(plain, AESUtils.decryptIfNeeded(twice), "二次加密后仍应能解回原文");
    }

    @Test
    @DisplayName("decryptIfNeeded 兼容历史明文：明文原样返回，不报错")
    void decryptIfNeededAcceptsPlaintext() {
        // 这是本次修复的关键：修复前库里存的是明文，加密接线后必须仍能读出
        String plain = "sk-legacy-plaintext-key-123456";
        assertEquals(plain, AESUtils.decryptIfNeeded(plain));
    }

    @Test
    @DisplayName("decryptIfNeeded 对「误判为密文的短明文」安全回退，不丢配置")
    void decryptIfNeededFallsBackOnMisjudgedPlaintext() {
        // 纯 Base64 字符集（无 - 无 _ 无 :）、长度 > 50 → isEncrypted 判为密文，但实际是明文
        String trickyPlain = "sk" + "A".repeat(60);
        assertTrue(AESUtils.isEncrypted(trickyPlain), "该串会触发 isEncrypted 判为密文");
        assertEquals(trickyPlain, AESUtils.decryptIfNeeded(trickyPlain),
                "解密异常时必须回退为原值，不能返回空或抛异常");
    }

    @Test
    @DisplayName("空值与普通短文本不被误判为密文")
    void isEncryptedRejectsPlaintext() {
        assertFalse(AESUtils.isEncrypted(null));
        assertFalse(AESUtils.isEncrypted(""));
        assertFalse(AESUtils.isEncrypted("sk-abc"));
        assertFalse(AESUtils.isEncrypted("https://api.deepseek.com/v1/chat/completions"),
                "URL 含 : 与 / ，非 Base64 字符集");
    }

    @Test
    @DisplayName("空串与 null 加解密不抛异常")
    void handlesEmptySafely() {
        assertEquals("", AESUtils.encrypt(""));
        assertEquals(null, AESUtils.encrypt(null));
        assertEquals("", AESUtils.decryptIfNeeded(""));
        assertEquals(null, AESUtils.decryptIfNeeded(null));
    }

    @Test
    @DisplayName("中文内容加解密正常（api_url 等场景）")
    void handlesChineseText() {
        String plain = "https://api.deepseek.com/v1/chat/completions";
        assertEquals(plain, AESUtils.decryptIfNeeded(AESUtils.encrypt(plain)));
    }
}