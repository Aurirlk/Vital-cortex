package cn.kmbeast.pojo.entity;

import lombok.Data;

@Data
public class QuizExamQuestion {
    private Integer id;
    private Integer examId;
    private Integer questionId;
    private Integer score;
    private Integer sortOrder;
}
