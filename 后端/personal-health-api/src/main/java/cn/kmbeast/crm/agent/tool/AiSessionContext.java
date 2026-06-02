package cn.kmbeast.crm.agent.tool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI会话元数据上下文
 * 存储每次对话的配置信息（是否联网搜索、是否调用知识库等）
 */
public class AiSessionContext {
    
    private static final ThreadLocal<Map<String, Object>> context = new ThreadLocal<>();
    
    /**
     * 设置会话元数据
     */
    public static void setMetadata(Map<String, Object> metadata) {
        context.set(metadata);
    }
    
    /**
     * 获取会话元数据
     */
    public static Map<String, Object> getMetadata() {
        Map<String, Object> metadata = context.get();
        return metadata != null ? metadata : new ConcurrentHashMap<>();
    }
    
    /**
     * 设置单个元数据
     */
    public static void set(String key, Object value) {
        Map<String, Object> metadata = context.get();
        if (metadata == null) {
            metadata = new ConcurrentHashMap<>();
            context.set(metadata);
        }
        metadata.put(key, value);
    }
    
    /**
     * 获取单个元数据
     */
    public static Object get(String key) {
        Map<String, Object> metadata = context.get();
        return metadata != null ? metadata.get(key) : null;
    }
    
    /**
     * 获取布尔类型元数据
     */
    public static Boolean getBoolean(String key) {
        Object value = get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }
    
    /**
     * 清除上下文
     */
    public static void clear() {
        context.remove();
    }
}
