package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.RetrievalLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 检索日志持久化接口（追加写入，无更新无删除）。
 */
@Mapper
public interface RetrievalLogMapper {

    int save(RetrievalLog entity);

    List<RetrievalLog> query(@Param("queryText") String queryText, @Param("audience") String audience,
                             @Param("path") String path, @Param("userId") Integer userId,
                             @Param("current") Integer current, @Param("size") Integer size);

    Integer queryCount(@Param("queryText") String queryText, @Param("audience") String audience,
                       @Param("path") String path, @Param("userId") Integer userId);

    /**
     * 按路径聚合（管理端「召回链路健康度」）。
     *
     * @return 每行 {path, cnt, avgLatency}
     */
    List<Map<String, Object>> aggregateByPath(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    /**
     * 退化统计：两路中有一路完全没出结果的比例。
     *
     * <p>这是最值得盯的一个数：{@code sparse_only} 长期占多数，
     * 说明向量服务（EMBEDDING_API_URL）其实一直没在工作，
     * 而系统表面上是「正常的」—— 它只是悄悄地退化成了一台关键词检索。</p>
     *
     * @return {total, hybrid, sparseOnly, denseOnly, none}
     */
    Map<String, Object> summarize(@Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    /** 清理历史日志（默认保留期由调用方给定） */
    int deleteBefore(@Param("before") LocalDateTime before);
}
