package cn.kmbeast.crm.rag.parse;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 纯文本解析器（txt / md / csv / json / log）。
 *
 * <h3>为什么这个「最简」的解析器反而要写编解码逻辑</h3>
 * <p>中文环境下的 txt/csv 有一半不是 UTF-8：Windows 记事本、Excel「另存为
 * CSV」、大量公文导出默认都是 <b>GBK</b>。用 {@code new String(bytes, UTF_8)}
 * 硬读，这些文件不会报错 —— 它会产出一串 {@code �} 替换字符，
 * 然后被正常切块、正常入库、正常检索，只是检索永远命中不了任何中文词。
 * 这类事故排查起来极难，因为「入库成功、状态 INDEXED」看上去一切正常。</p>
 *
 * <p>因此这里按「BOM → 严格 UTF-8 → GBK」三级判定：
 * <ol>
 *   <li>先看字节开头有没有 BOM（UTF-8 / UTF-16LE / UTF-16BE 三种）；</li>
 *   <li>再用<b>严格模式</b>解码器试 UTF-8 —— 遇到非法字节序列直接抛，
 *       而不是插入替换字符。这是整个判定成立的关键：
 *       宽松解码器对任何字节都能「成功」返回，无法用来区分编码；</li>
 *   <li>最后回落 GBK。GBK 对绝大多数字节序列都合法，是安全的兜底；
 *       真遇到既非 UTF-8 又非 GBK 的内容（如日文 Shift-JIS），
 *       解出来是乱码但不会抛，此时由上游的「疑似乱码」检测发现。</li>
 * </ol>
 */
@Component
public class PlainTextParser implements DocumentParser {

    private static final Set<String> EXTS = Set.of("txt", "md", "markdown", "csv", "json", "log", "text");

    /** 低于此比例的可打印字符即判定为乱码，交由上游告警 */
    private static final double MIN_PRINTABLE_RATIO = 0.60;

    @Override
    public Set<String> extensions() {
        return EXTS;
    }

    @Override
    public ExtractedText parse(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            throw new DocumentParseException("文件内容为空：" + fileName);
        }

        Decoded decoded = decode(content);
        String text = normalize(decoded.text);

        java.util.List<String> warnings = new java.util.ArrayList<>();
        if (!"UTF-8".equals(decoded.charset)) {
            warnings.add("文件不是 UTF-8 编码，已按 " + decoded.charset + " 解码；"
                    + "若检索结果异常，建议转为 UTF-8 后重新上传。");
        }
        if (isProbablyGarbled(text)) {
            warnings.add("正文中大量字符无法识别，可能是该文件的真实编码既非 UTF-8 也非 GBK"
                    + "（如日文 Shift-JIS）。检索可能失效，建议转码后重新上传。");
        }

        // 以空行分段的段数作为「单元数」：纯文本没有更可靠的结构可统计
        int sections = text.isEmpty() ? 0 : text.split("\\n{2,}").length;
        return new ExtractedText(text, sections, "text", warnings);
    }

    // ==================== 编码判定 ====================

    private static final class Decoded {
        final String text;
        final String charset;

        Decoded(String text, String charset) {
            this.text = text;
            this.charset = charset;
        }
    }

    private Decoded decode(byte[] bytes) {
        // ① BOM
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new Decoded(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8), "UTF-8-BOM");
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new Decoded(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE), "UTF-16LE");
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new Decoded(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE), "UTF-16BE");
        }

        // ② 严格 UTF-8：非法序列直接抛，这才是「能区分编码」的那个性质
        try {
            CharsetDecoder strict = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return new Decoded(strict.decode(ByteBuffer.wrap(bytes)).toString(), "UTF-8");
        } catch (CharacterCodingException notUtf8) {
            // ③ 回落 GBK —— 中文 Windows 导出的默认编码
            try {
                return new Decoded(new String(bytes, Charset.forName("GBK")), "GBK");
            } catch (Exception gbkUnavailable) {
                // JVM 理论上必带 GBK（sun.nio.cs），走到这里说明运行环境被裁剪过
                return new Decoded(new String(bytes, StandardCharsets.UTF_8), "UTF-8-fallback");
            }
        }
    }

    /** 统一换行、去 BOM 残留、压缩连续空行。 */
    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace("\r\n", "\n").replace('\r', '\n');
        if (!s.isEmpty() && s.charAt(0) == '﻿') {
            s = s.substring(1);
        }
        // 三个以上连续换行压成两个：切块按 \n\n 分段，多余空行只会制造空块
        return s.replaceAll("\n{3,}", "\n\n").trim();
    }

    /** 可打印字符占比过低即判定为乱码。 */
    private static boolean isProbablyGarbled(String text) {
        if (text.isEmpty()) {
            return false;
        }
        int printable = 0;
        int sampled = 0;
        for (int i = 0; i < text.length() && sampled < 2000; i++, sampled++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\t' || c == '\r') {
                printable++;
            } else if (Character.isLetterOrDigit(c) || isCjkPunct(c)) {
                printable++;
            }
        }
        return sampled > 0 && (double) printable / sampled < MIN_PRINTABLE_RATIO;
    }

    private static boolean isCjkPunct(char c) {
        return c >= 0x3000 && c <= 0x303F;
    }
}
