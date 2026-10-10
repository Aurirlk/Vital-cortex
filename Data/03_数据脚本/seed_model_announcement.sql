-- =============================================================================
-- seed_model_announcement.sql
-- 目的：给 model_announcement 补种子数据
-- 依据：docs/数据资产与AI能力整改方案.md 阶段 0-A / A6
-- 制定：2026-10-06
-- =============================================================================
--
-- 【问题】
--   model_announcement 是 0 行空表，但代码完整在线：
--     - 后端 AnnouncementController 提供 /ai/announcement/active
--     - 前端 ModelBanner.vue 在挂载时调用它并渲染横幅
--   代码两端都就绪、唯独没有数据 → 首页横幅永远不显示。
--
--   ⚠️ 这是「空表 ≠ 无用表」的典型：表是空的，但功能链路是完整的。
--      8/19 以来多轮"清理空表"的讨论中它多次被误判为可删除对象。
--
-- 【为什么用 INSERT IGNORE】
--   uk_model_key 唯一键保证幂等，重复执行不会产生重复行，也不会覆盖
--   运营人员后续在管理端修改过的文案（IGNORE 会跳过已存在的 key）。
--
-- 【数据来源】
--   model_key / model_name 取自 ai_config 实际配置：
--     provider = deepseek
--     model          = deepseek-v4-flash（主对话）
--     reasoner_model = deepseek-v4-pro  （深度推理）
--   ⚠️ 当前 ai_config.api_key 为空 → 实际调用会失败。
--      本脚本只补公告文案，不解决凭据问题。
--
-- =============================================================================

-- 显式声明连接编码，避免依赖客户端默认值（否则中文 INSERT/列注释会报 ERROR 1406 或乱码）
SET NAMES utf8mb4;

USE personal_health;

-- 主对话模型（当前展示给用户的那条）
INSERT IGNORE INTO model_announcement
    (model_key, model_name, title, content, bg_color, icon, is_online, is_active, sort_order)
VALUES
    ('deepseek-v4-flash', '智康云健康助手',
     '健康助手已接入 DeepSeek 大模型',
     '支持健康问答、指标解读、用药咨询与就医建议，回答仅供参考，请以医生诊断为准。',
     '#0050cb', 'MagicStick', 1, 1, 10);

-- 深度推理模型（备选，默认不展示）
INSERT IGNORE INTO model_announcement
    (model_key, model_name, title, content, bg_color, icon, is_online, is_active, sort_order)
VALUES
    ('deepseek-v4-pro', '智康云深度分析',
     '深度推理模型可用于体检报告解读',
     '针对复杂体检报告与多指标联合分析，推理更充分，响应略慢。',
     '#0a9f6d', 'DataAnalysis', 1, 0, 20);

-- -----------------------------------------------------------------------------
-- 校验：应看到 2 行，且 is_active=1 的恰好 1 行
-- -----------------------------------------------------------------------------
SELECT
    COUNT(*)                    AS total_rows,
    SUM(is_active = 1)          AS active_rows,
    SUM(is_online = 1)          AS online_rows
FROM model_announcement;

SELECT model_key, model_name, title, is_online, is_active
FROM model_announcement ORDER BY sort_order;
