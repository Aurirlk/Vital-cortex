package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.ModelAnnouncement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 模型公告/横幅通知Mapper接口
 */
@Mapper
public interface ModelAnnouncementMapper {

    /**
     * 查询所有横幅列表
     * @return 横幅列表
     */
    List<ModelAnnouncement> findAll();

    /**
     * 根据ID查询横幅
     * @param id 横幅ID
     * @return 横幅信息
     */
    ModelAnnouncement findById(@Param("id") Integer id);

    /**
     * 根据模型标识查询横幅
     * @param modelKey 模型标识
     * @return 横幅信息
     */
    ModelAnnouncement findByModelKey(@Param("modelKey") String modelKey);

    /**
     * 查询当前展示的横幅（is_active=1且is_online=1）
     * @return 当前展示的横幅
     */
    ModelAnnouncement findActiveAnnouncement();

    /**
     * 新增横幅
     * @param announcement 横幅信息
     * @return 影响行数
     */
    int insert(ModelAnnouncement announcement);

    /**
     * 更新横幅
     * @param announcement 横幅信息
     * @return 影响行数
     */
    int update(ModelAnnouncement announcement);

    /**
     * 删除横幅
     * @param id 横幅ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Integer id);

    /**
     * 批量删除横幅
     * @param ids 横幅ID列表
     * @return 影响行数
     */
    int batchDelete(@Param("ids") List<Integer> ids);

    /**
     * 设置当前展示横幅（先取消所有，再设置指定的）
     * @param id 横幅ID
     * @return 影响行数
     */
    int setActive(@Param("id") Integer id);
}
