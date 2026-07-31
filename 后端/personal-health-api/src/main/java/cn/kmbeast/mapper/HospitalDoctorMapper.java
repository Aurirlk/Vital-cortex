package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.HospitalDoctor;
import cn.kmbeast.pojo.vo.DoctorVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface HospitalDoctorMapper {
    void save(HospitalDoctor doctor);
    void update(HospitalDoctor doctor);
    void batchDelete(@Param("ids") List<Long> ids);
    List<DoctorVO> queryByDepartment(@Param("departmentId") Integer departmentId);
    DoctorVO getById(@Param("id") Integer id);
    List<DoctorVO> queryAll();
}
