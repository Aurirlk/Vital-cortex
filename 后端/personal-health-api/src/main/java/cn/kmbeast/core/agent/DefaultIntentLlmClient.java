package cn.kmbeast.core.agent;

import cn.kmbeast.core.nl2sql.Nl2SqlLlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 意图识别 LLM 兜底的默认实现。
 *
 * <p><b>复用而非重写</b>：直接走项目统一的 OpenAI 兼容调用链
 * {@link Nl2SqlLlmClient}（其内部 {@code temperature=0}、自建请求体、解析
 * {@code choices[0].message.content}，且未配置时抛明确异常）。本类只负责「把 6 标签白名单
 * 拼成 system prompt 送给模型、拿回文本」，严格的标签解析与回落交给 {@link IntentResolver}。
 *
 * <p>说明：{@code max_tokens=20} / 5s 超时属于微调项；本实现复用共享 LLM 客户端，
 * token 上限与超时继承 {@code AiConfig}（默认 60s）。标签白名单解析保证了即使模型多说几句，
 * 也只取精确匹配的标签，因此未强制截断长度不影响正确性。</p>
 */
@Slf4j
@Component
public class DefaultIntentLlmClient implements IntentLlmClient {

    private static final String PROMPT_RESOURCE = "ai-prompts/intent-classification.txt";

    private static final String FALLBACK_PROMPT =
            "你是一个健康咨询意图分类器。只从下面 6 个标签里选一个最贴切的，"
                    + "直接输出标签本身（带书名号），不要解释、不要输出其他内容：\n"
                    + "《闲聊或无关指令》\n《健康指标查询》\n《医学关系查询》\n"
                    + "《药品咨询》\n《健康科普》\n《数据分析请求》";

    @Resource
    private Nl2SqlLlmClient llm;

    private String systemPrompt;

    @PostConstruct
    public void init() {
        systemPrompt = loadPrompt();
    }

    private String loadPrompt() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(PROMPT_RESOURCE)) {
            if (in != null) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if (!text.isBlank()) {
                    return text;
                }
            }
        } catch (Exception e) {
            log.warn("[IntentLlmClient] 读取意图提示词失败，用内联兜底: {}", e.getMessage());
        }
        return FALLBACK_PROMPT;
    }

    @Override
    public String classify(String userQuery) throws Exception {
        if (llm == null || !llm.isConfigured()) {
            throw new IllegalStateException("LLM 未配置（ai_config.api_url / api_key 为空），意图兜底不可用");
        }
        // asSystem=false：Nl2SqlLlmClient 把 prompt 当 user 角色、userQuery 当附加 user 消息；
        // 这里把白名单系统提示放 prompt 位即可，模型输出即为标签。
        return llm.chat(systemPrompt, false, userQuery);
    }
}
