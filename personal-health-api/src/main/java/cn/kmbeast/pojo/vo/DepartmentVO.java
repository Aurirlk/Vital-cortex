package cn.kmbeast.pojo.vo;

import cn.kmbeast.pojo.entity.Department;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 科室视图对象（2026-10-03 科室支持多级层级）
 *
 * <p>在 {@link Department} 基础上补充树形导航所需的字段：
 * 医生数量与子节点列表，供管理端「科室树 + 医生列表」左侧树使用。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DepartmentVO extends Department {

    /** 该科室下的启用医生数（含子科室的医生数，便于父节点展示总量） */
    private Integer doctorCount;

    /** 直接子科室 */
    private List<DepartmentVO> children;
}
