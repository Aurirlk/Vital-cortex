package cn.kmbeast.mapper;

import cn.kmbeast.pojo.dto.query.extend.MallProductQueryDto;
import cn.kmbeast.pojo.entity.MallProduct;
import cn.kmbeast.pojo.vo.MallProductVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface MallProductMapper {
    void save(MallProduct product);
    void update(MallProduct product);
    void batchDelete(@Param("ids") List<Long> ids);
    List<MallProductVO> query(MallProductQueryDto queryDto);
    Integer queryCount(MallProductQueryDto queryDto);
    MallProductVO getById(@Param("id") Integer id);
    void incrementSalesCount(@Param("id") Integer id, @Param("quantity") Integer quantity);
    /**
     * 扣减库存（带 {@code stock >= quantity} 前置条件）。
     *
     * <p>2026-10-10：返回值由 {@code void} 改为影响行数。SQL 里一直有
     * {@code AND stock >= #{quantity}} 这道防线，但调用方（下单）把返回值丢了，
     * 于是库存不足时「扣不动」也照样下单成功 —— 静默超卖。
     * 现在调用方必须检查返回值，为 0 即库存不足，直接回滚整单。
     *
     * @return 影响行数：1 = 扣减成功，0 = 库存不足或商品不存在
     */
    Integer decrementStock(@Param("id") Integer id, @Param("quantity") Integer quantity);
    List<Map<String, Object>> topBySales(@Param("limit") int limit);
    List<Map<String, Object>> countByCategory();
}
