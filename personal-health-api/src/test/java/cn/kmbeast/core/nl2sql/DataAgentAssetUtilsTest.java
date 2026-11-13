package cn.kmbeast.core.nl2sql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 阶段 4 补充资产测试 · DataAgent 零依赖工具类摘取验证。
 *
 * <p>验证 {@link MarkdownParserUtil} 与 {@link ColumnTypeUtil} 原样摘取后行为正确，
 * 特别是 {@code MarkdownParserUtil} 替换自研 {@code stripFence} 后，
 * 对可变长度围栏的处理边界。</p>
 */
class DataAgentAssetUtilsTest {

    // ---------- MarkdownParserUtil ----------

    @Test
    @DisplayName("标准 3 反引号围栏提取")
    void extractFromTripleBacktickFence() {
        String md = "```sql\nSELECT * FROM user_health\n```";
        assertEquals("SELECT * FROM user_health", MarkdownParserUtil.extractRawText(md).trim());
    }

    @Test
    @DisplayName("无语言标注的围栏也能提取")
    void extractWithoutLanguageTag() {
        String md = "```\nSELECT 1\n```";
        assertEquals("SELECT 1", MarkdownParserUtil.extractRawText(md).trim());
    }

    @Test
    @DisplayName("可变长度围栏（4+ 反引号）—— 自研版本处理不了的场景")
    void extractFromLongerFence() {
        // 外层 4 反引号包裹，内层 3 反引号是代码内容的一部分
        String md = "````\n```\nSELECT 1\n```\n````";
        String extracted = MarkdownParserUtil.extractRawText(md);
        assertEquals("```\nSELECT 1\n```", extracted.trim(),
                "4 反引号围栏应完整保留内层 3 反引号内容");
    }

    @Test
    @DisplayName("无闭合围栏时返回起始标记之后的全部内容")
    void extractWithUnclosedFence() {
        String md = "```sql\nSELECT * FROM t";
        assertEquals("SELECT * FROM t", MarkdownParserUtil.extractRawText(md).trim());
    }

    @Test
    @DisplayName("无围栏时原样返回")
    void extractWithoutFence() {
        String plain = "SELECT * FROM user_health";
        assertSame(plain, MarkdownParserUtil.extractRawText(plain));
    }

    @Test
    @DisplayName("extractText 把换行统一替换为空格")
    void extractTextNormalizesNewlines() {
        String md = "```\nline1\nline2\r\nline3\rline4\n```";
        String flat = MarkdownParserUtil.extractText(md);
        assertEquals("line1 line2 line3 line4", flat.trim());
    }

    @Test
    @DisplayName("null 与超短输入安全处理")
    void handlesEdgeInputs() {
        assertNull(MarkdownParserUtil.extractRawText(null));
        assertNull(MarkdownParserUtil.extractText(null));
        assertEquals("", MarkdownParserUtil.extractRawText(""));
        assertEquals("ab", MarkdownParserUtil.extractRawText("ab"));
    }

    @Test
    @DisplayName("围栏前后有说明文字时只取代码块内容")
    void extractIgnoresSurroundingText() {
        String md = "生成的 SQL 如下：\n```sql\nSELECT a FROM b\n```\n请检查上述语句。";
        assertEquals("SELECT a FROM b", MarkdownParserUtil.extractRawText(md).trim());
    }

    // ---------- ColumnTypeUtil ----------

    @Test
    @DisplayName("数值类型归一为 number")
    void numericTypes() {
        assertEquals("number", ColumnTypeUtil.wrapType("int"));
        assertEquals("number", ColumnTypeUtil.wrapType("bigint"));
        assertEquals("number", ColumnTypeUtil.wrapType("decimal"));
        assertEquals("number", ColumnTypeUtil.wrapType("double"));
        assertEquals("number", ColumnTypeUtil.wrapType("boolean"));
        assertEquals("number", ColumnTypeUtil.wrapType("BIT"));
    }

    @Test
    @DisplayName("字符类型归一为 text")
    void textTypes() {
        assertEquals("text", ColumnTypeUtil.wrapType("varchar"));
        assertEquals("text", ColumnTypeUtil.wrapType("VARCHAR(255)"));
        assertEquals("text", ColumnTypeUtil.wrapType("char"));
    }

    @Test
    @DisplayName("其他类型原样返回")
    void otherTypesPassThrough() {
        assertEquals("datetime", ColumnTypeUtil.wrapType("datetime"));
        assertEquals("text", ColumnTypeUtil.wrapType("text"));
        assertEquals("json", ColumnTypeUtil.wrapType("json"));
        assertNull(ColumnTypeUtil.wrapType(null));
    }
}