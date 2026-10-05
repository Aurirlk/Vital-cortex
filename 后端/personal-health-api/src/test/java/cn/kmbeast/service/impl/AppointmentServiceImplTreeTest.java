package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.DepartmentMapper;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.Department;
import cn.kmbeast.pojo.vo.DepartmentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 科室树构建单元测试（2026-10-04 构建）
 *
 * <p>2026-10-03 科室表支持多级层级（parent_id）后新增
 * {@code AppointmentServiceImpl.getDepartmentTree()}，本测试覆盖其全部边界：
 * <ul>
 *   <li>平铺数据（parent_id 全为 0）→ 全部作为根节点</li>
 *   <li>多级嵌套 → 正确挂载，父节点 doctorCount 累加子节点</li>
 *   <li>脏数据自环（parent_id 指向自身）→ 不死循环，当作根节点</li>
 *   <li>孤儿节点（parent_id 指向不存在的科室）→ 当作根节点</li>
 *   <li>医生数统计同一科室出现多行（UNION 两路命中）→ 取最大值而非累加</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("科室树构建")
class AppointmentServiceImplTreeTest {

    @Mock
    private DepartmentMapper departmentMapper;

    @InjectMocks
    private AppointmentServiceImpl service;

    private Department dept(Integer id, String name, Integer parentId) {
        Department d = new Department();
        d.setId(id);
        d.setName(name);
        d.setParentId(parentId);
        d.setLevel(parentId != null && parentId != 0 ? 2 : 1);
        d.setCode("DEPT_" + String.format("%03d", id));
        d.setSortOrder(id);
        d.setStatus(1);
        d.setCreateTime(LocalDateTime.now());
        return d;
    }

    private Map<String, Object> row(Integer deptId, Integer cnt) {
        Map<String, Object> m = new HashMap<>();
        m.put("deptId", deptId);
        m.put("cnt", cnt);
        return m;
    }

    @BeforeEach
    void setUp() {
        // 默认：统计结果为空，测试可按需覆盖
        when(departmentMapper.countDoctorByDepartment()).thenReturn(new ArrayList<>());
    }

    @Test
    @DisplayName("平铺数据：parent_id 全为 0 时全部作为根节点")
    void flatDepartmentsShouldAllBeRoots() {
        when(departmentMapper.queryAll()).thenReturn(
                Arrays.asList(dept(1, "内科", 0), dept(2, "外科", 0), dept(3, "儿科", 0)));

        Result<List<DepartmentVO>> r = service.getDepartmentTree();

        assertEquals(200, r.getCode());
        List<DepartmentVO> roots = r.getData();
        assertEquals(3, roots.size());
        roots.forEach(n -> assertNotNull(n.getChildren()));
        roots.forEach(n -> assertTrue(n.getChildren().isEmpty()));
    }

    @Test
    @DisplayName("多级嵌套：子科室挂到父节点下，父节点 doctorCount 累加子节点")
    void nestedDepartmentsShouldNestAndAccumulateCount() {
        when(departmentMapper.queryAll()).thenReturn(Arrays.asList(
                dept(1, "内科", 0),
                dept(2, "心血管内科", 1),
                dept(3, "呼吸内科", 1),
                dept(4, "外科", 0)));
        when(departmentMapper.countDoctorByDepartment()).thenReturn(Arrays.asList(
                row(1, 10),   // 内科自身 10 人
                row(2, 4),    // 心血管 4 人
                row(3, 3)));  // 呼吸 3 人

        Result<List<DepartmentVO>> r = service.getDepartmentTree();
        List<DepartmentVO> roots = r.getData();

        assertEquals(2, roots.size(), "应有两个根科室：内科、外科");

        DepartmentVO internal = roots.stream().filter(n -> n.getId() == 1).findFirst().orElseThrow();
        assertEquals(2, internal.getChildren().size(), "内科应有两个子科室");
        // 10(自身) + 4 + 3 = 17
        assertEquals(17, internal.getDoctorCount(), "父节点医生数应累加子科室");

        DepartmentVO surgery = roots.stream().filter(n -> n.getId() == 4).findFirst().orElseThrow();
        assertTrue(surgery.getChildren().isEmpty());
        assertEquals(0, surgery.getDoctorCount(), "无统计数据的科室医生数应为 0");
    }

    @Test
    @DisplayName("脏数据自环：parent_id 指向自身时不死循环，当作根节点")
    void selfReferencingParentShouldNotLoopForever() {
        when(departmentMapper.queryAll()).thenReturn(Arrays.asList(
                dept(1, "异常科室", 1),   // parent_id 指向自己
                dept(2, "正常科室", 0)));
        when(departmentMapper.countDoctorByDepartment()).thenReturn(Arrays.asList(row(1, 5)));

        Result<List<DepartmentVO>> r = service.getDepartmentTree();

        assertEquals(200, r.getCode());
        List<DepartmentVO> roots = r.getData();
        assertEquals(2, roots.size(), "自环节点应被当作根节点，否则会被挂到自己 children 里丢失");
    }

    @Test
    @DisplayName("孤儿节点：parent_id 指向不存在的科室时降级为根节点")
    void orphanNodeShouldBecomeRoot() {
        when(departmentMapper.queryAll()).thenReturn(Arrays.asList(
                dept(1, "内科", 0),
                dept(99, "孤儿科室", 12345)));  // 父节点不存在

        Result<List<DepartmentVO>> r = service.getDepartmentTree();

        List<DepartmentVO> roots = r.getData();
        assertEquals(2, roots.size(), "找不到父节点的记录必须保留为根，否则会从树上消失");
    }

    @Test
    @DisplayName("医生数多行命中取最大值：SQL 的 UNION 两路命中同一科室不应重复累加")
    void duplicateCountRowsShouldTakeMaxNotSum() {
        when(departmentMapper.queryAll()).thenReturn(
                Arrays.asList(dept(1, "内科", 0), dept(2, "外科", 0)));
        // 主科室统计到 3，多科室 JSON 统计到 3（同一批医生被 UNION 到两路）
        when(departmentMapper.countDoctorByDepartment()).thenReturn(Arrays.asList(
                row(1, 3), row(1, 3)));

        Result<List<DepartmentVO>> r = service.getDepartmentTree();

        DepartmentVO internal = r.getData().stream()
                .filter(n -> n.getId() == 1).findFirst().orElseThrow();
        assertEquals(3, internal.getDoctorCount(), "同一科室的重复统计行应取最大值(3)，而非相加(6)");
    }

    @Test
    @DisplayName("空表：返回空列表而非 null")
    void emptyTableShouldReturnEmptyList() {
        when(departmentMapper.queryAll()).thenReturn(new ArrayList<>());

        Result<List<DepartmentVO>> r = service.getDepartmentTree();

        assertEquals(200, r.getCode());
        assertNotNull(r.getData());
        assertTrue(r.getData().isEmpty());
    }

    @Test
    @DisplayName("统计结果含 null 值时不抛异常")
    void nullCountShouldBeTreatedAsZero() {
        when(departmentMapper.queryAll()).thenReturn(
                Arrays.asList(dept(1, "内科", 0), dept(2, "外科", 0)));
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(1, null));
        rows.add(row(null, 7));      // deptId 为 null 的脏行
        when(departmentMapper.countDoctorByDepartment()).thenReturn(rows);

        Result<List<DepartmentVO>> r = service.getDepartmentTree();

        assertEquals(2, r.getData().size());
        assertEquals(0, r.getData().get(0).getDoctorCount());
    }
}
