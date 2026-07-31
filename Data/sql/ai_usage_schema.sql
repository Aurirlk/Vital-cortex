-- ============================================================
-- 智康云 - AI Token 用量成本表（ENG-10）
-- 用于统计各用户/AI 场景的 token 消耗与成本核算
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id INT DEFAULT NULL COMMENT '用户ID（可为空，非登录态调用）',
    scene VARCHAR(50) DEFAULT 'chat' COMMENT '场景：chat/stream/keyword/websearch/rag_eval',
    model VARCHAR(100) DEFAULT '' COMMENT '模型名',
    prompt_tokens INT DEFAULT 0 COMMENT '输入token数',
    completion_tokens INT DEFAULT 0 COMMENT '输出token数',
    total_tokens INT DEFAULT 0 COMMENT '总token数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_ai_usage_user (user_id),
    KEY idx_ai_usage_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI token 用量记录';
