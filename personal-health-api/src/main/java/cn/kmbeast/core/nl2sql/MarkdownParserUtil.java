/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.kmbeast.core.nl2sql;

/**
 * 阶段 4 补充资产 · Markdown 围栏提取器。
 *
 * <p><b>来源</b>：DataAgent {@code com.alibaba.cloud.ai.dataagent.util.MarkdownParserUtil}（70 行，
 * Apache 2.0），零依赖、零 Spring AI，整类原样摘取，仅改包名。</p>
 *
 * <p><b>为何替换自研实现</b>：原 {@code MySqlText2SqlService.stripFence()} 只处理固定 3 反引号，
 * 遇到 4+ 反引号围栏（模型输出嵌套代码块时常见）会解析错误；本实现支持可变长度围栏、
 * 语言标注、CRLF/CR/LF 三种换行，边界更完整。</p>
 */
public final class MarkdownParserUtil {

    private MarkdownParserUtil() {
    }

    /**
     * 提取围栏内文本，并把换行统一替换为空格（适配 {@code NewLineParser.format()}）。
     */
    public static String extractText(String markdownCode) {
        if (markdownCode == null) {
            return null;
        }
        String code = extractRawText(markdownCode);
        if (code == null) {
            return null;
        }
        // 正确处理各种换行符：\r\n、\n、\r
        return code.replaceAll("\r\n", " ").replaceAll("\n", " ").replaceAll("\r", " ");
    }

    /**
     * 提取围栏内原始文本（保留换行）。
     *
     * <p>无围栏时原样返回；无闭合围栏时返回起始标记之后的全部内容。</p>
     */
    public static String extractRawText(String markdownCode) {
        if (markdownCode == null || markdownCode.length() < 3) {
            return markdownCode;
        }
        // 找到代码块起始（3 个及以上反引号）
        int startIndex = -1;
        int delimiterLength = 0;

        for (int i = 0; i <= markdownCode.length() - 3; i++) {
            if (markdownCode.substring(i, i + 3).equals("```")) {
                startIndex = i;
                delimiterLength = 3;
                // 统计额外反引号
                while (i + delimiterLength < markdownCode.length()
                        && markdownCode.charAt(i + delimiterLength) == '`') {
                    delimiterLength++;
                }
                break;
            }
        }

        if (startIndex == -1) {
            return markdownCode; // 无代码块
        }

        // 跳过起始标记与可选语言标注
        int contentStart = startIndex + delimiterLength;
        while (contentStart < markdownCode.length() && markdownCode.charAt(contentStart) != '\n') {
            contentStart++;
        }
        if (contentStart < markdownCode.length() && markdownCode.charAt(contentStart) == '\n') {
            contentStart++; // 跳过语言标注后的换行
        }

        // 查找闭合标记
        String closingDelimiter = "`".repeat(delimiterLength);
        int endIndex = markdownCode.indexOf(closingDelimiter, contentStart);

        if (endIndex == -1) {
            // 无闭合标记，返回从内容起点到结尾
            return markdownCode.substring(contentStart);
        }

        // 只取两个标记之间的内容
        return markdownCode.substring(contentStart, endIndex);
    }
}