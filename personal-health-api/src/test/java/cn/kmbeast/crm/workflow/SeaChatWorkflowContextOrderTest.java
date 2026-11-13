package cn.kmbeast.crm.workflow;

import cn.kmbeast.core.agent.Audience;
import cn.kmbeast.core.agent.orchestrator.AgentOrchestrator;
import cn.kmbeast.core.harness.HarnessEngine;
import cn.kmbeast.crm.agent.ReActAgent;
import cn.kmbeast.crm.agent.model.ReActResponse;
import cn.kmbeast.crm.dto.CrmChatRequest;
import cn.kmbeast.crm.sqlite.SqliteChatHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 同步链路「先读历史、再落库、最后拼装」的顺序回归测试。
 *
 * <p><b>为什么必须锁这个顺序</b>：2026-10-20 复核时，这条链路是「保存本轮用户消息 →
 * 读最近 10 条历史 → 逐条塞进 prompt → 再显式加一句当前问题」。
 * 而保存发生在读之前，当前这句会同时从历史里回来和被显式加一遍，
 * 结果是同一句话在 prompt 里出现两次：token 直接翻倍，模型还容易把同一问题回答两遍。
 * {@code /user/chat/stream} 与 {@code /crm/chat/stream} 出过完全一样的错，
 * 三处的成因都是「落库写在拼装前面，看上去只是顺序不同，实际改了读取的快照」。
 *
 * <p>所以这里断言的不是某一行代码的位置，而是两个外部可见的性质：
 * <ul>
 *   <li>{@code getHistory} 必须在保存用户消息<b>之前</b>被调用；</li>
 *   <li>交给 agent 的消息里，当前这句只能出现一次。</li>
 * </ul>
 * 将来谁再把保存挪回前面，这两条会同时失败。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("同步对话：历史读取与消息落库的先后顺序")
class SeaChatWorkflowContextOrderTest {

    private static final String PHONE = "13800000000";
    private static final String SESSION = "s-1";
    private static final String QUERY = "我最近血压有点高";

    @Mock
    private ReActAgent reActAgent;

    @Mock
    private SqliteChatHistoryService chatHistoryService;

    @Mock
    private AgentOrchestrator agentOrchestrator;

    @Mock
    private HarnessEngine harnessEngine;

    @InjectMocks
    private SeaChatWorkflow workflow;

    /** 历史行：SqliteChatHistoryService.getHistory 返回的就是这种 role/content 结构 */
    private static Map<String, Object> record(String role, String content) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("role", role);
        row.put("content", content);
        return row;
    }

    private static CrmChatRequest request() {
        CrmChatRequest r = new CrmChatRequest();
        r.setPhoneNumber(PHONE);
        r.setQuery(QUERY);
        r.setSessionId(SESSION);
        return r;
    }

    /** 路由与 agent 的最小可用桩：只关心喂给 agent 的消息长什么样 */
    private void stubHappyPath(String reply) {
        when(agentOrchestrator.route(any(), any(), any())).thenReturn("doctor");
        when(harnessEngine.extractSlots(any())).thenReturn(Map.of());
        when(reActAgent.runWithContext(any(), any(), any(), any())).thenReturn(ReActResponse.text(reply));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> messagesGivenToAgent() {
        ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
        verify(reActAgent).runWithContext(captor.capture(), eq("doctor"), isNull(), any());
        return captor.getValue();
    }

    @Test
    @DisplayName("老用户：当前这句在 prompt 里只出现一次，且历史先于落库被读取")
    void historyIsReadBeforeTheCurrentTurnIsPersisted() {
        // 用一个小内存假库替 chat_history：saveMessage 追加、getHistory 读当前快照。
        // 只有这样断言才能复现真实症状 —— 桩如果返回固定列表，「先存后读」到底多喂了
        // 哪一条是看不出来的，也就测不到 token 翻倍这件事本身。
        List<Map<String, Object>> store = new ArrayList<>(
                List.of(record("user", "昨晚睡不好"), record("assistant", "几点睡的？")));

        when(chatHistoryService.isNewUser(PHONE)).thenReturn(false);
        when(chatHistoryService.getHistory(eq(PHONE), anyInt())).thenAnswer(inv -> List.copyOf(store));
        when(chatHistoryService.saveMessage(eq(PHONE), eq(SESSION), eq("user"), eq(QUERY), isNull(), isNull()))
                .thenAnswer(inv -> {
                    store.add(record("user", QUERY));
                    return true;
                });
        stubHappyPath("建议每天固定时间测血压");

        workflow.processChat(request(), 4, Audience.USER);

        List<Map<String, String>> messages = messagesGivenToAgent();
        // 2 条历史 + 1 条当前问题；先落库时当前这句会从历史里再回来一次，变成 4 条
        assertEquals(3, messages.size(), "历史里混进当前这句会让消息条数变多");
        long times = messages.stream()
                .filter(m -> "user".equals(m.get("role")) && QUERY.equals(m.get("content")))
                .count();
        assertEquals(1, times, "同一句话出现两次会让 token 翻倍，模型还容易回答两遍");
        assertEquals(QUERY, messages.get(messages.size() - 1).get("content"), "当前这句必须排在最后");

        InOrder order = inOrder(chatHistoryService);
        order.verify(chatHistoryService).getHistory(eq(PHONE), anyInt());
        order.verify(chatHistoryService).saveMessage(eq(PHONE), eq(SESSION), eq("user"), eq(QUERY), isNull(), isNull());
    }

    @Test
    @DisplayName("新用户：走欢迎语，本轮不读历史（欢迎语不会被刚写入的问题挤掉）")
    void newUserTakesWelcomeMessageAndSkipsHistory() {
        when(chatHistoryService.isNewUser(PHONE)).thenReturn(true);
        when(chatHistoryService.saveMessage(eq(PHONE), eq(SESSION), eq("user"), eq(QUERY), isNull(), isNull()))
                .thenReturn(true);
        stubHappyPath("好的");

        workflow.processChat(request(), 4, Audience.USER);

        verify(chatHistoryService, never()).getHistory(any(), anyInt());
        List<Map<String, String>> messages = messagesGivenToAgent();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).get("content").contains(QUERY));
        assertTrue(messages.get(0).get("content").startsWith("您好！我是健康管理员助手"),
                "新用户欢迎语必须与本轮问题一起发出，不能因为先落库而被当成老用户跳过");
    }

    @Test
    @DisplayName("落库失败只告警，不影响本轮回答")
    void persistFailureDoesNotBreakTheReply() {
        when(chatHistoryService.isNewUser(PHONE)).thenReturn(false);
        when(chatHistoryService.getHistory(eq(PHONE), anyInt()))
                .thenReturn(List.of(record("user", "上次的问题")));
        when(chatHistoryService.saveMessage(eq(PHONE), eq(SESSION), eq("user"), eq(QUERY), isNull(), isNull()))
                .thenReturn(false);
        stubHappyPath("照常回答");

        String reply = workflow.processChat(request(), 4, Audience.USER).getReply();

        assertEquals("照常回答", reply, "聊天记录是留痕手段，写不进去不该让用户的这一轮拿不到回答");
    }
}
