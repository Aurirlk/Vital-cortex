package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.QuizAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface QuizAnswerMapper {
    void save(QuizAnswer answer);
    void batchSave(@Param("answers") List<QuizAnswer> answers);
    List<QuizAnswer> queryByRecordId(@Param("recordId") Integer recordId);
}
