package cn.kmbeast.service.impl;


import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.*;

import cn.kmbeast.pojo.api.ApiResult;

import cn.kmbeast.pojo.api.PageResult;

import cn.kmbeast.pojo.api.Result;

import cn.kmbeast.pojo.dto.query.extend.QuizQuestionQueryDto;

import cn.kmbeast.pojo.entity.*;

import cn.kmbeast.pojo.vo.QuizQuestionVO;

import cn.kmbeast.service.QuizService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;


import jakarta.annotation.Resource;

import java.time.LocalDateTime;

import java.util.List;


@Slf4j

@Service

public class QuizServiceImpl implements QuizService {


    @Resource private QuizQuestionMapper questionMapper;

    @Resource private QuizExamMapper examMapper;

    @Resource private QuizExamQuestionMapper examQuestionMapper;

    @Resource private QuizRecordMapper recordMapper;

    @Resource private QuizAnswerMapper answerMapper;


    @Override

    public Result<Void> saveQuestion(QuizQuestion question) {

        question.setCreateTime(LocalDateTime.now());

        questionMapper.save(question);

        return ApiResult.success();

    }


    @Override

    public Result<Void> updateQuestion(QuizQuestion question) {

        questionMapper.update(question);

        return ApiResult.success();

    }


    @Override

    public Result<Void> deleteQuestions(List<Long> ids) {

        questionMapper.batchDelete(ids);

        return ApiResult.success();

    }


    @Override

    public Result<List<QuizQuestionVO>> queryQuestions(QuizQuestionQueryDto queryDto) {

        List<QuizQuestionVO> list = questionMapper.query(queryDto);

        Integer count = questionMapper.queryCount(queryDto);

        return PageResult.success(list, count);

    }


    @Override

    public Result<QuizQuestionVO> getQuestionById(Integer id) {

        return ApiResult.success(questionMapper.getById(id));

    }


    @Override

    public Result<Void> saveExam(QuizExam exam) {

        exam.setCreateTime(LocalDateTime.now());

        examMapper.save(exam);

        return ApiResult.success();

    }


    @Override

    public Result<Void> updateExam(QuizExam exam) {

        examMapper.update(exam);

        return ApiResult.success();

    }


    @Override

    public Result<Void> deleteExams(List<Long> ids) {

        examMapper.batchDelete(ids);

        return ApiResult.success();

    }


    @Override

    public Result<List<QuizExam>> getExams() {

        return ApiResult.success(examMapper.queryAll());

    }


    @Override

    public Result<QuizExam> getExamById(Integer id) {

        return ApiResult.success(examMapper.getById(id));

    }


    @Override

    @Transactional

    public Result<Void> addQuestionsToExam(Integer examId, List<QuizExamQuestion> questions) {

        examQuestionMapper.deleteByExamId(examId);

        int totalScore = 0;

        for (int i = 0; i < questions.size(); i++) {

            QuizExamQuestion eq = questions.get(i);

            eq.setExamId(examId);

            eq.setSortOrder(i + 1);

            examQuestionMapper.save(eq);

            totalScore += eq.getScore();

        }

        // 更新试卷信息

        QuizExam exam = new QuizExam();

        exam.setId(examId);

        exam.setQuestionCount(questions.size());

        exam.setTotalScore(totalScore);

        examMapper.update(exam);

        return ApiResult.success();

    }


    @Override

    public Result<QuizRecord> startExam(Integer examId, Integer userId) {

        QuizExam exam = examMapper.getById(examId);

        if (exam == null) {

            return ApiResult.error("试卷不存在");

        }

        QuizRecord record = new QuizRecord();

        record.setExamId(examId);

        record.setUserId(userId);

        record.setStartTime(LocalDateTime.now());

        record.setTotalScore(exam.getTotalScore());

        record.setQuestionCount(exam.getQuestionCount());

        record.setStatus(0);

        record.setCreateTime(LocalDateTime.now());

        recordMapper.save(record);

        return ApiResult.success(record);

    }


    @Override

    @Transactional

    public Result<Void> submitExam(Integer recordId, List<QuizAnswer> answers) {

        QuizRecord record = recordMapper.getById(recordId);

        if (record == null) {

            return ApiResult.error("考试记录不存在");

        }

        // 2026-10-10 修复（IDOR）：交卷前必须确认这是本人的答题记录，
        // 否则任何登录用户拿一个 recordId 就能把答案写进他人答卷并改掉其分数。

        if (!LocalThreadHolder.isSelfOrAdmin(record.getUserId())) {

            log.warn("越权提交答题记录被拒绝: recordId={}, ownerId={}, operatorId={}",

                    recordId, record.getUserId(), LocalThreadHolder.getUserId());

            return ApiResult.error("无权操作该答题记录");

        }

        int totalScore = 0;

        int correctCount = 0;

        for (QuizAnswer answer : answers) {

            answer.setRecordId(recordId);

            answer.setCreateTime(LocalDateTime.now());

            // 自动评分（客观题）
            QuizQuestionVO question = questionMapper.getById(answer.getQuestionId());

            if (question != null && question.getQuestionType() < 3) {

                boolean correct = question.getAnswer().trim().equalsIgnoreCase(answer.getAnswer().trim());

                answer.setIsCorrect(correct ? 1 : 0);

                answer.setScore(correct ? question.getScore() : 0);

                if (correct) correctCount++;

                totalScore += answer.getScore();

            } else {

                answer.setIsCorrect(0);

                answer.setScore(0);

            }

        }

        answerMapper.batchSave(answers);

        // 更新记录

        QuizRecord update = new QuizRecord();

        update.setId(recordId);

        update.setSubmitTime(LocalDateTime.now());

        update.setScore(totalScore);

        update.setCorrectCount(correctCount);

        update.setStatus(1);

        recordMapper.update(update);

        return ApiResult.success();

    }


    @Override

    public Result<List<QuizRecord>> getUserRecords(Integer userId) {

        return ApiResult.success(recordMapper.queryByUserId(userId));

    }


    @Override

    public Result<List<QuizAnswer>> getRecordAnswers(Integer recordId) {

        // 2026-10-10 修复（IDOR）：原先不看归属直接返回，传 recordId 即可读走
        // 他人答卷（含其作答内容与对错）。
        QuizRecord record = recordMapper.getById(recordId);

        if (record == null) {

            return ApiResult.error("答题记录不存在");

        }

        if (!LocalThreadHolder.isSelfOrAdmin(record.getUserId())) {

            log.warn("越权查看答题记录被拒绝: recordId={}, ownerId={}, operatorId={}",

                    recordId, record.getUserId(), LocalThreadHolder.getUserId());

            return ApiResult.error("无权查看该答题记录");

        }

        return ApiResult.success(answerMapper.queryByRecordId(recordId));

    }

    @Override
    public Result<List<QuizQuestionVO>> getExamQuestions(Integer examId) {

        QuizExam exam = examMapper.getById(examId);

        if (exam == null) {

            return ApiResult.error("试卷不存在");

        }

        List<QuizQuestionVO> questions = questionMapper.queryByExamId(examId);

        // 只下发题干与选项：评分完全在服务端做（见 submitExam），
        // 把 answer / analysis 送给客户端等于把答案发给了考生。

        questions.forEach(q -> {

            q.setAnswer(null);

            q.setAnalysis(null);

        });

        return ApiResult.success(questions);

    }

}

