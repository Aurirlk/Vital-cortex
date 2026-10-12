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

    /**
     * 取某张试卷的题目（答题用，已剔除答案与解析）。
     *
     * <p>2026-10-10 新增：此前用户端拿不到「本卷的题」——{@code QuizQuestionQueryDto}
     * 里根本没有 examId 字段，前端传 {@code {examId: n}} 会被静默忽略，
     * 于是所有试卷都会加载出题库全量题目（换个参数、题量、分数全不对）。
     * Mapper 里 {@code queryByExamId} 一直都存在，只是没有任何 Controller 暴露它。
     */
    Result<List<QuizQuestionVO>> getExamQuestions(Integer examId);

    // 答题
    Result<QuizRecord> startExam(Integer examId, Integer userId);
    Result<Void> submitExam(Integer recordId, List<QuizAnswer> answers);

    // 记录
    Result<List<QuizRecord>> getUserRecords(Integer userId);
    Result<List<QuizAnswer>> getRecordAnswers(Integer recordId);
}
