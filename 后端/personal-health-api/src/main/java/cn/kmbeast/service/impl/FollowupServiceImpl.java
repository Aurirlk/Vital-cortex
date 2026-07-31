package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.FollowupRecordMapper;
import cn.kmbeast.mapper.FollowupTaskMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.FollowupRecord;
import cn.kmbeast.pojo.entity.FollowupTask;
import cn.kmbeast.pojo.vo.FollowupTaskVO;
import cn.kmbeast.service.FollowupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class FollowupServiceImpl implements FollowupService {

    @Resource private FollowupTaskMapper taskMapper;
    @Resource private FollowupRecordMapper recordMapper;

    @Override
    public Result<Void> saveTask(FollowupTask task) {
        task.setStatus(0);
        task.setCreateTime(LocalDateTime.now());
        taskMapper.save(task);
        return ApiResult.success();
    }

    @Override
    public Result<Void> updateTask(FollowupTask task) {
        taskMapper.update(task);
        return ApiResult.success();
    }

    @Override
    public Result<Void> deleteTasks(List<Long> ids) {
        taskMapper.batchDelete(ids);
        return ApiResult.success();
    }

    @Override
    public Result<List<FollowupTaskVO>> getPatientTasks(Integer patientId) {
        return ApiResult.success(taskMapper.queryByPatientId(patientId));
    }

    @Override
    public Result<List<FollowupTaskVO>> getDoctorTasks(Integer doctorId) {
        return ApiResult.success(taskMapper.queryByDoctorId(doctorId));
    }

    @Override
    public Result<FollowupTaskVO> getTaskById(Integer id) {
        return ApiResult.success(taskMapper.getById(id));
    }

    @Override
    public Result<Void> checkIn(FollowupRecord record) {
        record.setStatus(0);
        record.setCreateTime(LocalDateTime.now());
        recordMapper.save(record);
        // 更新任务状态为进行中
        FollowupTask task = new FollowupTask();
        task.setId(record.getTaskId());
        task.setStatus(1);
        taskMapper.update(task);
        return ApiResult.success();
    }

    @Override
    public Result<List<FollowupRecord>> getTaskRecords(Integer taskId) {
        return ApiResult.success(recordMapper.queryByTaskId(taskId));
    }
}
