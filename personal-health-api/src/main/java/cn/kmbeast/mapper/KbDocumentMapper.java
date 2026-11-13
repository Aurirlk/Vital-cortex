package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库文档持久化接口。
 */
@Mapper
public interface KbDocumentMapper {

    int save(KbDocument entity);

    int update(KbDocument entity);

    /** 逻辑删除（deleted=1）；物理清理由 kb_chunk 联动与管理端显式操作负责 */
    int softDelete(@Param("id") Long id);

    int hardDelete(@Param("id") Long id);

    /**
     * 物理删除某知识库下的全部文档行。
     *
     * <p>只用于「删除知识库」的级联：父记录都要没了，保留软删的文档行
     * 只会留下指向不存在知识库的孤儿数据。没有物理外键，这一步必须手写。</p>
     */
    int deleteByKbId(@Param("kbId") Integer kbId);

    List<KbDocument> query(@Param("kbId") Integer kbId, @Param("title") String title,
                           @Param("status") String status, @Param("sourceType") String sourceType,
                           @Param("uploaderId") Integer uploaderId,
                           @Param("current") Integer current, @Param("size") Integer size);

    Integer queryCount(@Param("kbId") Integer kbId, @Param("title") String title,
                       @Param("status") String status, @Param("sourceType") String sourceType,
                       @Param("uploaderId") Integer uploaderId);

    KbDocument getById(@Param("id") Long id);

    /** 同一知识库内按正文摘要查重（返回最早那条，用于「重复上传」提示） */
    KbDocument findByHash(@Param("kbId") Integer kbId, @Param("contentHash") String contentHash);

    /** 某知识库的全部文档 id（重建向量库时遍历用） */
    List<Long> listIdsByKb(@Param("kbId") Integer kbId);

    /** 按状态统计（管理端概览） */
    int countByStatus(@Param("status") String status);
}
