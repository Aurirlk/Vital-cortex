package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.PostReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostReportMapper {

    void save(PostReport postReport);

    void updateStatus(@Param("id") Integer id, @Param("status") Integer status);

    List<PostReport> queryPending();

    Integer countPending();
}
