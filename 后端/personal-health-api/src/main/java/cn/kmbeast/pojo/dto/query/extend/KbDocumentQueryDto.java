package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档查询 DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class KbDocumentQueryDto extends QueryDto {

    private Long id;

    /** 所属知识库 */
    private Integer kbId;

    /** 标题（模糊） */
    private String title;

    /** 解析状态：PENDING / PARSING / INDEXED / FAILED */
    private String status;

    /** 来源：file / text */
    private String sourceType;

    /** 上传者 */
    private Integer uploaderId;
}
