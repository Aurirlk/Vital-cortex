package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class QuizAnswer {
    private Integer id;
    private Integer recordId;
    private Integer questionId;
    private String answer;
    private Integer score;
    private Integer isCorrect;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
