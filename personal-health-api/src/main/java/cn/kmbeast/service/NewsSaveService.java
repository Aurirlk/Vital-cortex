package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.NewsSaveQueryDto;
import cn.kmbeast.pojo.entity.NewsSave;
import cn.kmbeast.pojo.vo.NewsSaveVO;

import java.util.List;

public interface NewsSaveService {

    Result<Void> save(NewsSave newsSave);

    Result<Void> batchDelete(List<Long> ids);

    Result<List<NewsSaveVO>> query(NewsSaveQueryDto newsSaveQueryDto);

    Result<Void> operation(NewsSave newsSave);


}
