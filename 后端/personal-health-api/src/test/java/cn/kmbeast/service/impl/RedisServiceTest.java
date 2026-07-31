package cn.kmbeast.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RedisService单元测试
 */
@ExtendWith(MockitoExtension.class)
class RedisServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RedisService redisService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testSet() {
        // Given
        String key = "test:key";
        Object value = "test:value";
        long expire = 3600;

        // When
        redisService.set(key, value, expire);

        // Then
        verify(valueOperations).set(key, value, expire, TimeUnit.SECONDS);
    }

    @Test
    void testSet_DefaultExpire() {
        // Given
        String key = "test:key";
        Object value = "test:value";

        // When
        redisService.set(key, value);

        // Then
        verify(valueOperations).set(key, value, 3600, TimeUnit.SECONDS);
    }

    @Test
    void testGet() {
        // Given
        String key = "test:key";
        Object expectedValue = "test:value";
        when(valueOperations.get(key)).thenReturn(expectedValue);

        // When
        Object result = redisService.get(key);

        // Then
        assertEquals(expectedValue, result);
    }

    @Test
    void testGet_WithClass() {
        // Given
        String key = "test:key";
        String expectedValue = "test:value";
        when(valueOperations.get(key)).thenReturn(expectedValue);

        // When
        String result = redisService.get(key, String.class);

        // Then
        assertEquals(expectedValue, result);
    }

    @Test
    void testDelete() {
        // Given
        String key = "test:key";

        // When
        redisService.delete(key);

        // Then
        verify(redisTemplate).delete(key);
    }

    @Test
    void testHasKey_Exists() {
        // Given
        String key = "test:key";
        when(redisTemplate.hasKey(key)).thenReturn(true);

        // When
        boolean result = redisService.hasKey(key);

        // Then
        assertTrue(result);
    }

    @Test
    void testHasKey_NotExists() {
        // Given
        String key = "test:key";
        when(redisTemplate.hasKey(key)).thenReturn(false);

        // When
        boolean result = redisService.hasKey(key);

        // Then
        assertFalse(result);
    }

    @Test
    void testSetWithRandomExpire() {
        // Given
        String key = "test:key";
        Object value = "test:value";
        long baseExpire = 3600;

        // When
        redisService.setWithRandomExpire(key, value, baseExpire);

        // Then
        verify(valueOperations).set(eq(key), eq(value), anyLong(), eq(TimeUnit.SECONDS));
    }
}
