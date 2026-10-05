-- ============================================================================
-- 02_patient_profile_normalize.sql
-- 患者档案数据归一化（2026-10-04）
--
-- 背景：建医生端「患者档案详情」时发现 patient_profile 存在三类脏数据，
--       不修会让医生看到错误信息（例如把「无」当成过敏史）：
--
--   1) 性别列中英混杂：'男' / 'male' / 'female' 混存
--   2) lifestyle 等 JSON 字段键名不统一：
--        一行是 {"smoking":false,"drinking":"偶尔","exercise":"每周3次"}
--        另一行是 {"smoke":"否","drink":"偶尔","exercise":"每周2次"}
--   3) ['无'] 被当作真实内容存进 chronic_diseases/allergies/medications 等
--      —— 医生端若直接展示，会误判为「患者有一条叫"无"的过敏史」
--
-- 约定：
--   · 脚本幂等，重复执行结果一致
--   · 只做 UPDATE，不做 DROP / DELETE
--   · 统一为：性别中文；JSON 键名用 smoking/drinking/exercise；
--     语义为「无」的数组统一存为 '[]'（空数组），而非 ['无']
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 性别归一化：male/female/MALE/Female/1/2 → 男/女
-- ---------------------------------------------------------------------------
UPDATE patient_profile SET gender = '男' WHERE LOWER(TRIM(gender)) IN ('male', 'm', '1', 'man');
UPDATE patient_profile SET gender = '女' WHERE LOWER(TRIM(gender)) IN ('female', 'f', '2', 'woman');
-- 清理其它无法识别的取值，避免前端直接透传英文
UPDATE patient_profile SET gender = '未知'
 WHERE gender IS NOT NULL
   AND TRIM(gender) NOT IN ('男', '女', '未知');

-- ---------------------------------------------------------------------------
-- 2) lifestyle 键名归一化
--    旧键 smoke/drink → 新键 smoking/drinking（值一并转换）
--    值为 '否' 的转成 false，与另一批数据的布尔类型保持一致
-- ---------------------------------------------------------------------------
UPDATE patient_profile
   SET lifestyle = JSON_SET(
         lifestyle,
         '$.smoking',
         CASE
           WHEN JSON_EXTRACT(lifestyle, '$.smoking') IS NOT NULL
             THEN JSON_EXTRACT(lifestyle, '$.smoking')
           WHEN JSON_UNQUOTE(JSON_EXTRACT(lifestyle, '$.smoke')) = '是' THEN CAST('true' AS JSON)
           WHEN JSON_UNQUOTE(JSON_EXTRACT(lifestyle, '$.smoke')) = '否' THEN CAST('false' AS JSON)
           ELSE CAST('false' AS JSON)
         END
       )
 WHERE lifestyle IS NOT NULL
   AND JSON_VALID(lifestyle)
   AND JSON_EXTRACT(lifestyle, '$.smoke') IS NOT NULL;

UPDATE patient_profile
   SET lifestyle = JSON_SET(
         lifestyle,
         '$.drinking',
         CASE
           WHEN JSON_EXTRACT(lifestyle, '$.drinking') IS NOT NULL
             THEN JSON_EXTRACT(lifestyle, '$.drinking')
           WHEN JSON_UNQUOTE(JSON_EXTRACT(lifestyle, '$.drink')) IN ('', '无', '不喝')
             THEN '无'
           ELSE JSON_UNQUOTE(JSON_EXTRACT(lifestyle, '$.drink'))
         END
       )
 WHERE lifestyle IS NOT NULL
   AND JSON_VALID(lifestyle)
   AND JSON_EXTRACT(lifestyle, '$.drink') IS NOT NULL;

-- 删掉旧键，只留新键
UPDATE patient_profile
   SET lifestyle = JSON_REMOVE(lifestyle, '$.smoke')
 WHERE lifestyle IS NOT NULL AND JSON_VALID(lifestyle)
   AND JSON_EXTRACT(lifestyle, '$.smoke') IS NOT NULL;

UPDATE patient_profile
   SET lifestyle = JSON_REMOVE(lifestyle, '$.drink')
 WHERE lifestyle IS NOT NULL AND JSON_VALID(lifestyle)
   AND JSON_EXTRACT(lifestyle, '$.drink') IS NOT NULL;

-- lifestyle 为 ['无'] / '无' / '{}' 的，统一成空对象
UPDATE patient_profile SET lifestyle = JSON_OBJECT()
 WHERE lifestyle IS NOT NULL
   AND (lifestyle = '无'
        OR lifestyle = '\"无\"'
        OR lifestyle = '{}'
        OR lifestyle = 'null');

-- ---------------------------------------------------------------------------
-- 3) 语义「无」的数组归一化为空数组 []
--    仅处理「整个数组只有一个元素且该元素是占位词」的情况，
--    不动 ['无', '青霉素'] 这种混合值（那本身就是脏数据但需人工确认）
-- ---------------------------------------------------------------------------
UPDATE patient_profile SET chronic_diseases = '[]'
 WHERE chronic_diseases IS NOT NULL
   AND JSON_VALID(chronic_diseases)
   AND JSON_LENGTH(chronic_diseases) = 1
   AND JSON_UNQUOTE(JSON_EXTRACT(chronic_diseases, '$[0]')) IN ('无', '没有', 'null', '-');

UPDATE patient_profile SET allergies = '[]'
 WHERE allergies IS NOT NULL
   AND JSON_VALID(allergies)
   AND JSON_LENGTH(allergies) = 1
   AND JSON_UNQUOTE(JSON_EXTRACT(allergies, '$[0]')) IN ('无', '没有', 'null', '-');

UPDATE patient_profile SET medications = '[]'
 WHERE medications IS NOT NULL
   AND JSON_VALID(medications)
   AND JSON_LENGTH(medications) = 1
   AND JSON_UNQUOTE(JSON_EXTRACT(medications, '$[0]')) IN ('无', '没有', 'null', '-');

UPDATE patient_profile SET surgeries = '[]'
 WHERE surgeries IS NOT NULL
   AND JSON_VALID(surgeries)
   AND JSON_LENGTH(surgeries) = 1
   AND JSON_UNQUOTE(JSON_EXTRACT(surgeries, '$[0]')) IN ('无', '没有', 'null', '-');

UPDATE patient_profile SET family_history = '[]'
 WHERE family_history IS NOT NULL
   AND JSON_VALID(family_history)
   AND JSON_LENGTH(family_history) = 1
   AND JSON_UNQUOTE(JSON_EXTRACT(family_history, '$[0]')) IN ('无', '没有', 'null', '-');

-- 空字符串 / 非法 JSON 一律置为对应类型的空值，避免后端解析抛异常
UPDATE patient_profile SET chronic_diseases = '[]' WHERE chronic_diseases IS NULL OR chronic_diseases = '';
UPDATE patient_profile SET allergies        = '[]' WHERE allergies        IS NULL OR allergies        = '';
UPDATE patient_profile SET medications     = '[]' WHERE medications     IS NULL OR medications     = '';
UPDATE patient_profile SET surgeries       = '[]' WHERE surgeries       IS NULL OR surgeries       = '';
UPDATE patient_profile SET family_history = '[]' WHERE family_history IS NULL OR family_history = '';
UPDATE patient_profile SET health_goals   = '[]' WHERE health_goals   IS NULL OR health_goals   = '';
UPDATE patient_profile SET lifestyle      = NULL  WHERE lifestyle      = '';

-- 非法 JSON（历史脏数据）重置为空数组
UPDATE patient_profile SET chronic_diseases = '[]'
 WHERE chronic_diseases IS NOT NULL AND NOT JSON_VALID(chronic_diseases);
UPDATE patient_profile SET allergies = '[]'
 WHERE allergies IS NOT NULL AND NOT JSON_VALID(allergies);
UPDATE patient_profile SET medications = '[]'
 WHERE medications IS NOT NULL AND NOT JSON_VALID(medications);
UPDATE patient_profile SET surgeries = '[]'
 WHERE surgeries IS NOT NULL AND NOT JSON_VALID(surgeries);
UPDATE patient_profile SET family_history = '[]'
 WHERE family_history IS NOT NULL AND NOT JSON_VALID(family_history);
UPDATE patient_profile SET health_goals = '[]'
 WHERE health_goals IS NOT NULL AND NOT JSON_VALID(health_goals);
UPDATE patient_profile SET lifestyle = NULL
 WHERE lifestyle IS NOT NULL AND NOT JSON_VALID(lifestyle);

-- ---------------------------------------------------------------------------
-- 4) BMI 缺失时按身高体重补算（医生端要展示 BMI，空着不专业）
--    仅在 height/weight 都有值且 bmi 为空时计算，不覆盖已有值
-- ---------------------------------------------------------------------------
UPDATE patient_profile
   SET bmi = ROUND(weight / POW(height / 100, 2), 2)
 WHERE (bmi IS NULL OR bmi = 0)
   AND height IS NOT NULL AND height > 0
   AND weight IS NOT NULL AND weight > 0;

-- ============================================================================
-- 校验（执行后应看到：性别只有 男/女/未知；无 ['无'] 残留）
--   SELECT gender, COUNT(*) FROM patient_profile GROUP BY gender;
--   SELECT COUNT(*) FROM patient_profile WHERE chronic_diseases = '["无"]';
--   SELECT user_id, lifestyle FROM patient_profile;
-- ============================================================================
