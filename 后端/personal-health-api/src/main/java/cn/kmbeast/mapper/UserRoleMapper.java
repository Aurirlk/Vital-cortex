package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserRoleMapper {
    void save(UserRole userRole);
    void deleteByUserId(@Param("userId") Integer userId);
    void deleteByUserAndRole(@Param("userId") Integer userId, @Param("roleId") Integer roleId);
}
