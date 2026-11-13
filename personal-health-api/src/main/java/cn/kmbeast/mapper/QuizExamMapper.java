package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.QuizExam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface QuizExamMapper {
    void save(QuizExam exam);
    void update(QuizExam exam);
    void batchDelete(@Param("ids") List<Long> ids);
    List<QuizExam> queryAll();
    QuizExam getById(@Param("id") Integer id);
}
