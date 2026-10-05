package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子查询条件（2026-10-04 表合并）
 *
 * <p>背景：{@code post} 表已并入 {@code news}（{@code content_type='POST'}），
 * 原 {@code PostQueryDto} 随 Mapper 一并废弃。本 DTO 承接其查询条件，
 * 字段与原 DTO 保持一致，以便 {@code PostService} 接口签名不变。
 *
 * <p>与 {@code NewsQueryDto} 的区别：本 DTO 面向<b>帖子</b>，
 * 强制过滤 {@code content_type='POST'}，不混入资讯。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class PostContentQueryDto extends QueryDto {

    /** 标题模糊匹配（对应 news.title） */
    private String title;

    /** 标签筛选 */
    private Integer tagId;

    /** 作者筛选 */
    private Integer userId;

    /** 状态：0草稿 1已发布 2已下架 */
    private Integer status;

    /** 是否置顶 */
    private Boolean isTop;

    /** 关键词（标题或正文） */
    private String keyword;

    /** 排序：hot / latest */
    private String orderBy;
}
