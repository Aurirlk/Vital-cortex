package cn.kmbeast.pojo.dto.query.extend;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
public class MallProductQueryDto extends QueryDto {
    private Integer categoryId;
    private String productType;
    private String name;
    private Integer status;
    private Integer isHot;
    private Integer isNew;
    private String keyword;

    /**
     * 价格区间下限（含）。
     *
     * <p>2026-10-13（阶段 4）：前端的双头价格滑块要真的能筛，
     * 而不是拖完什么也不发生。用 {@link BigDecimal} 而不是 Double ——
     * price 列是 DECIMAL，用 Double 接会在边界值上出现
     * 「筛 9.9 却漏掉 9.9 的商品」这类浮点误差问题。</p>
     *
     * <p>语义：<b>null 表示不限</b>，0 表示「从 0 起」。两者不能混为一谈 ——
     * 若是把 null 当成 0 参与比较，等于给每次没传价格的查询都加了一道
     * {@code price >= 0} 的条件，看不出错，但会让索引选择变差。</p>
     */
    private BigDecimal minPrice;

    /** 价格区间上限（含）；null 表示不限。 */
    private BigDecimal maxPrice;
}
