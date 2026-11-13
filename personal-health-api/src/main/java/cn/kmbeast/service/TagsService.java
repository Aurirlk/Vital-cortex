package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.TagsQueryDto;
import cn.kmbeast.pojo.entity.Tags;

import java.util.List;

/**
 * 标签业务逻辑接口
 */
public interface TagsService {

    Result<Void> save(Tags tags);

    Result<Void> batchDelete(List<Long> ids);

    Result<Void> update(Tags tags);

    Result<List<Tags>> query(TagsQueryDto tagsQueryDto);

}
