package cn.kmbeast.mapper;

import cn.kmbeast.pojo.dto.query.extend.DrugQueryDto;
import cn.kmbeast.pojo.entity.Drug;
import cn.kmbeast.pojo.entity.DrugSubscription;
import cn.kmbeast.pojo.vo.DrugVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DrugMapper {

    void save(Drug drug);

    void update(Drug drug);

    void batchDelete(@Param(value = "ids") List<Long> ids);

    List<DrugVO> query(DrugQueryDto drugQueryDto);

    Integer queryCount(DrugQueryDto drugQueryDto);

    DrugVO getById(@Param("id") Integer id);

    void subscribe(DrugSubscription subscription);

    void unsubscribe(@Param("userId") Integer userId, @Param("drugId") Integer drugId);

    List<DrugVO> getSubscribedDrugs(@Param("userId") Integer userId);

    List<DrugVO> searchByName(@Param("keyword") String keyword, @Param("limit") int limit);
}
