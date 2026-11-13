package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.RagEvalRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * RAG 评测运行记录持久化接口（追加写入）。
 */
@Mapper
public interface RagEvalRunMapper {

    int save(RagEvalRun entity);

    List<RagEvalRun> query(@Param("audience") String audience, @Param("current") Integer current,
                           @Param("size") Integer size);

    Integer queryCount(@Param("audience") String audience);

    RagEvalRun getByRunId(@Param("runId") String runId);

    /** 最近一次评测（管理端首屏展示当前质量水位） */
    RagEvalRun latest(@Param("audience") String audience);

    /** 清理历史运行（只保留最近 N 次） */
    int deleteBefore(@Param("keepId") Long keepId);
}
