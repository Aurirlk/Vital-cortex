package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.PostReply;
import cn.kmbeast.pojo.vo.PostReplyVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostReplyMapper {

    void save(PostReply postReply);

    void batchDelete(@Param("ids") List<Long> ids);

    List<PostReplyVO> queryByPostId(@Param("postId") Integer postId);

    Integer countByPostId(@Param("postId") Integer postId);
}
