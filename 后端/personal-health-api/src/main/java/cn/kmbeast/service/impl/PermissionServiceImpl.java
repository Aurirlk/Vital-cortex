package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.PermissionMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.Permission;
import cn.kmbeast.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class PermissionServiceImpl implements PermissionService {

    @Resource
    private PermissionMapper permissionMapper;

    @Override
    public Result<Void> save(Permission permission) {
        permission.setCreateTime(LocalDateTime.now());
        permissionMapper.save(permission);
        return ApiResult.success();
    }

    @Override
    public Result<Void> update(Permission permission) {
        permissionMapper.update(permission);
        return ApiResult.success();
    }

    @Override
    public Result<Void> delete(List<Long> ids) {
        permissionMapper.batchDelete(ids);
        return ApiResult.success();
    }

    @Override
    public Result<List<Permission>> getAll() {
        return ApiResult.success(permissionMapper.queryAll());
    }

    @Override
    public Result<List<Permission>> getByRoleId(Integer roleId) {
        return ApiResult.success(permissionMapper.getByRoleId(roleId));
    }

    @Override
    public Result<List<Permission>> getByUserId(Integer userId) {
        return ApiResult.success(permissionMapper.getByUserId(userId));
    }
}
