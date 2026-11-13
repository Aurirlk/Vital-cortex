package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.QuizExamQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface QuizExamQuestionMapper {
    void save(QuizExamQuestion eq);
    void deleteByExamId(@Param("examId") Integer examId);
    List<QuizExamQuestion> queryByExamId(@Param("examId") Integer examId);
}
