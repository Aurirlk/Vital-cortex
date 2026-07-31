package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.Permission;
import java.util.List;

public interface PermissionService {
    Result<Void> save(Permission permission);
    Result<Void> update(Permission permission);
    Result<Void> delete(List<Long> ids);
    Result<List<Permission>> getAll();
    Result<List<Permission>> getByRoleId(Integer roleId);
    Result<List<Permission>> getByUserId(Integer userId);
}
