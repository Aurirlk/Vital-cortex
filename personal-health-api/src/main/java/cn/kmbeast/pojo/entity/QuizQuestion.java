package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class QuizQuestion {
    private Integer id;
    private Integer categoryId;
    private Integer questionType;
    private String title;
    private String options;
    private String answer;
    private String analysis;
    private Integer difficulty;
    private Integer score;
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
