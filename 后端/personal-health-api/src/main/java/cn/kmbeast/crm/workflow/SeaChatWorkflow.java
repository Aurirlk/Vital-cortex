package cn.kmbeast.crm.workflow;

import cn.kmbeast.crm.agent.ReActAgent;
import cn.kmbeast.crm.agent.model.ReActResponse;
import cn.kmbeast.crm.agent.tool.ToolContext;
import cn.kmbeast.crm.dto.CrmChatRequest;
import cn.kmbeast.crm.dto.CrmChatResponse;
import cn.kmbeast.crm.sqlite.SqliteChatHistoryService;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class SeaChatWorkflow {

    @Resource
    private ReActAgent reActAgent;

    @Resource
    private SqliteChatHistoryService chatHistoryService;

    @Resource
    private UserMapper userMapper;

    private static final String WELCOME_MESSAGE =
            "您好！我是健康管理助手，可以帮您解答健康问题、分析体检报告、提供营养饮食建议。" +
            "请告诉我您想咨询什么？";

    public CrmChatResponse processChat(CrmChatRequest request) {
        String phoneNumber = request.getPhoneNumber();
        String query = request.getQuery();

        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return CrmChatResponse.builder()
                    .reply("手机号不能为空")
                    .rounds(0)
                    .isNewUser(false)
                    .build();
        }

        if (query == null || query.trim().isEmpty()) {
            return CrmChatResponse.builder()
                    .reply("请输入您的问题")
                    .rounds(0)
                    .isNewUser(false)
                    .build();
        }

        boolean isNewUser = chatHistoryService.isNewUser(phoneNumber);

        String sessionId;
        if (request.getSessionId() != null && !request.getSessionId().trim().isEmpty()) {
            sessionId = request.getSessionId();
        } else {
            sessionId = UUID.randomUUID().toString();
        }

        chatHistoryService.saveMessage(phoneNumber, sessionId, "user", query, null, null);

        Integer userId = resolveUserId(phoneNumber);

        try {
            ToolContext.setPhoneAndUserId(phoneNumber, userId);

            List<Map<String, String>> userMessages = new ArrayList<>();

            if (isNewUser) {
                userMessages.add(userMsg("user", WELCOME_MESSAGE + "\n\n现在用户说：" + query));
            } else {
                List<Map<String, Object>> history = chatHistoryService.getHistory(phoneNumber, 10);
                for (Map<String, Object> record : history) {
                    String role = (String) record.get("role");
                    String content = (String) record.get("content");
                    if (role != null && content != null) {
                        userMessages.add(userMsg(role, content));
                    }
                }
                userMessages.add(userMsg("user", query));
            }

            ReActResponse reActResponse = reActAgent.run(userMessages);
            String aiReply = reActResponse != null ? reActResponse.getContent() : "AI服务暂时不可用";
            List<String> toolsUsed = reActResponse != null ? reActResponse.getToolsUsed() : new ArrayList<>();

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("tools_used", toolsUsed);
            metadata.put("session_id", sessionId);
            chatHistoryService.saveMessage(phoneNumber, sessionId, "assistant", aiReply, null, metadata);

            return CrmChatResponse.builder()
                    .reply(aiReply)
                    .rounds(1)
                    .toolsUsed(toolsUsed)
                    .sessionId(sessionId)
                    .isNewUser(isNewUser)
                    .build();
        } finally {
            ToolContext.clear();
        }
    }

    public List<Map<String, String>> buildInitialMessages(CrmChatRequest request) {
        String phoneNumber = request.getPhoneNumber();
        String query = request.getQuery();

        boolean isNewUser = chatHistoryService.isNewUser(phoneNumber);
        List<Map<String, String>> messages = new ArrayList<>();

        if (isNewUser) {
            messages.add(userMsg("user", WELCOME_MESSAGE + "\n\n现在用户说：" + query));
        } else {
            List<Map<String, Object>> history = chatHistoryService.getHistory(phoneNumber, 10);
            for (Map<String, Object> record : history) {
                String role = (String) record.get("role");
                String content = (String) record.get("content");
                if (role != null && content != null) {
                    messages.add(userMsg(role, content));
                }
            }
            messages.add(userMsg("user", query));
        }

        return messages;
    }

    public List<Map<String, Object>> getHistory(String phoneNumber) {
        return chatHistoryService.getHistory(phoneNumber, 50);
    }

    public Map<String, Object> getSqliteStats() {
        return chatHistoryService.getStats();
    }

    public List<Map<String, Object>> executeSqlQuery(String sql) {
        return chatHistoryService.executeQuery(sql);
    }

    private Integer resolveUserId(String phoneNumber) {
        try {
            User user = userMapper.findByPhone(phoneNumber);
            return user != null ? user.getId() : null;
        } catch (Exception e) {
            log.warn("[CRM] phone→userId映射失败: phone={}", phoneNumber);
            return null;
        }
    }

    private Map<String, String> userMsg(String role, String content) {
        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }
}
