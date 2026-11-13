package cn.kmbeast.crm.rag.parse;

import java.util.Set;

/**
 * 单一文件格式的正文提取器。
 *
 * <p>实现类必须是<b>无状态</b>且线程安全的：它们会被
 * {@link DocumentParserRegistry} 在启动期收集一次，之后并发复用。</p>
 */
public interface DocumentParser {

    /**
     * 本解析器负责的扩展名（小写、不带点）。
     *
     * @return 如 {@code Set.of("pdf")}
     */
    Set<String> extensions();

    /**
     * 从字节流提取正文。
     *
     * @param content  文件字节（上传的文件已在内存里；本项目知识库文档上限 20MB，
     *                 不值得为它引入落盘-回读的额外状态）
     * @param fileName 原始文件名，仅用于拼装错误消息
     * @return 提取结果
     * @throws DocumentParseException 提取失败；消息面向管理员，需可操作
     */
    ExtractedText parse(byte[] content, String fileName) throws DocumentParseException;
}
