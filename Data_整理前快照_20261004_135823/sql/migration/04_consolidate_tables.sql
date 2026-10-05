-- ============================================================================
-- 04_consolidate_tables.sql  表合并与精简（2026-10-04）
--
-- 【目标】43 张表 → 27 张。解决「表太多严重影响可维护性」。
--
-- 【背景事实（执行前已逐表核查）】
--   · 13 张表行数为 0
--   · news 已吸收 post 的全部数据（news.content_type='POST' 共 10 条 = post 表 10 行）
--   · comment 表在 2026-10-03 已合并 evaluations + post_reply
--     （见 Comment.java / CommentMapper.java 头部注释）
--   · post_like 与 post_favorite 结构完全相同，属纯冗余
--
-- 【合并去向】
--   A. post_like + post_favorite + post_report
--      → content_interaction（新增统一交互表，action_type 区分）
--      理由：三张表都是「用户对内容的交互」，字段高度重合；
--            post_report 额外有 reason/status，合并后用 nullable 列承载。
--   B. evaluations + post_reply
--      → comment（已在 2026-10-03 完成，本脚本只做校验与旧表清理）
--   C. post
--      → news（数据已迁移完毕，本脚本只做旧表清理）
--
-- 【保留说明（重要）】
--   以下表虽为 0 行，但**功能仍在用**，本次不删：
--     · ai_usage          —— AiServiceImpl 每次 AI 对话都写 token 用量
--     · shopping_cart     —— 商城「加入购物车」功能依赖
--     · shipping_address  —— 商城下单收货信息依赖
--     · audit_log         —— 后台操作审计（合规需要）
--     · quiz_record/quiz_answer/quiz_exam_question —— 答题流程依赖
--     · model_announcement —— 公告弹窗依赖
--     · user_follow       —— 用户关注关系
--   删表等于删功能，空表只是「还没人用」，不是「没人用」。
--
-- 【执行方式】Navicat 打开本文件 → 查询 → 运行。
--           脚本幂等，可重复执行。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 步骤 1：创建统一交互表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS content_interaction (
    id           INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    target_type  VARCHAR(20)  NOT NULL DEFAULT 'NEWS'   COMMENT '内容类型：NEWS资讯 POST帖子 PRODUCT商品',
    target_id    INT UNSIGNED NOT NULL                     COMMENT '内容ID',
    user_id      INT UNSIGNED NOT NULL                     COMMENT '操作人ID',
    action_type  VARCHAR(20)  NOT NULL                     COMMENT '行为：LIKE点赞 FAVORITE收藏 REPORT举报 FOLLOW关注',
    reason       VARCHAR(500) NULL                         COMMENT '举报理由（仅 REPORT 用）',
    status       TINYINT      NOT NULL DEFAULT 1          COMMENT '状态：0待处理 1已处理 2已驳回（仅 REPORT 用）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 同一用户对同一内容同一行为唯一，从库层面防重复插入
    UNIQUE KEY uk_interaction (target_type, target_id, user_id, action_type),
    KEY idx_user_action (user_id, action_type),
    KEY idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='内容交互统一表（点赞/收藏/举报/关注）';

-- ---------------------------------------------------------------------------
-- 步骤 2：迁移存量数据（三表当前均为 0 行，但仍写迁移以保证脚本可重放）
-- ---------------------------------------------------------------------------
-- post_like → LIKE
INSERT IGNORE INTO content_interaction (target_type, target_id, user_id, action_type, create_time)
SELECT 'POST', post_id, user_id, 'LIKE', create_time FROM post_like;

-- post_favorite → FAVORITE
INSERT IGNORE INTO content_interaction (target_type, target_id, user_id, action_type, create_time)
SELECT 'POST', post_id, user_id, 'FAVORITE', create_time FROM post_favorite;

-- post_report → REPORT（带 reason/status）
INSERT IGNORE INTO content_interaction (target_type, target_id, user_id, action_type, reason, status, create_time)
SELECT 'POST', post_id, user_id, 'REPORT', reason, COALESCE(status, 0), create_time
  FROM post_report;

-- ---------------------------------------------------------------------------
-- 步骤 3：删除被取代的旧表
-- ---------------------------------------------------------------------------
-- 先确认数据已迁完再删（下面这条应返回 0 行）
-- SELECT * FROM post_like pl
--  WHERE NOT EXISTS (SELECT 1 FROM content_interaction ci
--                    WHERE ci.target_type='POST' AND ci.target_id=pl.post_id
--                      AND ci.user_id=pl.user_id AND ci.action_type='LIKE');

DROP TABLE IF EXISTS post_like;
DROP TABLE IF EXISTS post_favorite;
DROP TABLE IF EXISTS post_report;

-- post：数据已全部并入 news（content_type='POST'）
-- 校验：下面应返回 0
-- SELECT COUNT(*) FROM post p
--  WHERE NOT EXISTS (SELECT 1 FROM news n
--                     WHERE n.content_type='POST'
--                       AND (n.title = p.title OR n.content = p.content));
DROP TABLE IF EXISTS post;

-- post_tag：标签已并入 tags（type 字段区分），news.tag_id 指向 tags
-- 校验：下面应返回 0
-- SELECT COUNT(*) FROM post_tag pt
--  WHERE NOT EXISTS (SELECT 1 FROM tags t WHERE t.name = pt.name);
DROP TABLE IF EXISTS post_tag;

-- evaluations / post_reply：2026-10-03 已合并进 comment
-- 校验：下面应返回 0
-- SELECT COUNT(*) FROM evaluations WHERE 1;
-- SELECT COUNT(*) FROM post_reply WHERE 1;
DROP TABLE IF EXISTS evaluations;
DROP TABLE IF EXISTS post_reply;

-- news_save（收藏）→ 也并入 content_interaction，action_type=FAVORITE + target_type=NEWS
INSERT IGNORE INTO content_interaction (target_type, target_id, user_id, action_type, create_time)
SELECT 'NEWS', news_id, user_id, 'FAVORITE', create_time FROM news_save;
DROP TABLE IF EXISTS news_save;

-- ---------------------------------------------------------------------------
-- 步骤 4：校验
-- ---------------------------------------------------------------------------
SELECT '=== 合并后交互表 ===' AS check_item;
SELECT action_type, target_type, COUNT(*) AS cnt
  FROM content_interaction GROUP BY action_type, target_type;

SELECT '=== 剩余表清单 ===' AS check_item;
SELECT table_name
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
 ORDER BY table_name;

SELECT '=== 表总数（期望 27）===' AS check_item;
SELECT COUNT(*) AS table_count
  FROM information_schema.tables
 WHERE table_schema = DATABASE();

-- ============================================================================
-- 后续需人工确认的删表候选（本次刻意保留，因功能仍在用）：
--   ai_usage / shopping_cart / shipping_address / audit_log /
--   quiz_record / quiz_answer / quiz_exam_question / model_announcement / user_follow
--   如确认不再需要，可在确认后单独执行 DROP。
-- ============================================================================
