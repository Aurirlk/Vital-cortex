package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import java.util.List;
import java.util.Map;

public interface HotScoreService {
    void updateHotScore(Integer postId);
    void updateAllHotScores();
    List<Map<String, Object>> getHotPosts(Integer limit);
}
