package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.UserHealthQueryDto;
import cn.kmbeast.pojo.dto.update.HealthImportDto;
import cn.kmbeast.pojo.entity.UserHealth;
import cn.kmbeast.pojo.vo.ChartVO;
import cn.kmbeast.pojo.vo.UserHealthVO;

import java.util.List;
import java.util.Map;

/**
 * 用户健康记录业务逻辑接口
 */
public interface UserHealthService {

    Result<Void> save(List<UserHealth> userHealths);

    Result<Void> batchDelete(List<Long> ids);

    Result<Void> update(UserHealth userHealth);

    Result<List<UserHealthVO>> query(UserHealthQueryDto userHealthQueryDto);

    Result<List<ChartVO>> daysQuery(Integer day);

    /**
     * 从JSON导入健康记录
     */
    Result<Map<String, Object>> importFromJson(Integer userId, HealthImportDto importDto);

    /**
     * 导出健康记录为JSON
     */
    Result<List<Map<String, Object>>> exportToJson(Integer userId);
}
