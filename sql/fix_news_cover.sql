-- ============================================================
-- 资讯封面批量填充 SQL（可选，后端已做兜底，此脚本用于彻底修复数据库）
-- 用法：先跑 UPDATE，再跑 SELECT 验证
-- ============================================================

-- 1. 批量填充 cover 字段（随机从图片池抽取）
-- 先创建临时序号表
DROP TEMPORARY TABLE IF EXISTS temp_cover_seq;
CREATE TEMPORARY TABLE temp_cover_seq (
    seq INT AUTO_INCREMENT PRIMARY KEY,
    filename VARCHAR(255)
);

-- 插入可用图片文件名
INSERT INTO temp_cover_seq (filename) VALUES
('pic_1.jpg'), ('pic_2.jpg'), ('pic_3.jpg'), ('pic_4.jpg'), ('pic_5.jpg'),
('pic_6.jpg'), ('pic_7.jpg'), ('pic_8.jpg'), ('pic_9.jpg'), ('pic_10.jpg'),
('体重.png'), ('心率.png'), ('血压.png'), ('身高.png'), ('步数.png'),
('转氨酶.png'), ('静谧.png'), ('熊猫.png'), ('猫头鹰.png');

-- 更新 news 表 cover 为空或 null 的记录
-- 注意：修改 baseUrl 为你的实际地址
SET @baseUrl = 'http://localhost:21090/api/personal-health/v1.0/file/getFile?fileName=';

UPDATE news n
JOIN (
    SELECT 
        n2.id,
        (SELECT filename FROM temp_cover_seq ORDER BY RAND() LIMIT 1) AS new_cover
    FROM news n2
    WHERE n2.cover IS NULL OR n2.cover = ''
) t ON n.id = t.id
SET n.cover = CONCAT(@baseUrl, t.new_cover);

-- 2. 验证填充结果
SELECT COUNT(*) AS total, 
       SUM(CASE WHEN cover IS NULL OR cover = '' THEN 1 ELSE 0 END) AS still_empty
FROM news;
