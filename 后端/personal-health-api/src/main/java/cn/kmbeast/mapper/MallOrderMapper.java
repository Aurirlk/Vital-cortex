package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.MallOrder;
import cn.kmbeast.pojo.entity.OrderItem;
import cn.kmbeast.pojo.vo.MallOrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface MallOrderMapper {
    void save(MallOrder order);
    void update(MallOrder order);
    List<MallOrderVO> queryByUserId(@Param("userId") Integer userId);
    MallOrderVO getById(@Param("id") Integer id);
    MallOrder getByOrderNo(@Param("orderNo") String orderNo);
    Integer count();
    Integer countToday();
    BigDecimal sumAmount();
    BigDecimal sumTodayAmount();
    List<Map<String, Object>> countByStatus();
    List<Map<String, Object>> countByDays(@Param("days") int days);
}
