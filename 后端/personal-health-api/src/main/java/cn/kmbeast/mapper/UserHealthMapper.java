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

    /**
     * 带归属约束的修改：只允许改「属于 {@code userId}」的记录。
     *
     * <p>2026-10-10 新增，用于修 IDOR。原 {@link #update} 只带 {@code WHERE id = ?}，
     * 任何登录用户传一个 id 就能改写他人健康记录。
     *
     * @param userId 归属用户；传 null 表示不做归属限制（仅管理员走这条）
     * @return 影响行数：0 = 记录不存在或不属于该用户
     */
    Integer updateOwned(@Param("id") Integer id, @Param("value") String value,
                        @Param("userId") Integer userId);

    /**
     * 带归属约束的批量删除，语义同 {@link #updateOwned}。
     *
     * @return 实际删除行数；调用方据此判断是否发生了越权（删得比请求的少）
     */
    Integer batchDeleteOwned(@Param(value = "ids") List<Long> ids,
                             @Param("userId") Integer userId);

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
