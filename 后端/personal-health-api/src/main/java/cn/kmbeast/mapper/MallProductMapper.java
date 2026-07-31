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
    void decrementStock(@Param("id") Integer id, @Param("quantity") Integer quantity);
    List<Map<String, Object>> topBySales(@Param("limit") int limit);
    List<Map<String, Object>> countByCategory();
}
