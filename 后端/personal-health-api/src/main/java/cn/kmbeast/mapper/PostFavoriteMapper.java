package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.PostFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostFavoriteMapper {

    void save(PostFavorite postFavorite);

    void delete(@Param("userId") Integer userId, @Param("postId") Integer postId);

    PostFavorite getByUserAndPost(@Param("userId") Integer userId, @Param("postId") Integer postId);
}
