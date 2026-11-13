package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识块 —— 检索的最小单位。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KbChunk {

    /** 主键 */
    private Long id;

    /** 所属知识库 */
    private Integer kbId;

    /** 所属文档 */
    private Long docId;

    /** 块在文档内的序号，从 0 递增 */
    private Integer chunkIndex;

    /** 块所属文档标题（冗余） */
    private String title;

    /** 块正文 */
    private String content;

    /** 正文长度（字符） */
    private Integer contentLen;

    /** 是否参与检索 */
    private Boolean enabled;

    /** 创建时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
