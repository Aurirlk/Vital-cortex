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

SET @@GLOBAL.GTID_PURGED=/*!80000 '+'*/ '68c8e41b-479b-11f1-98c8-005056c00001:1-49061';

--
-- Table structure for table `appointment`
--

DROP TABLE IF EXISTS `appointment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `appointment` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `patient_id` int NOT NULL COMMENT '患者用户ID',
  `doctor_id` int NOT NULL COMMENT '医生ID',
  `schedule_id` int NOT NULL COMMENT '排班ID',
  `department_id` int NOT NULL COMMENT '科室ID',
  `appointment_date` date NOT NULL COMMENT '预约日期',
  `time_slot` varchar(20) NOT NULL COMMENT '时间段(morning/afternoon/evening)',
  `serial_number` int unsigned NOT NULL COMMENT '就诊序号',
  `symptom_description` varchar(1000) DEFAULT NULL COMMENT '症状描述',
  `cancel_reason` varchar(500) DEFAULT NULL COMMENT '取消原因',
  `status` tinyint(1) DEFAULT '0' COMMENT '状态(0:待确认;1:已确认;2:已完成;3:已取消;4:已爽约)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_serial` (`schedule_id`,`serial_number`),
  KEY `idx_patient_id` (`patient_id`),
  KEY `idx_doctor_id` (`doctor_id`),
  KEY `idx_appointment_date` (`appointment_date`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=5006 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `appointment`
--

LOCK TABLES `appointment` WRITE;
/*!40000 ALTER TABLE `appointment` DISABLE KEYS */;
INSERT INTO `appointment` VALUES (5001,1006,3001,4001,1,'2026-07-07','morning',1,'头晕、血压偏高',NULL,2,'2026-07-02 10:00:00','2026-08-28 13:36:25'),(5002,1007,3001,4001,1,'2026-07-07','morning',2,'血糖控制不佳',NULL,1,'2026-07-02 14:00:00','2026-08-28 13:36:25'),(5003,1008,3002,4003,2,'2026-07-07','morning',1,'膝盖疼痛',NULL,0,'2026-07-03 09:00:00','2026-08-28 13:36:25'),(5004,1009,3003,4005,3,'2026-07-07','afternoon',1,'孩子发烧咳嗽',NULL,1,'2026-07-03 16:00:00','2026-08-28 13:36:25'),(5005,1010,3001,4002,1,'2026-07-07','afternoon',1,'血压不稳定',NULL,0,'2026-07-04 11:00:00','2026-08-28 13:36:25');
/*!40000 ALTER TABLE `appointment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `visit_record`
--

DROP TABLE IF EXISTS `visit_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `visit_record` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `appointment_id` int NOT NULL COMMENT '预约ID',
  `patient_id` int NOT NULL COMMENT '患者ID',
  `doctor_id` int NOT NULL COMMENT '医生ID',
  `chief_complaint` varchar(1000) DEFAULT NULL COMMENT '主诉',
  `present_illness` text COMMENT '现病史',
  `diagnosis` varchar(500) DEFAULT NULL COMMENT '诊断',
  `prescription` text COMMENT '处方/医嘱',
  `examination_results` text COMMENT '检查结果',
  `follow_up_plan` varchar(500) DEFAULT NULL COMMENT '随访计划',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_appointment` (`appointment_id`),
  KEY `idx_patient_id` (`patient_id`),
  KEY `idx_doctor_id` (`doctor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='就诊记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `visit_record`
--

LOCK TABLES `visit_record` WRITE;
/*!40000 ALTER TABLE `visit_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `visit_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `followup_task`
--

DROP TABLE IF EXISTS `followup_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `followup_task` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `patient_id` int NOT NULL COMMENT '患者用户ID',
  `doctor_id` int NOT NULL COMMENT '医生ID',
  `title` varchar(200) NOT NULL COMMENT '任务标题',
  `description` varchar(1000) DEFAULT NULL COMMENT '任务描述',
  `task_type` varchar(50) NOT NULL COMMENT '任务类型(medication/appointment/indicator/exercise/diet)',
  `due_date` date NOT NULL COMMENT '截止日期',
  `status` tinyint(1) DEFAULT '0' COMMENT '状态(0:待完成;1:进行中;2:已完成;3:已逾期;4:已取消)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_patient_id` (`patient_id`),
  KEY `idx_doctor_id` (`doctor_id`),
  KEY `idx_due_date` (`due_date`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=12006 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='随访任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `followup_task`
--

LOCK TABLES `followup_task` WRITE;
/*!40000 ALTER TABLE `followup_task` DISABLE KEYS */;
INSERT INTO `followup_task` VALUES (12001,1006,3001,'每日血压监测','请每天早晚各测量一次血压并记录','indicator','2026-07-14',1,'2026-07-01 10:00:00','2026-08-28 13:36:25'),(12002,1006,3001,'按时服药','请按时服用降压药，不要擅自停药','medication','2026-07-14',0,'2026-07-01 10:00:00','2026-08-28 13:36:25'),(12003,1007,3001,'血糖监测','请每天监测空腹和餐后血糖','indicator','2026-07-14',1,'2026-07-02 14:00:00','2026-08-28 13:36:25'),(12004,1007,3001,'饮食控制','请控制饮食，避免高糖食物','diet','2026-07-14',0,'2026-07-02 14:00:00','2026-08-28 13:36:25'),(12005,1008,3002,'术后复查','请于7月10日来院复查','appointment','2026-07-10',0,'2026-07-03 09:00:00','2026-08-28 13:36:25');
/*!40000 ALTER TABLE `followup_task` ENABLE KEYS */;
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

-- Dump completed on 2026-10-04 13:18:17
