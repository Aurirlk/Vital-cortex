package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 科室 Mapper
 *
 * <p>2026-10-03 科室支持多级层级（parent_id / level / code / leader_id），
 * 新增按科室统计医生数的方法，供「科室树 + 医生列表」导航使用。
 */
@Mapper
public interface DepartmentMapper {

    void save(Department department);

    void update(Department department);

    void batchDelete(@Param("ids") List<Long> ids);

    /** 查询全部启用科室（按 sort_order 排序） */
    List<Department> queryAll();

    Department getById(@Param("id") Integer id);

    /**
     * 按科室统计启用医生数（用于科室树节点显示「内科 (12)」）
     *
     * <p>同时统计多科室医生：若医生 {@code dept_ids} 的 JSON 数组中包含该科室，也计入。
     *
     * <p>⚠️ 这里返回 {@code List<Map>} 而非 {@code Map<Integer,Integer>}：
     * {@code @MapKey} 注解的 value 会是<b>整行</b>而非聚合数字，
     * 因此由 Service 层自行转换为 {@code Map<Integer,Integer>}。
     *
     * @return 每行 {deptId: 科室ID, cnt: 医生数}
     */
    List<Map<String, Object>> countDoctorByDepartment();
}
