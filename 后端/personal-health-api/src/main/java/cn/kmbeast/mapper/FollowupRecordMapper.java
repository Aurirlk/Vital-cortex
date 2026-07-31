package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.FollowupRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface FollowupRecordMapper {
    void save(FollowupRecord record);
    List<FollowupRecord> queryByTaskId(@Param("taskId") Integer taskId);
}
