package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.QuizQuestionQueryDto;
import cn.kmbeast.pojo.entity.*;
import cn.kmbeast.pojo.vo.QuizQuestionVO;

import java.util.List;

public interface QuizService {
    // 题库
    Result<Void> saveQuestion(QuizQuestion question);
    Result<Void> updateQuestion(QuizQuestion question);
    Result<Void> deleteQuestions(List<Long> ids);
    Result<List<QuizQuestionVO>> queryQuestions(QuizQuestionQueryDto queryDto);
    Result<QuizQuestionVO> getQuestionById(Integer id);

    // 试卷
    Result<Void> saveExam(QuizExam exam);
    Result<Void> updateExam(QuizExam exam);
    Result<Void> deleteExams(List<Long> ids);
    Result<List<QuizExam>> getExams();
    Result<QuizExam> getExamById(Integer id);

    // 组卷
    Result<Void> addQuestionsToExam(Integer examId, List<QuizExamQuestion> questions);

    // 答题
    Result<QuizRecord> startExam(Integer examId, Integer userId);
    Result<Void> submitExam(Integer recordId, List<QuizAnswer> answers);

    // 记录
    Result<List<QuizRecord>> getUserRecords(Integer userId);
    Result<List<QuizAnswer>> getRecordAnswers(Integer recordId);
}
