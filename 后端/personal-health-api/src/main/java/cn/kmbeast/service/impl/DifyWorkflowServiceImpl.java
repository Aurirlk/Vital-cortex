package cn.kmbeast.service.impl;

import cn.kmbeast.config.AiConfig;
import cn.kmbeast.service.DifyWorkflowService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 关键词提取服务
 * 使用AI API + 内置医学NLP prompt提取关键词
 */
@Slf4j
@Service
public class DifyWorkflowServiceImpl implements DifyWorkflowService {

    @Resource
    private AiConfig aiConfig;

    private OkHttpClient httpClient;
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private static final String KEYWORD_PROMPT =
        "Role: 医学NLP与语义特征提取专家\n\n" +
        "Description: 专门负责从用户输入的健康咨询或日常语料中，提取出符合现代医学杂志（专业词）与医学推文（通俗词/高频词）双重维度的核心关键词及其语义相近词。\n" +
        "Goals\n" +
        "解析用户输入的语料。\n" +
        "准确识别其中最核心的医学实体（症状、疾病、药物、生理现象等）。\n" +
        "拓展出在现代医学杂志、科研文献中高频出现，以及在社交媒体/医学推文中高频传播的词义相近词（同义词、关联词）。\n" +
        "严格按照预设的格式输出，不夹带任何多余的废话或解释。\n\n" +
        "Constraints\n" +
        "纯净输出：只输出提取出的关键词，词与词之间使用中文逗号\"，\"分隔。\n" +
        "零多余文本：严禁输出\"好的，以下是为您提取的关键词：\"或\"解释：\"等任何解释性、礼貌性、引导性文本。\n" +
        "无相关内容兜底：如果用户输入的语料完全不涉及医学、健康、饮食、运动、养生或生理症状，统一输出：无。\n" +
        "频率与相关性优先：提取的词汇必须是现代医学界和科普推文中出现频率最高、公众接受度最高的词汇。\n\n" +
        "Skills\n" +
        "医学术语映射：能够将用户的通俗口语（如\"拉肚子\"）精准映射到医学杂志高频词（如\"腹泻\"）和推文高频词（如\"肠胃炎\"、\"受凉\"）。\n" +
        "语义相近词拓展：精通医学实体识别（NER）与同义词库拓展。\n\n" +
        "Workflow\n" +
        "深度分析：分析用户输入，提取其核心痛点或医学诉求。\n" +
        "双轨检索：检索现代医学杂志高频对应的专业词汇；检索大众医学科普/医学推文中的高频通俗词汇。\n" +
        "词汇精简：去粗取精，保留2-4个关联度最紧密的关键词。\n" +
        "格式化输出：以\"词汇A，词汇B\"的形式输出结果。\n\n" +
        "Few-Shot Examples\n" +
        "示例1: 输入：最近经常失眠怎么办？ 输出：失眠，睡眠，睡眠障碍\n" +
        "示例2: 输入：肚子痛，而且一上午拉了好几次稀。 输出：腹泻，拉肚子，肠胃炎\n" +
        "示例3: 输入：明天北京天气怎么样？ 输出：无\n" +
        "示例4: 输入：查询地中海饮食的帖子 输出：地中海饮食，健康饮食\n" +
        "示例5: 输入：高血压吃什么好？ 输出：高血压，降压，饮食调理\n" +
        "示例6: 输入：搜一下减肥方法 输出：减肥，减重，运动";

    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public List<String> extractKeywords(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String apiKey = aiConfig.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            log.info("[关键词] API Key为空，使用本地提取");
            return localExtract(userMessage);
        }

        try {
            JSONArray messages = new JSONArray();
            
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", KEYWORD_PROMPT);
            messages.add(sysMsg);
            
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            JSONObject body = new JSONObject();
            body.put("model", aiConfig.getModel());
            body.put("messages", messages);
            body.put("temperature", 0.1);
            body.put("max_tokens", 100);
            body.put("top_p", 0.3);

            Request request = new Request.Builder()
                    .url(aiConfig.getApiUrl())
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toJSONString(), JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String respBody = response.body().string();
                    JSONObject json = JSON.parseObject(respBody);
                    JSONArray choices = json.getJSONArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
                        if (content != null && !content.isEmpty()) {
                            content = content.trim();
                            if ("无".equals(content)) {
                                log.info("[关键词] AI判定非医学: \"{}\"", userMessage);
                                return Collections.emptyList();
                            }
                            List<String> keywords = parseKeywords(content);
                            log.info("[关键词] AI提取: \"{}\" -> {}", userMessage, keywords);
                            return keywords;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[关键词] AI调用异常: {}", e.getMessage());
        }

        // AI提取失败，回退本地提取（RAG-18 整改：原实现截取原文前 8 字符，
        // "我最近血压有点高怎么办"→"我最近血压"→ LIKE 必然零召回；改用词库+停用词提取）
        log.info("[关键词] AI提取失败，回退本地提取: \"{}\"", userMessage);
        return localExtract(userMessage);
    }

    private List<String> parseKeywords(String text) {
        return Arrays.stream(text.split("[,，\n]"))
                .map(String::trim)
                .filter(k -> !k.isEmpty() && k.length() >= 1)
                .filter(k -> !"无".equals(k))
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * 本地关键词提取（RAG-18 整改）。
     * 第一优先：医学/健康领域词库命中（覆盖常见健康话题，LIKE 召回率高）；
     * 第二优先：去停用词后返回完整剩余短语（不再截断成无意义的 6 字符）。
     */
    private List<String> localExtract(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 医学词库命中优先
        String text = userMessage.replaceAll("[\\s？！？。，；：、,.!?\"']", "");
        List<String> hits = new ArrayList<>();
        for (String term : MEDICAL_TERMS) {
            if (text.contains(term)) {
                hits.add(term);
            }
        }
        if (!hits.isEmpty()) {
            return hits.stream().distinct().limit(5).collect(Collectors.toList());
        }

        // 词库未命中：去停用词取剩余短语
        Set<String> stops = new HashSet<>(Arrays.asList(
                "我", "你", "他", "她", "它", "们", "的", "了", "是", "在", "有", "和", "就", "都", "也", "还",
                "吗", "呢", "吧", "啊", "这", "那", "么", "什", "怎", "如", "何", "为", "能", "会", "要", "可",
                "以", "应", "该", "需", "想", "觉得", "不", "没", "否", "一", "下", "些", "点", "办", "样",
                "请", "问", "帮", "看", "给", "让", "把", "被", "对", "从", "很", "非", "常", "特", "别",
                "比", "较", "太", "挺", "最近", "经", "总", "已", "正", "将", "搜", "索", "网", "站", "中",
                "关", "于", "帖", "子", "查", "找", "浏", "览", "页", "面"
        ));

        StringBuilder sb = new StringBuilder();
        for (char c : userMessage.toCharArray()) {
            if (!stops.contains(String.valueOf(c)) && c > 32) {
                sb.append(c);
            }
        }
        String cleaned = sb.toString().replaceAll("[？！？。，；：、\\s]", "").trim();
        if (cleaned.isEmpty()) {
            // 全部是停用词时，退回整句前 12 字（比原 8 字更完整）
            String raw = userMessage.replaceAll("[\\s？！？。，；：、]", "");
            return Collections.singletonList(raw.length() > 12 ? raw.substring(0, 12) : raw);
        }
        // 返回完整短语（不截断），长度 ≤ 20 字避免过宽
        return Collections.singletonList(cleaned.length() > 20 ? cleaned.substring(0, 20) : cleaned);
    }

    private static final List<String> MEDICAL_TERMS = Arrays.asList(
            "血压", "血糖", "血脂", "心率", "脉搏", "体重", "身高", "BMI", "尿酸", "甘油三酯",
            "高血压", "低血压", "糖尿病", "高血糖", "低血糖", "失眠", "感冒", "发烧", "发热", "咳嗽",
            "头痛", "偏头痛", "胃痛", "胃胀", "腹泻", "便秘", "过敏", "哮喘", "贫血", "骨质疏松",
            "甲状腺", "甲亢", "甲减", "脂肪肝", "肝功能", "肾功能", "心电图", "体检", "报告",
            "症状", "化验", "指标", "正常", "异常", "吃药", "用药", "剂量", "处方", "维生素",
            "钙片", "益生菌", "饮食", "运动", "睡眠", "情绪", "焦虑", "抑郁", "心理"
    );
}
