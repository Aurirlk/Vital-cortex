package cn.kmbeast.mapper;

import cn.kmbeast.pojo.dto.query.extend.HealthModelConfigQueryDto;
import cn.kmbeast.pojo.entity.HealthModelConfig;
import cn.kmbeast.pojo.vo.HealthModelConfigVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HealthModelConfigMapper {

    void save(HealthModelConfig healthModelConfig);

    void update(HealthModelConfig healthModelConfig);

    /**
     * 批量删除健康模型配置。
     *
     * <p>2026-10-10 修复（IDOR）：增加 {@code userId} 归属参数并由 SQL 强制过滤，
     * 非管理员只能删除「自己创建的非全局模型」；{@code userId} 为 null 表示管理员，
     * 不加归属限制。返回受影响行数供调用方判断是否混入了无权记录。
     *
     * @param ids    待删除的模型 ID 集合
     * @param userId 当前用户 ID；管理员传 null
     * @return 实际删除的行数
     */
    int batchDelete(@Param(value = "ids") List<Long> ids,
                    @Param(value = "userId") Integer userId);

    List<HealthModelConfigVO> query(HealthModelConfigQueryDto healthModelConfigQueryDto);

    Integer queryCount(HealthModelConfigQueryDto healthModelConfigQueryDto);

    /**
     * 获取所有全局健康模型配置
     *
     * @return 全局模型列表
     */
    List<HealthModelConfig> getGlobalModels();

}
