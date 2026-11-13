package cn.kmbeast.core.memory;

import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构化记忆 REST 的共享业务层（阶段 8 / D3 改写）。
 *
 * <p>用户端与医生端只差「命名空间」和「身份从哪个 ThreadLocal 取」，
 * REST 行为完全一致，故收敛到本类，避免两个 Controller 各写一份导致口径漂移。</p>
 *
 * <p><b>反提示词注入的关键约定</b>：</p>
 * <ol>
 *   <li>请求体只认 {@code facts / enabled / optOut} 三个白名单字段，
 *       每条事实只认 {@code key / value / kind / confidence / expiresAt}；</li>
 *   <li>请求体里出现 {@code initialPrompt / systemPrompt / persona / prompt}
 *       这类自由文本字段 → <b>整单拒绝</b>（旧 D3 方案把它们拼进 system prompt，
 *       是已被正式废弃的注入反模式）；</li>
 *   <li>{@code source} 恒为 {@code explicit}，不接受客户端伪造；</li>
 *   <li>所有 SQL 的 WHERE 恒带 {@code id = ownerId}，ownerId 由 Controller 从
 *       {@code LocalThreadHolder.getUserId()} 取，端点不接收 userId 参数。</li>
 * </ol>
 */
@Slf4j
@Component
public class MemoryApiSupport {

    /** 明确禁止的自由文本字段（旧画像方案残留）。 */
    private static final String[] FORBIDDEN_FIELDS = {
            "initial_prompt", "initialPrompt", "system_prompt", "systemPrompt",
            "persona", "prompt", "role_prompt", "rolePrompt"
    };

    @Resource
    private StructuredMemoryService memoryService;

    @Resource
    private FactExtractor factExtractor;

    /** 读取我的记忆（含开关与限制说明）。 */
    public Result<Map<String, Object>> view(StructuredMemoryService.Namespace ns, int ownerId) {
        StructuredMemoryService.Settings settings = memoryService.settings(ns, ownerId);
        List<PreferenceFact> facts = memoryService.load(ns, ownerId);
        Map<String, Object> data = new LinkedHashMap<>();
        JSONArray arr = new JSONArray();
        for (PreferenceFact f : facts) {
            arr.add(f.toJson());
        }
        data.put("facts", arr);
        data.put("enabled", settings.enabled());
        data.put("optOut", settings.optOut());
        data.put("count", facts.size());
        data.put("limits", limits());
        return ApiResult.success(data);
    }

    /**
     * 整批保存结构化偏好 + 可选开关。
     *
     * <p>{@code facts} 缺省表示「不改事实」；显式传 {@code []} 表示清空事实。</p>
     */
    public Result<Map<String, Object>> save(StructuredMemoryService.Namespace ns, int ownerId,
                                            JSONObject body) {
        if (body == null || body.isEmpty()) {
            return ApiResult.error("请求体为空");
        }
        for (String forbidden : FORBIDDEN_FIELDS) {
            if (body.containsKey(forbidden)) {
                return ApiResult.error("不接受自由文本提示词字段：" + forbidden
                        + "；请改用结构化偏好 facts");
            }
        }

        Boolean enabled = body.getBoolean("enabled");
        Boolean optOut = body.getBoolean("optOut");

        if (body.containsKey("facts")) {
            JSONArray raw = body.getJSONArray("facts");
            if (raw == null) {
                return ApiResult.error("facts 必须是数组");
            }
            List<PreferenceFact> facts = new ArrayList<>();
            String now = Instant.now().toString();
            for (int i = 0; i < raw.size(); i++) {
                JSONObject item = raw.getJSONObject(i);
                if (item == null) {
                    return ApiResult.error("第 " + (i + 1) + " 条事实不是对象");
                }
                // 只取白名单字段，客户端传的 source/role/其它字段一律丢弃
                String kind = item.getString("kind");
                double confidence = item.containsKey("confidence")
                        ? item.getDoubleValue("confidence") : 1.0;
                facts.add(new PreferenceFact(
                        item.getString("key"),
                        item.getString("value"),
                        kind == null ? "preference" : kind,
                        confidence,
                        "explicit",
                        now,
                        now,
                        item.getString("expiresAt")));
            }
            MemoryPoisoningValidator.Result v = MemoryPoisoningValidator.validate(facts);
            if (!v.ok()) {
                return ApiResult.error("偏好校验未通过（整批未保存）：" + v.reason());
            }
            if (!memoryService.replaceAll(ns, ownerId, facts)) {
                return ApiResult.error("保存失败，请稍后重试");
            }
        }
        if ((enabled != null || optOut != null)
                && !memoryService.setSwitch(ns, ownerId, enabled, optOut)) {
            return ApiResult.error("开关保存失败，请稍后重试");
        }
        return view(ns, ownerId);
    }

    /** 删除我的某一条事实。 */
    public Result<Map<String, Object>> delete(StructuredMemoryService.Namespace ns, int ownerId,
                                              String key) {
        if (key == null || key.isBlank()) {
            return ApiResult.error("key 不能为空");
        }
        if (!memoryService.delete(ns, ownerId, key)) {
            return ApiResult.error("未找到该条记忆，或删除失败");
        }
        return view(ns, ownerId);
    }

    /** 清空我的全部事实（开关保持不变）。 */
    public Result<Map<String, Object>> clear(StructuredMemoryService.Namespace ns, int ownerId) {
        if (!memoryService.clear(ns, ownerId)) {
            return ApiResult.error("清空失败，请稍后重试");
        }
        return view(ns, ownerId);
    }

    /** 自动抽取（默认关闭）。抽取结果逐条 upsert，upsert 内部会再过一次整批校验。 */
    public Result<Map<String, Object>> extract(StructuredMemoryService.Namespace ns, int ownerId) {
        if (!factExtractor.isEnabled()) {
            return ApiResult.error("自动事实抽取未启用（crm.ai.memory-extract-enabled=false）");
        }
        List<PreferenceFact> candidates = factExtractor.extract(ns, ownerId);
        int added = 0;
        for (PreferenceFact f : candidates) {
            if (memoryService.upsert(ns, ownerId, f)) {
                added++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("candidates", candidates.size());
        out.put("added", added);
        Result<Map<String, Object>> current = view(ns, ownerId);
        out.put("memory", current.getData());
        return ApiResult.success(out);
    }

    private static Map<String, Object> limits() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("maxFacts", MemoryPoisoningValidator.MAX_FACTS);
        m.put("maxValueChars", MemoryPoisoningValidator.MAX_VALUE_CHARS);
        m.put("maxTtlDays", MemoryPoisoningValidator.MAX_TTL_DAYS);
        m.put("kinds", PreferenceFact.KINDS);
        m.put("tokenBudgetMin", StructuredMemoryService.MIN_TOKEN_BUDGET);
        m.put("tokenBudgetMax", StructuredMemoryService.MAX_TOKEN_BUDGET);
        return m;
    }
}
