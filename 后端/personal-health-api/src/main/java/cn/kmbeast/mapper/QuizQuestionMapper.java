package cn.kmbeast.mapper;

import cn.kmbeast.pojo.dto.query.extend.QuizQuestionQueryDto;
import cn.kmbeast.pojo.entity.QuizQuestion;
import cn.kmbeast.pojo.vo.QuizQuestionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface QuizQuestionMapper {
    void save(QuizQuestion question);
    void update(QuizQuestion question);
    void batchDelete(@Param("ids") List<Long> ids);
    List<QuizQuestionVO> query(QuizQuestionQueryDto queryDto);
    Integer queryCount(QuizQuestionQueryDto queryDto);
    QuizQuestionVO getById(@Param("id") Integer id);
    List<QuizQuestionVO> queryByExamId(@Param("examId") Integer examId);
}
