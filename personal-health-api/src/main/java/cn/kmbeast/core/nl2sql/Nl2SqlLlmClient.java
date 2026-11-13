package cn.kmbeast.core.nl2sql;

import cn.kmbeast.config.AiConfig;
import cn.kmbeast.core.provider.LLMProvider;
import cn.kmbeast.core.provider.LLMProviderFactory;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;

/**
 * NL2SQL / 数据智能体共用的 LLM 调用客户端。
 *
 * <p>从 {@code MySqlText2SqlService} 抽出的公共能力（原 {@code callLlm} / {@code extractContent}），
 * 供编排服务与流水线共用，避免重复；同时给单测留出替换点。</p>
 *
 * <p>走项目既有 {@link LLMProviderFactory}（默认 {@code DeepSeekProvider}），
 * 自建 OpenAI 兼容请求体，解析 {@code choices[0].message.content}。
 * <b>不引</b> Spring AI。</p>
 */
@Slf4j
@Component
public class Nl2SqlLlmClient {

    @Resource
    private LLMProviderFactory llmProviderFactory;

    @Resource
    private AiConfig aiConfig;

    /** LLM 是否已配置（api_url + api_key 均非空）。 */
    public boolean isConfigured() {
        String url = aiConfig.getApiUrl();
        String key = aiConfig.getApiKey();
        return url != null && !url.trim().isEmpty() && key != null && !key.trim().isEmpty();
    }

    /**
     * 调用 LLM。
     *
     * @param prompt      完整提示词
     * @param asSystem    true 走 system 角色（生成/修复/报告），false 走 user 角色（选表/分类）
     * @param userMessage 附加的用户消息；为空时用一句占位
     */
    public String chat(String prompt, boolean asSystem, String userMessage) {
        if (!isConfigured()) {
            throw new IllegalStateException("LLM 未配置（ai_config.api_url / api_key 为空），数据智能体不可用");
        }
        String apiUrl = aiConfig.getApiUrl();
        String apiKey = aiConfig.getApiKey();

        JSONArray messages = new JSONArray();
        JSONObject sys = new JSONObject();
        sys.put("role", asSystem ? "system" : "user");
        sys.put("content", prompt);
        messages.add(sys);
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", userMessage == null || userMessage.isEmpty() ? "请按上述要求输出。" : userMessage);
        messages.add(user);

        JSONObject body = new JSONObject();
        body.put("model", aiConfig.getModel());
        body.put("messages", messages);
        body.put("temperature", 0);
        body.put("stream", false);

        try {
            LLMProvider provider = llmProviderFactory.getProvider();
            String raw = provider.chat(body.toJSONString(), apiUrl, apiKey);
            return extractContent(raw);
        } catch (IOException e) {
            throw new IllegalStateException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    /** 从 OpenAI 兼容响应中取 {@code choices[0].message.content}。 */
    public String extractContent(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        JSONObject obj = JSON.parseObject(raw);
        if (obj == null) {
            return "";
        }
        if (obj.containsKey("error")) {
            throw new IllegalStateException("LLM 返回错误: " + obj.get("error"));
        }
        JSONArray choices = obj.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        JSONObject first = choices.getJSONObject(0);
        JSONObject message = first.getJSONObject("message");
        String content = message == null ? null : message.getString("content");
        return content == null ? "" : content;
    }

    /**
     * 去掉模型偶尔输出的 Markdown 代码围栏。
     *
     * <p>复用摘取自 DataAgent 的 {@link MarkdownParserUtil}（零依赖），
     * 支持可变长度围栏与 CRLF/CR/LF。</p>
     */
    public static String stripFence(String text) {
        if (text == null) {
            return "";
        }
        return MarkdownParserUtil.extractRawText(text).trim();
    }
}
