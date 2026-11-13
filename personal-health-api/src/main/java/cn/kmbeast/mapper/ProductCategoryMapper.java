package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.ProductCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProductCategoryMapper {
    void save(ProductCategory category);
    List<ProductCategory> queryAll();
}
