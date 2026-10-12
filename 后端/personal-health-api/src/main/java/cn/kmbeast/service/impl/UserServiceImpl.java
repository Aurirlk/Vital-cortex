package cn.kmbeast.service.impl;

import cn.kmbeast.core.auth.AuthSessionManager;
import cn.kmbeast.core.auth.LoginAttemptGuard;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.base.QueryDto;
import cn.kmbeast.pojo.dto.query.extend.UserQueryDto;
import cn.kmbeast.pojo.dto.update.UserLoginDTO;
import cn.kmbeast.pojo.dto.update.UserRegisterDTO;
import cn.kmbeast.pojo.dto.update.UserUpdateDTO;
import cn.kmbeast.pojo.em.LoginStatusEnum;
import cn.kmbeast.pojo.em.RoleEnum;
import cn.kmbeast.pojo.em.WordStatusEnum;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.pojo.vo.ChartVO;
import cn.kmbeast.pojo.vo.UserVO;
import cn.kmbeast.service.UserService;
import cn.kmbeast.utils.DateUtil;
import cn.kmbeast.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    /** 登录失败统一提示（不区分「账号不存在」与「密码错误」，避免账号枚举） */
    private static final String LOGIN_FAIL_MSG = "账号或密码错误";

    // ================================================================
    // 用户设置（2026-10-10 实现，此前只有硬编码默认值与「未实现」错误）
    //
    // 设置项按最初的简化决定并入 user 表（不单建 user_settings 表），见
    // Data/02_迁移脚本/08_user_settings_columns.sql。
    //
    // 下面这些白名单与前端 views/user/UserSettings.vue 里 <el-option> 的取值一一对应。
    // 前端加了新选项却忘了加到这里时，保存会<b>明确报错</b>而不是静默丢弃 ——
    // 「保存成功但没生效」正是原来这个页面最大的问题。
    // ================================================================

    private static final String DEFAULT_LANGUAGE = "zh-CN";
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";
    private static final String DEFAULT_PROFILE_VISIBILITY = "private";
    private static final String DEFAULT_AI_AGENT = "general_assistant";
    private static final String DEFAULT_AI_CHAT_STYLE = "friendly";
    private static final int DEFAULT_CONTEXT_ROUNDS = 10;
    private static final int MIN_CONTEXT_ROUNDS = 1;
    private static final int MAX_CONTEXT_ROUNDS = 20;

    private static final Set<String> TOP_LEVEL_KEYS =
            Set.of("language", "timezone", "notification", "privacy", "ai");
    private static final Set<String> ALLOWED_LANGUAGES = Set.of("zh-CN", "en-US");
    private static final Set<String> ALLOWED_TIMEZONES =
            Set.of("Asia/Shanghai", "America/New_York", "Europe/London");
    private static final Set<String> ALLOWED_PROFILE_VISIBILITY = Set.of("public", "private");
    private static final Set<String> ALLOWED_AI_AGENTS =
            Set.of("general_assistant", "doctor", "nutritionist", "psychologist", "analyst");
    private static final Set<String> ALLOWED_AI_CHAT_STYLES =
            Set.of("professional", "friendly", "concise");

    @Resource
    private UserMapper userMapper;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private AuthSessionManager authSessionManager;

    /** 登录失败计数与临时封禁（防撞库），见 LoginAttemptGuard 的类注释 */
    @Resource
    private LoginAttemptGuard loginAttemptGuard;

    @Override
    public Result<String> register(UserRegisterDTO userRegisterDTO) {
        User user = userMapper.getByActive(
                User.builder().userName(userRegisterDTO.getUserName()).build()
        );
        if (Objects.nonNull(user)) {
            return ApiResult.error("用户名已经被使用，请换一个");
        }
        User entity = userMapper.getByActive(
                User.builder().userAccount(userRegisterDTO.getUserAccount()).build()
        );
        if (Objects.nonNull(entity)) {
            return ApiResult.error("账号不可用");
        }
        User saveEntity = User.builder()
                .userRole(RoleEnum.USER.getRole())
                .userName(userRegisterDTO.getUserName())
                .userAccount(userRegisterDTO.getUserAccount())
                .userAvatar(userRegisterDTO.getUserAvatar())
                .userPwd(passwordEncoder.encode(userRegisterDTO.getUserPwd()))
                .userEmail(userRegisterDTO.getUserEmail())
                .createTime(LocalDateTime.now())
                .isLogin(LoginStatusEnum.USE.getFlag())
                .isWord(WordStatusEnum.USE.getFlag()).build();
        userMapper.insert(saveEntity);
        return ApiResult.success("注册成功");
    }

    @Override
    public Result<Object> login(UserLoginDTO userLoginDTO) {
        if (userLoginDTO == null || userLoginDTO.getUserAccount() == null
                || userLoginDTO.getUserAccount().isBlank()
                || userLoginDTO.getUserPwd() == null) {
            return ApiResult.error("请输入账号和密码");
        }
        String account = userLoginDTO.getUserAccount();
        // 2026-10-10 修复（无防撞库）：此前登录只有 Sentinel 一条全局 QPS=10 的流控，
        // 语义是「整个接口每秒 10 次」而非「某账号错了几次」——攻击者一人即可吃满配额
        // 把真实用户挡在门外，且对同一账号每秒可试 10 次密码（一天 86 万次）。
        // 现在先按账号与来源 IP 查临时封禁，再进入查库与验密。
        String blocked = loginAttemptGuard.checkBlocked(account);
        if (blocked != null) {
            return ApiResult.error(blocked);
        }
        User user = userMapper.getByActive(
                User.builder().userAccount(account).build()
        );
        if (!Objects.nonNull(user)) {
            // 2026-10-10 修复（账号枚举 + 计数）：原先「账号不存在」与「密码错误」
            // 是两句不同的话，等于对外提供了一个账号有效性探测接口。
            // 统一为通用提示；且不存在也照样计数 —— 否则「计不计数」本身又成了枚举信号。
            loginAttemptGuard.recordFailure(account);
            return ApiResult.error(LOGIN_FAIL_MSG);
        }
        // 使用BCrypt验证密码
        if (!passwordEncoder.matches(userLoginDTO.getUserPwd(), user.getUserPwd())) {
            loginAttemptGuard.recordFailure(account);
            return ApiResult.error(LOGIN_FAIL_MSG);
        }
        if (Boolean.TRUE.equals(user.getIsLogin())) {
            return ApiResult.error("登录状态异常");
        }
        // 登录成功清零失败计数，避免「白天输错几次、晚上正常登录被误封」
        loginAttemptGuard.clearOnSuccess(account);
        // roadmap §1.3：登录即递增会话版本，版本写入 token（ver claim）；
        // 后续锁定/登出/改密再次递增，旧 token 立即失效。
        int ver = authSessionManager.nextVersion(user.getId());
        String token = jwtUtil.toToken(user.getId(), user.getUserRole(), ver);
        Map<String, Object> map = new HashMap<>();
        map.put("token", token);
        map.put("role", user.getUserRole());
        return ApiResult.success("登录成功", map);
    }

    @Override
    public Result<UserVO> auth() {
        Integer userId = LocalThreadHolder.getUserId();
        User queryEntity = User.builder().id(userId).build();
        User user = userMapper.getByActive(queryEntity);
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return ApiResult.success(userVO);
    }

    @Override
    public Result<List<User>> query(UserQueryDto userQueryDto) {
        List<User> users = userMapper.query(userQueryDto);
        Integer count = userMapper.queryCount(userQueryDto);
        return PageResult.success(users, count);
    }

    @Override
    public Result<String> update(UserUpdateDTO userUpdateDTO) {
        User updateEntity = User.builder().id(LocalThreadHolder.getUserId()).build();
        BeanUtils.copyProperties(userUpdateDTO, updateEntity);
        if (updateEntity.getUserPwd() != null && !updateEntity.getUserPwd().isEmpty()) {
            updateEntity.setUserPwd(passwordEncoder.encode(updateEntity.getUserPwd()));
        }
        userMapper.update(updateEntity);
        return ApiResult.success();
    }

    @Override
    public Result<String> batchDelete(List<Integer> ids) {
        userMapper.batchDelete(ids);
        return ApiResult.success();
    }

    @Override
    public Result<String> updatePwd(Map<String, String> map) {
        String oldPwd = map.get("oldPwd");
        String newPwd = map.get("newPwd");
        User user = userMapper.getByActive(
                User.builder().id(LocalThreadHolder.getUserId()).build()
        );
        // 使用BCrypt验证旧密码
        if (!passwordEncoder.matches(oldPwd, user.getUserPwd())) {
            return ApiResult.error("原始密码验证失败");
        }
        // 使用BCrypt加密新密码
        user.setUserPwd(passwordEncoder.encode(newPwd));
        userMapper.update(user);
        // roadmap §1.3：改密后使该用户所有旧 token 失效（含其他设备的登录态）
        authSessionManager.nextVersion(user.getId());
        return ApiResult.success();
    }

    @Override
    public Result<String> logout() {
        // roadmap §1.3：登出即递增会话版本，使该用户全部存量 token 失效（服务端可感知）
        Integer userId = LocalThreadHolder.getUserId();
        if (userId != null) {
            authSessionManager.nextVersion(userId);
        }
        return ApiResult.success("已退出登录");
    }

    @Override
    public Result<UserVO> getById(Integer id) {
        User user = userMapper.getByActive(User.builder().id(id).build());
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return ApiResult.success(userVO);
    }

    @Override
    public Result<String> insert(UserRegisterDTO userRegisterDTO) {
        return register(userRegisterDTO);
    }

    @Override
    public Result<String> backUpdate(User user) {
        // 防止管理员把账号（含自己或最后一个可登录管理员）锁死，导致无法登录后台
        if (Boolean.TRUE.equals(user.getIsLogin())) {
            Integer currentUserId = LocalThreadHolder.getUserId();
            // 不能锁定当前登录的账号
            if (currentUserId != null && currentUserId.equals(user.getId())) {
                return ApiResult.error("不能锁定当前登录的账号");
            }
            // 目标为管理员时，确保锁定后仍有至少一个可登录的管理员
            User existing = userMapper.getByActive(User.builder().id(user.getId()).build());
            if (existing != null && RoleEnum.ADMIN.getRole().equals(existing.getUserRole())) {
                UserQueryDto adminQuery = new UserQueryDto();
                adminQuery.setRole(true);
                List<User> admins = userMapper.query(adminQuery);
                long remainingUnlocked = admins.stream()
                        .filter(a -> !Boolean.TRUE.equals(a.getIsLogin())
                                && !a.getId().equals(user.getId()))
                        .count();
                if (remainingUnlocked <= 0) {
                    return ApiResult.error("至少需保留一个可登录的管理员账号");
                }
            }
        }
        // 密码若提供则 BCrypt 编码（与注册/改密保持一致）；空值不覆盖原密码
        if (user.getUserPwd() != null && !user.getUserPwd().isEmpty()) {
            user.setUserPwd(passwordEncoder.encode(user.getUserPwd()));
        }
        userMapper.update(user);
        // roadmap §1.3：锁定/解锁即时生效——
        // 锁定 → 递增会话版本（存量 token 全部失效）+ 状态缓存置锁定；
        // 解锁 → 状态缓存置正常（存量 token 需重新登录后签发新版本）。
        Boolean targetIsLogin = user.getIsLogin();
        if (targetIsLogin != null) {
            authSessionManager.setAccountState(user.getId(), Boolean.TRUE.equals(targetIsLogin));
            if (Boolean.TRUE.equals(targetIsLogin)) {
                authSessionManager.nextVersion(user.getId());
            }
        }
        return ApiResult.success();
    }

    @Override
    public Result<List<ChartVO>> daysQuery(Integer day) {
        QueryDto queryDto = DateUtil.startAndEndTime(day);
        UserQueryDto userQueryDto = new UserQueryDto();
        userQueryDto.setStartTime(queryDto.getStartTime());
        userQueryDto.setEndTime(queryDto.getEndTime());
        List<User> userList = userMapper.query(userQueryDto);
        List<LocalDateTime> localDateTimes = userList.stream().map(User::getCreateTime).collect(Collectors.toList());
        List<ChartVO> chartVOS = DateUtil.countDatesWithinRange(day, localDateTimes);
        return ApiResult.success(chartVOS);
    }

    /**
     * 读取用户设置。
     *
     * <p>2026-10-10 实现：此前这里返回的是一份硬编码默认值，用户改完再看还是原样。
     * 现直接从 user 表对应列读取；列为 NULL（早于本次迁移的历史行）时回退默认值，
     * 保证前端永远拿到完整的一组设置，不必自己做空判断。
     */
    @Override
    public Map<String, Object> getUserSettings(Integer userId) {
        User user = userId == null ? null : userMapper.getUserById(userId);
        return buildSettings(user);
    }

    private Map<String, Object> buildSettings(User user) {
        Map<String, Object> settings = new HashMap<>(8);
        settings.put("language", textOrDefault(user == null ? null : user.getLanguage(), DEFAULT_LANGUAGE));
        settings.put("timezone", textOrDefault(user == null ? null : user.getTimezone(), DEFAULT_TIMEZONE));

        Map<String, Object> notification = new HashMap<>(8);
        notification.put("systemNotify", boolOrDefault(user == null ? null : user.getNotifySystem(), true));
        notification.put("healthRemind", boolOrDefault(user == null ? null : user.getNotifyHealthRemind(), true));
        notification.put("aiChatNotify", boolOrDefault(user == null ? null : user.getNotifyAiChat(), true));
        notification.put("appointmentRemind", boolOrDefault(user == null ? null : user.getNotifyAppointment(), true));
        notification.put("orderNotify", boolOrDefault(user == null ? null : user.getNotifyOrder(), true));
        settings.put("notification", notification);

        Map<String, Object> privacy = new HashMap<>(4);
        privacy.put("healthDataShare",
                boolOrDefault(user == null ? null : user.getPrivacyHealthDataShare(), true));
        privacy.put("profileVisibility",
                textOrDefault(user == null ? null : user.getPrivacyProfileVisibility(),
                        DEFAULT_PROFILE_VISIBILITY));
        settings.put("privacy", privacy);

        Map<String, Object> ai = new HashMap<>(8);
        ai.put("defaultAgent", textOrDefault(user == null ? null : user.getAiDefaultAgent(), DEFAULT_AI_AGENT));
        ai.put("chatStyle", textOrDefault(user == null ? null : user.getAiChatStyle(), DEFAULT_AI_CHAT_STYLE));
        ai.put("enableWebSearch", boolOrDefault(user == null ? null : user.getAiEnableWebSearch(), true));
        ai.put("enableKnowledgeBase",
                boolOrDefault(user == null ? null : user.getAiEnableKnowledgeBase(), true));
        Integer rounds = user == null ? null : user.getAiContextRounds();
        ai.put("contextRounds", rounds == null || rounds < MIN_CONTEXT_ROUNDS || rounds > MAX_CONTEXT_ROUNDS
                ? DEFAULT_CONTEXT_ROUNDS : rounds);
        settings.put("ai", ai);

        return settings;
    }

    /**
     * 保存用户设置（局部更新）。
     *
     * <p>此前该方法直接 {@code return error("用户设置持久化暂未实现")} —— 那是上一轮
     * 审计留下的「宁可报错也不假报成功」的临时处置。现在 user 表已有对应列，改为真正落库。
     *
     * <p>只处理**提交了**的项：前端每次只提交一组（通知 / 隐私 / AI），
     * 未提交的组保持库中原值。取值一律校验白名单，越界或未知键直接报错，
     * 不做静默忽略 —— 这个页面此前的毛病就是「看着保存成功，其实没生效」。
     */
    @Override
    public Result<String> updateUserSettings(Integer userId, Map<String, Object> settings) {
        if (userId == null) {
            return ApiResult.error("未登录");
        }
        if (settings == null || settings.isEmpty()) {
            return ApiResult.error("没有需要保存的设置项");
        }
        for (String key : settings.keySet()) {
            if (!TOP_LEVEL_KEYS.contains(key)) {
                return ApiResult.error("不支持的设置项：" + key);
            }
        }

        User patch = new User();
        patch.setId(userId);
        boolean touched = false;

        // ---------------- 基本设置 ----------------
        if (settings.containsKey("language")) {
            String v = asText(settings.get("language"));
            if (v == null || !ALLOWED_LANGUAGES.contains(v)) {
                return ApiResult.error("不支持的语言：" + v);
            }
            patch.setLanguage(v);
            touched = true;
        }
        if (settings.containsKey("timezone")) {
            String v = asText(settings.get("timezone"));
            if (v == null || !ALLOWED_TIMEZONES.contains(v)) {
                return ApiResult.error("不支持的时区：" + v);
            }
            patch.setTimezone(v);
            touched = true;
        }

        // ---------------- 通知设置 ----------------
        if (settings.containsKey("notification")) {
            Map<String, Object> n = asMap(settings.get("notification"));
            if (n == null) {
                return ApiResult.error("通知设置格式不正确");
            }
            for (Map.Entry<String, Object> e : n.entrySet()) {
                Boolean v = asBoolean(e.getValue());
                if (v == null) {
                    return ApiResult.error("通知设置项 " + e.getKey() + " 只能为布尔值");
                }
                switch (e.getKey()) {
                    case "systemNotify" -> patch.setNotifySystem(v);
                    case "healthRemind" -> patch.setNotifyHealthRemind(v);
                    case "aiChatNotify" -> patch.setNotifyAiChat(v);
                    case "appointmentRemind" -> patch.setNotifyAppointment(v);
                    case "orderNotify" -> patch.setNotifyOrder(v);
                    default -> {
                        return ApiResult.error("不支持的通知设置项：" + e.getKey());
                    }
                }
                touched = true;
            }
        }

        // ---------------- 隐私设置 ----------------
        if (settings.containsKey("privacy")) {
            Map<String, Object> p = asMap(settings.get("privacy"));
            if (p == null) {
                return ApiResult.error("隐私设置格式不正确");
            }
            for (Map.Entry<String, Object> e : p.entrySet()) {
                switch (e.getKey()) {
                    case "healthDataShare" -> {
                        Boolean v = asBoolean(e.getValue());
                        if (v == null) {
                            return ApiResult.error("healthDataShare 只能为布尔值");
                        }
                        patch.setPrivacyHealthDataShare(v);
                    }
                    case "profileVisibility" -> {
                        String v = asText(e.getValue());
                        if (v == null || !ALLOWED_PROFILE_VISIBILITY.contains(v)) {
                            return ApiResult.error("不支持的资料可见性：" + v);
                        }
                        patch.setPrivacyProfileVisibility(v);
                    }
                    default -> {
                        return ApiResult.error("不支持的隐私设置项：" + e.getKey());
                    }
                }
                touched = true;
            }
        }

        // ---------------- AI 助手设置 ----------------
        if (settings.containsKey("ai")) {
            Map<String, Object> a = asMap(settings.get("ai"));
            if (a == null) {
                return ApiResult.error("AI 设置格式不正确");
            }
            for (Map.Entry<String, Object> e : a.entrySet()) {
                switch (e.getKey()) {
                    case "defaultAgent" -> {
                        String v = asText(e.getValue());
                        if (v == null || !ALLOWED_AI_AGENTS.contains(v)) {
                            return ApiResult.error("不支持的 AI 角色：" + v);
                        }
                        patch.setAiDefaultAgent(v);
                    }
                    case "chatStyle" -> {
                        String v = asText(e.getValue());
                        if (v == null || !ALLOWED_AI_CHAT_STYLES.contains(v)) {
                            return ApiResult.error("不支持的对话风格：" + v);
                        }
                        patch.setAiChatStyle(v);
                    }
                    case "enableWebSearch" -> {
                        Boolean v = asBoolean(e.getValue());
                        if (v == null) {
                            return ApiResult.error("enableWebSearch 只能为布尔值");
                        }
                        patch.setAiEnableWebSearch(v);
                    }
                    case "enableKnowledgeBase" -> {
                        Boolean v = asBoolean(e.getValue());
                        if (v == null) {
                            return ApiResult.error("enableKnowledgeBase 只能为布尔值");
                        }
                        patch.setAiEnableKnowledgeBase(v);
                    }
                    case "contextRounds" -> {
                        Integer v = asInt(e.getValue());
                        if (v == null || v < MIN_CONTEXT_ROUNDS || v > MAX_CONTEXT_ROUNDS) {
                            return ApiResult.error("上下文轮数需在 " + MIN_CONTEXT_ROUNDS
                                    + "~" + MAX_CONTEXT_ROUNDS + " 之间");
                        }
                        patch.setAiContextRounds(v);
                    }
                    default -> {
                        return ApiResult.error("不支持的 AI 设置项：" + e.getKey());
                    }
                }
                touched = true;
            }
        }

        if (!touched) {
            return ApiResult.error("没有需要保存的设置项");
        }
        userMapper.updateSettings(patch);
        return ApiResult.success("设置已保存");
    }

    // ---------------- 设置的取值兜底与校验 ----------------

    /** 空串按未设置处理：库里可能有 ''（建表 DEFAULT 只挡 NULL，不挡空串） */
    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Boolean boolOrDefault(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    /** 只接受字符串，数字/布尔等其他类型视为非法，避免被塞进枚举列 */
    private static String asText(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    /** 布尔项既收 JSON 布尔，也收 0/1（前端 switch 与表单提交两种来源） */
    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            int i = n.intValue();
            if (i == 0) {
                return Boolean.FALSE;
            }
            if (i == 1) {
                return Boolean.TRUE;
            }
        }
        return null;
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number n) {
            // 3.5 这类非整数轮数不接受，避免落库后前端滑块显示成小数
            double d = n.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return (int) d;
            }
        }
        return null;
    }
}
