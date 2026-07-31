package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RoleMapper {
    void save(Role role);
    void update(Role role);
    void batchDelete(@Param("ids") List<Long> ids);
    List<Role> queryAll();
    Role getById(@Param("id") Integer id);
    Role getByCode(@Param("code") String code);
    List<Role> getByUserId(@Param("userId") Integer userId);
}
