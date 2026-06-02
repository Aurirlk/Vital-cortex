package cn.kmbeast.crm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 联网搜索结果
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WebSearchResult {

    /**
     * 标题
     */
    private String title;

    /**
     * 来源链接
     */
    private String url;

    /**
     * 摘要内容
     */
    private String snippet;

    /**
     * 来源网站名
     */
    private String source;

    /**
     * 发布日期
     */
    private String publishDate;
}
