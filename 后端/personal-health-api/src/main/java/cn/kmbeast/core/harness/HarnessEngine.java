package cn.kmbeast.core.harness;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.PatientProfileMapper;
import cn.kmbeast.pojo.entity.PatientProfile;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据引导引擎（SlotFilling，2026-10-04 重构）
 *
 * <p><b>为什么重构</b>：原实现只有三个静态方法（查完整性、算 BMI、评 BMI），
 * 且<b>全项目无人调用</b> —— 是一段死代码。而 AI 对话时最常见的失败模式恰恰是：
 * 用户说「我最近血压有点高」，AI 不知道用户的年龄/性别/既往史，给出的建议只能泛泛而谈。
 *
 * <p><b>现在的职责</b>（三件事，都接入真实对话流）：
 * <ol>
 *   <li>{@link #checkDataCompleteness} 找出缺失的关键槽位（性别/年龄/身高/体重/慢病）</li>
 *   <li>{@link #buildGuidancePrompt} 把缺失槽位转成 system prompt 里的追问指令，
 *       让 AI <b>主动追问</b>而不是空泛回答</li>
 *   <li>{@link #extractSlots} 从用户自然语言里抽取已说出的数值
 *       （如「我 175 厘米 70 公斤」），供后续落库</li>
 * </ol>
 *
 * <p><b>设计约束</b>：本类<b>不做 LLM 调用</b>，只用正则做轻量抽取。
 * 理由：抽取一次 LLM 调用要几百毫秒，而对话主循环已经至少调一次；
 * 且高频轻量抽取用规则更稳（不会幻觉出「我身高 300cm」）。
 */
@Slf4j
@Component
public class HarnessEngine {

    /**
     * 关键槽位定义。
     *
     * <p>只列「缺了就没法给个性化建议」的字段。血压/血糖等指标不进此列 ——
     * 它们属于 {@code user_health} 的 EAV 序列，由 get_health_data 工具按需取。
     */
    private static final List<SlotDef> REQUIRED_SLOTS = List.of(
            new SlotDef("gender", "性别", "请告诉我您的性别（男/女），以便我为您提供更准确的健康建议。"),
            new SlotDef("age", "年龄", "请问您今年多大年龄？年龄会影响很多健康指标的建议阈值。"),
            new SlotDef("height", "身高", "方便问一下您的身高吗？我需要它来计算 BMI。"),
            new SlotDef("weight", "体重", "方便问一下您的体重吗？和身高一起可以算 BMI。"),
            new SlotDef("chronicDiseases", "既往病史", "请问您有没有慢性病或者长期在吃的药？比如高血压、糖尿病。")
    );

    @Resource
    private PatientProfileMapper patientProfileMapper;

    // ================================================================ 1. 完整性检查

    /**
     * 检查健康档案缺失项。
     *
     * @param existingData 已有数据（key 为槽位名）；传 null 表示完全没数据
     * @return 缺失的槽位 → 引导话术。全部齐全时返回空 Map
     */
    public Map<String, String> checkDataCompleteness(Map<String, String> existingData) {
        Map<String, String> guidance = new LinkedHashMap<>();
        Map<String, String> data = existingData != null ? existingData : Collections.emptyMap();
        for (SlotDef def : REQUIRED_SLOTS) {
            if (isSlotMissing(data.get(def.key()))) {
                guidance.put(def.key(), def.guidance());
            }
        }
        return guidance;
    }

    /**
     * 判定槽位是否缺失。
     *
     * <p>除了 null / 空串，<b>还要把「空 JSON 数组」与「无」视为缺失</b>：
     * 档案里慢病存的是 {@code chronic_diseases} JSON 文本，
     * 迁移脚本归一化后「无慢性病」会写成 {@code []}，
     * 若不识别就会认为「用户已填写病史」而不再追问 —— 但实际等于没填。
     */
    private boolean isSlotMissing(String v) {
        if (v == null) {
            return true;
        }
        String t = v.trim();
        if (t.isEmpty() || "[]".equals(t) || "{}".equals(t)) {
            return true;
        }
        // 「无」「暂无」这类占位词同样算未填
        return "无".equals(t) || "暂无".equals(t) || "null".equalsIgnoreCase(t);
    }

    /**
     * 从数据库读取当前用户的档案，返回槽位视图。
     *
     * <p>未建档时返回全 null 的结构（不报错），
     * 由 {@link #checkDataCompleteness} 判定为「全缺失」。
     */
    public Map<String, String> loadUserSlots(Integer userId) {
        Map<String, String> slots = new LinkedHashMap<>();
        for (SlotDef def : REQUIRED_SLOTS) {
            slots.put(def.key(), null);
        }
        if (userId == null) {
            return slots;
        }
        try {
            PatientProfile p = patientProfileMapper.getByUserId(userId);
            if (p == null) {
                return slots;
            }
            slots.put("gender", normalizeGender(p.getGender()));
            slots.put("age", p.getAge() != null ? String.valueOf(p.getAge()) : null);
            slots.put("height", p.getHeight() != null ? trimNum(p.getHeight()) : null);
            slots.put("weight", p.getWeight() != null ? trimNum(p.getWeight()) : null);
            // 慢病是 JSON 数组文本，取非空判断即可
            String chronic = p.getChronicDiseases();
            slots.put("chronicDiseases",
                    (chronic == null || chronic.isBlank() || "[]".equals(chronic.trim())) ? null : chronic);
        } catch (Exception e) {
            // 档案表查询失败不能阻断对话，降级为「全部缺失」让 AI 追问
            log.warn("[Harness] 读取用户档案失败，按全缺失处理: userId={}, err={}", userId, e.getMessage());
        }
        return slots;
    }

    /** 便捷方法：读当前登录用户的槽位 */
    public Map<String, String> loadCurrentUserSlots() {
        return loadUserSlots(LocalThreadHolder.getUserId());
    }

    // ================================================================ 2. 引导 Prompt

    /**
     * 把缺失槽位转成 system prompt 的追问指令。
     *
     * <p>注入到 ReAct 的 system prompt 末尾，让 AI 在回答时顺带追问。
     *
     * @param missingSlots 缺失槽位 → 话术
     * @return 可直接拼接的提示词；无缺失时返回空串
     */
    public String buildGuidancePrompt(Map<String, String> missingSlots) {
        if (missingSlots == null || missingSlots.isEmpty()) {
            return "";
        }
        // 按 REQUIRED_SLOTS 的声明顺序取，而不是直接遍历 missingSlots.values() ——
        // 后者若传入 HashMap 顺序不确定，会导致每次追问的项都不一样。
        List<String> ordered = new ArrayList<>();
        for (SlotDef def : REQUIRED_SLOTS) {
            String g = missingSlots.get(def.key());
            if (g != null && !g.isBlank()) {
                ordered.add(g);
            }
        }
        if (ordered.isEmpty()) {
            return "";
        }

        // 一次最多问 2 项，问太多会让人烦
        int askCount = Math.min(ordered.size(), 2);
        String ask = String.join(" ", ordered.subList(0, askCount));

        return "\n\n## 本次需要主动追问的信息（重要）\n"
                + "用户尚未提供以下健康档案信息，请在回答问题时自然地顺带追问，不要生硬罗列：\n"
                + ask + "\n"
                + "要求：\n"
                + "1. 追问要嵌在回答里，不要单独列成问卷；\n"
                + "2. 一次最多问 " + askCount + " 项，其余留待后续对话；\n"
                + "3. 用户已经说过的信息不要重复问。\n";
    }

    /**
     * 一站式：读当前用户档案 → 找缺失项 → 生成引导 prompt。
     *
     * @return 无缺失时返回空串
     */
    public String buildGuidancePromptForCurrentUser() {
        Map<String, String> missing = checkDataCompleteness(loadCurrentUserSlots());
        return buildGuidancePrompt(missing);
    }

    // ================================================================ 3. 槽位抽取

    /**
     * 槽位抽取规则。
     *
     * <p><b>关键设计：单位一律必需</b>。
     * 初版把单位写成可选（{@code (?:cm|厘米)?}），结果正则会先匹配到句中任意两位数字 ——
     * 实测「我34岁，身高175厘米」里身高规则先抓到「34」、体重规则也抓到「34」，
     * 因为 {@code \d{2,3}} 不区分语义。抽错会污染患者档案，比漏抽危害大得多。
     *
     * <p>因此改为：<b>只有带明确单位或紧跟「身高/体重」二字时才抽取</b>。
     * 用户说「我 175」这种省略单位的说法不予采信 —— 宁可漏抽，等引导追问。
     */
    private static final List<ExtractRule> EXTRACT_RULES = List.of(
            // 身高：175cm / 175厘米 / 身高175 / 身高175cm
            new ExtractRule("height",
                    Pattern.compile("(?:身高|身长)\\s*(?:是|为|：|:)?\\s*(\\d{2,3}(?:\\.\\d)?)\\s*(?:cm|CM|厘米|公分)?"
                            + "|(\\d{2,3}(?:\\.\\d)?)\\s*(?:cm|CM|厘米|公分)"),
                    50, 250, "身高"),
            // 体重：70kg / 70公斤 / 体重70 / 140斤（斤在 convert 里换算）
            new ExtractRule("weight",
                    Pattern.compile("(?:体重)\\s*(?:是|为|：|:)?\\s*(\\d{2,3}(?:\\.\\d)?)\\s*(?:kg|KG|公斤|千克|斤)?"
                            + "|(\\d{2,3}(?:\\.\\d)?)\\s*(?:kg|KG|公斤|千克|斤)"),
                    20, 300, "体重"),
            // 年龄：34岁（岁字必需）
            new ExtractRule("age", Pattern.compile("(\\d{1,3})\\s*(?:岁|周岁)"), 1, 120, "年龄")
    );

    /**
     * 从用户消息中抽取健康槽位。
     *
     * <p><b>正则优先匹配带单位的形式</b>，避免「我今年 34 岁」里的 34 被当成身高。
     * 无单位的裸数字<b>不猜</b>（宁可漏抽也不能抽错，抽错会污染档案）。
     *
     * @param userMessage 用户原话
     * @return 槽位名 → 值（字符串形式）。没抽到返回空 Map
     */
    public Map<String, String> extractSlots(String userMessage) {
        Map<String, String> extracted = new LinkedHashMap<>();
        if (userMessage == null || userMessage.isBlank()) {
            return extracted;
        }
        String msg = userMessage.trim();

        for (ExtractRule rule : EXTRACT_RULES) {
            Matcher m = rule.pattern.matcher(msg);
            // 同一规则可能多次匹配（如「身高175 体重70」里两条规则各命中自己的），
            // 取第一个落在合理区间的值即可
            while (m.find()) {
                Double v = firstNumericGroup(m, m.groupCount());
                if (v == null) {
                    continue;
                }
                if (v < rule.min || v > rule.max) {
                    continue;   // 超出人体合理范围，视为误匹配，继续找下一个
                }
                // 体重用「斤」表述时换算为公斤
                if ("weight".equals(rule.key) && isJinExpression(msg, m.group())) {
                    v = v / 2.0;
                }
                extracted.put(rule.key, formatValue(v));
                break;
            }
        }

        // 性别靠关键词（数字无意义）
        String gender = extractGender(msg);
        if (gender != null) {
            extracted.put("gender", gender);
        }
        return extracted;
    }

    /**
     * 取第一个可解析的数值分组。
     *
     * <p>规则里「带前缀」与「仅单位」是两组，用 {@code |} 分开，
     * 哪组命中取哪组。
     *
     * @return 解析成功返回数值，全部为空或非法返回 null
     */
    private Double firstNumericGroup(Matcher m, int groupCount) {
        for (int g = 1; g <= groupCount; g++) {
            String s = m.group(g);
            if (s == null || s.isBlank()) {
                continue;
            }
            try {
                return Double.valueOf(s.trim());
            } catch (NumberFormatException ignored) {
                // 该组不是数字，试下一组
            }
        }
        return null;
    }

    /**
     * 判断这次匹配是不是「斤」表述。
     *
     * <p>看<b>本次匹配到的片段</b>而不是整条消息 —— 整句里可能同时出现
     * 「体重140斤」和「身高175cm」，用整句判断会误判身高那次的单位。
     */
    private boolean isJinExpression(String fullMsg, String matchedText) {
        if (matchedText == null) {
            return false;
        }
        // 匹配片段里有「斤」且没有 kg/公斤/千克
        if (matchedText.contains("斤")) {
            return !matchedText.toLowerCase().contains("kg")
                    && !matchedText.contains("公斤")
                    && !matchedText.contains("千克");
        }
        return false;
    }

    private String extractGender(String msg) {
        // 先判否定式，避免「我不是男的」被误判
        boolean negated = msg.contains("不是男") || msg.contains("不是女");
        boolean male = msg.contains("我是男") || msg.contains("男性")
                || msg.equals("男") || msg.startsWith("男 ")
                || msg.contains("我是男的");
        boolean female = msg.contains("我是女") || msg.contains("女性")
                || msg.equals("女") || msg.startsWith("女 ")
                || msg.contains("我是女的");
        if (negated) {
            return null;
        }
        if (male) return "男";
        if (female) return "女";
        return null;
    }

    // ================================================================ 4. BMI（保留原能力）

    public Double calculateBMI(Double heightCm, Double weightKg) {
        if (heightCm == null || weightKg == null || heightCm <= 0 || weightKg <= 0) {
            return null;
        }
        double heightM = heightCm / 100.0;
        return Math.round(weightKg / (heightM * heightM) * 10.0) / 10.0;
    }

    public String evaluateBMI(Double bmi) {
        if (bmi == null) return "未知";
        if (bmi < 18.5) return "偏瘦";
        if (bmi < 24.0) return "正常";
        if (bmi < 28.0) return "超重";
        return "肥胖";
    }

    // ================================================================ 5. 落库

    /**
     * 把抽取到的槽位写入患者档案。
     *
     * <p><b>只写 null 槽位</b>：已有值不覆盖，避免用户口误改掉已核实的档案数据。
     *
     * @return 实际写入的字段数
     */
    public int persistSlots(Integer userId, Map<String, String> slots) {
        if (userId == null || slots == null || slots.isEmpty()) {
            return 0;
        }
        try {
            PatientProfile p = patientProfileMapper.getByUserId(userId);
            boolean isNew = (p == null);
            if (isNew) {
                p = new PatientProfile();
                p.setUserId(userId);
            }

            int changed = 0;
            if (slots.get("gender") != null && isBlank(p.getGender())) {
                p.setGender(slots.get("gender"));
                changed++;
            }
            if (slots.get("age") != null && p.getAge() == null) {
                p.setAge(parseInt(slots.get("age")));
                changed++;
            }
            if (slots.get("height") != null && p.getHeight() == null) {
                p.setHeight(parseDouble(slots.get("height")));
                changed++;
            }
            if (slots.get("weight") != null && p.getWeight() == null) {
                p.setWeight(parseDouble(slots.get("weight")));
                changed++;
            }
            // 身高体重都齐了才算得 BMI，补算一次
            if (p.getHeight() != null && p.getWeight() != null && p.getBmi() == null) {
                Double bmi = calculateBMI(p.getHeight(), p.getWeight());
                if (bmi != null) {
                    p.setBmi(bmi);
                }
            }

            if (changed > 0) {
                patientProfileMapper.insertOrUpdate(p);
                log.info("[Harness] 写入患者档案 {} 个字段: userId={}, keys={}", changed, userId, slots.keySet());
            }
            return changed;
        } catch (Exception e) {
            // 档案写入失败不影响对话，AI 仍可基于本轮上下文回答
            log.warn("[Harness] 写入患者档案失败: userId={}, err={}", userId, e.getMessage());
            return 0;
        }
    }

    /** 当前登录用户的缺失槽位（供对外接口查询） */
    public Map<String, String> currentUserMissingSlots() {
        return checkDataCompleteness(loadCurrentUserSlots());
    }

    /** 所有关键槽位名（前端可用来渲染「完善档案」表单） */
    public Set<String> getRequiredSlotKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (SlotDef def : REQUIRED_SLOTS) {
            keys.add(def.key());
        }
        return keys;
    }

    // ================================================================ 内部

    private String normalizeGender(String g) {
        if (g == null || g.isBlank()) return null;
        String s = g.trim().toLowerCase();
        if (s.equals("male") || s.equals("m") || s.equals("1") || s.equals("男")) return "男";
        if (s.equals("female") || s.equals("f") || s.equals("2") || s.equals("女")) return "女";
        return g.trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty() || "[]".equals(s.trim());
    }

    private String formatValue(double v) {
        if (v == Math.floor(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    private String trimNum(Double d) {
        return formatValue(d);
    }

    private Integer parseInt(String s) {
        try {
            return s == null ? null : Integer.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseDouble(String s) {
        try {
            return s == null ? null : Double.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 槽位定义 */
    private record SlotDef(String key, String label, String guidance) {
    }

    /**
     * 抽取规则
     *
     * @param key   槽位名
     * @param pattern 匹配模式，可能含多个数值分组（见 firstNumericGroup）
     * @param min   合理下界，超出视为误匹配
     * @param max   合理上界
     * @param label 中文字段名（供日志与调试）
     */
    private record ExtractRule(String key, Pattern pattern,
                               double min, double max, String label) {
    }
}
