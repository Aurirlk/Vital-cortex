package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.PostTag;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PostTagMapper {

    List<PostTag> queryAll();
}
