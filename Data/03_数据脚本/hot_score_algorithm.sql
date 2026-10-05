-- ============================================================
-- 智康云 - 热门算法实现
-- 热度分 = 浏览*1 + 点赞*3 + 收藏*2 + 评论*4 + 分享*5 + 时间衰减
-- ============================================================

-- 热度分计算函数（存储过程）
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS update_hot_score(IN post_id INT)
BEGIN
    DECLARE view_score DOUBLE DEFAULT 0;
    DECLARE like_score DOUBLE DEFAULT 0;
    DECLARE fav_score DOUBLE DEFAULT 0;
    DECLARE comment_score DOUBLE DEFAULT 0;
    DECLARE share_score DOUBLE DEFAULT 0;
    DECLARE time_decay DOUBLE DEFAULT 1;
    DECLARE hours_since_create DOUBLE DEFAULT 0;
    DECLARE final_score DOUBLE DEFAULT 0;
    
    -- 获取帖子数据
    SELECT 
        COALESCE(view_count, 0) * 1,
        COALESCE(like_count, 0) * 3,
        COALESCE(favorite_count, 0) * 2,
        COALESCE(comment_count, 0) * 4,
        COALESCE(share_count, 0) * 5,
        TIMESTAMPDIFF(HOUR, create_time, NOW())
    INTO view_score, like_score, fav_score, comment_score, share_score, hours_since_create
    FROM post WHERE id = post_id;
    
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
    
    -- 更新帖子热度分
    UPDATE post SET hot_score = final_score WHERE id = post_id;
END //
DELIMITER ;

-- 批量更新所有帖子热度分
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS update_all_hot_scores()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE post_id INT;
    DECLARE cur CURSOR FOR SELECT id FROM post WHERE status = 1;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    
    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO post_id;
        IF done THEN
            LEAVE read_loop;
        END IF;
        CALL update_hot_score(post_id);
    END LOOP;
    CLOSE cur;
END //
DELIMITER ;

-- 定时任务：每小时更新一次热度分（需要开启事件调度器）
-- SET GLOBAL event_scheduler = ON;
-- CREATE EVENT IF NOT EXISTS update_hot_scores_event
-- ON SCHEDULE EVERY 1 HOUR
-- DO CALL update_all_hot_scores();

SELECT '热门算法创建完成！' AS status;
