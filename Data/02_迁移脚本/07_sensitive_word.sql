-- ============================================================
-- 07_sensitive_word.sql —— 补齐「敏感词」表
--
-- 背景（2026-10-10 修复）：
--   SensitiveWordMapper.xml 中有 save / batchDelete / queryAll /
--   queryAllWords 四条语句，全部操作 `sensitive_word` 表，但
--   Data/ 下<b>没有任何一个 .sql 创建过该表</b>（全库 grep 为 0）。
--   后果：内容审核相关的敏感词读写一律抛
--   `Table 'personal_health.sensitive_word' doesn't exist`。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `sensitive_word` (
  `id`          INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `word`        VARCHAR(100) NOT NULL COMMENT '敏感词',
  `status`      TINYINT(1)   DEFAULT 1 COMMENT '状态(1:启用;0:停用)',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_sensitive_word_status` (`status`),
  KEY `idx_sensitive_word_word` (`word`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin COMMENT = '敏感词表';

SET FOREIGN_KEY_CHECKS = 1;
