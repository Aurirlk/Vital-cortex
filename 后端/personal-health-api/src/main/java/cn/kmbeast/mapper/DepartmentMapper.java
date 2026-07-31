package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DepartmentMapper {
    void save(Department department);
    void update(Department department);
    void batchDelete(@Param("ids") List<Long> ids);
    List<Department> queryAll();
    Department getById(@Param("id") Integer id);
}
