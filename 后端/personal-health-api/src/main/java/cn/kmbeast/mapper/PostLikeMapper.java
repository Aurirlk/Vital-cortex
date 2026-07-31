package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.PostLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostLikeMapper {

    void save(PostLike postLike);

    void delete(@Param("userId") Integer userId, @Param("postId") Integer postId);

    PostLike getByUserAndPost(@Param("userId") Integer userId, @Param("postId") Integer postId);
}
