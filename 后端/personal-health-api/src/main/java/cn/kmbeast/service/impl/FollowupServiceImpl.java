package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.FollowupRecordMapper;
import cn.kmbeast.mapper.FollowupTaskMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.em.RoleEnum;
import cn.kmbeast.pojo.entity.FollowupRecord;
import cn.kmbeast.pojo.entity.FollowupTask;
import cn.kmbeast.pojo.vo.FollowupTaskVO;
import cn.kmbeast.service.FollowupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
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
        // 2026-10-10 修复（IDOR）：原先把前端传来的 patientId 原样透传给 Mapper，
        // 而 SQL 里带着 `#{patientId} = 0` 的「全部」分支——普通用户传自己的 id 能看
        // 自己的任务，传 0 却可拉走全库随访任务（含 patient_name / doctor_name /
        // 标题 / 病情描述）。现普通用户一律强制按自己过滤，0 这个逃逸口对其失效；
        // 医生可查看指定患者（随访属其职责），但同样不准用 0 查全库。
        if (!LocalThreadHolder.isAdmin()) {
            if (isDoctor()) {
                if (patientId == null || patientId == 0) {
                    return ApiResult.error("请指定患者");
                }
            } else {
                patientId = LocalThreadHolder.getUserId();
            }
        }
        return ApiResult.success(taskMapper.queryByPatientId(patientId));
    }

    @Override
    public Result<List<FollowupTaskVO>> getDoctorTasks(Integer doctorId) {
        // 2026-10-10 修复（IDOR）：同 getPatientTasks，原先原样透传 doctorId，
        // 且 SQL 有 `#{doctorId} = 0` 的「全部」分支，任何登录用户传 0 即可拿走
        // 全部医生的随访任务。现非管理员强制按自己过滤：医生只能查自己的任务
        // （其 token 里的 id 即 hospital_doctor.id），0 逃逸口被关闭。
        if (!LocalThreadHolder.isAdmin()) {
            doctorId = LocalThreadHolder.getUserId();
        }
        return ApiResult.success(taskMapper.queryByDoctorId(doctorId));
    }

    @Override
    public Result<FollowupTaskVO> getTaskById(Integer id) {
        FollowupTaskVO task = taskMapper.getById(id);
        if (task == null) {
            return ApiResult.error("随访任务不存在");
        }
        // 2026-10-10 修复（IDOR）：原先传 id 即返回任意患者的随访任务详情
        // （含患者姓名/医生姓名/病情描述）。现只允许「患者本人 / 该任务的医生 / 管理员」。
        if (!canAccessTask(task)) {
            log.warn("越权查看随访任务被拒绝: taskId={}, patientId={}, doctorId={}, operatorId={}",
                    id, task.getPatientId(), task.getDoctorId(), LocalThreadHolder.getUserId());
            return ApiResult.error("无权查看该随访任务");
        }
        return ApiResult.success(task);
    }

    @Override
    @Transactional
    public Result<Void> checkIn(FollowupRecord record) {
        if (record == null || record.getTaskId() == null) {
            return ApiResult.error("缺少随访任务 ID");
        }
        FollowupTaskVO task = taskMapper.getById(record.getTaskId());
        if (task == null) {
            return ApiResult.error("随访任务不存在");
        }
        // 2026-10-10 修复（IDOR + 缺事务）：
        // 1) 原先不看任务归属，任何登录用户传一个 taskId 就能给他人随访任务打卡；
        //    且 record.patientId 直接采信前端（前端根本不传 patientId，落库为 null）。
        //    现要求打卡人有权访问该任务，patient_id 一律取任务真实归属，不采信前端。
        // 2) 原先「插记录 + 改任务状态」两次写没有事务，中途失败会留下半应用状态
        //    （记录已写、任务仍停在「待随访」），故补 @Transactional 保证原子性。
        if (!canAccessTask(task)) {
            log.warn("越权随访打卡被拒绝: taskId={}, patientId={}, operatorId={}",
                    record.getTaskId(), task.getPatientId(), LocalThreadHolder.getUserId());
            return ApiResult.error("无权操作该随访任务");
        }
        record.setPatientId(task.getPatientId());
        record.setStatus(0);
        record.setCreateTime(LocalDateTime.now());
        recordMapper.save(record);
        // 更新任务状态为进行中
        FollowupTask update = new FollowupTask();
        update.setId(record.getTaskId());
        update.setStatus(1);
        taskMapper.update(update);
        return ApiResult.success();
    }

    @Override
    public Result<List<FollowupRecord>> getTaskRecords(Integer taskId) {
        FollowupTaskVO task = taskMapper.getById(taskId);
        if (task == null) {
            return ApiResult.error("随访任务不存在");
        }
        // 2026-10-10 修复（IDOR）：随访记录含患者打卡内容/凭证，原先传 taskId 即可
        // 读取任意任务的记录。现与 getTaskById 采用同一套归属校验。
        if (!canAccessTask(task)) {
            log.warn("越权查看随访记录被拒绝: taskId={}, patientId={}, operatorId={}",
                    taskId, task.getPatientId(), LocalThreadHolder.getUserId());
            return ApiResult.error("无权查看该随访记录");
        }
        return ApiResult.success(recordMapper.queryByTaskId(taskId));
    }

    /** 当前请求者是否可访问该随访任务：患者本人 / 该任务的医生 / 管理员 */
    private boolean canAccessTask(FollowupTask task) {
        if (task == null) {
            return false;
        }
        if (LocalThreadHolder.isSelfOrAdmin(task.getPatientId())) {
            return true;
        }
        return isDoctor()
                && task.getDoctorId() != null
                && task.getDoctorId().equals(LocalThreadHolder.getUserId());
    }

    /** 当前请求者是否为医生 */
    private boolean isDoctor() {
        return Integer.valueOf(RoleEnum.DOCTOR.getRole()).equals(LocalThreadHolder.getRoleId());
    }
}
