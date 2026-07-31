package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.FollowupRecord;
import cn.kmbeast.pojo.entity.FollowupTask;
import cn.kmbeast.pojo.vo.FollowupTaskVO;
import cn.kmbeast.service.FollowupService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/followup")
public class FollowupController {

    @Resource
    private FollowupService followupService;

    @Protector(role = "管理员")
    @PostMapping("/task/save")
    public Result<Void> saveTask(@RequestBody FollowupTask task) {
        return followupService.saveTask(task);
    }

    @Protector(role = "管理员")
    @PutMapping("/task/update")
    public Result<Void> updateTask(@RequestBody FollowupTask task) {
        return followupService.updateTask(task);
    }

    @Protector(role = "管理员")
    @PostMapping("/task/batchDelete")
    public Result<Void> deleteTasks(@RequestBody List<Long> ids) {
        return followupService.deleteTasks(ids);
    }

    @Protector
    @GetMapping("/task/patient/{patientId}")
    public Result<List<FollowupTaskVO>> getPatientTasks(@PathVariable Integer patientId) {
        return followupService.getPatientTasks(patientId);
    }

    @Protector
    @GetMapping("/task/doctor/{doctorId}")
    public Result<List<FollowupTaskVO>> getDoctorTasks(@PathVariable Integer doctorId) {
        return followupService.getDoctorTasks(doctorId);
    }

    @Protector
    @GetMapping("/task/{id}")
    public Result<FollowupTaskVO> getTaskById(@PathVariable Integer id) {
        return followupService.getTaskById(id);
    }

    @Protector
    @PostMapping("/checkin")
    public Result<Void> checkIn(@RequestBody FollowupRecord record) {
        return followupService.checkIn(record);
    }

    @Protector
    @GetMapping("/task/{taskId}/records")
    public Result<List<FollowupRecord>> getTaskRecords(@PathVariable Integer taskId) {
        return followupService.getTaskRecords(taskId);
    }
}
