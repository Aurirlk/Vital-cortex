package cn.kmbeast.core.agent;

import cn.kmbeast.crm.agent.IntentRouter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 一轮对话的路由结果：识别出的角色 + 本轮新采到的槽位 + 缺失项追问话术 + 意图倾向。
 *
 * <p><b>为什么要有这个类</b>：同步链路（/user/chat、/crm/chat）与流式链路
 * （/user/chat/stream、/crm/chat/stream）原本各自「识别角色 → 抽槽位 → 落库 →
 * 拼追问」，代码抄了四遍，任何一处改动都容易漏。这里把它收敛成一次调用的结果，
 * 由 {@link AgentCoordinator} 与 {@link cn.kmbeast.core.harness.HarnessEngine}
 * 在应用层完成，工作流只负责把结果喂给对应角色的 agent。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRouting {

    /**
     * 本轮角色编码，如 doctor / nutritionist；恒不为 null（未知角色由
     * AgentCoordinator 归一为默认角色），调用方不必再判空。
     */
    private String agentType;

    /** 本轮从用户话术里新采集到的健康槽位名（用于前端展示与回显） */
    private Set<String> extractedSlots = new LinkedHashSet<>();

    /** 档案缺失项生成的追问话术；档案完整时为 null */
    private String guidance;

    /**
     * 阶段 3（2026-10-08）：意图软路由结果。供 agent 拼 prompt 引导与四层 fail-safe 使用。
     * 可能为 null（纯闲聊 / 词表缺失降级）。
     */
    private IntentRouter.IntentHint intentHint;

    public ChatRouting(String agentType, String guidance) {
        this.agentType = agentType;
        this.extractedSlots = new LinkedHashSet<>();
        this.guidance = guidance;
    }

    /**
     * 是否产生了新槽位——前端据此决定是否刷新患者档案等展示。
     */
    public boolean hasNewSlots() {
        return extractedSlots != null && !extractedSlots.isEmpty();
    }
}
