package cn.kmbeast.core.agent;

/**
 * 意图识别的 LLM 兜底客户端（可注入、可替换）。
 *
 * <p>设计目的：{@link IntentResolver} 走「规则先行 + LLM 兜底」，但 LLM 调用必须与
 * 具体厂商解耦，否则单测无法替换、也无法在 LLM 不可用时不抛异常降级。本接口即这一 seam。
 *
 * <p>实现方（{@code DefaultIntentLlmClient}）负责按 6 标签白名单向模型提问并返回
 * 原始标签文本；<b>严格的标签解析与回落由 {@link IntentResolver} 完成</b>，
 * 客户端只管「把这句话送给模型、拿回文本」。</p>
 */
public interface IntentLlmClient {

    /**
     * 对一句话做意图二分类，返回模型原始输出（应为 6 个标签之一，但不强制）。
     *
     * @param userQuery 用户原始提问
     * @return 模型原始文本（可能含空白/书名号/代码围栏，由解析方清洗）
     * @throws Exception 任何异常（未配置 / 网络 / 超时）——{@link IntentResolver} 会捕获并降级
     */
    String classify(String userQuery) throws Exception;
}
