-- ============================================================
-- 【已归档 2026-10-12】本脚本从未执行过，其前提也已不复存在。
--
-- 归档原因：
--   1. 脚本原本要修的是 `SensitiveWordMapper.xml` 操作不存在的表。
--      但该 Mapper 文件（及其同名 Java 接口）**早已从代码库删除** ——
--      2026-10-12 全仓检索 `SensitiveWord` / `sensitive_word`，
--      src/main 下命中数为 0，只剩 MapperTableConsistencyTest 的历史注释。
--   2. `sensitive_word` 表不在权威结构基线
--      `Data/04_结构基线/数据表结构基线-20261003.md` 中，
--      即该能力从未落地（属该文档所述 "B 类：从未落地的纸面能力"）。
--   3. 后端也不存在任何用户可管理的敏感词子系统：
--      OutputValidator 用硬编码正则做**剂量合规**提示（\d+mg 等），
--      SystemConfigServiceImpl 的 sensitive 指**掩码配置值**（API Key），
--      两者与「敏感词库」无关。
--   4. 前端 admin/AuditManage.vue 的「敏感词管理」Tab 调用的
--      audit/sensitive-word/* 三个接口后端全无，是必然 404 的死 UI，
--      该 Tab 已随之删除。
--
-- 保留本文件仅为留痕：日后若真要建敏感词能力，可从这里的建表语句起步。
-- 在此之前请勿把它当成待执行的迁移脚本。
--
-- ------------------------------------------------------------
-- 原文件内容（Data/02_迁移脚本/07_sensitive_word.sql）：
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
