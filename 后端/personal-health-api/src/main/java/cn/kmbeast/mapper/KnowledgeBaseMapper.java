package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库持久化接口。
 */
@Mapper
public interface KnowledgeBaseMapper {

    int save(KnowledgeBase entity);

    int update(KnowledgeBase entity);

    int deleteById(@Param("id") Integer id);

    List<KnowledgeBase> query(@Param("code") String code, @Param("name") String name,
                              @Param("audience") String audience, @Param("enabled") Boolean enabled,
                              @Param("current") Integer current, @Param("size") Integer size);

    Integer queryCount(@Param("code") String code, @Param("name") String name,
                       @Param("audience") String audience, @Param("enabled") Boolean enabled);

    KnowledgeBase getById(@Param("id") Integer id);

    KnowledgeBase getByCode(@Param("code") String code);

    /**
     * 按端取启用中的知识库。
     *
     * @param audience user / doctor / admin；为 all 或 null 时取全部
     */
    List<KnowledgeBase> listEnabled(@Param("audience") String audience);

    /** 按 id 集合取（检索时把 kb_ids 参数转成对象列表） */
    List<KnowledgeBase> listByIds(@Param("ids") List<Integer> ids);

    /** 用真实计数重算冗余的 doc_count / chunk_count（不一致时的修复入口） */
    int refreshCounts(@Param("id") Integer id);

    /** 全部知识库 id（评测默认全库检索时用） */
    List<Integer> listAllEnabledIds();
}
