package cn.kmbeast.mapper;

import cn.kmbeast.pojo.dto.query.extend.TagsQueryDto;
import cn.kmbeast.pojo.entity.Tags;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 标签持久化接口
 */
@Mapper
public interface TagsMapper {

    void save(Tags tags);

    void update(Tags tags);

    void batchDelete(@Param(value = "ids") List<Long> ids);

    List<Tags> query(TagsQueryDto tagsQueryDto);

    Integer queryCount(TagsQueryDto tagsQueryDto);

}
