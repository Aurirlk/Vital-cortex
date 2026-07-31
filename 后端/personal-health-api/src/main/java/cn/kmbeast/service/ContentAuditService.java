package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.SensitiveWord;
import java.util.List;

public interface ContentAuditService {
    boolean containsSensitiveWord(String content);
    String filterSensitiveWord(String content);
    Result<Void> addSensitiveWord(SensitiveWord word);
    Result<Void> deleteSensitiveWord(List<Long> ids);
    Result<List<SensitiveWord>> getSensitiveWords();
}
