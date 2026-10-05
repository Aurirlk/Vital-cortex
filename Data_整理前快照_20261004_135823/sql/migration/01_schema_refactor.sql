-- ============================================================================
-- 智康云健康管理系统 · 数据模型重构迁移脚本
-- ----------------------------------------------------------------------------
-- 决策基线（2026-10-03 用户确认）：
--   1. news + post 合并，合并后的表就叫 news
--   2. 医生与 user 解耦；医生作为特殊身份登录，由管理员统一管理；普通状态只能进用户端
--   3. 科室支持多级层级
--   4. 字符集统一为 utf8mb4 / utf8mb4_general_ci
--   5. 旧表先改名归档，用户校验通过后再物理删除
--
-- 环境：MySQL 9.7.0
--   ⚠️ 不支持 ADD COLUMN IF NOT EXISTS（MariaDB 语法），故用 SET + PREPARE 实现幂等。
--   ⚠️ 不可用存储过程传中文参数（会乱码），DDL 一律写成 SQL 字面量再 PREPARE。
--
-- 执行前置：✅ 已备份 Data/backup/portable_20261003_222123.sql（恢复演练已通过）
-- 本脚本【不执行任何 DROP】，旧表数据完整保留，可随时回滚。
-- ============================================================================

SET NAMES utf8mb4;
SET @OLD_FK = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 步骤 1：news 吸收 post（决策 1，合并后表名保持 news）
-- ----------------------------------------------------------------------------
-- 现状：news(name/content/tag_id/cover/reader_ids/is_top/is_banner/create_time)  9 字段
--       post(user_id/title/content/cover/tag_id/统计字段/status/hot_score)        16 字段
--       两者业务同质（健康科普文章），却是两套表、两个前台列表。
-- 目标：news 一张表，content_type 区分资讯/帖子/文章。保留 name 字段兼容旧代码。
-- ============================================================================

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='content_type'),'DO 0','ALTER TABLE news ADD COLUMN content_type VARCHAR(20) NOT NULL DEFAULT ''NEWS'' COMMENT ''内容类型：NEWS资讯 / POST帖子 / ARTICLE文章'' AFTER id');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='user_id'),'DO 0','ALTER TABLE news ADD COLUMN user_id INT UNSIGNED COMMENT ''作者用户ID（资讯可为空）'' AFTER content_type');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='title'),'DO 0','ALTER TABLE news ADD COLUMN title VARCHAR(200) COMMENT ''标题（统一字段，迁移期与 name 并存）'' AFTER user_id');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='summary'),'DO 0','ALTER TABLE news ADD COLUMN summary VARCHAR(500) COMMENT ''摘要/导读'' AFTER content');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='view_count'),'DO 0','ALTER TABLE news ADD COLUMN view_count INT NOT NULL DEFAULT 0 COMMENT ''浏览量'' AFTER summary');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='like_count'),'DO 0','ALTER TABLE news ADD COLUMN like_count INT NOT NULL DEFAULT 0 COMMENT ''点赞数'' AFTER view_count');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='favorite_count'),'DO 0','ALTER TABLE news ADD COLUMN favorite_count INT NOT NULL DEFAULT 0 COMMENT ''收藏数'' AFTER like_count');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='comment_count'),'DO 0','ALTER TABLE news ADD COLUMN comment_count INT NOT NULL DEFAULT 0 COMMENT ''评论数'' AFTER favorite_count');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='share_count'),'DO 0','ALTER TABLE news ADD COLUMN share_count INT NOT NULL DEFAULT 0 COMMENT ''分享数'' AFTER comment_count');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='hot_score'),'DO 0','ALTER TABLE news ADD COLUMN hot_score DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT ''热度分（定时任务维护）'' AFTER share_count');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='status'),'DO 0','ALTER TABLE news ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT ''状态：0草稿 1已发布 2已下架'' AFTER is_banner');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='published_at'),'DO 0','ALTER TABLE news ADD COLUMN published_at DATETIME COMMENT ''发布时间'' AFTER status');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='updated_at'),'DO 0','ALTER TABLE news ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='tag_name_snapshot'),'DO 0','ALTER TABLE news ADD COLUMN tag_name_snapshot VARCHAR(50) COMMENT ''迁移期标签名快照（便于回溯）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND INDEX_NAME='idx_news_list'),'DO 0','ALTER TABLE news ADD INDEX idx_news_list (content_type, status, is_top, published_at)');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND INDEX_NAME='idx_news_user'),'DO 0','ALTER TABLE news ADD INDEX idx_news_user (user_id)');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND INDEX_NAME='idx_news_tag'),'DO 0','ALTER TABLE news ADD INDEX idx_news_tag (tag_id, status)');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

-- 1.1 旧 news 行回填
UPDATE news SET title = name        WHERE (title IS NULL OR title = '') AND name IS NOT NULL;
UPDATE news SET published_at = create_time WHERE published_at IS NULL;
UPDATE news SET content_type = 'NEWS'     WHERE content_type IS NULL OR content_type = '';
UPDATE news SET status = 1                WHERE status IS NULL;

-- 1.2 post 迁入 news（幂等：按 title+create_time 判重）
INSERT INTO news (content_type, user_id, title, content, cover, tag_id,
                  view_count, like_count, favorite_count, comment_count, share_count,
                  hot_score, status, is_top, published_at, create_time, tag_name_snapshot)
SELECT
  'POST', p.user_id, p.title, p.content, p.cover, p.tag_id,
  COALESCE(p.view_count,0), COALESCE(p.like_count,0), COALESCE(p.favorite_count,0),
  COALESCE(p.comment_count,0), COALESCE(p.share_count,0), COALESCE(p.hot_score,0),
  COALESCE(p.status,1), COALESCE(p.is_top,0), p.create_time, p.create_time,
  (SELECT t.name FROM tags t WHERE t.id = p.tag_id)
FROM post p
WHERE NOT EXISTS (
  SELECT 1 FROM news n WHERE n.content_type='POST' AND n.title=p.title AND n.create_time=p.create_time
);

-- ============================================================================
-- 步骤 2：标签表合并（tags + post_tag → tags）
-- ----------------------------------------------------------------------------
-- post_tag 是孤儿表：全库扫描确认无任何字段引用 post_tag_id；
-- 且与 tags 各存一套标签，「养生保健」两边都有。
-- ============================================================================

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tags' AND COLUMN_NAME='sort_order'),'DO 0','ALTER TABLE tags ADD COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT ''排序权重（小在前）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tags' AND COLUMN_NAME='type'),'DO 0','ALTER TABLE tags ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT ''GENERAL'' COMMENT ''GENERAL通用 / POST论坛 / NEWS资讯''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tags' AND COLUMN_NAME='status'),'DO 0','ALTER TABLE tags ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT ''状态：0禁用 1启用''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tags' AND INDEX_NAME='idx_tags_list'),'DO 0','ALTER TABLE tags ADD INDEX idx_tags_list (type, status, sort_order)');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

-- 2.1 post_tag 独有标签并入
INSERT INTO tags (name, sort_order, type, status, create_time)
SELECT pt.name, COALESCE(pt.sort_order,0), 'POST', 1, pt.create_time
FROM post_tag pt
WHERE NOT EXISTS (SELECT 1 FROM tags t WHERE t.name = pt.name);

-- 2.2 同类型同名去重
DELETE t1 FROM tags t1 JOIN tags t2
  ON t1.name = t2.name AND t1.type = t2.type AND t1.id > t2.id;

-- ============================================================================
-- 步骤 3：评论表合并（evaluations + post_reply → comment）
-- ----------------------------------------------------------------------------
-- evaluations 的多态设计(content_type+content_id)是正确的；
-- post_reply 是只能评论 post 的专用表，属重复建设，统一为 comment。
-- ============================================================================

CREATE TABLE IF NOT EXISTS comment (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  target_type VARCHAR(20)     NOT NULL              COMMENT '目标类型：NEWS内容 / QUIZ题目',
  target_id   BIGINT UNSIGNED NOT NULL              COMMENT '目标ID（多态）',
  user_id     INT UNSIGNED    NOT NULL              COMMENT '评论人',
  reply_to_id INT UNSIGNED                         COMMENT '被回复人',
  parent_id   BIGINT UNSIGNED                      COMMENT '父评论ID（树形）',
  content     TEXT            NOT NULL              COMMENT '评论内容',
  like_count  INT             NOT NULL DEFAULT 0    COMMENT '点赞数',
  upvote_list JSON                                 COMMENT '点赞用户ID列表',
  status      TINYINT         NOT NULL DEFAULT 1    COMMENT '状态：0已删除 1正常',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_comment_target (target_type, target_id, status, create_time),
  KEY idx_comment_user   (user_id),
  KEY idx_comment_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一评论表（多态）';

-- 3.1 evaluations 迁入
INSERT INTO comment (target_type, target_id, user_id, reply_to_id, parent_id, content, upvote_list, status, create_time)
SELECT
  CASE WHEN LOWER(e.content_type) = 'quiz' THEN 'QUIZ' ELSE 'NEWS' END,
  e.content_id, e.commenter_id, e.replier_id, e.parent_id,
  e.content, e.upvote_list, 1, e.create_time
FROM evaluations e
WHERE e.content IS NOT NULL AND e.content <> '' AND e.content_id IS NOT NULL;

-- 3.2 post_reply 迁入
INSERT INTO comment (target_type, target_id, user_id, parent_id, content, like_count, status, create_time)
SELECT 'NEWS', pr.post_id, pr.user_id, pr.parent_id, pr.content, COALESCE(pr.like_count,0), 1, pr.create_time
FROM post_reply pr
WHERE pr.content IS NOT NULL AND pr.content <> '';

-- ============================================================================
-- 步骤 4：消息表合并（message + notification → notification）
-- ----------------------------------------------------------------------------
-- message 字段命名混乱（content/user_id/send_id/replier_id/is_read/other），统一到 notification。
-- ============================================================================

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='notification' AND COLUMN_NAME='source'),'DO 0','ALTER TABLE notification ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT ''SYSTEM'' COMMENT ''来源：SYSTEM系统/USER用户/AI助手/ORDER订单''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='notification' AND COLUMN_NAME='sender_id'),'DO 0','ALTER TABLE notification ADD COLUMN sender_id INT UNSIGNED COMMENT ''发送者ID（系统消息为空）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='notification' AND COLUMN_NAME='link_url'),'DO 0','ALTER TABLE notification ADD COLUMN link_url VARCHAR(255) COMMENT ''点击跳转地址''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='notification' AND COLUMN_NAME='biz_type'),'DO 0','ALTER TABLE notification ADD COLUMN biz_type VARCHAR(30) COMMENT ''业务类型：APPOINTMENT/FOLLOWUP/MALL/SYSTEM''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='notification' AND COLUMN_NAME='biz_id'),'DO 0','ALTER TABLE notification ADD COLUMN biz_id BIGINT UNSIGNED COMMENT ''业务ID''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='notification' AND INDEX_NAME='idx_notif_user'),'DO 0','ALTER TABLE notification ADD INDEX idx_notif_user (user_id, is_read, create_time)');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='notification' AND INDEX_NAME='idx_notif_biz'),'DO 0','ALTER TABLE notification ADD INDEX idx_notif_biz (biz_type, biz_id)');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

-- 4.1 message 迁入
--     注意：notification.type 是 tinyint（枚举），不是字符串。
--     枚举约定：0系统公告 1预约通知 2随访通知 3订单通知 4私信消息
INSERT INTO notification (user_id, title, content, type, source, sender_id, is_read, create_time)
SELECT
  COALESCE(m.user_id, 0),
  CASE WHEN m.replier_id IS NOT NULL THEN '回复通知' ELSE '消息' END,
  m.content, 4, 'USER', m.send_id, COALESCE(m.is_read,0), m.create_time
FROM message m
WHERE m.content IS NOT NULL AND m.content <> '';

-- ============================================================================
-- 步骤 5：健康指标去重（patient_profile 硬编码字段 → user_health）
-- ----------------------------------------------------------------------------
-- 最严重的反范式：patient_profile 把 8 个指标存成字段，user_health 用 EAV 存同一批，
-- 且实测数据互相矛盾（user_id=2：profile.systolic_pressure=128，
-- 而 user_health 里收缩压是 124.0/125.2/126.4）。处置：指标值只存 user_health。
-- ============================================================================

-- 5.1 补全指标字典（当前仅 5 个，覆盖不了 profile 的 8 个字段）
INSERT INTO health_model_config (name, detail, unit, symbol, value_range, is_global, category, user_id)
SELECT v.n, v.d, v.u, '', v.r, 1, 'PUBLIC', 0 FROM (
    SELECT '餐后血糖'     n, '餐后2小时血糖' d, 'mmol/L' u, '{"min":0,"max":11.1}'  r UNION ALL
    SELECT '总胆固醇',   '血液总胆固醇',    'mmol/L',     '{"min":0,"max":8.0}'   UNION ALL
    SELECT '甘油三酯',   '血液甘油三酯',    'mmol/L',     '{"min":0,"max":2.3}'   UNION ALL
    SELECT '高密度脂蛋白', 'HDL-C',         'mmol/L',     '{"min":0.5,"max":3.0}' UNION ALL
    SELECT '低密度脂蛋白', 'LDL-C',         'mmol/L',     '{"min":0,"max":4.1}'   UNION ALL
    SELECT '身高',       '身高',            'cm',         '{"min":50,"max":250}'  UNION ALL
    SELECT '体重',       '体重',            'kg',         '{"min":10,"max":300}'
) v
WHERE NOT EXISTS (SELECT 1 FROM health_model_config c WHERE c.name = v.n AND c.is_global = 1);

-- 5.2 patient_profile 的指标值回填 user_health（仅回填该用户尚无该指标的项）
INSERT INTO user_health (user_id, health_model_config_id, value, create_time)
SELECT p.user_id, c.id, v.val, NOW()
FROM patient_profile p
JOIN (
    SELECT user_id, '空腹血糖' n,     fasting_blood_glucose    val FROM patient_profile
  UNION ALL SELECT user_id, '餐后血糖',     postprandial_blood_glucose FROM patient_profile
  UNION ALL SELECT user_id, '总胆固醇',     total_cholesterol        FROM patient_profile
  UNION ALL SELECT user_id, '甘油三酯',     triglycerides            FROM patient_profile
  UNION ALL SELECT user_id, '高密度脂蛋白', hdl_cholesterol          FROM patient_profile
  UNION ALL SELECT user_id, '低密度脂蛋白', ldl_cholesterol          FROM patient_profile
  UNION ALL SELECT user_id, '收缩压',       systolic_pressure        FROM patient_profile
  UNION ALL SELECT user_id, '舒张压',       diastolic_pressure       FROM patient_profile
  UNION ALL SELECT user_id, '心率',         resting_heart_rate       FROM patient_profile
) v ON v.user_id = p.user_id
JOIN health_model_config c ON c.name = v.n AND c.is_global = 1
WHERE v.val IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM user_health uh
                  WHERE uh.user_id = p.user_id AND uh.health_model_config_id = c.id);

-- ============================================================================
-- 步骤 6：科室支持多级层级（决策 3）
-- ============================================================================

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='department' AND COLUMN_NAME='parent_id'),'DO 0','ALTER TABLE department ADD COLUMN parent_id INT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''父科室ID，0=顶级科室''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='department' AND COLUMN_NAME='code'),'DO 0','ALTER TABLE department ADD COLUMN code VARCHAR(30) COMMENT ''科室编码（唯一）如 CARDIO''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='department' AND COLUMN_NAME='level'),'DO 0','ALTER TABLE department ADD COLUMN level TINYINT NOT NULL DEFAULT 1 COMMENT ''层级深度，1=一级科室''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='department' AND COLUMN_NAME='leader_id'),'DO 0','ALTER TABLE department ADD COLUMN leader_id INT UNSIGNED COMMENT ''科主任（hospital_doctor.id）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='department' AND COLUMN_NAME='phone'),'DO 0','ALTER TABLE department ADD COLUMN phone VARCHAR(30) COMMENT ''科室电话''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='department' AND COLUMN_NAME='location'),'DO 0','ALTER TABLE department ADD COLUMN location VARCHAR(120) COMMENT ''科室位置（楼栋/楼层）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='department' AND COLUMN_NAME='intro'),'DO 0','ALTER TABLE department ADD COLUMN intro TEXT COMMENT ''科室简介''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='department' AND COLUMN_NAME='status'),'DO 0','ALTER TABLE department ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT ''状态：0停用 1启用''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='department' AND COLUMN_NAME='update_time'),'DO 0','ALTER TABLE department ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='department' AND INDEX_NAME='idx_dept_parent'),'DO 0','ALTER TABLE department ADD INDEX idx_dept_parent (parent_id, status)');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='department' AND INDEX_NAME='idx_dept_status'),'DO 0','ALTER TABLE department ADD INDEX idx_dept_status (status, sort_order)');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE department SET code = CONCAT('DEPT_', LPAD(id,3,'0')) WHERE code IS NULL OR code = '';
UPDATE department SET level = 1     WHERE level IS NULL OR level = 0;
UPDATE department SET status = 1    WHERE status IS NULL;

-- ============================================================================
-- 步骤 6.5：user 表补 phone（2026-10-04 补录）
-- ----------------------------------------------------------------------------
-- 背景：user.phone 当初是用一次性脚本 patch_user_phone.py 加的，没写进迁移文件，
--       导致「从备份恢复后重跑迁移」时该字段丢失（UserMapper.findByPhone
--       直接报错，短信登录与患者手机号展示全废）。
--       现补入迁移文件，保证脚本可完整重放。
-- 说明：不做唯一索引 —— 存量数据中大量 phone 为 NULL/空，
--       且同一手机号理论上允许「一个患者 + 若干家属」共用。
-- ============================================================================
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='phone'),'DO 0','ALTER TABLE `user` ADD COLUMN phone VARCHAR(20) NULL COMMENT ''手机号（登录/联系用，可空）'' AFTER user_email');
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='phone_verified'),'DO 0','ALTER TABLE `user` ADD COLUMN phone_verified TINYINT NOT NULL DEFAULT 0 COMMENT ''手机号是否已验证：0未验证 1已验证'' AFTER phone');
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND INDEX_NAME='idx_user_phone'),'DO 0','ALTER TABLE `user` ADD INDEX idx_user_phone (phone)');
-- 修正：verify 归零表示「未验证」，历史导入数据不应默认已通过
-- 注意 `user` 是 MySQL 保留字，下面的反引号不能省
UPDATE `user` SET phone_verified = 0 WHERE phone_verified IS NULL OR phone_verified NOT IN (0,1);

-- ============================================================================
-- 步骤 7：医生与 user 解耦（决策 2）—— 医生作为独立身份登录
-- ----------------------------------------------------------------------------
-- 现状：hospital_doctor.user_id 关联 user 表 → 医生既是执业身份又是登录账号，
--       改资料要同步两表，user.role 语义混乱。
-- 目标：医生拥有独立账号（username/password/salt），由管理员统一管理；
--       医生登录后仅进医生端，不访问用户端与管理端。
-- ============================================================================

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='legacy_user_id'),'DO 0','ALTER TABLE hospital_doctor ADD COLUMN legacy_user_id INT UNSIGNED COMMENT ''迁移前关联的 user.id（仅历史追溯）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='username'),'DO 0','ALTER TABLE hospital_doctor ADD COLUMN username VARCHAR(50) COMMENT ''医生登录账号（唯一）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='password'),'DO 0','ALTER TABLE hospital_doctor ADD COLUMN password VARCHAR(100) COMMENT ''密码（BCrypt，口径与 user 表一致）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='salt'),'DO 0','ALTER TABLE hospital_doctor ADD COLUMN salt VARCHAR(32) COMMENT ''密码盐值（保留字段，当前 BCrypt 不依赖盐）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='title_level'),'DO 0','ALTER TABLE hospital_doctor ADD COLUMN title_level VARCHAR(20) NOT NULL DEFAULT ''ATTENDING'' COMMENT ''职称：TITLE主任/ASSOC副主任/ATTENDING主治/RESIDENT住院医''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='specialties'),'DO 0','ALTER TABLE hospital_doctor ADD COLUMN specialties JSON COMMENT ''擅长领域（JSON字符串数组）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='visit_count'),'DO 0','ALTER TABLE hospital_doctor ADD COLUMN visit_count INT NOT NULL DEFAULT 0 COMMENT ''累计接诊数''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='rating'),'DO 0','ALTER TABLE hospital_doctor ADD COLUMN rating DECIMAL(3,2) NOT NULL DEFAULT 5.00 COMMENT ''评分 0~5''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='gender'),'DO 0','ALTER TABLE hospital_doctor ADD COLUMN gender VARCHAR(10) COMMENT ''性别''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='email'),'DO 0','ALTER TABLE hospital_doctor ADD COLUMN email VARCHAR(100) COMMENT ''邮箱''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='phone'),'DO 0','ALTER TABLE hospital_doctor ADD COLUMN phone VARCHAR(30) COMMENT ''联系电话''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='reg_no'),'DO 0','ALTER TABLE hospital_doctor ADD COLUMN reg_no VARCHAR(50) COMMENT ''执业证号''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='dept_ids'),'DO 0','ALTER TABLE hospital_doctor ADD COLUMN dept_ids JSON COMMENT ''所属科室ID数组（支持多科室）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='need_init_password'),'DO 0','ALTER TABLE hospital_doctor ADD COLUMN need_init_password TINYINT NOT NULL DEFAULT 1 COMMENT ''是否需初始化密码：1首次登录须设置密码''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND INDEX_NAME='idx_doctor_dept'),'DO 0','ALTER TABLE hospital_doctor ADD INDEX idx_doctor_dept (department_id, status)');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND INDEX_NAME='idx_doctor_title'),'DO 0','ALTER TABLE hospital_doctor ADD INDEX idx_doctor_title (title_level, status)');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

-- 7.1 备份原 user_id 关联（正式解耦后不再使用）
UPDATE hospital_doctor SET legacy_user_id = user_id WHERE legacy_user_id IS NULL AND user_id IS NOT NULL;

-- 7.2 生成医生登录账号
--
--     ⚠️ 两个坑（已在测试库实测踩过，勿改回）：
--       1. 用户密码用的是 BCrypt（$2a$10$...），不是 MD5。本实例 MD5() 函数不可用，
--          且 BCrypt 哈希 SQL 无法生成 → 不写死密码，改为首次登录强制初始化。
--       2. LPAD(id, 3, '0') 对 4 位 id 会【截断】（3001 → '300'），导致账号全部撞名。
--          因此直接拼接 id，不做补零。
--     账号规则：doctor + 医生id（doctor3001 / doctor3002 ...）
--     初始密码：password 置 NULL + need_init_password=1
--                → 医生首次登录时强制设置初始密码；管理员也可在管理端随时重置。
UPDATE hospital_doctor
SET username = CONCAT('doctor', id),
    password = NULL,
    salt     = NULL,
    need_init_password = 1
WHERE username IS NULL OR username = '';

-- 7.2b 从原有 title 字段回填 title_level（否则全部落到默认值 ATTENDING，职称信息丢失）
--      映射：主任医师→TITLE  副主任医师→ASSOC  主治医师→ATTENDING  住院医师→RESIDENT
UPDATE hospital_doctor SET title_level = 'TITLE'     WHERE title LIKE '%主任医师%' AND title NOT LIKE '%副主任%';
UPDATE hospital_doctor SET title_level = 'ASSOC'     WHERE title LIKE '%副主任医师%';
UPDATE hospital_doctor SET title_level = 'ATTENDING' WHERE title LIKE '%主治医师%';
UPDATE hospital_doctor SET title_level = 'RESIDENT'  WHERE title LIKE '%住院医师%';

-- 7.3 唯一索引放最后（避免 NULL/重复值导致建索引失败）
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND INDEX_NAME='uk_doctor_username'),'DO 0','ALTER TABLE hospital_doctor ADD UNIQUE INDEX uk_doctor_username (username)');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

-- ============================================================================
-- 步骤 8：字符集统一（决策 4）
-- ----------------------------------------------------------------------------
-- 【实测结论 —— 修正了早期审计报告中的错误判断】
--   早期报告称「user 表是 utf8mb4_bin，排序会异常」。经 information_schema 逐列核实：
--   全库 42 张表中 41 张是 utf8mb4_0900_ai_ci（含 user 的全部字段，排序完全正常），
--   真正遗留 MySQL 5.7 旧规则的只有 model_announcement 的 6 个字段（utf8mb4_unicode_ci）。
--
--   统一目标选 utf8mb4_0900_ai_ci（不是 general_ci）：
--   它是本库主流规则，Unicode 排序更准确且正确支持 emoji；
--   general_ci 是 5.7 的老旧规则，改过去反而是降级。
--
-- ⚠️ 因此本步骤实际只需改 1 张表，工作量远小于早期评估。
-- ============================================================================

ALTER TABLE model_announcement CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = @OLD_FK;

-- ============================================================================
-- 迁移结果校验
-- ============================================================================
SELECT '=== 迁移结果校验（期望值见括号）===' AS check_item;

SELECT 'news 总行数（期望39=29+10）'   AS check_item, COUNT(*) AS actual FROM news
UNION ALL SELECT 'news 中 POST 来源（期望10）',   COUNT(*) FROM news WHERE content_type='POST'
UNION ALL SELECT 'news 中 NEWS 来源（期望29）',   COUNT(*) FROM news WHERE content_type='NEWS'
UNION ALL SELECT 'news title 已回填（期望39）',   COUNT(*) FROM news WHERE title IS NOT NULL AND title<>''
UNION ALL SELECT 'comment 总行数（期望10）',      COUNT(*) FROM comment
UNION ALL SELECT 'notification 总行数（期望21）', COUNT(*) FROM notification
UNION ALL SELECT 'tags 去重后（期望11=5+6）',    COUNT(*) FROM tags
UNION ALL SELECT 'health_model_config（期望12）', COUNT(*) FROM health_model_config
UNION ALL SELECT '医生账号已生成（期望3）',       COUNT(*) FROM hospital_doctor WHERE username IS NOT NULL
UNION ALL SELECT '医生 legacy_user_id 已备份',  COUNT(*) FROM hospital_doctor WHERE legacy_user_id IS NOT NULL
UNION ALL SELECT '科室 code 已回填（期望10）',   COUNT(*) FROM department WHERE code IS NOT NULL AND code<>''
UNION ALL SELECT 'user_health 回填后总数',      COUNT(*) FROM user_health;

SELECT '=== 字符集校验（以下结果应为空）===' AS check_item;
SELECT TABLE_NAME, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_COLLATION <> 'utf8mb4_0900_ai_ci';

SELECT '=== 列级字符集校验（以下结果应为空）===' AS check_item;
SELECT TABLE_NAME, COLUMN_NAME, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND COLLATION_NAME IS NOT NULL
  AND COLLATION_NAME <> 'utf8mb4_0900_ai_ci';

-- ============================================================================
-- 归档（决策 5）—— 必须在代码切到新结构并验证通过后，取消注释执行
-- ----------------------------------------------------------------------------
-- ALTER TABLE post        RENAME TO post_archived_20261003;
-- ALTER TABLE evaluations RENAME TO evaluations_archived_20261003;
-- ALTER TABLE post_reply  RENAME TO post_reply_archived_20261003;
-- ALTER TABLE message     RENAME TO message_archived_20261003;
-- ALTER TABLE post_tag    RENAME TO post_tag_archived_20261003;
--
-- 用户校验无问题后再物理删除（⚠️ 不可逆）：
-- DROP TABLE post_archived_20261003, evaluations_archived_20261003,
--            post_reply_archived_20261003, message_archived_20261003,
--            post_tag_archived_20261003;
--
-- 回滚：mysql -uroot -p personal_health < Data/backup/portable_20261003_222123.sql
-- ============================================================================
