package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PermissionMapper {
    void save(Permission permission);
    void update(Permission permission);
    void batchDelete(@Param("ids") List<Long> ids);
    List<Permission> queryAll();
    List<Permission> getByRoleId(@Param("roleId") Integer roleId);
    List<Permission> getByUserId(@Param("userId") Integer userId);
}
