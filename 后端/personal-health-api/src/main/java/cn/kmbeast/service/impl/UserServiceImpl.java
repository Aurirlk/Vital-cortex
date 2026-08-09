package cn.kmbeast.service.impl;

import cn.kmbeast.core.auth.AuthSessionManager;
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
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private AuthSessionManager authSessionManager;

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
        User user = userMapper.getByActive(
                User.builder().userAccount(userLoginDTO.getUserAccount()).build()
        );
        if (!Objects.nonNull(user)) {
            return ApiResult.error("账号不存在");
        }
        // 使用BCrypt验证密码
        if (!passwordEncoder.matches(userLoginDTO.getUserPwd(), user.getUserPwd())) {
            return ApiResult.error("密码错误");
        }
        if (Boolean.TRUE.equals(user.getIsLogin())) {
            return ApiResult.error("登录状态异常");
        }
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
}
