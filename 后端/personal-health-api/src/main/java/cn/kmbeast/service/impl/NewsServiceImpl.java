package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.NewsMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.NewsQueryDto;
import cn.kmbeast.pojo.entity.News;
import cn.kmbeast.pojo.vo.NewsVO;
import cn.kmbeast.service.NewsService;
import cn.kmbeast.utils.PathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class NewsServiceImpl implements NewsService {

    @Resource
    private NewsMapper newsMapper;

    @Value("${server.port}")
    private String port;

    @Value("${my-server.api-context-path}")
    private String contextPath;

    private final List<String> defaultCovers = new ArrayList<>();
    private final Random random = new Random();

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"
    ));

    @PostConstruct
    public void init() {
        try {
            File picDir = new File(PathUtils.getClassLoadRootPath(), "pic");
            if (picDir.exists() && picDir.isDirectory()) {
                File[] files = picDir.listFiles((dir, name) -> {
                    String lower = name.toLowerCase();
                    for (String ext : IMAGE_EXTENSIONS) {
                        if (lower.endsWith(ext)) return true;
                    }
                    return false;
                });
                if (files != null) {
                    for (File f : files) {
                        defaultCovers.add(f.getName());
                    }
                }
            }
            log.info("[News] 封面图兜底池加载完成: {} 张", defaultCovers.size());
        } catch (Exception e) {
            log.warn("[News] 封面图目录扫描失败", e);
        }
    }

    @Override
    public Result<Void> save(News news) {
        news.setCreateTime(LocalDateTime.now());
        if (news.getCover() == null || news.getCover().trim().isEmpty()) {
            news.setCover(randomCover());
        }
        newsMapper.save(news);
        return ApiResult.success();
    }

    @Override
    public Result<Void> batchDelete(List<Long> ids) {
        newsMapper.batchDelete(ids);
        return ApiResult.success();
    }

    @Override
    public Result<Void> update(News news) {
        newsMapper.update(news);
        return ApiResult.success();
    }

    @Override
    public Result<List<NewsVO>> query(NewsQueryDto queryDto) {
        List<NewsVO> newsList = newsMapper.query(queryDto);
        Integer totalCount = newsMapper.queryCount(queryDto);

        for (NewsVO vo : newsList) {
            if (vo.getCover() == null || vo.getCover().trim().isEmpty()) {
                vo.setCover(randomCover());
            }
        }

        return PageResult.success(newsList, totalCount);
    }

    private String randomCover() {
        if (defaultCovers.isEmpty()) return "";
        String filename = defaultCovers.get(random.nextInt(defaultCovers.size()));
        return "http://localhost:" + port + contextPath + "/file/getFile?fileName=" + filename;
    }
}
