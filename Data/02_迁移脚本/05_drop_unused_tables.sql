-- ============================================================================
-- 05_drop_unused_tables.sql  空表清理（2026-10-04）
--
-- 【前置】必须先执行 04_consolidate_tables.sql
--
-- 【重要更正】
--   初稿曾把 shopping_cart / shipping_address / user_follow 列入「可安全删除」，
--   实际核查后发现这三张表**都有完整可用的 Mapper**（购物车、收货地址、关注），
--   0 行只代表「还没人用」，不代表「没人用」—— 删表等于删功能。
--   已全部移入下方「待确认」区，默认保留。
--
-- 【筛选原则】删表必须同时满足：
--   ① 当前 0 行  ② 后端无 Mapper  ③ 前端无接口调用
--   ④ 该功能已确认下线（人工确认，非技术判断）
--
-- 当前结论：**43 张表已合并至 34 张，本脚本暂无可安全删除的表。**
-- 保留 34 张是合理的 —— 每一张都对应一个真实功能或已被合并表替代。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 已确认可删除（当前为空，本脚本无操作）
-- 说明：04 已删除 post / post_tag / evaluations / post_reply /
--       post_like / post_favorite / post_report / news_save（8 张）
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 待人工确认（0 行但功能在线，删表=删功能，默认保留）
-- ---------------------------------------------------------------------------
-- DROP TABLE IF EXISTS shopping_cart;        -- 购物车：ShoppingCartMapper 完整可用
-- DROP TABLE IF EXISTS shipping_address;     -- 收货地址：ShippingAddressMapper 完整可用
-- DROP TABLE IF EXISTS user_follow;          -- 关注：UserFollowMapper 完整可用
-- DROP TABLE IF EXISTS ai_usage;             -- AI token 用量：AiServiceImpl 每次对话写入
-- DROP TABLE IF EXISTS audit_log;            -- 操作审计：合规要求
-- DROP TABLE IF EXISTS model_announcement;  -- 公告弹窗
-- DROP TABLE IF EXISTS quiz_record;          -- 答题记录
-- DROP TABLE IF EXISTS quiz_answer;          -- 答题答案
-- DROP TABLE IF EXISTS quiz_exam_question;   -- 试卷题目关联

-- ---------------------------------------------------------------------------
-- 校验
-- ---------------------------------------------------------------------------
SELECT '=== 剩余表清单 ===' AS check_item;
SELECT table_name FROM information_schema.tables
 WHERE table_schema = DATABASE() ORDER BY table_name;

SELECT '=== 表总数（合并前 43）===' AS check_item;
SELECT COUNT(*) AS table_count FROM information_schema.tables
 WHERE table_schema = DATABASE();
