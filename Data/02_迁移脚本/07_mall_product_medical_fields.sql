-- =============================================================================
-- 07_mall_product_medical_fields.sql
-- 目的：修复 2026-08-28「药品合并商城」的收尾遗留
--       把药品的医学字段并入 mall_product（加列，不新建表）
-- 依据：docs/数据资产与AI能力整改方案.md 阶段 0-A / A3
--        docs/项目现状基线.md  §问题 3（drug 表已删但 DrugMapper 未改）
-- 制定：2026-10-06
-- =============================================================================
--
-- 【背景】
-- 2026-08-28 用户决定删除 drug 表、商品数据并入 mall_product（决策正确）。
-- 但两件事没做干净：
--   1) DrugMapper.xml 仍 FROM drug / drug_subscription —— 10 条 SQL 一调必崩
--      （报 Table 'personal_health.drug' doesn't exist）
--   2) mall_product 缺药品的医学字段：generic_name / specification /
--      manufacturer / is_otc / medical_category。这些是 AI 的 drugs.json 里有、
--      商城表单里没有的。另 product_type 也是漏回写的一次性 patch，见步骤 0。
--
-- 【为什么选「给 mall_product 加列」而不是「重建 drug 表」】
-- 项目强耦合原则：能加列就不建新表。且问题的本质是两条药品数据线割裂——
-- AI 的知识（drugs.json）与用户能买的商品（mall_product）互不相通，
-- 导致 AI 会推荐商城买不到的药。把它们合并到同一张表才是根治。
--
-- 【实测数据】（执行前）
--   商城药品 107 条（去重药名 62）
--   drugs.json 55 条（去重药名 53）
--   两边都有 49 个药名（占 drugs.json 的 92%）
--   仅 drugs.json 有 4 个  → AI 会推荐这 4 种但商城没有（另案处理）
--
-- ⚠️ MySQL 9.7 不支持 ADD COLUMN IF NOT EXISTS，故用 information_schema 判定 +
--    PREPARE 动态 DDL 实现幂等（见下方每个字段的处理）。
-- ⚠️ `user` 是 MySQL 保留字，但本脚本只涉及 mall_product 表。
--
-- =============================================================================

-- ⚠️ 本脚本**不切换数据库**：调用方需先 `USE <目标库>`（或 mysql 命令带上库名）。
--    原先这里硬编码 `USE personal_health;`，导致针对其它库执行时会**静默写进 personal_health**。
--    MySQL 9.7 无 ADD COLUMN IF NOT EXISTS，下面的自省一律用 DATABASE() 取当前库。


-- -----------------------------------------------------------------------------
-- 步骤 0：补 product_type（历史欠账）
--   2026-08-28「药品合并商城」时用一次性脚本加过该列，但没写进迁移文件，
--   导致「从备份恢复 / 全新部署后重跑迁移」时该列丢失，
--   DrugMapper（按 product_type='drug' 过滤）与商城药品筛选全废。
-- -----------------------------------------------------------------------------
-- 显式声明连接编码，避免依赖客户端默认值（否则中文 INSERT/列注释会报 ERROR 1406 或乱码）
SET NAMES utf8mb4;

SET @exist := (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='mall_product'
                 AND column_name='product_type');
SET @sql := IF(@exist > 0, 'DO 0',
  "ALTER TABLE mall_product ADD COLUMN product_type VARCHAR(20) DEFAULT 'health' COMMENT '商品类型: drug=药品, device=医疗器械, health=保健品' AFTER category_id");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- -----------------------------------------------------------------------------
-- 步骤 1：给 mall_product 增加 5 个医学字段（幂等）
-- -----------------------------------------------------------------------------
SET @exist := (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='mall_product'
                 AND column_name='generic_name');
SET @sql := IF(@exist > 0, 'DO 0',
  "ALTER TABLE mall_product ADD COLUMN generic_name VARCHAR(200) DEFAULT NULL COMMENT '通用名（药品专用，源 ai_data/drugs.json）' AFTER name");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @exist := (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='mall_product'
                 AND column_name='specification');
SET @sql := IF(@exist > 0, 'DO 0',
  "ALTER TABLE mall_product ADD COLUMN specification VARCHAR(200) DEFAULT NULL COMMENT '药品规格（如 0.4gx30片）' AFTER generic_name");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @exist := (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='mall_product'
                 AND column_name='manufacturer');
SET @sql := IF(@exist > 0, 'DO 0',
  "ALTER TABLE mall_product ADD COLUMN manufacturer VARCHAR(200) DEFAULT NULL COMMENT '生产厂家' AFTER specification");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @exist := (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='mall_product'
                 AND column_name='is_otc');
SET @sql := IF(@exist > 0, 'DO 0',
  "ALTER TABLE mall_product ADD COLUMN is_otc TINYINT(1) DEFAULT 0 COMMENT '是否OTC非处方药(0:否;1:是)' AFTER manufacturer");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @exist := (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='mall_product'
                 AND column_name='medical_category');
SET @sql := IF(@exist > 0, 'DO 0',
  "ALTER TABLE mall_product ADD COLUMN medical_category VARCHAR(50) DEFAULT NULL COMMENT '医学分类（中成药/感冒药/抗生素/消化系统/心脑血管/解热镇痛/维生素/外用药），仅药品有值' AFTER is_otc");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- -----------------------------------------------------------------------------
-- 步骤 2：从 ai_data/drugs.json 回填（53 条去重药名）
--          按 TRIM(name) 匹配 product_type='drug' 的商品，幂等可重复执行
-- -----------------------------------------------------------------------------

UPDATE mall_product SET generic_name='乳酸菌素片', specification='0.4g×30片', manufacturer='江中药业股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='乳酸菌素片';
UPDATE mall_product SET generic_name='云南白药气雾剂', specification='50g+85g', manufacturer='云南白药集团股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='云南白药气雾剂';
UPDATE mall_product SET generic_name='健胃消食片', specification='0.5g×32片', manufacturer='江中药业股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='健胃消食片';
UPDATE mall_product SET generic_name='克拉霉素', specification='0.25g×6片', manufacturer='雅培贸易(上海)有限公司', is_otc=0 WHERE product_type='drug' AND TRIM(name)='克拉霉素片';
UPDATE mall_product SET generic_name='六味地黄丸', specification='200丸', manufacturer='河南宛西制药股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='六味地黄丸';
UPDATE mall_product SET generic_name='苯扎氯铵贴', specification='100片', manufacturer='云南白药集团股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='创可贴';
UPDATE mall_product SET generic_name='双黄连口服液', specification='10ml×10支', manufacturer='哈药集团三精制药有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='双黄连口服液';
UPDATE mall_product SET generic_name='复方丹参滴丸', specification='27mg×180丸', manufacturer='天士力医药集团股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='复方丹参滴丸';
UPDATE mall_product SET generic_name='复方氨酚烷胺胶囊', specification='10粒', manufacturer='哈药集团制药总厂', is_otc=1 WHERE product_type='drug' AND TRIM(name)='复方氨酚烷胺胶囊';
UPDATE mall_product SET generic_name='复方甘草片', specification='100片', manufacturer='华北制药股份有限公司', is_otc=0 WHERE product_type='drug' AND TRIM(name)='复方甘草片';
UPDATE mall_product SET generic_name='多潘立酮', specification='10mg×30片', manufacturer='西安杨森制药有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='多潘立酮片';
UPDATE mall_product SET generic_name='多维元素片', specification='100片', manufacturer='惠氏制药有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='多维元素片';
UPDATE mall_product SET generic_name='头孢克洛', specification='0.25g×12粒', manufacturer='礼来苏州制药有限公司', is_otc=0 WHERE product_type='drug' AND TRIM(name)='头孢克洛胶囊';
UPDATE mall_product SET generic_name='奥美拉唑', specification='20mg×14粒', manufacturer='阿斯利康制药有限公司', is_otc=0 WHERE product_type='drug' AND TRIM(name)='奥美拉唑肠溶胶囊';
UPDATE mall_product SET generic_name='安宫牛黄丸', specification='3g×1丸', manufacturer='北京同仁堂股份有限公司', is_otc=0 WHERE product_type='drug' AND TRIM(name)='安宫牛黄丸';
UPDATE mall_product SET generic_name='对乙酰氨基酚', specification='0.5g×12片', manufacturer='华北制药股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='对乙酰氨基酚片';
UPDATE mall_product SET generic_name='小柴胡颗粒', specification='10g×10袋', manufacturer='四川光大制药有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='小柴胡颗粒';
UPDATE mall_product SET generic_name='左氧氟沙星', specification='0.5g×6片', manufacturer='第一三共制药(北京)有限公司', is_otc=0 WHERE product_type='drug' AND TRIM(name)='左氧氟沙星片';
UPDATE mall_product SET generic_name='布洛芬', specification='0.3g*20粒', manufacturer='中美天津史克制药有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='布洛芬缓释胶囊';
UPDATE mall_product SET generic_name='感冒清热颗粒', specification='12g×10袋', manufacturer='北京同仁堂科技发展股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='感冒清热颗粒';
UPDATE mall_product SET generic_name='复方感冒灵颗粒', specification='10g×9袋', manufacturer='华润三九医药股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='感冒灵颗粒';
UPDATE mall_product SET generic_name='板蓝根颗粒', specification='10g×20袋', manufacturer='广州白云山和记黄埔中药有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='板蓝根颗粒';
UPDATE mall_product SET generic_name='枸橼酸铋钾', specification='0.3g×40片', manufacturer='丽珠集团丽珠制药厂', is_otc=0 WHERE product_type='drug' AND TRIM(name)='枸橼酸铋钾片';
UPDATE mall_product SET generic_name='硫酸氢氯吡格雷', specification='75mg×7片', manufacturer='赛诺菲(杭州)制药有限公司', is_otc=0 WHERE product_type='drug' AND TRIM(name)='氯吡格雷片';
UPDATE mall_product SET generic_name='法莫替丁', specification='20mg×30片', manufacturer='安斯泰来制药(中国)有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='法莫替丁片';
UPDATE mall_product SET generic_name='活血止痛膏', specification='6贴', manufacturer='黄石市力健药业有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='活血止痛膏';
UPDATE mall_product SET generic_name='牛黄解毒片', specification='0.3g×36片', manufacturer='北京同仁堂科技发展股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='牛黄解毒片';
UPDATE mall_product SET generic_name='甲硝唑', specification='0.2g×21片', manufacturer='华北制药股份有限公司', is_otc=0 WHERE product_type='drug' AND TRIM(name)='甲硝唑片';
UPDATE mall_product SET generic_name='复方醋酸地塞米松乳膏', specification='20g', manufacturer='华润三九医药股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='皮炎平软膏';
UPDATE mall_product SET generic_name='硝苯地平', specification='10mg×30片', manufacturer='拜耳医药保健有限公司', is_otc=0 WHERE product_type='drug' AND TRIM(name)='硝苯地平缓释片';
UPDATE mall_product SET generic_name='聚维酮碘溶液', specification='100ml', manufacturer='上海利康消毒高科技有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='碘伏消毒液';
UPDATE mall_product SET generic_name='碳酸钙D3', specification='60片', manufacturer='惠氏制药有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='碳酸钙D3片';
UPDATE mall_product SET generic_name='红霉素', specification='1%×10g', manufacturer='马应龙药业集团股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='红霉素软膏';
UPDATE mall_product SET generic_name='复合维生素B', specification='100片', manufacturer='东北制药集团股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='维生素B族片';
UPDATE mall_product SET generic_name='维生素C', specification='100mg×100片', manufacturer='东北制药集团股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='维生素C片';
UPDATE mall_product SET generic_name='维生素D3', specification='400IU×36粒', manufacturer='青岛双鲸药业有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='维生素D滴剂';
UPDATE mall_product SET generic_name='维生素E', specification='100mg×60粒', manufacturer='浙江医药股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='维生素E软胶囊';
UPDATE mall_product SET generic_name='缬沙坦', specification='80mg×7粒', manufacturer='北京诺华制药有限公司', is_otc=0 WHERE product_type='drug' AND TRIM(name)='缬沙坦胶囊';
UPDATE mall_product SET generic_name='罗红霉素', specification='0.15g×6片', manufacturer='哈药集团制药总厂', is_otc=0 WHERE product_type='drug' AND TRIM(name)='罗红霉素分散片';
UPDATE mall_product SET generic_name='酒石酸美托洛尔', specification='47.5mg×7片', manufacturer='阿斯利康制药有限公司', is_otc=0 WHERE product_type='drug' AND TRIM(name)='美托洛尔缓释片';
UPDATE mall_product SET generic_name='蒙脱石散', specification='3g×10袋', manufacturer='博福-益普生(天津)制药有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='蒙脱石散';
UPDATE mall_product SET generic_name='蒙脱石散', specification='3g×10袋（草莓味）', manufacturer='博福-益普生(天津)制药有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='蒙脱石散（儿童装）';
UPDATE mall_product SET generic_name='藿香正气水', specification='10ml×10支', manufacturer='太极集团重庆涪陵制药厂', is_otc=1 WHERE product_type='drug' AND TRIM(name)='藿香正气水';
UPDATE mall_product SET generic_name='补中益气丸', specification='200丸', manufacturer='河南宛西制药股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='补中益气丸';
UPDATE mall_product SET generic_name='硝酸咪康唑乳膏', specification='2%×20g', manufacturer='西安杨森制药有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='达克宁乳膏';
UPDATE mall_product SET generic_name='连花清瘟胶囊', specification='0.35g×48粒', manufacturer='石家庄以岭药业股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='连花清瘟胶囊';
UPDATE mall_product SET generic_name='逍遥丸', specification='200丸', manufacturer='河南宛西制药股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='逍遥丸';
UPDATE mall_product SET generic_name='通宣理肺丸', specification='200丸', manufacturer='北京同仁堂股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='通宣理肺丸';
UPDATE mall_product SET generic_name='金匮肾气丸', specification='200丸', manufacturer='河南宛西制药股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='金匮肾气丸';
UPDATE mall_product SET generic_name='阿司匹林', specification='100mg×30片', manufacturer='拜耳医药保健有限公司', is_otc=0 WHERE product_type='drug' AND TRIM(name)='阿司匹林肠溶片';
UPDATE mall_product SET generic_name='阿奇霉素', specification='0.25g×6片', manufacturer='辉瑞制药有限公司', is_otc=0 WHERE product_type='drug' AND TRIM(name)='阿奇霉素片';
UPDATE mall_product SET generic_name='阿莫西林', specification='0.25g*24粒', manufacturer='哈药集团制药总厂', is_otc=1 WHERE product_type='drug' AND TRIM(name)='阿莫西林胶囊';
UPDATE mall_product SET generic_name='风油精', specification='3ml', manufacturer='漳州水仙药业股份有限公司', is_otc=1 WHERE product_type='drug' AND TRIM(name)='风油精';



-- 医学分类回填（drugs.json 的 category → medical_category，8 个取值）
UPDATE mall_product SET medical_category='消化系统' WHERE product_type='drug' AND TRIM(name)='乳酸菌素片';
UPDATE mall_product SET medical_category='外用药' WHERE product_type='drug' AND TRIM(name)='云南白药气雾剂';
UPDATE mall_product SET medical_category='消化系统' WHERE product_type='drug' AND TRIM(name)='健胃消食片';
UPDATE mall_product SET medical_category='抗生素' WHERE product_type='drug' AND TRIM(name)='克拉霉素片';
UPDATE mall_product SET medical_category='中成药' WHERE product_type='drug' AND TRIM(name)='六味地黄丸';
UPDATE mall_product SET medical_category='外用药' WHERE product_type='drug' AND TRIM(name)='创可贴';
UPDATE mall_product SET medical_category='中成药' WHERE product_type='drug' AND TRIM(name)='双黄连口服液';
UPDATE mall_product SET medical_category='心脑血管' WHERE product_type='drug' AND TRIM(name)='复方丹参滴丸';
UPDATE mall_product SET medical_category='感冒药' WHERE product_type='drug' AND TRIM(name)='复方氨酚烷胺胶囊';
UPDATE mall_product SET medical_category='感冒药' WHERE product_type='drug' AND TRIM(name)='复方甘草片';
UPDATE mall_product SET medical_category='消化系统' WHERE product_type='drug' AND TRIM(name)='多潘立酮片';
UPDATE mall_product SET medical_category='维生素' WHERE product_type='drug' AND TRIM(name)='多维元素片';
UPDATE mall_product SET medical_category='抗生素' WHERE product_type='drug' AND TRIM(name)='头孢克洛胶囊';
UPDATE mall_product SET medical_category='消化系统' WHERE product_type='drug' AND TRIM(name)='奥美拉唑肠溶胶囊';
UPDATE mall_product SET medical_category='中成药' WHERE product_type='drug' AND TRIM(name)='安宫牛黄丸';
UPDATE mall_product SET medical_category='感冒药' WHERE product_type='drug' AND TRIM(name)='对乙酰氨基酚片';
UPDATE mall_product SET medical_category='感冒药' WHERE product_type='drug' AND TRIM(name)='小柴胡颗粒';
UPDATE mall_product SET medical_category='抗生素' WHERE product_type='drug' AND TRIM(name)='左氧氟沙星片';
UPDATE mall_product SET medical_category='解热镇痛' WHERE product_type='drug' AND TRIM(name)='布洛芬缓释胶囊';
UPDATE mall_product SET medical_category='感冒药' WHERE product_type='drug' AND TRIM(name)='感冒清热颗粒';
UPDATE mall_product SET medical_category='感冒药' WHERE product_type='drug' AND TRIM(name)='感冒灵颗粒';
UPDATE mall_product SET medical_category='感冒药' WHERE product_type='drug' AND TRIM(name)='板蓝根颗粒';
UPDATE mall_product SET medical_category='消化系统' WHERE product_type='drug' AND TRIM(name)='枸橼酸铋钾片';
UPDATE mall_product SET medical_category='心脑血管' WHERE product_type='drug' AND TRIM(name)='氯吡格雷片';
UPDATE mall_product SET medical_category='消化系统' WHERE product_type='drug' AND TRIM(name)='法莫替丁片';
UPDATE mall_product SET medical_category='外用药' WHERE product_type='drug' AND TRIM(name)='活血止痛膏';
UPDATE mall_product SET medical_category='中成药' WHERE product_type='drug' AND TRIM(name)='牛黄解毒片';
UPDATE mall_product SET medical_category='抗生素' WHERE product_type='drug' AND TRIM(name)='甲硝唑片';
UPDATE mall_product SET medical_category='外用药' WHERE product_type='drug' AND TRIM(name)='皮炎平软膏';
UPDATE mall_product SET medical_category='心脑血管' WHERE product_type='drug' AND TRIM(name)='硝苯地平缓释片';
UPDATE mall_product SET medical_category='外用药' WHERE product_type='drug' AND TRIM(name)='碘伏消毒液';
UPDATE mall_product SET medical_category='维生素' WHERE product_type='drug' AND TRIM(name)='碳酸钙D3片';
UPDATE mall_product SET medical_category='外用药' WHERE product_type='drug' AND TRIM(name)='红霉素软膏';
UPDATE mall_product SET medical_category='维生素' WHERE product_type='drug' AND TRIM(name)='维生素B族片';
UPDATE mall_product SET medical_category='维生素' WHERE product_type='drug' AND TRIM(name)='维生素C片';
UPDATE mall_product SET medical_category='维生素' WHERE product_type='drug' AND TRIM(name)='维生素D滴剂';
UPDATE mall_product SET medical_category='维生素' WHERE product_type='drug' AND TRIM(name)='维生素E软胶囊';
UPDATE mall_product SET medical_category='心脑血管' WHERE product_type='drug' AND TRIM(name)='缬沙坦胶囊';
UPDATE mall_product SET medical_category='抗生素' WHERE product_type='drug' AND TRIM(name)='罗红霉素分散片';
UPDATE mall_product SET medical_category='心脑血管' WHERE product_type='drug' AND TRIM(name)='美托洛尔缓释片';
UPDATE mall_product SET medical_category='消化系统' WHERE product_type='drug' AND TRIM(name)='蒙脱石散';
UPDATE mall_product SET medical_category='消化系统' WHERE product_type='drug' AND TRIM(name)='蒙脱石散（儿童装）';
UPDATE mall_product SET medical_category='中成药' WHERE product_type='drug' AND TRIM(name)='藿香正气水';
UPDATE mall_product SET medical_category='中成药' WHERE product_type='drug' AND TRIM(name)='补中益气丸';
UPDATE mall_product SET medical_category='外用药' WHERE product_type='drug' AND TRIM(name)='达克宁乳膏';
UPDATE mall_product SET medical_category='感冒药' WHERE product_type='drug' AND TRIM(name)='连花清瘟胶囊';
UPDATE mall_product SET medical_category='中成药' WHERE product_type='drug' AND TRIM(name)='逍遥丸';
UPDATE mall_product SET medical_category='中成药' WHERE product_type='drug' AND TRIM(name)='通宣理肺丸';
UPDATE mall_product SET medical_category='中成药' WHERE product_type='drug' AND TRIM(name)='金匮肾气丸';
UPDATE mall_product SET medical_category='心脑血管' WHERE product_type='drug' AND TRIM(name)='阿司匹林肠溶片';
UPDATE mall_product SET medical_category='抗生素' WHERE product_type='drug' AND TRIM(name)='阿奇霉素片';
UPDATE mall_product SET medical_category='抗生素' WHERE product_type='drug' AND TRIM(name)='阿莫西林胶囊';
UPDATE mall_product SET medical_category='外用药' WHERE product_type='drug' AND TRIM(name)='风油精';

-- -----------------------------------------------------------------------------
-- 步骤 3：校验（应看到 filled > 0，且 total_rows = 107）
-- -----------------------------------------------------------------------------
SELECT
  COUNT(*)                                        AS total_drug_rows,
  SUM(generic_name   IS NOT NULL)                 AS has_generic_name,
  SUM(specification  IS NOT NULL)                 AS has_specification,
  SUM(manufacturer   IS NOT NULL)                 AS has_manufacturer,
  SUM(is_otc = 1)                                 AS is_otc_count,
  SUM(medical_category IS NOT NULL)                  AS has_medical_category
FROM mall_product WHERE product_type = 'drug';

SELECT '迁移 07 完成：mall_product 已具备药品医学字段' AS result;
