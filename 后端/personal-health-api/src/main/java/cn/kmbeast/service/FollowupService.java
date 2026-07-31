package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.FollowupRecord;
import cn.kmbeast.pojo.entity.FollowupTask;
import cn.kmbeast.pojo.vo.FollowupTaskVO;
import java.util.List;

public interface FollowupService {
    Result<Void> saveTask(FollowupTask task);
    Result<Void> updateTask(FollowupTask task);
    Result<Void> deleteTasks(List<Long> ids);
    Result<List<FollowupTaskVO>> getPatientTasks(Integer patientId);
    Result<List<FollowupTaskVO>> getDoctorTasks(Integer doctorId);
    Result<FollowupTaskVO> getTaskById(Integer id);
    Result<Void> checkIn(FollowupRecord record);
    Result<List<FollowupRecord>> getTaskRecords(Integer taskId);
}
