package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.QuizQuestionQueryDto;
import cn.kmbeast.pojo.entity.*;
import cn.kmbeast.pojo.vo.QuizQuestionVO;
import cn.kmbeast.service.QuizService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/quiz")
public class QuizController {

    @Resource
    private QuizService quizService;

    // ========== 题库 ==========

    @Protector(role = "管理员")
    @PostMapping("/question/save")
    public Result<Void> saveQuestion(@RequestBody QuizQuestion question) {
        return quizService.saveQuestion(question);
    }

    @Protector(role = "管理员")
    @PutMapping("/question/update")
    public Result<Void> updateQuestion(@RequestBody QuizQuestion question) {
        return quizService.updateQuestion(question);
    }

    @Protector(role = "管理员")
    @PostMapping("/question/batchDelete")
    public Result<Void> deleteQuestions(@RequestBody List<Long> ids) {
        return quizService.deleteQuestions(ids);
    }

    @Pager
    @Protector
    @PostMapping("/question/query")
    public Result<List<QuizQuestionVO>> queryQuestions(@RequestBody QuizQuestionQueryDto queryDto) {
        return quizService.queryQuestions(queryDto);
    }

    @Protector
    @GetMapping("/question/{id}")
    public Result<QuizQuestionVO> getQuestionById(@PathVariable Integer id) {
        return quizService.getQuestionById(id);
    }

    // ========== 试卷 ==========

    @Protector(role = "管理员")
    @PostMapping("/exam/save")
    public Result<Void> saveExam(@RequestBody QuizExam exam) {
        return quizService.saveExam(exam);
    }

    @Protector(role = "管理员")
    @PutMapping("/exam/update")
    public Result<Void> updateExam(@RequestBody QuizExam exam) {
        return quizService.updateExam(exam);
    }

    @Protector(role = "管理员")
    @PostMapping("/exam/batchDelete")
    public Result<Void> deleteExams(@RequestBody List<Long> ids) {
        return quizService.deleteExams(ids);
    }

    @Protector
    @GetMapping("/exam/list")
    public Result<List<QuizExam>> getExams() {
        return quizService.getExams();
    }

    @Protector
    @GetMapping("/exam/{id}")
    public Result<QuizExam> getExamById(@PathVariable Integer id) {
        return quizService.getExamById(id);
    }

    // ========== 组卷 ==========

    @Protector(role = "管理员")
    @PostMapping("/exam/{examId}/questions")
    public Result<Void> addQuestionsToExam(@PathVariable Integer examId, @RequestBody List<QuizExamQuestion> questions) {
        return quizService.addQuestionsToExam(examId, questions);
    }

    // ========== 答题 ==========

    @Protector
    @PostMapping("/start/{examId}")
    public Result<QuizRecord> startExam(@PathVariable Integer examId, @RequestAttribute("userId") Integer userId) {
        return quizService.startExam(examId, userId);
    }

    @Protector
    @PostMapping("/submit/{recordId}")
    public Result<Void> submitExam(@PathVariable Integer recordId, @RequestBody List<QuizAnswer> answers) {
        return quizService.submitExam(recordId, answers);
    }

    // ========== 记录 ==========

    @Protector
    @GetMapping("/records")
    public Result<List<QuizRecord>> getUserRecords(@RequestAttribute("userId") Integer userId) {
        return quizService.getUserRecords(userId);
    }

    @Protector
    @GetMapping("/record/{recordId}/answers")
    public Result<List<QuizAnswer>> getRecordAnswers(@PathVariable Integer recordId) {
        return quizService.getRecordAnswers(recordId);
    }
}
