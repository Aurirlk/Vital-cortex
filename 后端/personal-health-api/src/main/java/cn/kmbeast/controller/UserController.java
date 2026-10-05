package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.config.SentinelBlockHandlers;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.mapper.UserStatsMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.UserQueryDto;
import cn.kmbeast.pojo.dto.update.UserLoginDTO;
import cn.kmbeast.pojo.dto.update.UserRegisterDTO;
import cn.kmbeast.pojo.dto.update.UserUpdateDTO;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.pojo.vo.ChartVO;
import cn.kmbeast.pojo.vo.UserVO;
import cn.kmbeast.service.UserService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private UserStatsMapper userStatsMapper;

    @Resource
    private UserMapper userMapper;

    /**
     * 用户登录
     *
     * @param userLoginDTO 登录入参
     * @return Result<String> 响应结果
     */
    @SentinelResource(value = "user:login",
            blockHandler = "loginBlocked", blockHandlerClass = SentinelBlockHandlers.class)
    @PostMapping(value = "/login")
    @ResponseBody
    public Result<Object> login(@RequestBody UserLoginDTO userLoginDTO) {
        return userService.login(userLoginDTO);
    }


    /**
     * token校验
     */
    @Protector
    @GetMapping(value = "/auth")
    @ResponseBody
    public Result<UserVO> auth() {
        return userService.auth();
    }

    /**
     * 获取当前登录用户完整信息（个人中心用，与 /auth 等价）
     */
    @Protector
    @GetMapping(value = "/info")
    @ResponseBody
    public Result<UserVO> info() {
        return userService.auth();
    }

    /**
     * 个人中心统计：收藏数 / 健康记录数 / AI对话数 / 用药订阅数 / 预约数 / 订单数
     */
    @Protector
    @GetMapping(value = "/stats")
    @ResponseBody
    public Result<Map<String, Object>> stats() {
        Integer userId = LocalThreadHolder.getUserId();
        Map<String, Object> data = new HashMap<>();
        data.put("favoriteCount", userStatsMapper.countFavorites(userId));
        data.put("healthRecordCount", userStatsMapper.countHealthRecords(userId));
        data.put("aiChatCount", userStatsMapper.countAiChats(userId));
        data.put("drugSubscribeCount", userStatsMapper.countDrugSubscriptions(userId));
        data.put("appointmentCount", userStatsMapper.countAppointments(userId));
        data.put("orderCount", userStatsMapper.countOrders(userId));
        return ApiResult.success(data);
    }


    /**
     * 通过ID查询用户信息
     *
     * @param id 用户ID
     * @return Result<UserVO>
     */
    @Protector
    @GetMapping(value = "/getById/{id}")
    @ResponseBody
    public Result<UserVO> getById(@PathVariable Integer id) {
        return userService.getById(id);
    }

    /**
     * 患者/用户远程搜索（2026-10-03 新增，供 {@code <PatientSelect>} 与医生端使用）
     *
     * <p>支持按姓名 / 账号 / 手机号（含后4位）模糊匹配，仅返回普通用户（role=2）。
     * 手机号来自本次库表整理新增的 {@code user.phone} 字段。
     *
     * @param keyword 搜索关键字（可空，空则按默认顺序返回前 limit 条）
     * @param limit   返回条数上限
     */
    @Protector
    @GetMapping(value = "/search")
    @ResponseBody
    public Result<List<User>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") Integer limit) {
        int max = (limit == null || limit <= 0) ? 20 : Math.min(limit, 50);
        return ApiResult.success(userMapper.searchForSelect(keyword, max));
    }


    /**
     * 用户注册
     *
     * @param userRegisterDTO 注册入参
     * @return Result<String> 响应结果
     */
    @PostMapping(value = "/register")
    @ResponseBody
    public Result<String> register(@RequestBody UserRegisterDTO userRegisterDTO) {
        return userService.register(userRegisterDTO);
    }

    /**
     * 后台新增用户
     *
     * @param userRegisterDTO 注册入参
     * @return Result<String> 响应结果
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/insert")
    @ResponseBody
    public Result<String> insert(@RequestBody UserRegisterDTO userRegisterDTO) {
        return userService.insert(userRegisterDTO);
    }

    /**
     * 用户信息修改
     *
     * @param userUpdateDTO 修改信息入参
     * @return Result<String> 响应结果
     */
    @Protector
    @PutMapping(value = "/update")
    @ResponseBody
    public Result<String> update(@RequestBody UserUpdateDTO userUpdateDTO) {
        return userService.update(userUpdateDTO);
    }

    /**
     * 后台用户信息修改
     *
     * @param user 信息实体
     * @return Result<String> 响应结果
     */
    @Protector(role = "管理员")
    @PutMapping(value = "/backUpdate")
    @ResponseBody
    public Result<String> backUpdate(@RequestBody User user) {
        return userService.backUpdate(user);
    }

    /**
     * 用户修改密码
     *
     * @param map 修改信息入参
     * @return Result<String> 响应结果
     */
    @Protector
    @PutMapping(value = "/updatePwd")
    @ResponseBody
    public Result<String> updatePwd(@RequestBody Map<String, String> map) {
        return userService.updatePwd(map);
    }

    /**
     * 登出：递增会话版本，使该用户全部存量 token 失效（roadmap §1.3）
     */
    @Protector
    @PostMapping(value = "/logout")
    @ResponseBody
    public Result<String> logout() {
        return userService.logout();
    }

    /**
     * 批量删除用户信息
     */
    @Protector(role = "管理员")
    @PostMapping(value = "/batchDelete")
    @ResponseBody
    public Result<String> batchDelete(@RequestBody List<Integer> ids) {
        return userService.batchDelete(ids);
    }

    /**
     * 查询用户数据
     *
     * @param userQueryDto 查询参数
     * @return Result<List < User>> 响应结果
     */
    @Pager
    @Protector(role = "管理员")
    @PostMapping(value = "/query")
    @ResponseBody
    public Result<List<User>> query(@RequestBody UserQueryDto userQueryDto) {
        return userService.query(userQueryDto);
    }

    /**
     * 统计用户存量数据
     *
     * @return Result<List < ChartVO>> 响应结果
     */
    @Protector(role = "管理员")
    @GetMapping(value = "/daysQuery/{day}")
    @ResponseBody
    public Result<List<ChartVO>> query(@PathVariable Integer day) {
        return userService.daysQuery(day);
    }

    /**
     * 获取用户设置
     *
     * @return Result<Map<String, Object>> 用户设置
     */
    @Protector
    @GetMapping(value = "/settings")
    @ResponseBody
    public Result<Map<String, Object>> getSettings() {
        Integer userId = LocalThreadHolder.getUserId();
        Map<String, Object> settings = userService.getUserSettings(userId);
        return ApiResult.success(settings);
    }

    /**
     * 更新用户设置
     *
     * @param settings 设置信息
     * @return Result<String> 响应结果
     */
    @Protector
    @PutMapping(value = "/settings")
    @ResponseBody
    public Result<String> updateSettings(@RequestBody Map<String, Object> settings) {
        Integer userId = LocalThreadHolder.getUserId();
        return userService.updateUserSettings(userId, settings);
    }

}

