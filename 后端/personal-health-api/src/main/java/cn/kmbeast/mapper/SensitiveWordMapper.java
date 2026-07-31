package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SensitiveWordMapper {
    void save(SensitiveWord word);
    void batchDelete(@Param("ids") List<Long> ids);
    List<SensitiveWord> queryAll();
    List<String> queryAllWords();
}
