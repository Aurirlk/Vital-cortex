-- ============================================================
-- 智康云 - 热门算法（news.hot_score 维护）
--
-- ⚠️ 2026-10-07 修正：原脚本指向 `post` 表，而 post 已于
--    02_迁移脚本/04_consolidate_tables.sql 合并进 `news`
--    （content_type='POST'）并 DROP。原存储过程调用即报
--    ERROR 1146，属死代码。现统一改指 `news`。
--
-- 说明：后端业务侧的热度分由 Java 实现（HotScoreServiceImpl +
--      NewsMapper.updateHotScore / topPostsByHotScore），本文件
--      仅提供等价的库内实现，供无应用进程时（如数据修复）使用。
--
-- 公式：热度分 = 浏览*1 + 点赞*3 + 收藏*2 + 评论*4 + 分享*5，再乘时间衰减
-- ============================================================

-- 先删后建，保证本脚本可重复执行时过程体也被刷新
-- （CREATE PROCEDURE IF NOT EXISTS 在过程已存在时会静默跳过，旧定义不会更新）
-- 显式声明连接编码，避免依赖客户端默认值（否则中文 INSERT/列注释会报 ERROR 1406 或乱码）
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS update_hot_score;
DROP PROCEDURE IF EXISTS update_all_hot_scores;

DELIMITER //
CREATE PROCEDURE update_hot_score(IN p_news_id INT)
BEGIN
    DECLARE view_score DOUBLE DEFAULT 0;
    DECLARE like_score DOUBLE DEFAULT 0;
    DECLARE fav_score DOUBLE DEFAULT 0;
    DECLARE comment_score DOUBLE DEFAULT 0;
    DECLARE share_score DOUBLE DEFAULT 0;
    DECLARE time_decay DOUBLE DEFAULT 1;
    DECLARE hours_since_create DOUBLE DEFAULT 0;
    DECLARE final_score DOUBLE DEFAULT 0;

    -- 获取内容数据（news 表：NEWS 资讯与 POST 帖子共存于一张表）
    SELECT
        COALESCE(view_count, 0) * 1,
        COALESCE(like_count, 0) * 3,
        COALESCE(favorite_count, 0) * 2,
        COALESCE(comment_count, 0) * 4,
        COALESCE(share_count, 0) * 5,
        TIMESTAMPDIFF(HOUR, create_time, NOW())
    INTO view_score, like_score, fav_score, comment_score, share_score, hours_since_create
    FROM news WHERE id = p_news_id;

    -- 时间衰减算法（48小时内衰减缓慢，之后加速）
    IF hours_since_create <= 48 THEN
        SET time_decay = 1.0;
    ELSEIF hours_since_create <= 168 THEN -- 7天内
        SET time_decay = 0.8;
    ELSEIF hours_since_create <= 720 THEN -- 30天内
        SET time_decay = 0.5;
    ELSE
        SET time_decay = 0.2;
    END IF;

    -- 计算最终热度分
    SET final_score = (view_score + like_score + fav_score + comment_score + share_score) * time_decay;

    -- 回写热度分
    UPDATE news SET hot_score = final_score WHERE id = p_news_id;
END //
DELIMITER ;

-- 批量更新所有已发布内容的热度分
DELIMITER //
CREATE PROCEDURE update_all_hot_scores()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_news_id INT;
    DECLARE cur CURSOR FOR SELECT id FROM news WHERE status = 1;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO v_news_id;
        IF done THEN
            LEAVE read_loop;
        END IF;
        CALL update_hot_score(v_news_id);
    END LOOP;
    CLOSE cur;
END //
DELIMITER ;

-- 本脚本**只创建过程、不自动执行**（与历史行为一致，避免覆盖线上已有的 hot_score）。
-- 需要时手动调用：
--   CALL update_hot_score(123);        -- 单个内容
--   CALL update_all_hot_scores();      -- 全量刷新
--
-- 定时任务示例：每小时更新一次热度分（需开启事件调度器）
-- SET GLOBAL event_scheduler = ON;
-- CREATE EVENT IF NOT EXISTS update_hot_scores_event
-- ON SCHEDULE EVERY 1 HOUR
-- DO CALL update_all_hot_scores();

SELECT '热门算法已就绪（作用于 news 表）' AS status;
