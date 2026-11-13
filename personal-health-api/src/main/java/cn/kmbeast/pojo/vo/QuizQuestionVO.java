package cn.kmbeast.pojo.vo;

import cn.kmbeast.pojo.entity.QuizQuestion;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class QuizQuestionVO extends QuizQuestion {
    private String categoryName;
}
