package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RolePermissionMapper {
    void save(RolePermission rolePermission);
    void deleteByRoleId(@Param("roleId") Integer roleId);
    void batchSave(@Param("list") List<RolePermission> list);
}
