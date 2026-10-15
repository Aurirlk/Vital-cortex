package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.KbChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识块持久化接口。
 *
 * <p>{@link #fullTextSearch} 走 ngram 全文索引，{@link #likeSearch} 是它的兜底。
 * 两条都在这里而不是在 Service 里拼 SQL：检索是这张表唯一的重活，
 * 语句必须能被 SQL 审查工具与 {@code MapperTableConsistencyTest} 看到。</p>
 */
@Mapper
public interface KbChunkMapper {

    /** 单条落库 */
    int save(KbChunk entity);

    /** 批量落库（一份文档切成几百块时逐条 insert 会慢到没法用） */
    int batchInsert(@Param("list") List<KbChunk> list);

    int deleteByDocId(@Param("docId") Long docId);

    int deleteByKbId(@Param("kbId") Integer kbId);

    int deleteByIds(@Param("ids") List<Long> ids);

    /** 按文档取块，按序号升序（管理端预览、向量重建） */
    List<KbChunk> listByDocId(@Param("docId") Long docId);

    /** 按 id 取块（检索命中后回填正文，避免把正文塞进索引结果） */
    List<KbChunk> listByIds(@Param("ids") List<Long> ids);

    int countByKbId(@Param("kbId") Integer kbId);

    int countByDocId(@Param("docId") Long docId);

    /**
     * ngram 全文检索（稀疏路主路径）。
     *
     * <p>{@code MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE)}：
     * 用自然语言模式而不是布尔模式，是因为用户问题里没有
     * {@code +} / {@code -} 这类算子，布尔模式下中文查询容易被解析成
     * 「必须同时包含全部 token」，召回过窄。</p>
     *
     * @param query 查询词（原句即可，ngram 会自行切分）
     * @param kbIds 限定知识库；为空表示不限
     * @param limit 返回条数上限
     */
    List<KbChunk> fullTextSearch(@Param("query") String query,
                                 @Param("kbIds") List<Integer> kbIds,
                                 @Param("limit") int limit);

    /**
     * LIKE 兜底检索（按关键词逐个 OR）。
     *
     * <p>仅在 ngram 全文索引不可用时使用（本机 MySQL 未启用 ngram
     * 解析器、或索引尚未建好）。功能可用，但中文长词的召回明显弱于全文索引，
     * 因此调用方必须把「走了兜底」这件事记进 {@code retrieval_log.path}。</p>
     */
    List<KbChunk> likeSearch(@Param("keywords") List<String> keywords,
                             @Param("kbIds") List<Integer> kbIds,
                             @Param("limit") int limit);
}
