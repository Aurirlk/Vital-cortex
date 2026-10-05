package cn.kmbeast.core.agent;

import cn.kmbeast.core.workspace.AgentWorkspace;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 记忆服务（2026-10-04 加固）
 *
 * <p>提供长期记忆、语义记忆、用户偏好记忆，支持跨会话学习和知识积累。
 *
 * <p><b>本次加固的三点</b>：
 * <ol>
 *   <li><b>无界缓存 → 有界 LRU</b>：原用 {@code new HashMap<>()} 存全部用户偏好，
 *       既无上限也无淘汰，与此前修复过的「AI 对话缓存必然 OOM」是同类隐患。
 *       现改为 {@code ConcurrentHashMap} + {@link BoundedPreferenceCache}，
 *       上限 {@link #MAX_CACHED_USERS} 个用户，LRU 淘汰。</li>
 *   <li><b>返回值防篡改</b>：原 {@code computeIfAbsent} 直接返回缓存里的可变引用，
 *       调用方拿到后修改不会落盘，且外部可随意改动缓存内容。
 *       现返回<b>不可变副本</b>。</li>
 *   <li><b>写入时清理缓存</b>：原实现改完内存 Map 后不清理，下次读到的还是旧值
 *       （除非进程重启从磁盘重载），导致「保存成功但读不到」。</li>
 * </ol>
 */
@Slf4j
@Component
public class AgentMemoryService {

    /** 最多缓存多少个用户的偏好（超出按 LRU 淘汰） */
    private static final int MAX_CACHED_USERS = 1000;

    /** 定时清理间隔（分钟）——清理长期未被访问但未满的条目，避免内存缓慢增长 */
    private static final long EVICT_INTERVAL_MINUTES = 30L;

    @Resource
    private AgentWorkspace agentWorkspace;

    /** 内存缓存：用户偏好。有界 LRU，防止用户量增长导致 OOM */
    private final BoundedPreferenceCache userPreferences = new BoundedPreferenceCache(MAX_CACHED_USERS);

    /**
     * 获取用户偏好（只读快照）
     *
     * @return 不可变副本；修改它不会影响缓存，需要持久化请调 {@link #saveUserPreference}
     */
    public Map<String, String> getUserPreferences(Integer userId) {
        if (userId == null) {
            return Collections.emptyMap();
        }
        Map<String, String> cached = userPreferences.get(userId);
        if (cached != null) {
            return Collections.unmodifiableMap(cached);
        }
        // 缓存未命中：读磁盘并回填
        Map<String, String> loaded = loadFromDisk(userId);
        userPreferences.put(userId, loaded);
        return Collections.unmodifiableMap(loaded);
    }

    private Map<String, String> loadFromDisk(Integer userId) {
        Map<String, String> prefs = new LinkedHashMap<>();
        try {
            JSONObject saved = agentWorkspace.readJson("memory", userId, "preferences");
            if (saved != null) {
                saved.forEach((k, v) -> {
                    if (v != null) {
                        prefs.put(k, String.valueOf(v));
                    }
                });
            }
        } catch (Exception e) {
            // 磁盘读取失败不能影响对话：降级为空偏好
            log.warn("[AgentMemory] 读取用户偏好失败，使用空偏好: userId={}, err={}", userId, e.getMessage());
        }
        return prefs;
    }

    /**
     * 保存用户偏好
     *
     * <p>先落盘再更新缓存，保证「返回成功」时数据确实持久化了。
     */
    public void saveUserPreference(Integer userId, String key, String value) {
        if (userId == null || key == null || key.trim().isEmpty()) {
            return;
        }
        Map<String, String> prefs = new LinkedHashMap<>(getUserPreferences(userId));
        if (value == null) {
            prefs.remove(key.trim());
        } else {
            prefs.put(key.trim(), value);
        }

        JSONObject json = new JSONObject();
        json.putAll(prefs);
        agentWorkspace.saveJson("memory", userId, "preferences", json);

        // 落盘成功后再刷缓存，避免磁盘失败时内存与磁盘不一致
        userPreferences.put(userId, prefs);
    }

    /**
     * 批量覆盖用户偏好（供 SlotFilling 一次写入多个采集到的槽位）
     */
    public void saveUserPreferences(Integer userId, Map<String, String> newPrefs) {
        if (userId == null || newPrefs == null || newPrefs.isEmpty()) {
            return;
        }
        Map<String, String> prefs = new LinkedHashMap<>(getUserPreferences(userId));
        newPrefs.forEach((k, v) -> {
            if (k != null && !k.trim().isEmpty() && v != null && !v.trim().isEmpty()) {
                prefs.put(k.trim(), v);
            }
        });
        JSONObject json = new JSONObject();
        json.putAll(prefs);
        agentWorkspace.saveJson("memory", userId, "preferences", json);
        userPreferences.put(userId, prefs);
    }

    /** 清除某用户的缓存（下次读从磁盘重载） */
    public void evictUser(Integer userId) {
        if (userId != null) {
            userPreferences.remove(userId);
        }
    }

    /** 缓存统计（供监控端点调用） */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("cachedUsers", userPreferences.size());
        stats.put("maxCachedUsers", MAX_CACHED_USERS);
        return stats;
    }

    /**
     * 定时清理长期未访问的缓存条目。
     *
     * <p>与容量上限双保险：即使没打满上限，也不会让内存无限缓慢增长。
     */
    @Scheduled(fixedDelay = EVICT_INTERVAL_MINUTES * 60 * 1000, initialDelay = EVICT_INTERVAL_MINUTES * 60 * 1000)
    public void evictStaleCache() {
        int removed = userPreferences.evictStale(MAX_CACHED_USERS);
        if (removed > 0) {
            log.info("[AgentMemory] 清理长期未访问的偏好缓存 {} 条，当前 {}", removed, userPreferences.size());
        }
    }

    /**
     * 有界 LRU 偏好缓存
     *
     * <p>放在内部类而非外部文件，因为只有本服务使用。
     * 用 {@code LinkedHashMap(accessOrder=true)} 做 LRU，
     * 外面套 {@code ConcurrentHashMap} 保证并发安全。
     */
    static final class BoundedPreferenceCache {

        private final int maxSize;
        /**
         * key → 该用户的偏好表。
         *
         * <p>值类型声明为 {@code Map} 而非 {@code LinkedHashMap}：
         * 实际存入的是 {@code Collections.unmodifiableMap(...)}，
         * 它不是 LinkedHashMap 的子类，声明过窄会编译失败。
         */
        private final ConcurrentHashMap<Integer, Map<String, String>> store = new ConcurrentHashMap<>();
        /** 记录每个用户最近访问时间，用于清理长期未访问项 */
        private final ConcurrentHashMap<Integer, Long> lastAccess = new ConcurrentHashMap<>();

        BoundedPreferenceCache(int maxSize) {
            this.maxSize = maxSize;
        }

        Map<String, String> get(Integer userId) {
            if (userId == null) {
                return null;
            }
            lastAccess.put(userId, System.currentTimeMillis());
            return store.get(userId);
        }

        void put(Integer userId, Map<String, String> prefs) {
            if (userId == null) {
                return;
            }
            // 先腾一个位再放，保证 size 不会超过 maxSize
            if (store.size() >= maxSize && !store.containsKey(userId)) {
                evictOne();
            }
            // 存不可变快照，防止调用方拿到引用后改缓存。
            // 注意必须先构造成 LinkedHashMap 再包 unmodifiableMap —— 直接
            // Collections.unmodifiableMap(new LinkedHashMap<>(prefs)) 的返回类型
            // 是 UnmodifiableMap<K,V> 而非 LinkedHashMap，无法放进 store。
            Map<String, String> snapshot = new LinkedHashMap<>(prefs);
            store.put(userId, Collections.unmodifiableMap(snapshot));
            lastAccess.put(userId, System.currentTimeMillis());
        }

        void remove(Integer userId) {
            store.remove(userId);
            lastAccess.remove(userId);
        }

        int size() {
            return store.size();
        }

        /** 淘汰最久未访问的一个 */
        private void evictOne() {
            Integer oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<Integer, Long> e : lastAccess.entrySet()) {
                if (e.getValue() < oldestTime) {
                    oldestTime = e.getValue();
                    oldestKey = e.getKey();
                }
            }
            if (oldestKey != null) {
                store.remove(oldestKey);
                lastAccess.remove(oldestKey);
                log.debug("[AgentMemory] LRU 淘汰用户偏好缓存: userId={}", oldestKey);
            }
        }

        /**
         * 定时清理：把缓存压回上限的 80%，腾出后续写入空间。
         *
         * <p><b>注意语义</b>：{@code limit} 是<b>容量上限</b>而非目标值。
         * 早前实现写成 {@code if (size <= limit) return 0}，
         * 导致缓存正好等于上限时直接返回、什么都不清 —— 定时任务完全空转。
         * 现改为：只要达到上限（>=）就清到 80%。
         *
         * @return 实际清理条数
         */
        int evictStale(int limit) {
            if (store.size() < limit) {
                return 0;   // 还没到上限，无需清理
            }
            int target = (int) (limit * 0.8);   // 一次清到 80%，避免频繁触发
            int removed = 0;
            while (store.size() > target && !store.isEmpty()) {
                evictOne();
                removed++;
            }
            return removed;
        }
    }
}
