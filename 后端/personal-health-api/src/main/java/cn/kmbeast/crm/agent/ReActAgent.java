package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.CrmException;
import cn.kmbeast.crm.agent.model.ReActResponse;
import cn.kmbeast.crm.agent.model.ToolCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class ReActAgent extends BaseReActAgent {

    public ReActResponse run(List<Map<String, String>> userMessages) {
        List<Map<String, Object>> messages = buildInitialMessages(userMessages);

        List<String> allToolsUsed = new ArrayList<>();
        int maxRounds = crmConfig.getMaxReactRounds();

        for (int round = 1; round <= maxRounds; round++) {
            log.info("[ReAct] 第 {} 轮推理, messages count={}", round, messages.size());

            ReActResponse response;
            try {
                response = callLLMWithTools(messages);
            } catch (CrmException e) {
                log.error("[ReAct] LLM调用失败: {}", e.getMessage());
                return ReActResponse.text("AI服务暂时不可用: " + e.getMessage(), allToolsUsed);
            }

            if (response.hasToolCalls()) {
                addAssistantToolCallMessage(messages, response.getToolCalls());

                for (ToolCall tc : response.getToolCalls()) {
                    log.info("[ReAct] 执行工具: {}, args={}", tc.getName(), tc.getArguments());
                    allToolsUsed.add(tc.getName());

                    addToolResultMessage(messages, tc, executeTool(tc));
                }
            } else {
                return ReActResponse.text(response.getContent(), allToolsUsed);
            }
        }

        return ReActResponse.text("推理轮次超过上限(" + maxRounds + ")，请重新提问", allToolsUsed);
    }
}
