-- MySQL dump 10.13  Distrib 9.7.0, for Win64 (x86_64)
--
-- Host: localhost    Database: personal_health
-- ------------------------------------------------------
-- Server version	9.7.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
SET @MYSQLDUMP_TEMP_LOG_BIN = @@SESSION.SQL_LOG_BIN;
SET @@SESSION.SQL_LOG_BIN= 0;

--
-- GTID state at the beginning of the backup 
--

SET @@GLOBAL.GTID_PURGED=/*!80000 '+'*/ '68c8e41b-479b-11f1-98c8-005056c00001:1-49050';

--
-- Table structure for table `patient_profile`
--

DROP TABLE IF EXISTS `patient_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `patient_profile` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `gender` varchar(10) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `birth_date` date DEFAULT NULL,
  `height` decimal(5,2) DEFAULT NULL COMMENT '身高cm',
  `weight` decimal(5,2) DEFAULT NULL COMMENT '体重kg',
  `bmi` decimal(5,2) DEFAULT NULL,
  `chronic_diseases` json DEFAULT NULL COMMENT '基础疾病',
  `allergies` json DEFAULT NULL COMMENT '过敏史',
  `medications` json DEFAULT NULL COMMENT '用药史',
  `surgeries` json DEFAULT NULL COMMENT '手术史',
  `family_history` json DEFAULT NULL COMMENT '家族病史',
  `lifestyle` json DEFAULT NULL COMMENT '生活习惯',
  `health_goals` json DEFAULT NULL COMMENT '健康目标',
  `fasting_blood_glucose` decimal(5,2) DEFAULT NULL COMMENT '空腹血糖',
  `postprandial_blood_glucose` decimal(5,2) DEFAULT NULL COMMENT '餐后血糖',
  `total_cholesterol` decimal(5,2) DEFAULT NULL COMMENT '总胆固醇',
  `triglycerides` decimal(5,2) DEFAULT NULL COMMENT '甘油三酯',
  `hdl_cholesterol` decimal(5,2) DEFAULT NULL COMMENT '高密度脂蛋白',
  `ldl_cholesterol` decimal(5,2) DEFAULT NULL COMMENT '低密度脂蛋白',
  `systolic_pressure` int DEFAULT NULL COMMENT '收缩压',
  `diastolic_pressure` int DEFAULT NULL COMMENT '舒张压',
  `resting_heart_rate` int DEFAULT NULL COMMENT '静息心率',
  `last_update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `patient_profile`
--

LOCK TABLES `patient_profile` WRITE;
/*!40000 ALTER TABLE `patient_profile` DISABLE KEYS */;
INSERT INTO `patient_profile` VALUES (1,3,'男',34,'1992-03-15',175.00,72.50,23.67,'[\"高血压\"]','[\"青霉素\"]','[\"氨氯地平\"]','[]','[\"糖尿病\"]','{\"smoking\": false, \"drinking\": \"偶尔\", \"exercise\": \"每周3次\"}','[\"控制血压\", \"减重5kg\"]',5.40,7.80,4.80,1.50,1.40,2.90,138,88,72,'2026-08-28 12:19:41','2026-08-28 12:19:41'),(8,2,'male',30,'1996-03-12',175.00,72.00,23.50,'[\"轻度脂肪肝\"]','[\"无\"]','[\"无\"]','[\"无\"]','[\"父亲高血压\"]','{\"drink\": \"偶尔\", \"smoke\": \"否\", \"exercise\": \"每周2次\"}','[\"减重5kg\", \"规律作息\"]',5.40,7.20,4.80,1.40,1.30,2.90,128,82,76,'2026-07-20 09:00:00','2026-04-01 09:00:00'),(9,4,'female',28,'1998-07-21',163.00,52.00,19.60,'[\"无\"]','[\"花粉\"]','[\"无\"]','[\"无\"]','[\"无\"]','{\"drink\": \"否\", \"smoke\": \"否\", \"exercise\": \"每周4次瑜伽\"}','[\"保持体重\", \"改善睡眠\"]',4.90,6.30,4.20,0.90,1.50,2.20,110,70,72,'2026-07-20 09:00:00','2026-04-01 09:00:00'),(10,5,'male',45,'1981-02-09',178.00,88.00,27.80,'[\"2型糖尿病\", \"高尿酸\"]','[\"青霉素\"]','[\"二甲双胍\", \"别嘌醇\"]','[\"阑尾切除\"]','[\"母亲糖尿病\"]','{\"drink\": \"每日啤酒\", \"smoke\": \"是(15支/天)\", \"exercise\": \"很少\"}','[\"控糖\", \"戒烟\"]',8.60,12.40,5.60,2.80,1.00,3.60,146,94,82,'2026-07-20 09:00:00','2026-04-01 09:00:00'),(11,6,'female',52,'1974-11-03',160.00,65.00,25.40,'[\"高血压\", \"骨质疏松\"]','[\"磺胺类\"]','[\"氨氯地平\", \"钙片\"]','[\"子宫肌瘤切除\"]','[\"姐姐乳腺癌\"]','{\"drink\": \"否\", \"smoke\": \"否\", \"exercise\": \"每日散步\"}','[\"骨密度改善\", \"平稳血压\"]',5.80,7.80,5.20,1.70,1.20,3.30,138,86,78,'2026-07-20 09:00:00','2026-04-01 09:00:00'),(12,7,'male',36,'1990-05-18',180.00,80.00,24.70,'[\"高血脂\"]','[\"无\"]','[\"无\"]','[\"无\"]','[\"无\"]','{\"drink\": \"社交饮酒\", \"smoke\": \"偶尔\", \"exercise\": \"每周3次健身\"}','[\"增肌\", \"降脂\"]',5.20,6.90,5.40,2.20,1.10,3.40,132,85,74,'2026-07-20 09:00:00','2026-04-01 09:00:00'),(13,8,'female',40,'1986-09-27',166.00,60.00,21.80,'[\"甲状腺功能减退\"]','[\"海鲜\"]','[\"左甲状腺素钠\"]','[\"无\"]','[\"无\"]','{\"drink\": \"否\", \"smoke\": \"否\", \"exercise\": \"瑜伽+游泳\"}','[\"体重管理\", \"精力提升\"]',5.00,6.50,4.60,1.10,1.40,2.60,118,76,70,'2026-07-20 09:00:00','2026-04-01 09:00:00');
/*!40000 ALTER TABLE `patient_profile` ENABLE KEYS */;
UNLOCK TABLES;
SET @@SESSION.SQL_LOG_BIN = @MYSQLDUMP_TEMP_LOG_BIN;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-04 13:11:30
