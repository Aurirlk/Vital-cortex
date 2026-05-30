package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.base.QueryDto;
import cn.kmbeast.pojo.dto.query.extend.UserHealthQueryDto;
import cn.kmbeast.pojo.entity.UserHealth;
import cn.kmbeast.pojo.vo.ChartVO;
import cn.kmbeast.pojo.vo.UserHealthVO;
import cn.kmbeast.service.UserHealthService;
import cn.kmbeast.utils.DateUtil;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 用户健康记录的 Controller
 */
@RestController
@RequestMapping(value = "/user-health")
public class UserHealthController {

    @Resource
    private UserHealthService userHealthService;

    /**
     * 用户健康记录新增
     */
    @Protector
    @PostMapping(value = "/save")
    public Result<Void> save(@RequestBody List<UserHealth> userHealths) {
        return userHealthService.save(userHealths);
    }

    /**
     * 用户健康记录删除
     */
    @Protector
    @PostMapping(value = "/batchDelete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        return userHealthService.batchDelete(ids);
    }

    /**
     * 用户健康记录修改
     */
    @Protector
    @PutMapping(value = "/update")
    public Result<Void> update(@RequestBody UserHealth userHealth) {
        return userHealthService.update(userHealth);
    }

    /**
     * 用户健康记录查询（管理员）
     */
    @Pager
    @Protector(role = "管理员")
    @PostMapping(value = "/query")
    public Result<List<UserHealthVO>> query(@RequestBody UserHealthQueryDto userHealthQueryDto) {
        return userHealthService.query(userHealthQueryDto);
    }

    /**
     * 用户健康记录查询（当前用户）
     */
    @Pager
    @Protector
    @PostMapping(value = "/queryUser")
    public Result<List<UserHealthVO>> queryUser(@RequestBody UserHealthQueryDto userHealthQueryDto) {
        userHealthQueryDto.setUserId(LocalThreadHolder.getUserId());
        return userHealthService.query(userHealthQueryDto);
    }

    /**
     * 用户健康记录时间范围查询
     */
    @Protector
    @GetMapping(value = "/timeQuery/{id}/{day}")
    public Result<List<UserHealthVO>> timeQuery(@PathVariable Integer id,
                                                @PathVariable Integer day) {
        Integer userId = LocalThreadHolder.getUserId();
        QueryDto queryDto = DateUtil.startAndEndTime(day);
        UserHealthQueryDto userHealthQueryDto = new UserHealthQueryDto();
        userHealthQueryDto.setHealthModelConfigId(id);
        userHealthQueryDto.setUserId(userId);
        userHealthQueryDto.setStartTime(queryDto.getStartTime());
        userHealthQueryDto.setEndTime(queryDto.getEndTime());
        return userHealthService.query(userHealthQueryDto);
    }

    /**
     * 统计模型存量数据
     */
    @Protector(role = "管理员")
    @GetMapping(value = "/daysQuery/{day}")
    @ResponseBody
    public Result<List<ChartVO>> query(@PathVariable Integer day) {
        return userHealthService.daysQuery(day);
    }
}
