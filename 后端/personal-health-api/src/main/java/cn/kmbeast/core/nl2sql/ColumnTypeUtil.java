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
 * 阶段 4 补充资产 · 列类型归一化。
 *
 * <p><b>来源</b>：DataAgent {@code com.alibaba.cloud.ai.dataagent.util.ColumnTypeUtil}（32 行，
 * Apache 2.0），零依赖、零 Spring AI，整类原样摘取，仅改包名。</p>
 *
 * <p>用于把 MySQL 原始 {@code data_type} 归一为对 LLM 更友好的语义类型
 * （数值型统一为 {@code number}、字符型统一为 {@code text}），减少 LLM 误用类型。</p>
 */
public final class ColumnTypeUtil {

    private ColumnTypeUtil() {
    }

/**
 * 归一列类型：数值型 → {@code number}，字符型 → {@code text}，其余原样返回。
 *
 * <p><b>与上游的一处修正</b>：DataAgent 原实现对精确匹配用 {@code equalsIgnoreCase}，
 * 但对前缀匹配用<b>区分大小写</b>的 {@code startsWith}，导致大写的 {@code VARCHAR(255)}
 * （MySQL 中很常见）漏判、原样返回而非归一为 {@code text}。此处统一为忽略大小写。</p>
 *
 * @param s information_schema 里的 {@code data_type}
 */
public static String wrapType(String s) {
        if (s == null) {
            return null;
        }
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("decimal") || lower.equals("int") || lower.equals("bigint")
                || lower.equals("bool") || lower.equals("bit") || lower.equals("boolean")
                || lower.equals("double")) {
            return "number";
        } else if (lower.startsWith("varchar") || lower.startsWith("char")) {
            return "text";
        }
        return s;
    }
}