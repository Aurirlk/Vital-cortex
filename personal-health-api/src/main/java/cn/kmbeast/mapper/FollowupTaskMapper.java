package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.FollowupTask;
import cn.kmbeast.pojo.vo.FollowupTaskVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface FollowupTaskMapper {
    void save(FollowupTask task);
    void update(FollowupTask task);
    void batchDelete(@Param("ids") List<Long> ids);
    List<FollowupTaskVO> queryByPatientId(@Param("patientId") Integer patientId);
    List<FollowupTaskVO> queryByDoctorId(@Param("doctorId") Integer doctorId);
    FollowupTaskVO getById(@Param("id") Integer id);
}
