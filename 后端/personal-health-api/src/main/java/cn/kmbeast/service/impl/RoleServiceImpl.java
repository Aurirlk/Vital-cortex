package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.RoleMapper;
import cn.kmbeast.mapper.RolePermissionMapper;
import cn.kmbeast.mapper.UserRoleMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.Role;
import cn.kmbeast.pojo.entity.RolePermission;
import cn.kmbeast.pojo.entity.UserRole;
import cn.kmbeast.service.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class RoleServiceImpl implements RoleService {

    @Resource
    private RoleMapper roleMapper;
    @Resource
    private RolePermissionMapper rolePermissionMapper;
    @Resource
    private UserRoleMapper userRoleMapper;

    @Override
    public Result<Void> save(Role role) {
        role.setCreateTime(LocalDateTime.now());
        roleMapper.save(role);
        return ApiResult.success();
    }

    @Override
    public Result<Void> update(Role role) {
        roleMapper.update(role);
        return ApiResult.success();
    }

    @Override
    public Result<Void> delete(List<Long> ids) {
        for (Long id : ids) {
            rolePermissionMapper.deleteByRoleId(id.intValue());
        }
        roleMapper.batchDelete(ids);
        return ApiResult.success();
    }

    @Override
    public Result<List<Role>> getAll() {
        return ApiResult.success(roleMapper.queryAll());
    }

    @Override
    public Result<Role> getById(Integer id) {
        return ApiResult.success(roleMapper.getById(id));
    }

    @Override
    public Result<List<Role>> getByUserId(Integer userId) {
        return ApiResult.success(roleMapper.getByUserId(userId));
    }

    @Override
    @Transactional
    public Result<Void> assignPermissions(Integer roleId, List<Integer> permissionIds) {
        rolePermissionMapper.deleteByRoleId(roleId);
        for (Integer permId : permissionIds) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(permId);
            rp.setCreateTime(LocalDateTime.now());
            rolePermissionMapper.save(rp);
        }
        return ApiResult.success();
    }

    @Override
    @Transactional
    public Result<Void> assignRoles(Integer userId, List<Integer> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        for (Integer roleId : roleIds) {
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            ur.setCreateTime(LocalDateTime.now());
            userRoleMapper.save(ur);
        }
        return ApiResult.success();
    }
}
