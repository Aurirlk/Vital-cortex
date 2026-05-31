-- ============================================================
-- 新闻数据导入脚本
-- 从 personal_health.sql 中提取的新闻数据
-- ============================================================

SET NAMES utf8mb4;

-- 清空现有新闻数据
TRUNCATE TABLE `news`;

-- 导入新闻数据
INSERT INTO `news` (`id`, `name`, `content`, `tag_id`, `cover`, `reader_ids`, `is_top`, `create_time`) VALUES
(1, '成年男性平均身高接近170，女性158', '<p>中国人长高了！</p><p>成年男性平均身高接近170，女性158</p><p>报告显示，中国居民体格发育与营养不足问题持续改善，城乡差异逐步缩小。</p><p>中国成人平均身高继续增长，18-44岁男性和女性的平均身高分别为169.7厘米和158.0厘米。</p>', 2, 'http://localhost:21090/api/personal-health/v1.0/file/getFile?fileName=f0890964.jpg', NULL, 0, '2024-07-11 15:40:04'),

(2, '疾病，就像一位潜藏在暗处的狡猾猎手', '<p>健康问题越来越引起重视。</p><p><strong>中国人的健康数据，触目惊心！</strong></p><p>高血压：1.6-1.7亿人</p><p>高血脂：1亿多人</p><p>糖尿病患者：1240万人</p><p>超重或者肥胖症：7000万-2亿人</p><p>这些冰冷的数字背后，是无数家庭的痛苦和无奈。</p>', 4, 'http://localhost:21090/api/personal-health/v1.0/file/getFile?fileName=127ddf0pic_4.jpg', NULL, 1, '2024-07-12 10:00:00'),

(3, '身体健康的十项指标，你知道几个？', '<p>健康的准确定义应该是身体健康，心理健康，具有良好的社会适应性。</p><h4>1.饮食指标</h4><p>成人每天应该吃500克左右的食物。</p><h4>2.体重指标</h4><p>体重并不是越轻越好。</p><h4>3.体温指标</h4><p>正常人的体温应该在36℃～37℃。</p>', 3, 'http://localhost:21090/api/personal-health/v1.0/file/getFile?fileName=5421cf7pic_3.jpg', NULL, 0, '2024-07-13 14:30:00'),

(4, '健康5大准则，做到2个就很厉害', '<p>打败一个年轻人，只需要一张体检报告单。</p><p>不少疾病都在呈现年轻化的趋势。</p><p>骨质疏松、中风、二型糖尿病等等。</p><p>疾病不是源于人身体结构上的不足或缺陷，而是源于生活方式带来的自我损伤。</p>', 5, 'http://localhost:21090/api/personal-health/v1.0/file/getFile?fileName=20f9c4cpic_4.jpg', NULL, 1, '2024-07-14 09:00:00'),

(5, '10条身体健康的"金标准"', '<p>一个人的身体是否健康，各项身体常见指标指数是最直接的判断。</p><p><strong>血压标准：</strong>收缩压＜120mmHg，舒张压＜80mmHg</p><p><strong>血糖标准：</strong>空腹血糖3.9-6.1mmol/L</p><p><strong>血脂标准：</strong>总胆固醇＜5.2mmol/L</p>', 3, 'http://localhost:21090/api/personal-health/v1.0/file/getFile?fileName=9edc2e89.jpg', NULL, 0, '2024-07-14 14:34:58'),

(6, '睡眠质量对健康的影响', '<p>睡眠是人体恢复的重要过程。</p><p>成年人每天需要7-8小时睡眠。</p><p>良好的睡眠可以：</p><p>1. 增强免疫力</p><p>2. 改善记忆力</p><p>3. 促进新陈代谢</p>', 5, 'http://localhost:21090/api/personal-health/v1.0/file/getFile?fileName=722fdbepic_6.jpg', NULL, 0, '2024-07-15 16:00:00'),

(7, '快节奏的现代生活与健康', '<p>快节奏的现代生活，如同一只无形的手，推动着我们不断前行。</p><p>作息不规律、高油高盐的饮食等，悄然侵蚀着我们的健康。</p><p>我们需要的，不仅仅是医疗技术的日新月异，更需要的是健康理念的深入人心。</p>', 1, 'http://localhost:21090/api/personal-health/v1.0/file/getFile?fileName=a1cad98pic_8.jpg', NULL, 0, '2024-07-16 11:00:00'),

(8, '我们对健康的重视程度仍然远远不够', '<p>在过去，随着中国经济的高速发展，许多人的生活水平和物质条件得到了很大的改善。</p><p>然而，这种快速发展的背后，却隐藏着一些被忽视的问题，其中较为突出的就是健康问题。</p><p>健康对于每个人来说都是刻不容缓的。</p>', 2, 'http://localhost:21090/api/personal-health/v1.0/file/getFile?fileName=605c5f9pic_7.jpg', NULL, 0, '2024-07-17 09:30:00'),

(9, '慢性疾病的预防与管理', '<p>慢性疾病是现代社会的主要健康威胁。</p><p>常见慢性病包括：</p><p>1. 高血压</p><p>2. 糖尿病</p><p>3. 心脏病</p><p>预防措施：健康饮食、适量运动、定期体检</p>', 4, 'http://localhost:21090/api/personal-health/v1.0/file/getFile?fileName=8f50f65pic_7.jpg', NULL, 0, '2024-07-18 14:00:00'),

(10, '如何建立健康的生活方式', '<p>建立健康的生活方式需要：</p><p>1. 均衡饮食</p><p>2. 规律运动</p><p>3. 充足睡眠</p><p>4. 良好心态</p><p>5. 定期体检</p><p>从现在开始，为健康投资！</p>', 1, 'http://localhost:21090/api/personal-health/v1.0/file/getFile?fileName=227857apic_10.jpg', NULL, 0, '2024-07-19 10:00:00'),

(11, '保健养生从现在做起', '<p>为了家人，为了亲朋，保健养生从现在做起！</p><p>养生小贴士：</p><p>1. 多喝水</p><p>2. 少熬夜</p><p>3. 多运动</p><p>4. 保持好心情</p>', 5, 'http://localhost:21090/api/personal-health/v1.0/file/getFile?fileName=64e63ebpic_10.jpg', NULL, 0, '2024-07-20 15:00:00'),

(12, '健康饮食的重要性', '<p>健康饮食是身体健康的基础。</p><p>建议：</p><p>1. 多吃蔬菜水果</p><p>2. 控制盐糖摄入</p><p>3. 适量蛋白质</p><p>4. 少吃加工食品</p><p>5. 规律饮食时间</p>', 1, 'http://localhost:21090/api/personal-health/v1.0/file/getFile?fileName=d829e54pic_1.jpg', NULL, 0, '2024-07-21 11:00:00');

-- 验证导入结果
SELECT COUNT(*) AS 新闻数量 FROM news;
SELECT '新闻数据导入完成！' AS status;
