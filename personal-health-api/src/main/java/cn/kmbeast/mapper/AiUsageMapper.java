package cn.kmbeast.mapper;

import cn.kmbeast.pojo.entity.AiUsage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI token 用量持久化（ENG-10���
 */
@Mapper
public interface AiUsageMapper {

    void insert(AiUsage usage);
}
