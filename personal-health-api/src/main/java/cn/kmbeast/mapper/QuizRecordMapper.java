package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.QuizRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface QuizRecordMapper {
    void save(QuizRecord record);
    void update(QuizRecord record);
    List<QuizRecord> queryByUserId(@Param("userId") Integer userId);
    QuizRecord getById(@Param("id") Integer id);
}
