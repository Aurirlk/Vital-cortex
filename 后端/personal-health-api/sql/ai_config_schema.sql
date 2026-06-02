-- AI配置表（持久化存储API Key等配置）
CREATE TABLE IF NOT EXISTS `ai_config` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `config_key` VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
  `config_value` TEXT COMMENT '配置值（加密存储）',
  `description` VARCHAR(255) DEFAULT '' COMMENT '配置描述',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI配置表';

-- 插入默认配置项
INSERT INTO `ai_config` (`config_key`, `config_value`, `description`) VALUES
('provider', 'deepseek', 'AI厂商'),
('api_key', '', '普通对话API Key（加密）'),
('api_url', 'https://api.deepseek.com/v1/chat/completions', '普通对话API地址'),
('model', 'deepseek-v4-flash', '普通对话模型'),
('reasoner_api_key', '', '深度思考API Key（加密）'),
('reasoner_api_url', 'https://api.deepseek.com/v1/chat/completions', '深度思考API地址'),
('reasoner_model', 'deepseek-v4-pro', '深度思考模型'),
('embedding_api_key', '', 'Embedding API Key（加密）'),
('embedding_api_url', 'https://api.deepseek.com/v1/embeddings', 'Embedding API地址'),
('embedding_model', 'text-embedding-3-small', 'Embedding模型'),
('web_search_enabled', 'true', '联网搜索是否启用'),
('web_search_provider', 'auto', '联网搜索引擎'),
('connect_timeout', '30000', '连接超时(ms)'),
('read_timeout', '60000', '读取超时(ms)'),
('max_tokens', '4096', '最大Token数'),
('max_history_rounds', '10', '最大历史轮数');
