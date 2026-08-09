package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.TagsMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.TagsQueryDto;
import cn.kmbeast.pojo.entity.Tags;
import cn.kmbeast.service.TagsService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 标签业务逻辑实现
 */
@Service
public class TagsServiceImpl implements TagsService {

    @Resource
    private TagsMapper tagsMapper;

    /**
     * 标签新增
     *
     * @param tags 参数
     * @return Result<Void>
     */
    @Override
    @CacheEvict(value = "tags", allEntries = true)
    public Result<Void> save(Tags tags) {
        tagsMapper.save(tags);
        return ApiResult.success();
    }

    /**
     * 标签删除
     *
     * @param ids 参数
     * @return Result<Void>
     */
    @Override
    @CacheEvict(value = "tags", allEntries = true)
    public Result<Void> batchDelete(List<Long> ids) {
        tagsMapper.batchDelete(ids);
        return ApiResult.success();
    }

    /**
     * 标签修改
     *
     * @param tags 参数
     * @return Result<Void>
     */
    @Override
    @CacheEvict(value = "tags", allEntries = true)
    public Result<Void> update(Tags tags) {
        tagsMapper.update(tags);
        return ApiResult.success();
    }

    /**
     * 标签查询
     *
     * @param tagsQueryDto 查询参数
     * @return Result<List < Tags>>
     */
    @Override
    @Cacheable(value = "tags", key = "#tagsQueryDto")
    public Result<List<Tags>> query(TagsQueryDto tagsQueryDto) {
        List<Tags> tagsList = tagsMapper.query(tagsQueryDto);
        Integer totalCount = tagsMapper.queryCount(tagsQueryDto);
        return PageResult.success(tagsList, totalCount);
    }


}
