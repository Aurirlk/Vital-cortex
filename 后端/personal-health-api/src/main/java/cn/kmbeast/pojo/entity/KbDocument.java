package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库文档（上传文件 / 粘贴文本）。
 *
 * <p>{@link #status} 是解析流水线的状态机：
 * {@code PENDING → PARSING → INDEXED / FAILED}。</p>
 *
 * <p>失败时 {@link #errorMsg} 必须留错误原文：上传失败最常见的原因是
 * 加密 PDF、扫描件、损坏文件，没有错误原文时管理员只能反复重传同一个坏文件。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KbDocument {

    /** 主键 */
    private Long id;

    /** 所属知识库 */
    private Integer kbId;

    /** 文档标题 */
    private String title;

    /** 原始文件名 */
    private String fileName;

    /** 扩展名（小写无点） */
    private String fileType;

    /** 原始字节数 */
    private Long fileSize;

    /** 来源：file / text */
    private String sourceType;

    /** 落盘路径（仅留档） */
    private String sourcePath;

    /** 正文 SHA-256 摘要 */
    private String contentHash;

    /** 解析状态：PENDING / PARSING / INDEXED / FAILED */
    private String status;

    /** 切出的块数 */
    private Integer chunkCount;

    /** 解析出的正文字符数 */
    private Integer charCount;

    /** 解析失败原因 */
    private String errorMsg;

    /** 上传者用户 id */
    private Integer uploaderId;

    /** 适用端 */
    private String audience;

    /** 逻辑删除 */
    private Boolean deleted;

    /** 创建时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
