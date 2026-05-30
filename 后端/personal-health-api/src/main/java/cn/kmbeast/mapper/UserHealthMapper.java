package cn.kmbeast.mapper;

import cn.kmbeast.pojo.dto.query.extend.UserHealthQueryDto;
import cn.kmbeast.pojo.entity.Tags;
import cn.kmbeast.pojo.entity.UserHealth;
import cn.kmbeast.pojo.vo.UserHealthVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户健康记录持久化接口
 */
@Mapper
public interface UserHealthMapper {

    void batchSave(List<UserHealth> userHealths);

    void update(UserHealth userHealth);

    void batchDelete(@Param(value = "ids") List<Long> ids);

    List<UserHealthVO> query(UserHealthQueryDto userHealthQueryDto);

    Integer queryCount(UserHealthQueryDto userHealthQueryDto);

    /**
     * 获取用户最近N天的健康记录
     *
     * @param userId 用户ID
     * @param days   天数
     * @return 健康记录列表
     */
    List<UserHealth> getRecentByUserId(@Param("userId") Integer userId, @Param("days") Integer days);

}
