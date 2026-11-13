package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.KbDataSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库数据源持久化接口。
 */
@Mapper
public interface KbDataSourceMapper {

    int save(KbDataSource entity);

    int update(KbDataSource entity);

    int deleteById(@Param("id") Integer id);

    List<KbDataSource> query(@Param("code") String code, @Param("name") String name,
                            @Param("type") String type, @Param("enabled") Boolean enabled,
                            @Param("current") Integer current, @Param("size") Integer size);

    Integer queryCount(@Param("code") String code, @Param("name") String name,
                       @Param("type") String type, @Param("enabled") Boolean enabled);

    KbDataSource getById(@Param("id") Integer id);

    KbDataSource getByCode(@Param("code") String code);

    /** 全部启用中的数据源 */
    List<KbDataSource> listEnabled();

    /** 记录一次连通性探测结果 */
    int updateCheckResult(@Param("id") Integer id, @Param("ok") boolean ok,
                          @Param("msg") String msg);

    /** 统计引用某数据源的知识库数量（删除前校验用） */
    Integer countKnowledgeBaseRefs(@Param("id") Integer id);
}
