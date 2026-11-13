package cn.kmbeast;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 项目启动类
 *
 * <p>2026-10-03 修复 P0：全项目此前<b>没有</b> {@code @EnableScheduling}，
 * 而 {@code HotScoreServiceImpl} 声明了 {@code @Scheduled(fixedRate = 3600000)}，
 * 导致帖子热度分的定时刷新<b>从未真正运行过</b>，且不产生任何报错——
 * 属于「看起来有、实际没跑」的最高危隐蔽故障。同理 {@code @Async} 也未生效。
 *
 * <p>开启后需注意：
 * <ul>
 *   <li>{@code HotScoreServiceImpl.refreshAllHotScores()} 内含 N+1 逐条 UPDATE，
 *       首次执行会较慢；已在该方法加日志便于观察</li>
 *   <li>若后续引入分布式部署，需把定时任务改为单实例执行（加分布式锁）</li>
 * </ul>
 */
@MapperScan("cn.kmbeast.mapper")
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class PersonalHealthApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonalHealthApplication.class, args);
    }
}
