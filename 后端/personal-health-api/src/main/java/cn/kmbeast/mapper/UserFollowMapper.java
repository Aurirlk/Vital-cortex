package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.UserFollow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserFollowMapper {

    void save(UserFollow userFollow);

    void delete(@Param("followerId") Integer followerId, @Param("followeeId") Integer followeeId);

    UserFollow getByFollowerAndFollowee(@Param("followerId") Integer followerId, @Param("followeeId") Integer followeeId);

    List<Integer> getFollowerIds(@Param("followeeId") Integer followeeId);

    Integer countFollowers(@Param("followeeId") Integer followeeId);

    Integer countFollowing(@Param("followerId") Integer followerId);
}
