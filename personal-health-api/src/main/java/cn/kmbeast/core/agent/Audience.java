package cn.kmbeast.core.agent;

/**
 * 「端」的词表 —— 多端解耦里那一维的全部合法取值。
 *
 * <p><b>为什么值得单独一个类</b>：这组字符串此前分散在三处，各写各的字面量——
 * NL2SQL 配置分层（{@code DataAgentConfigHolder.AUDIENCE_*}）、编排器的可见性判定
 * （{@code AgentOrchestrator.visibleTo} 里的 {@code "all"}）、以及
 * {@code agent_config.audience} 的列注释。同一个词写三遍，改一处漏两处只是时间问题；
 * 而这里漏一处的后果不是报错，是<b>某个 Agent 在某些端上莫名其妙地消失或出现</b>，
 * 排查时只能靠猜。</p>
 *
 * <p>取值与 {@code agent_config.audience} 的列注释严格对齐：
 * {@code user 用户端 / doctor 医生端 / admin 管理端 / all 全部}。</p>
 *
 * <p><b>null 与 {@link #ALL} 不是一回事</b>，虽然两者当前都表现为「不过滤」：
 * {@code ALL} 是「这个 Agent 声明自己对所有端开放」，null 是「调用方没有说自己属于哪个端」。
 * 后者只是历史兼容（改造前没有「端」的概念），不是一种正常状态 ——
 * 所以生产代码里的路由入口把 audience 做成必填参数，见
 * {@code SeaChatWorkflow#route(Integer, String, String)}。</p>
 */
public final class Audience {

    private Audience() {
    }

    /** 用户端（{@code user_account} 角色 2）。 */
    public static final String USER = "user";

    /** 医生端（角色 3）。 */
    public static final String DOCTOR = "doctor";

    /** 管理端。 */
    public static final String ADMIN = "admin";

    /** 通配：对所有端可见。 */
    public static final String ALL = "all";

    /**
     * 账号角色编码 → 端。
     *
     * <p>角色编码是{@code user_account.role_id} 的既有约定（1 管理员 / 2 用户 / 3 医生），
     * 与 {@code ToolContext.setIdentity} 同一套。放在这里是因为两个词表必须能互相翻译，
     * 而翻译表只该有一份。</p>
     *
     * <p><b>为什么运行审计要按角色取端、而不是让调用方再传一遍</b>：
     * 调用方传的那个值是「它自认为属于哪一端」，角色是「鉴权时实际按哪一端对待你」。
     * 两者不一致时（例如某条链路把身份写死成 roleId=2 却自称医生端），
     * 审计里记下的是事实、而如果按调用方所传记录，记下的就是那个错误的自述 ——
     * 出事时最需要审计说话，而它会跟着一起错。</p>
     *
     * @return 未知角色或未登录一律 null（= 端未知），不猜
     */
    public static String fromRoleId(Integer roleId) {
        if (roleId == null) {
            return null;
        }
        switch (roleId) {
            case 1:
                return ADMIN;
            case 2:
                return USER;
            case 3:
                return DOCTOR;
            default:
                return null;
        }
    }
}
