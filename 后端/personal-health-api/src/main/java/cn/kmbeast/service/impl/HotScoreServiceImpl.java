package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.PostMapper;
import cn.kmbeast.pojo.dto.query.extend.PostQueryDto;
import cn.kmbeast.pojo.vo.PostVO;
import cn.kmbeast.service.HotScoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HotScoreServiceImpl implements HotScoreService {

    @Resource
    private PostMapper postMapper;

    @Override
    public void updateHotScore(Integer postId) {
        postMapper.updateHotScore(postId);
    }

    @Override
    @Scheduled(fixedRate = 3600000) // 每小时执行一次
    public void updateAllHotScores() {
        PostQueryDto queryDto = new PostQueryDto();
        queryDto.setStatus(1);
        List<PostVO> posts = postMapper.query(queryDto);
        for (PostVO post : posts) {
            postMapper.updateHotScore(post.getId());
        }
        log.info("热度分更新完成，共处理 {} 条帖子", posts.size());
    }

    @Override
    public List<Map<String, Object>> getHotPosts(Integer limit) {
        PostQueryDto queryDto = new PostQueryDto();
        queryDto.setOrderBy("hot");
        queryDto.setSize(limit != null ? limit : 20);
        List<PostVO> posts = postMapper.query(queryDto);
        return posts.stream()
                .map(post -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", post.getId());
                    map.put("title", post.getTitle());
                    map.put("hotScore", post.getHotScore());
                    map.put("viewCount", post.getViewCount());
                    map.put("likeCount", post.getLikeCount());
                    map.put("userName", post.getUserName());
                    return map;
                })
                .collect(Collectors.toList());
    }
}
