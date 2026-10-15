package cn.kmbeast.crm.rag.parse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 文档解析入口：按扩展名分派到具体解析器，并统一做前置/后置校验。
 *
 * <p>它是「字节 → 正文」的<b>唯一咽喉</b>。把校验放在这里而不是各个解析器里，
 * 是为了让下面这三条对每一种格式都只实现一次、且不可能被漏掉：</p>
 * <ol>
 *   <li><b>体积上限</b> —— 不做时，一个 200MB 的 PDF 会先在内存里展开再进
 *       解析器，直接 OOM。上限在「读了字节之后、交给解析器之前」拦截。</li>
 *   <li><b>空正文判定</b> —— 任何解析器返回空白都视为失败。这是<b>最重要的一条</b>：
 *       解析出 0 字符却标记「入库成功」的文档，会长期躺在库里冒充知识，
 *       而它永远不会被检索到，且没有任何告警。</li>
 *   <li><b>未知扩展名</b> —— 给出「当前支持哪些格式」的可操作提示，
 *       而不是一句「不支持的类型」。</li>
 * </ol>
 *
 * <p>启动期还会检测<b>扩展名冲突</b>（两个解析器声明同一个扩展名）：
 * 这属于接线错误，映射到哪个是随机的，必须当场失败而不是等到某天
 * 解析出错误结果。</p>
 */
@Component
public class DocumentParserRegistry {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserRegistry.class);

    /**
     * 上传体积上限（默认 10MB）。超出时在读入内存前就该被拒绝，这里兜第二道。
     *
     * <p><b>默认值必须与 Spring 的 multipart 上限对齐</b>：所有 profile 的
     * {@code spring.servlet.multipart.max-file-size} 都是 {@code 10MB}。
     * 若这里配得比它大，超限文件会<b>先</b>被 Spring 拦掉，抛
     * {@code MaxUploadSizeExceededException}——用户看到的是容器的原始异常，
     * 而不是下面那条写明「多大、上限多少」的中文提示。
     * 要放宽上限，必须同时改 profile 里的 multipart 配置。</p>
     *
     * <p>字段初始化器里带默认值，而不是只靠 {@code @Value} 的 {@code :10485760} ——
     * 后者只在 Spring 环境里生效。纯单测（不启动容器）下 {@code maxBytes} 会是
     * {@code 0}，于是<b>任何文件都会「超过上限」</b>，表现为一片莫名其妙的全量失败。</p>
     */
    @Value("${crm.rag.parse.max-bytes:10485760}")
    private long maxBytes = 10_485_760L;

    /** 扩展名 → 解析器 */
    private final Map<String, DocumentParser> byExtension;

    /** 支持的全部扩展名（排序后，用于错误提示与前端 accept 属性） */
    private final Set<String> supportedExtensions;

    public DocumentParserRegistry(List<DocumentParser> parsers) {
        Map<String, DocumentParser> map = new LinkedHashMap<>();
        List<DocumentParser> available = parsers == null ? List.of() : parsers;
        for (DocumentParser parser : available) {
            for (String ext : parser.extensions()) {
                String key = ext.toLowerCase(Locale.ROOT);
                DocumentParser previous = map.put(key, parser);
                if (previous != null) {
                    throw new IllegalStateException(
                            "文档解析器扩展名冲突：." + key + " 同时被 "
                                    + previous.getClass().getSimpleName() + " 与 "
                                    + parser.getClass().getSimpleName() + " 声明。"
                                    + "分派结果将取决于 Bean 扫描顺序，必须消除歧义。");
                }
            }
        }
        this.byExtension = Collections.unmodifiableMap(map);
        this.supportedExtensions = Collections.unmodifiableSet(new TreeSet<>(map.keySet()));
        log.info("[RAG-parse] 文档解析器已装配 {} 个，支持格式: {}", available.size(), supportedExtensions);
    }

    /** 支持的全部扩展名（小写、不带点）。 */
    public Set<String> supportedExtensions() {
        return supportedExtensions;
    }

    /** 是否支持该文件名对应的格式。 */
    public boolean supports(String fileName) {
        return byExtension.containsKey(extensionOf(fileName));
    }

    /**
     * 从文件名取扩展名。
     *
     * <p>同时处理两种现实：浏览器（老版 IE / 部分客户端）会把完整路径塞进
     * `filename`，所以要取最后一个路径分隔符之后的部分；以及无扩展名文件
     * （返回空串而不是 null，避免调用方到处判空）。</p>
     */
    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        String name = fileName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 解析上传的文件字节。
     *
     * @param content  文件字节
     * @param fileName 原始文件名（决定用哪个解析器）
     * @return 提取结果（正文非空）
     * @throws DocumentParseException 体积超限 / 格式不支持 / 解析失败 / 正文字空
     */
    public ExtractedText parse(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            throw new DocumentParseException("文件内容为空：" + display(fileName));
        }
        if (content.length > maxBytes) {
            throw new DocumentParseException(
                    "文件体积 " + readableSize(content.length) + " 超过上限 "
                            + readableSize(maxBytes) + "（" + display(fileName) + "）。");
        }

        String ext = extensionOf(fileName);
        DocumentParser parser = byExtension.get(ext);
        if (parser == null) {
            throw new DocumentParseException(
                    "不支持的文件类型：." + (ext.isEmpty() ? "(无扩展名)" : ext)
                            + "（" + display(fileName) + "）。当前支持：" + supportedExtensions);
        }

        ExtractedText extracted = parser.parse(content, fileName);
        return requireNonBlank(extracted, fileName);
    }

    /**
     * 把「直接粘贴的文本」包装成与文件解析一致的产物。
     *
     * <p>走同一个出口意味着粘贴的文本也享有「空正文即失败」这条校验，
     * 而不是另开一条能塞进空文档的旁路。</p>
     */
    public ExtractedText parseText(String text, String title) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim();
        int sections = normalized.isEmpty() ? 0 : normalized.split("\\n{2,}").length;
        ExtractedText extracted = ExtractedText.of(normalized, sections, "text");
        return requireNonBlank(extracted, title);
    }

    /** 统一的「空正文即失败」出口。 */
    private ExtractedText requireNonBlank(ExtractedText extracted, String fileName) {
        if (extracted.getText().isBlank()) {
            throw new DocumentParseException(
                    "未能从「" + display(fileName) + "」中提取到任何文本。"
                            + "常见原因：① 扫描件/图片型 PDF（需 OCR，本项目未集成）"
                            + "② 文件内容为空 ③ 文件已损坏。");
        }
        return extracted;
    }

    /** 所有支持的扩展名拼成给用户看的串。 */
    public String supportedExtensionsHint() {
        return String.join(" / ", new ArrayList<>(supportedExtensions));
    }

    private static String display(String fileName) {
        return fileName == null || fileName.isBlank() ? "未命名文件" : fileName;
    }

    private static String readableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + "KB";
        }
        return String.format(Locale.ROOT, "%.1fMB", bytes / 1024.0 / 1024.0);
    }
}
