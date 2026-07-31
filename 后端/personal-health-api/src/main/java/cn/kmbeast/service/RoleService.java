package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.Role;
import cn.kmbeast.pojo.entity.Permission;
import java.util.List;

public interface RoleService {
    Result<Void> save(Role role);
    Result<Void> update(Role role);
    Result<Void> delete(List<Long> ids);
    Result<List<Role>> getAll();
    Result<Role> getById(Integer id);
    Result<List<Role>> getByUserId(Integer userId);
    Result<Void> assignPermissions(Integer roleId, List<Integer> permissionIds);
    Result<Void> assignRoles(Integer userId, List<Integer> roleIds);
}
