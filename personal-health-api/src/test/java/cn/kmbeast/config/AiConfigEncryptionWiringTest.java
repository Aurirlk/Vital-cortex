package cn.kmbeast.config;

import cn.kmbeast.mapper.AiConfigMapper;
import cn.kmbeast.pojo.entity.AiConfigEntity;
import cn.kmbeast.utils.AESUtils;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-11-09 · 阶段 10 加密接线回归测试。
 *
 * <p>目的：锁定 {@link AiConfigPersistenceService} 与 {@link AESUtils} 之间的加密接线，
 * 防止「加密后读不出 / 明文落库 / 缺密钥静默失败」这类回归。三大约束：<br>
 * ① 敏感 key（如 api_key）经 {@code saveConfig} 入库的必须是密文，且能解回明文；<br>
 * ② {@code decryptIfNeeded} 对密文 / 明文均幂等（兼容历史明文数据）；<br>
 * ③ 未配置 {@code AES_SECRET_KEY} 时仅告警、不阻断（fail-soft），且算法仍可正常工作。</p>
 */
class AiConfigEncryptionWiringTest {

    /** 用字段可配置的匿名-free 实现捕获 batchSaveOrUpdate 的入参，避免引入 Mockito 依赖。 */
    private static class CapturingMapper implements AiConfigMapper {
        List<AiConfigEntity> captured;
        List<AiConfigEntity> findAllResult = List.of();
        @Override public List<AiConfigEntity> findAll() { return findAllResult; }
        @Override public AiConfigEntity findByKey(String k) { return null; }
        @Override public void saveOrUpdate(AiConfigEntity e) { /* 迁移写回：本测试不校验 */ }
        @Override public void batchSaveOrUpdate(List<AiConfigEntity> c) { captured = c; }
    }

    private static AiConfigPersistenceService serviceWith(CapturingMapper mapper) {
        AiConfigPersistenceService svc = new AiConfigPersistenceService();
        try {
            Field f = AiConfigPersistenceService.class.getDeclaredField("aiConfigMapper");
            f.setAccessible(true);
            f.set(svc, mapper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return svc;
    }

    @Test
    @DisplayName("saveConfig 把 api_key 加密入库（密文≠明文，且能解回）— 锁定加密接线")
    void saveConfigEncryptsApiKey() {
        CapturingMapper mapper = new CapturingMapper();
        AiConfigPersistenceService svc = serviceWith(mapper);

        AiConfig config = new AiConfig();
        String realKey = "sk-real-test-key-1234567890abcd";
        config.setApiKey(realKey);

        svc.saveConfig(config);

        assertNotNull(mapper.captured, "batchSaveOrUpdate 必须被调用");
        AiConfigEntity apiKeyEntity = mapper.captured.stream()
                .filter(e -> "api_key".equals(e.getConfigKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("api_key 必须出现在保存列表中"));
        String stored = apiKeyEntity.getConfigValue();

        assertNotEquals(realKey, stored, "api_key 入库必须是密文，不得是明文");
        assertTrue(AESUtils.isEncrypted(stored), "入库值必须被判定为密文");
        assertEquals(realKey, AESUtils.decryptIfNeeded(stored), "密文必须能解回真实 key");
    }

    @Test
    @DisplayName("loadConfig 把密文 api_key 解密为明文返回")
    void loadConfigDecryptsApiKey() {
        CapturingMapper mapper = new CapturingMapper();
        AiConfigPersistenceService svc = serviceWith(mapper);

        AiConfigEntity enc = new AiConfigEntity();
        enc.setConfigKey("api_key");
        enc.setConfigValue(AESUtils.encryptIfNeeded("sk-loaded-key-0987654321"));
        enc.setDescription("API Key");
        mapper.findAllResult = List.of(enc);

        JSONObject loaded = svc.loadConfig();
        assertNotNull(loaded, "loadConfig 不应返回 null");
        assertEquals("sk-loaded-key-0987654321", loaded.getString("apiKey"),
                "loadConfig 必须把密文 api_key 解密为明文");
    }

    @Test
    @DisplayName("decryptIfNeeded / encryptIfNeeded 幂等：密文解回明文、明文原样返回、已加密不二次加密")
    void decryptIfNeededIsIdempotent() {
        String plain = "sk-idempotent-1234567890";
        String cipher = AESUtils.encryptIfNeeded(plain);

        // decryptIfNeeded 对密文：解密回明文（不是原样返回密文）
        assertEquals(plain, AESUtils.decryptIfNeeded(cipher), "密文必须解回明文");
        // decryptIfNeeded 对明文：原样返回，兼容历史明文数据
        assertEquals(plain, AESUtils.decryptIfNeeded(plain), "明文原样返回，兼容历史数据");
        // encryptIfNeeded 对已加密值幂等：不二次加密（这才是「不二次加密」的归属方法）
        assertEquals(cipher, AESUtils.encryptIfNeeded(cipher), "encryptIfNeeded 对已加密值幂等（不二次加密）");
        // 连续解密结果稳定：解密出的明文再次调用仍返回明文
        assertEquals(plain, AESUtils.decryptIfNeeded(AESUtils.decryptIfNeeded(cipher)), "连续解密结果稳定");
    }

    @Test
    @DisplayName("未配置 AES_SECRET_KEY 时仅告警、不阻断加密（fail-soft）")
    void missingEnvKeyWarnsButDoesNotBlock() {
        Logger aesLogger = (Logger) LoggerFactory.getLogger(AESUtils.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level prevLevel = aesLogger.getLevel();
        aesLogger.setLevel(Level.WARN);
        aesLogger.addAppender(appender);
        try {
            String cipher = AESUtils.encrypt("warn-check-value");
            // 核心约束：即便缺密钥，加密也必须成功且可解密（不抛异常、不阻断）
            assertEquals("warn-check-value", AESUtils.decrypt(cipher));

            String envKey = System.getenv("AES_SECRET_KEY");
            boolean keyMissing = (envKey == null || envKey.isEmpty());
            boolean warned = appender.list.stream().anyMatch(e ->
                    e.getLevel() == Level.WARN && e.getFormattedMessage().contains("AES_SECRET_KEY"));
            if (keyMissing) {
                assertTrue(warned, "未配置 AES_SECRET_KEY 时应输出 warn 且不阻断加密");
            } else {
                assertFalse(warned, "已配置 AES_SECRET_KEY 时不应输出默认密钥 warn");
            }
        } finally {
            aesLogger.detachAppender(appender);
            aesLogger.setLevel(prevLevel);
        }
    }
}
