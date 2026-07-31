package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class QuizQuestionQueryDto extends QueryDto {
    private Integer categoryId;
    private Integer questionType;
    private Integer difficulty;
    private Integer status;
    private String keyword;
}
