package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.ModelAnnouncementMapper;
import cn.kmbeast.pojo.entity.ModelAnnouncement;
import cn.kmbeast.service.ModelAnnouncementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 模型公告/横幅通知服务实现类
 */
@Slf4j
@Service
public class ModelAnnouncementServiceImpl implements ModelAnnouncementService {

    @Resource
    private ModelAnnouncementMapper modelAnnouncementMapper;

    @Override
    public List<ModelAnnouncement> findAll() {
        return modelAnnouncementMapper.findAll();
    }

    @Override
    public ModelAnnouncement findById(Integer id) {
        return modelAnnouncementMapper.findById(id);
    }

    @Override
    public ModelAnnouncement findActiveAnnouncement() {
        return modelAnnouncementMapper.findActiveAnnouncement();
    }

    @Override
    public boolean saveOrUpdate(ModelAnnouncement announcement) {
        try {
            if (announcement.getId() != null && announcement.getId() > 0) {
                // 更新
                int rows = modelAnnouncementMapper.update(announcement);
                log.info("更新横幅公告，ID: {}, 影响行数: {}", announcement.getId(), rows);
                return rows > 0;
            } else {
                // 新增
                int rows = modelAnnouncementMapper.insert(announcement);
                log.info("新增横幅公告，模型: {}, 影响行数: {}", announcement.getModelKey(), rows);
                return rows > 0;
            }
        } catch (Exception e) {
            log.error("保存横幅公告失败", e);
            return false;
        }
    }

    @Override
    public boolean deleteById(Integer id) {
        try {
            int rows = modelAnnouncementMapper.deleteById(id);
            log.info("删除横幅公告，ID: {}, 影响行数: {}", id, rows);
            return rows > 0;
        } catch (Exception e) {
            log.error("删除横幅公告失败，ID: {}", id, e);
            return false;
        }
    }

    @Override
    public boolean batchDelete(List<Integer> ids) {
        try {
            int rows = modelAnnouncementMapper.batchDelete(ids);
            log.info("批量删除横幅公告，数量: {}, 影响行数: {}", ids.size(), rows);
            return rows > 0;
        } catch (Exception e) {
            log.error("批量删除横幅公告失败", e);
            return false;
        }
    }

    @Override
    public boolean setActive(Integer id) {
        try {
            int rows = modelAnnouncementMapper.setActive(id);
            log.info("设置当前展示横幅，ID: {}, 影响行数: {}", id, rows);
            return rows > 0;
        } catch (Exception e) {
            log.error("设置当前展示横幅失败，ID: {}", id, e);
            return false;
        }
    }
}
