package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class MessageQueryDto extends QueryDto {

    /**
     * 用户ID
     */
    private Integer userId;
    /**
     * 消息类型
     */
    private Integer messageType;
    /**
     * 消息内容
     */
    private String content;
    /**
     * 是否阅读
     */
    private Boolean isRead;

}
