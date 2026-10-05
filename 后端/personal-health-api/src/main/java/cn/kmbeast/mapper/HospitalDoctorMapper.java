package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.HospitalDoctor;
import cn.kmbeast.pojo.vo.DoctorVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 医院医生 Mapper
 *
 * <p>2026-10-03 医生与 user 解耦后新增了账号相关查询方法（getByUsername / updatePassword 等）。
 * 详见 {@code docs/数据表结构基线-20261003.md}。
 */
@Mapper
public interface HospitalDoctorMapper {

    void save(HospitalDoctor doctor);

    void update(HospitalDoctor doctor);

    void batchDelete(@Param("ids") List<Long> ids);

    List<DoctorVO> queryByDepartment(@Param("departmentId") Integer departmentId);

    DoctorVO getById(@Param("id") Integer id);

    List<DoctorVO> queryAll();

    // ==================== 医生独立账号体系（解耦后新增） ====================

    /**
     * 按登录账号查询医生（含密码字段，仅供认证流程使用）
     *
     * <p>⚠️ 返回对象含 password 明文哈希，<b>严禁</b>直接序列化给前端。
     *
     * @param username 医生登录账号
     * @return 医生实体；账号不存在时返回 null
     */
    HospitalDoctor getByUsername(@Param("username") String username);

    /**
     * 更新医生密码
     *
     * @param doctorId     医生ID
     * @param password     BCrypt 哈希
     * @param salt         盐值（当前 BCrypt 不依赖，保留字段）
     * @param needInitPassword 是否仍需初始化（0 表示已设密，下次登录直接校验密码）
     */
    void updatePassword(@Param("doctorId") Integer doctorId,
                        @Param("password") String password,
                        @Param("salt") String salt,
                        @Param("needInitPassword") Integer needInitPassword);

    /** 账号唯一性校验：返回已存在的记录数（>0 表示被占用） */
    int countByUsername(@Param("username") String username, @Param("excludeId") Integer excludeId);

    // ==================== 医生/科室列表与远程搜索（扩展性改造） ====================

    /**
     * 医生列表：支持姓名模糊 + 科室筛选 + 状态筛选 + 分页
     *
     * @param name      姓名关键字（可为 null）
     * @param deptId    科室ID（可为 null）
     * @param titleLevel 职称（可为 null）
     * @param status    状态（可为 null）
     * @param offset    偏移量
     * @param size      每页条数
     */
    List<DoctorVO> queryDoctorPage(@Param("name") String name,
                                   @Param("deptId") Integer deptId,
                                   @Param("titleLevel") String titleLevel,
                                   @Param("status") Integer status,
                                   @Param("offset") Integer offset,
                                   @Param("size") Integer size);

    /** 医生列表总数（分页用） */
    int countDoctorPage(@Param("name") String name,
                        @Param("deptId") Integer deptId,
                        @Param("titleLevel") String titleLevel,
                        @Param("status") Integer status);

    /**
     * 医生选择器专用远程搜索（只返回启用中的医生，按姓名/科室过滤）
     *
     * <p>供前端 &lt;DoctorSelect&gt; 组件 remote-method 调用。
     */
    List<DoctorVO> searchForSelect(@Param("keyword") String keyword,
                                   @Param("deptId") Integer deptId,
                                   @Param("limit") Integer limit);
}
