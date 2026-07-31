package cn.kmbeast.service;

import cn.kmbeast.pojo.entity.ModelAnnouncement;

import java.util.List;

/**
 * 模型公告/横幅通知服务接口
 */
public interface ModelAnnouncementService {

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
    ModelAnnouncement findById(Integer id);

    /**
     * 查询当前展示的横幅（C端使用）
     * @return 当前展示的横幅
     */
    ModelAnnouncement findActiveAnnouncement();

    /**
     * 新增或更新横幅
     * @param announcement 横幅信息
     * @return 操作结果
     */
    boolean saveOrUpdate(ModelAnnouncement announcement);

    /**
     * 删除横幅
     * @param id 横幅ID
     * @return 操作结果
     */
    boolean deleteById(Integer id);

    /**
     * 批量删除横幅
     * @param ids 横幅ID列表
     * @return 操作结果
     */
    boolean batchDelete(List<Integer> ids);

    /**
     * 设置当前展示横幅
     * @param id 横幅ID
     * @return 操作结果
     */
    boolean setActive(Integer id);
}
