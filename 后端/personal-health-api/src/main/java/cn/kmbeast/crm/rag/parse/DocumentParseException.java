package cn.kmbeast.crm.rag.parse;

/**
 * 文档解析失败。
 *
 * <p><b>这条异常的消息是给管理员看的</b>，会原样写进
 * {@code kb_document.error_msg} 并显示在管理端文档列表上。
 * 因此 {@code message} 必须说清「哪个文件、为什么、怎么办」，
 * 而不是 {@code e.getMessage()} 那种 {@code InvalidFormatException} 式的
 * 技术噪音 —— 管理员看到后者只能反复重传同一个坏文件。</p>
 */
public class DocumentParseException extends RuntimeException {

    public DocumentParseException(String message) {
        super(message);
    }

    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
