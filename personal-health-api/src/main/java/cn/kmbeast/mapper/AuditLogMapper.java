package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface AuditLogMapper {
    void save(AuditLog auditLog);
    List<AuditLog> query(@Param("userId") Integer userId, @Param("action") String action,
                         @Param("resource") String resource, @Param("current") Integer current,
                         @Param("size") Integer size);
    Integer queryCount(@Param("userId") Integer userId, @Param("action") String action,
                       @Param("resource") String resource);
}
