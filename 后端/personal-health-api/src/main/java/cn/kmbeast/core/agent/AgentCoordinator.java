package cn.kmbeast.core.agent;

import cn.kmbeast.config.AiConfig;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Agent 协调器
 * 负责智能路由用户查询到最合适的专业 Agent
 * 支持意图识别、Agent 协作、动态路由
 */
@Slf4j
@Component
public class AgentCoordinator {

    @Resource
    private AiConfig aiConfig;

    @Resource
    private cn.kmbeast.crm.config.CrmConfig crmConfig;

    private OkHttpClient httpClient;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    /** Agent 角色定义（AG-11：词表可从 crm.intent.keywords 配置覆盖） */
    private final Map<String, AgentRole> AGENT_ROLES = new LinkedHashMap<>();

    private Map<String, AgentRole> buildDefaultRoles() {
        Map<String, AgentRole> roles = new LinkedHashMap<>();
        roles.put("doctor", new AgentRole(
                "doctor", "全科医生",
                Arrays.asList("症状", "疾病", "疼痛", "发烧", "咳嗽", "头痛", "感冒", "治疗", "诊断", "不适", "难受"),
                "症状分析、分诊建议、用药指导"
        ));
        roles.put("nutritionist", new AgentRole(
                "nutritionist", "营养师",
                Arrays.asList("饮食", "营养", "减肥", "增重", "卡路里", "蛋白质", "维生素", "食谱", "膳食", "体重管理"),
                "饮食规划、营养搭配、体重管理"
        ));
        roles.put("psychologist", new AgentRole(
                "psychologist", "心理咨询师",
                Arrays.asList("心理", "情绪", "焦虑", "抑郁", "压力", "失眠", "睡不着", "心情", "烦躁", "恐惧"),
                "情绪疏导、压力管理、心理支持"
        ));
        roles.put("analyst", new AgentRole(
                "analyst", "报告分析师",
                Arrays.asList("报告", "体检", "指标", "化验", "血糖", "血压", "血脂", "肝功", "肾功", "异常"),
                "体检报告解读、异常指标分析"
        ));
        roles.put("consultant", new AgentRole(
                "consultant", "健康助手",
                Arrays.asList("健康", "养生", "运动", "锻炼", "保健", "预防", "生活", "习惯"),
                "综合健康咨询、健康生活方式建议"
        ));
        roles.put("general_assistant", new AgentRole(
                "general_assistant", "全能助手",
                Collections.emptyList(),
                "综合健康咨询（默认角色）"
        ));
        return roles;
    }

    @PostConstruct
    public void init() {
        AGENT_ROLES.putAll(buildDefaultRoles());

        // AG-11 整改：词表外部化——允许通过 crm.intent.keywords 配置覆盖内置词表
        String json = crmConfig.getIntentKeywordsJson();
        if (json != null && !json.trim().isEmpty()) {
            try {
                JSONObject override = JSON.parseObject(json);
                for (String roleKey : override.keySet()) {
                    if (AGENT_ROLES.containsKey(roleKey)) {
                        AgentRole role = AGENT_ROLES.get(roleKey);
                        List<String> keywords = override.getJSONArray(roleKey).toJavaList(String.class);
                        role.setKeywords(keywords);
                    }
                }
                log.info("[AgentCoordinator] 已从配置加载意图词表覆盖");
            } catch (Exception e) {
                log.warn("[AgentCoordinator] 意图词表配置解析失败，使用内置默认: {}", e.getMessage());
            }
        }
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        log.info("AgentCoordinator 初始化完成，共加载 {} 个 Agent 角色", AGENT_ROLES.size());
    }

    /**
     * 意图识别：根据用户输入判断最合适的 Agent
     */
    public String identifyAgent(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "general_assistant";
        }
        String lowerMessage = userMessage.toLowerCase();
        Map<String, Integer> scores = new HashMap<>();
        for (Map.Entry<String, AgentRole> entry : AGENT_ROLES.entrySet()) {
            AgentRole role = entry.getValue();
            int score = 0;
            for (String keyword : role.getKeywords()) {
                if (lowerMessage.contains(keyword.toLowerCase())) {
                    score++;
                }
            }
            if (score > 0) {
                scores.put(entry.getKey(), score);
            }
        }
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("general_assistant");
    }

    /**
     * 获取 Agent 角色信息
     */
    public AgentRole getAgentRole(String agentType) {
        return AGENT_ROLES.getOrDefault(agentType, AGENT_ROLES.get("general_assistant"));
    }

    /**
     * 获取所有 Agent 角色
     */
    public Map<String, AgentRole> getAllAgentRoles() {
        return Collections.unmodifiableMap(AGENT_ROLES);
    }

    @Data
    public static class AgentRole {
        private final String type;
        private final String name;
        private List<String> keywords;
        private final String description;

        public AgentRole(String type, String name, List<String> keywords, String description) {
            this.type = type;
            this.name = name;
            this.keywords = keywords;
            this.description = description;
        }

        public String getType() { return type; }
        public String getName() { return name; }
        public List<String> getKeywords() { return keywords; }
        public String getDescription() { return description; }
        public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    }
}
