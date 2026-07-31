package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.Permission;
import cn.kmbeast.service.PermissionService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/permission")
public class PermissionController {

    @Resource
    private PermissionService permissionService;

    @Protector(role = "管理员")
    @PostMapping("/save")
    public Result<Void> save(@RequestBody Permission permission) {
        return permissionService.save(permission);
    }

    @Protector(role = "管理员")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Permission permission) {
        return permissionService.update(permission);
    }

    @Protector(role = "管理员")
    @PostMapping("/batchDelete")
    public Result<Void> delete(@RequestBody List<Long> ids) {
        return permissionService.delete(ids);
    }

    @Protector
    @GetMapping("/list")
    public Result<List<Permission>> getAll() {
        return permissionService.getAll();
    }

    @Protector
    @GetMapping("/role/{roleId}")
    public Result<List<Permission>> getByRoleId(@PathVariable Integer roleId) {
        return permissionService.getByRoleId(roleId);
    }

    @Protector
    @GetMapping("/user/{userId}")
    public Result<List<Permission>> getByUserId(@PathVariable Integer userId) {
        return permissionService.getByUserId(userId);
    }
}
