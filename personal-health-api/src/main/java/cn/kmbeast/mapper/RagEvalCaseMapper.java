package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.RagEvalCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * RAG 金标用例持久化接口。
 */
@Mapper
public interface RagEvalCaseMapper {

    int save(RagEvalCase entity);

    int update(RagEvalCase entity);

    int deleteById(@Param("id") Integer id);

    List<RagEvalCase> query(@Param("question") String question, @Param("matchLevel") String matchLevel,
                            @Param("audience") String audience, @Param("enabled") Boolean enabled,
                            @Param("current") Integer current, @Param("size") Integer size);

    Integer queryCount(@Param("question") String question, @Param("matchLevel") String matchLevel,
                       @Param("audience") String audience, @Param("enabled") Boolean enabled);

    RagEvalCase getById(@Param("id") Integer id);

    /**
     * 取启用中的用例（评测时的输入集）。
     *
     * @param audience 为 all 或 null 时不按端过滤
     */
    List<RagEvalCase> listEnabled(@Param("audience") String audience);

    int countEnabled();
}
