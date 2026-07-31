package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.SensitiveWordMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.SensitiveWord;
import cn.kmbeast.service.ContentAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ContentAuditServiceImpl implements ContentAuditService {

    @Resource
    private SensitiveWordMapper sensitiveWordMapper;

    @Override
    public boolean containsSensitiveWord(String content) {
        if (content == null || content.isEmpty()) return false;
        List<String> words = sensitiveWordMapper.queryAllWords();
        String lowerContent = content.toLowerCase();
        for (String word : words) {
            if (lowerContent.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String filterSensitiveWord(String content) {
        if (content == null || content.isEmpty()) return content;
        List<String> words = sensitiveWordMapper.queryAllWords();
        String filtered = content;
        for (String word : words) {
            String replacement = "*".repeat(word.length());
            filtered = filtered.replaceAll("(?i)" + word, replacement);
        }
        return filtered;
    }

    @Override
    public Result<Void> addSensitiveWord(SensitiveWord word) {
        word.setStatus(1);
        word.setCreateTime(LocalDateTime.now());
        sensitiveWordMapper.save(word);
        return ApiResult.success();
    }

    @Override
    public Result<Void> deleteSensitiveWord(List<Long> ids) {
        sensitiveWordMapper.batchDelete(ids);
        return ApiResult.success();
    }

    @Override
    public Result<List<SensitiveWord>> getSensitiveWords() {
        return ApiResult.success(sensitiveWordMapper.queryAll());
    }
}
