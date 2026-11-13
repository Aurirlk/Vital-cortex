package cn.kmbeast.core.voice;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 语音合成线程池生命周期管理（2026-10-03 新增）
 *
 * <p>{@link VoiceSynthesisExecutor} 是 final + 静态方法的工具类，<b>不是 Spring Bean</b>，
 * 因此 {@code @PostConstruct} / {@code @PreDestroy} 注解对它无效。
 * 本组件负责在应用启动时预热线程池、关闭时优雅停机。
 */
@Slf4j
@Component
public class VoiceExecutorLifecycle {

    @PostConstruct
    public void init() {
        VoiceSynthesisExecutor.warmUp();
        log.debug("[Voice] 语音合成线程池已预热");
    }

    @PreDestroy
    public void destroy() {
        VoiceSynthesisExecutor.shutdown();
    }
}
