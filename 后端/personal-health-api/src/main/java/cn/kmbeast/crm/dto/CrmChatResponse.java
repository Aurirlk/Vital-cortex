package cn.kmbeast.crm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CrmChatResponse {

    private String reply;

    private Integer rounds;

    @Builder.Default
    private List<String> toolsUsed = new ArrayList<>();

    private String sessionId;

    private Boolean isNewUser;

    /**
     * 2026-10-05：本轮意图路由识别到的角色编码。
     *
     * <p>同步链路（/user/chat、/crm/chat）由 {@code SeaChatWorkflow} 统一识别后回填，
     * 前端可直接展示「本次以营养师身份作答」之类的提示，无需自己再调一次识别接口。
     */
    private String agentType;

    /** 本轮生成的数据追问话术（档案已完整时为 null） */
    private String guidance;
}
