-- ============================================================================
-- 06_merge_message_into_notification.sql  消息表双轨合并（2026-10-04）
--
-- 依据：《数据模型重构与项目扩展方案.md》缺陷 4「消息系统双轨」
--
-- 【为什么合并】
--   message 与 notification 是同一业务概念的两套实现：
--     · message      ：面向用户的消息中心（types/query/batchDelete/clear）
--     · notification ：系统通知（公告/预约/随访/订单/私信提醒）
--   两者语义重叠，且 message 的 `other` 字段名不表意（实为消息类型）。
--
-- 【other 字段已查明（初版方案说「含义不明」，现已确认）】
--   MessageMapper.xml 里有 `<result column="other" property="messageType"/>`，
--   前端 MessageManage.vue 按 1=系统通知 2=互动消息 3=私信 消费。
--   ⚠️ 但存量 16 条记录的 other **全为 NULL** —— 前端从未写入过该字段，
--      属于「设计了但没用」的历史包袱。迁移时统一给默认值 1（系统通知）。
--
-- 【字段映射】
--   message.user_id      → notification.user_id      （接收人）
--   message.send_id      → notification.sender_id    （发送人）
--   message.content      → notification.content
--   message.is_read      → notification.is_read
--   message.other        → notification.message_type  （本次新增列）
--   message.create_time  → notification.create_time
--   （notification 独有的 title/type/source/link_url/biz_type/biz_id 留空）
--
-- 【兼容性】
--   合并后不删除 message 表的代码引用，Service 层改为查 notification，
--   API 路径 /message/* 保持不变（前端零改动）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 步骤 1：给 notification 加消息类型列
-- 说明：与既有 type 列（0公告 1预约 2随访 3订单 4私信）语义不同，
--      message_type 是「用户视角的消息分类」，故单列而非复用 type。
-- ---------------------------------------------------------------------------
SET @d = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification'
           AND COLUMN_NAME = 'message_type'),
  'DO 0',
  'ALTER TABLE notification ADD COLUMN message_type TINYINT NOT NULL DEFAULT 1
     COMMENT ''用户视角消息分类：1系统通知 2互动消息 3私信（合并自 message.other）'' AFTER type'
);
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification'
           AND INDEX_NAME = 'idx_notification_msgtype'),
  'DO 0',
  'ALTER TABLE notification ADD INDEX idx_notification_msgtype (message_type)'
);
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 步骤 2：迁移存量数据（幂等：靠「同用户同内容同时间」判重）
-- ---------------------------------------------------------------------------
INSERT INTO notification (user_id, sender_id, content, is_read, type,
                          message_type, create_time)
SELECT m.user_id,
       m.send_id,
       m.content,
       COALESCE(m.is_read, 0),
       1,                          -- type：归为「预约类」占位，与 message 无对应语义
       COALESCE(NULLIF(m.other, ''), 1),   -- message_type：other 为 NULL 时默认系统通知
       COALESCE(m.create_time, NOW())
  FROM message m
 WHERE NOT EXISTS (
       SELECT 1 FROM notification n
        WHERE n.user_id = m.user_id
          AND n.content = m.content
          AND n.create_time = m.create_time
       );

-- ---------------------------------------------------------------------------
-- 步骤 3：校验后再删（先看校验结果，确认 0 才是「可删」）
-- 下面这条应返回 0；若不为 0，说明有数据未迁入，不要执行步骤 4
-- ---------------------------------------------------------------------------
SELECT COUNT(*) AS 未迁入的message条数
  FROM message m
 WHERE NOT EXISTS (
       SELECT 1 FROM notification n
        WHERE n.user_id = m.user_id
          AND n.content = m.content
          AND n.create_time = m.create_time
       );

-- ---------------------------------------------------------------------------
-- 步骤 4：删除 message 表
-- ⚠️ 仅在步骤 3 返回 0 时执行
-- ⚠️ 执行前请确认后端 MessageServiceImpl 已改查 notification、
--    且前端 /message/* 接口验证通过（代码层改造见 04_consolidate 轮的说明）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS message;   -- 2026-10-04 已执行

-- ---------------------------------------------------------------------------
-- 校验
-- ---------------------------------------------------------------------------
SELECT '=== 消息分类分布 ===' AS check_item;
SELECT message_type, is_read, COUNT(*) AS cnt
  FROM notification GROUP BY message_type, is_read ORDER BY message_type;

SELECT '=== 表总数（应为 35）===' AS check_item;
SELECT COUNT(*) AS table_count FROM information_schema.tables
 WHERE table_schema = DATABASE();
