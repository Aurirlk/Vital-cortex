package cn.kmbeast.mapper;

import cn.kmbeast.pojo.dto.query.extend.PostQueryDto;
import cn.kmbeast.pojo.entity.Post;
import cn.kmbeast.pojo.vo.PostVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface PostMapper {

    void save(Post post);

    void update(Post post);

    void batchDelete(@Param("ids") List<Long> ids);

    List<PostVO> query(PostQueryDto queryDto);

    Integer queryCount(PostQueryDto queryDto);

    PostVO getById(@Param("id") Integer id);

    void incrementViewCount(@Param("id") Integer id);

    void incrementLikeCount(@Param("id") Integer id);

    void decrementLikeCount(@Param("id") Integer id);

    void incrementFavoriteCount(@Param("id") Integer id);

    void decrementFavoriteCount(@Param("id") Integer id);

    void incrementCommentCount(@Param("id") Integer id);

    void updateHotScore(@Param("id") Integer id);

    Integer countToday();

    List<Map<String, Object>> topByHotScore(@Param("limit") int limit);

    List<Map<String, Object>> countByTag();

    List<Map<String, Object>> countByDays(@Param("days") int days);
}
