-- ============================================================================
-- 03_align_demo_appointments.sql（可选，仅用于本地演示）
--
-- 【发现的问题】
--   迁移时数据来源不一致，导致医生端「点进患者 → 什么都看不到」：
--     · appointment.patient_id  → 1006~1010（测试用户，无 patient_profile）
--     · patient_profile / user_health 的完整数据 → user_id 2~8
--   医生端 90% 的接诊记录点进去都会命中「该患者尚未建立健康档案」空页。
--
-- 【本脚本做什么】
--   把预约的 patient_id 重指向有完整档案的真实用户，并同步 patient_profile，
--   同时补齐 visit_record（就诊记录表原本是空的，医生端「历史就诊」无数据可看）。
--
-- 【要不要执行】
--   · 本地演示 / 答辩展示 → 建议执行
--   · 生产环境 → **不要执行**，本脚本仅造演示数据
--
-- 幂等：可重复执行（先按固定映射重排，不做增量追加）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 预约重指向：1006~1010 → 3~7（张小明/李莉/王强/陈静/赵磊，均有完整档案）
--    保持原 doctor_id、日期、时段、序号不变，只换患者
-- ---------------------------------------------------------------------------
UPDATE appointment SET patient_id = 3 WHERE id = 5001;   -- 测试用户1 → 张小明
UPDATE appointment SET patient_id = 4 WHERE id = 5002;   -- 测试用户2 → 李莉
UPDATE appointment SET patient_id = 5 WHERE id = 5003;   -- 测试用户3 → 王强
UPDATE appointment SET patient_id = 6 WHERE id = 5004;   -- 测试用户4 → 陈静
UPDATE appointment SET patient_id = 7 WHERE id = 5005;   -- 测试用户5 → 赵磊

-- ---------------------------------------------------------------------------
-- 2) 补齐主诉内容（原本多数为 NULL，医生端列表「主诉」列全是「未填写」）
-- ---------------------------------------------------------------------------
UPDATE appointment SET symptom_description = '头晕、血压偏高，近一周晨起明显'
 WHERE id = 5001;
UPDATE appointment SET symptom_description = '体检发现血糖偏高，家族有糖尿病史'
 WHERE id = 5002;
UPDATE appointment SET symptom_description = '反复上腹隐痛，肝功能指标异常'
 WHERE id = 5003;
UPDATE appointment SET symptom_description = '绝经后骨密度下降，需评估干预方案'
 WHERE id = 5004;
UPDATE appointment SET symptom_description = '血脂多项升高，询问饮食调理'
 WHERE id = 5005;

-- ---------------------------------------------------------------------------
-- 3) 造 3 条历史就诊记录（visit_record 原本为空）
--    appointment_id 有 UNIQUE 约束，一个预约只能有一条，故挑 3 条不同的预约
--    刻意覆盖「同一医生复诊」与「换医生就诊」两种情形，便于验证时间线展示
-- ---------------------------------------------------------------------------
INSERT INTO visit_record
  (appointment_id, patient_id, doctor_id, chief_complaint, present_illness,
   diagnosis, prescription, examination_results, follow_up_plan, create_time, update_time)
VALUES
  (5001, 3, 3001,
   '头晕、血压偏高',
   '患者自述近一周晨起头晕，伴轻微心悸，无胸痛、无视物模糊。家中自测血压最高 150/95 mmHg。',
   '原发性高血压 1 级（需复测确认）',
   '苯磺酸氨氯地平片 5mg 口服 每日一次\n低盐饮食（<5g/日）\n每日快走 30 分钟',
   '诊室血压 148/92 mmHg；心率 82 次/分；BMI 23.7',
   '2 周后复测血压并记录家庭自测值；如收缩压持续 >140 则调整用药',
   '2026-07-07 09:30:00', '2026-07-07 09:30:00'),

  (5002, 4, 3001,
   '体检发现血糖偏高',
   '体检空腹血糖 6.2 mmol/L，患者无明显多饮多尿消瘦。母亲有 2 型糖尿病史。',
   '空腹血糖受损（IFG）',
   '饮食控制为主，暂不予降糖药物\n减少精制碳水与含糖饮料',
   '空腹血糖 6.2 mmol/L；BMI 19.6；血压 110/70 mmHg',
   '3 个月后复查空腹血糖；建议记录每日主食摄入量',
   '2026-07-07 10:15:00', '2026-07-07 10:15:00'),

  (5003, 5, 3002,
   '反复上腹隐痛',
   '上腹隐痛反复 2 月，餐后明显，无反酸嗳气。肝胆超声提示轻度脂肪肝。',
   '轻度脂肪肝（考虑非酒精性）',
   '避免熬夜与高脂饮食\n适度有氧运动减轻体重',
   'ALT 68 U/L；AST 52 U/L；腹部超声提示肝内回声增强',
   '6 个月后复查肝功能与腹部超声',
   '2026-07-07 14:20:00', '2026-07-07 14:20:00')
ON DUPLICATE KEY UPDATE
  -- 幂等：已存在则更新内容，不重复插入
  chief_complaint    = VALUES(chief_complaint),
  present_illness    = VALUES(present_illness),
  diagnosis          = VALUES(diagnosis),
  prescription       = VALUES(prescription),
  examination_results = VALUES(examination_results),
  follow_up_plan     = VALUES(follow_up_plan);

-- ---------------------------------------------------------------------------
-- 4) 造随访任务（医生端「随访」区块原本 5 条但 patientId 也指向测试用户）
-- ---------------------------------------------------------------------------
UPDATE followup_task SET patient_id = 3 WHERE id = 1;
UPDATE followup_task SET patient_id = 4 WHERE id = 2;
UPDATE followup_task SET patient_id = 5 WHERE id = 3;

-- ============================================================================
-- 校验：
--   -- 每条预约都应能查到档案
--   SELECT a.id, a.patient_id, u.user_name, p.bmi, p.chronic_diseases
--   FROM appointment a
--   LEFT JOIN `user` u ON u.id = a.patient_id
--   LEFT JOIN patient_profile p ON p.user_id = a.patient_id
--   WHERE a.id BETWEEN 5001 AND 5005;
--
--   -- 就诊记录应为 3 条
--   SELECT COUNT(*) FROM visit_record;
-- ============================================================================
