-- 模型公告/横幅通知表
-- 用于管理后台切换模型时，C端展示通知横幅

CREATE TABLE IF NOT EXISTS model_announcement (
  id INT PRIMARY KEY AUTO_INCREMENT,
  model_key VARCHAR(50) NOT NULL COMMENT '模型标识（如 zhikangyun-local, deepseek 等）',
  model_name VARCHAR(100) NOT NULL COMMENT '模型展示名称',
  title VARCHAR(200) NOT NULL COMMENT '横幅标题（如 "本草大模型已上线"）',
  content VARCHAR(500) COMMENT '横幅描述文字',
  bg_color VARCHAR(20) DEFAULT '#409EFF' COMMENT '横幅背景色',
  icon VARCHAR(50) DEFAULT 'MagicStick' COMMENT '图标名',
  is_online TINYINT DEFAULT 0 COMMENT '是否上线 0=下线 1=上线',
  is_active TINYINT DEFAULT 0 COMMENT '是否当前展示 0=否 1=是',
  sort_order INT DEFAULT 0 COMMENT '排序',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_model_key (model_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型公告/横幅通知表';

-- 插入默认数据：本地微调模型
INSERT INTO model_announcement (model_key, model_name, title, content, bg_color, icon, is_online, is_active, sort_order)
VALUES ('zhikangyun-local', '智康云本地医疗模型', '本草大模型已上线', '基于Qwen2.5-7B微调的医疗领域模型，专业服务您的健康', '#67C23A', 'MagicStick', 1, 1, 1)
ON DUPLICATE KEY UPDATE title = VALUES(title);
