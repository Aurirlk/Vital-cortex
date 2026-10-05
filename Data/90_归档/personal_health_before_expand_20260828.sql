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

SET @@GLOBAL.GTID_PURGED=/*!80000 '+'*/ '68c8e41b-479b-11f1-98c8-005056c00001:1-46745';

--
-- Table structure for table `ai_chat_record`
--

DROP TABLE IF EXISTS `ai_chat_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_record` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `conversation_id` int NOT NULL,
  `user_id` int NOT NULL,
  `role` varchar(20) NOT NULL COMMENT 'user/assistant',
  `content` text,
  `agent_type` varchar(50) DEFAULT '',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_record`
--

LOCK TABLES `ai_chat_record` WRITE;
/*!40000 ALTER TABLE `ai_chat_record` DISABLE KEYS */;
INSERT INTO `ai_chat_record` VALUES (1,1,3,'user','医生你好，我确诊高血压半年了，日常饮食有什么需要特别注意的吗？','nutritionist','2026-08-20 10:28:05'),(2,1,3,'assistant','高血压患者的饮食核心原则是“低钠、高钾、控脂”。建议：1）每日食盐不超过5克，少吃腌制食品和加工肉；2）多吃富含钾的蔬果如香蕉、菠菜；3）减少动物脂肪，优选橄榄油等不饱和脂肪酸；4）限制饮酒。配合目前用药，定期监测血压会更稳妥。','nutritionist','2026-08-20 10:29:10'),(3,1,3,'user','那每天盐具体吃多少合适？酱油算不算盐？','nutritionist','2026-08-20 10:31:00'),(4,1,3,'assistant','每天食盐总量建议不超过5克（约一啤酒瓶盖平铺量）。酱油、蚝油、豆瓣酱含盐都很高，10毫升酱油约含1.5克盐，需计入总量。建议做菜少放盐、起锅前再放，味道更明显也更省盐。','nutritionist','2026-08-20 10:32:00'),(5,2,3,'user','这是我最近的体检报告，能帮我看看吗？','analyst','2026-08-22 15:00:05'),(6,2,3,'assistant','根据您的报告：收缩压138/舒张压88处于正常高值，空腹血糖5.4 mmol/L正常，低密度脂蛋白（LDL）2.9 mmol/L略偏高，BMI 23.67正常。整体看血压和血脂是需要关注的重点，建议加强生活方式干预并复查。','analyst','2026-08-22 15:02:00'),(7,2,3,'user','低密度脂蛋白偏高要紧吗？需要吃药吗？','analyst','2026-08-22 15:05:00'),(8,2,3,'assistant','LDL 2.9属于边缘升高。对于已确诊高血压的人群，一般建议把LDL控制在2.6以下以护血管。目前可先通过饮食（减少饱和脂肪）+运动观察3个月，若仍偏高再考虑他汀类药物。请以主治医生意见为准。','analyst','2026-08-22 15:07:00'),(9,2,3,'user','好的，那运动方面有什么建议？','analyst','2026-08-22 15:09:00'),(10,2,3,'assistant','推荐中等强度有氧运动，如快走、慢跑、游泳，每周5次、每次30分钟。运动时心率控制在（220-年龄）×60%~70%，即约112~130次/分，微微出汗、能说话但不能唱歌的强度最合适。','analyst','2026-08-22 15:10:00'),(11,3,3,'user','最近工作压力大，总是失眠，半夜醒了好几次。','psychologist','2026-08-25 21:40:05'),(12,3,3,'assistant','长期失眠会放大压力，形成恶性循环。建议先建立“睡眠卫生”：固定作息、睡前1小时远离手机蓝光、卧室只用于睡觉。可试试4-7-8呼吸法（吸气4秒、屏息7秒、呼气8秒）帮助放松。','psychologist','2026-08-25 21:42:00'),(13,3,3,'user','有没有什么快速放松的方法？','psychologist','2026-08-25 21:44:00'),(14,3,3,'assistant','可以试试“渐进式肌肉放松”：从脚趾开始，依次绷紧再放松每个部位，到头顶约10分钟；或听白噪音/轻音乐。若两周仍无改善，建议到睡眠门诊评估，排除焦虑因素。','psychologist','2026-08-25 21:45:00');
/*!40000 ALTER TABLE `ai_chat_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_config`
--

DROP TABLE IF EXISTS `ai_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_config` (
  `id` int NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` text COMMENT '配置值',
  `description` varchar(255) DEFAULT '',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_config`
--

LOCK TABLES `ai_config` WRITE;
/*!40000 ALTER TABLE `ai_config` DISABLE KEYS */;
INSERT INTO `ai_config` VALUES (1,'provider','deepseek','AI厂商','2026-08-19 02:08:10','2026-08-19 02:08:10'),(2,'api_key','','普通对话API Key','2026-08-19 02:08:10','2026-08-19 02:08:10'),(3,'api_url','https://api.deepseek.com/v1/chat/completions','API地址','2026-08-19 02:08:10','2026-08-19 02:08:10'),(4,'model','deepseek-v4-flash','模型','2026-08-19 02:08:10','2026-08-19 02:08:10'),(5,'reasoner_api_key','','深度思考API Key','2026-08-19 02:08:10','2026-08-19 02:08:10'),(6,'reasoner_api_url','https://api.deepseek.com/v1/chat/completions','深度思考API地址','2026-08-19 02:08:10','2026-08-19 02:08:10'),(7,'reasoner_model','deepseek-v4-pro','深度思考模型','2026-08-19 02:08:10','2026-08-19 02:08:10'),(8,'embedding_api_key','','Embedding API Key','2026-08-19 02:08:10','2026-08-19 02:08:10'),(9,'embedding_api_url','https://api.deepseek.com/v1/embeddings','Embedding API地址','2026-08-19 02:08:10','2026-08-19 02:08:10'),(10,'embedding_model','text-embedding-3-small','Embedding模型','2026-08-19 02:08:10','2026-08-19 02:08:10'),(11,'web_search_enabled','true','联网搜索启用','2026-08-19 02:08:10','2026-08-19 02:08:10'),(12,'web_search_provider','auto','搜索引擎','2026-08-19 02:08:10','2026-08-19 02:08:10'),(13,'connect_timeout','30000','连接超时','2026-08-19 02:08:10','2026-08-19 02:08:10'),(14,'read_timeout','60000','读取超时','2026-08-19 02:08:10','2026-08-19 02:08:10'),(15,'max_tokens','4096','最大Token数','2026-08-19 02:08:10','2026-08-19 02:08:10'),(16,'max_history_rounds','10','最大历史轮数','2026-08-19 02:08:10','2026-08-19 02:08:10');
/*!40000 ALTER TABLE `ai_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_conversation`
--

DROP TABLE IF EXISTS `ai_conversation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_conversation` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `title` varchar(255) DEFAULT '',
  `agent_type` varchar(50) DEFAULT '',
  `message_count` int DEFAULT '0',
  `last_message_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_agent_type` (`agent_type`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_conversation`
--

LOCK TABLES `ai_conversation` WRITE;
/*!40000 ALTER TABLE `ai_conversation` DISABLE KEYS */;
INSERT INTO `ai_conversation` VALUES (1,3,'高血压日常饮食需要注意什么？','nutritionist',4,'2026-08-20 10:32:00','2026-08-20 10:28:00','2026-08-20 10:32:00'),(2,3,'帮我解读一下体检报告','analyst',6,'2026-08-22 15:10:00','2026-08-22 15:00:00','2026-08-22 15:10:00'),(3,3,'最近总是失眠睡不好','psychologist',4,'2026-08-25 21:45:00','2026-08-25 21:40:00','2026-08-25 21:45:00');
/*!40000 ALTER TABLE `ai_conversation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `drug`
--

DROP TABLE IF EXISTS `drug`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `drug` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL COMMENT '药品名称',
  `generic_name` varchar(200) DEFAULT NULL COMMENT '通用名',
  `category` varchar(100) DEFAULT NULL COMMENT '分类',
  `description` text COMMENT '说明',
  `price` decimal(10,2) DEFAULT NULL,
  `unit` varchar(50) DEFAULT NULL,
  `specification` varchar(200) DEFAULT NULL COMMENT '规格',
  `manufacturer` varchar(200) DEFAULT NULL COMMENT '厂家',
  `cover` varchar(500) DEFAULT NULL,
  `is_otc` tinyint(1) DEFAULT '1' COMMENT '0:处方药;1:OTC',
  `stock` int DEFAULT '0',
  `status` tinyint(1) DEFAULT '1' COMMENT '0:下架;1:上架',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `drug`
--

LOCK TABLES `drug` WRITE;
/*!40000 ALTER TABLE `drug` DISABLE KEYS */;
INSERT INTO `drug` VALUES (1,'苯磺酸氨氯地平片','氨氯地平','降压药','钙通道阻滞剂，用于高血压、心绞痛。',28.50,'盒','5mg*7片','辉瑞制药',NULL,0,120,1,'2026-08-28 12:19:41'),(2,'二甲双胍缓释片','二甲双胍','降糖药','2型糖尿病一线用药，改善胰岛素敏感性。',35.00,'盒','0.5g*30片','中美上海施贵宝',NULL,0,80,1,'2026-08-28 12:19:41'),(3,'阿司匹林肠溶片','阿司匹林','抗血小板','抑制血小板聚集，用于心脑血管疾病预防。',15.80,'盒','100mg*30片','拜耳医药',NULL,0,200,1,'2026-08-28 12:19:41'),(4,'维生素D3软胶囊','维生素D3','营养补充','促进钙吸收，维护骨骼健康。',49.90,'瓶','400IU*60粒','汤臣倍健',NULL,1,150,1,'2026-08-28 12:19:41');
/*!40000 ALTER TABLE `drug` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `drug_subscription`
--

DROP TABLE IF EXISTS `drug_subscription`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `drug_subscription` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `drug_id` int NOT NULL,
  `quantity` int DEFAULT '1',
  `status` tinyint(1) DEFAULT '1' COMMENT '0:取消;1:有效',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_drug` (`user_id`,`drug_id`),
  KEY `idx_drug_id` (`drug_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `drug_subscription`
--

LOCK TABLES `drug_subscription` WRITE;
/*!40000 ALTER TABLE `drug_subscription` DISABLE KEYS */;
INSERT INTO `drug_subscription` VALUES (1,3,1,1,1,'2026-08-28 12:19:41','2026-08-28 12:19:41');
/*!40000 ALTER TABLE `drug_subscription` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `evaluations`
--

DROP TABLE IF EXISTS `evaluations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `evaluations` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `parent_id` int DEFAULT NULL COMMENT '父评论ID',
  `commenter_id` int DEFAULT NULL COMMENT '评论者ID',
  `replier_id` int DEFAULT NULL COMMENT '回复者ID',
  `content_type` varchar(100) DEFAULT NULL,
  `content_id` int DEFAULT NULL,
  `content` varchar(255) DEFAULT NULL,
  `upvote_list` longtext COMMENT '点赞列表',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `evaluations`
--

LOCK TABLES `evaluations` WRITE;
/*!40000 ALTER TABLE `evaluations` DISABLE KEYS */;
/*!40000 ALTER TABLE `evaluations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `health_model_config`
--

DROP TABLE IF EXISTS `health_model_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `health_model_config` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int DEFAULT NULL COMMENT '用户ID(NULL=全局)',
  `name` varchar(100) DEFAULT NULL COMMENT '指标名称',
  `detail` varchar(500) DEFAULT NULL COMMENT '指标描述',
  `cover` varchar(255) DEFAULT NULL COMMENT '图标',
  `unit` varchar(50) DEFAULT NULL COMMENT '单位',
  `symbol` varchar(100) DEFAULT NULL COMMENT '符号',
  `value_range` varchar(100) DEFAULT NULL COMMENT '正常范围',
  `is_global` tinyint(1) DEFAULT '0' COMMENT '是否全局(0:否;1:是)',
  `category` varchar(20) DEFAULT 'PERSONALIZED' COMMENT 'PUBLIC/PERSONALIZED',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `health_model_config`
--

LOCK TABLES `health_model_config` WRITE;
/*!40000 ALTER TABLE `health_model_config` DISABLE KEYS */;
INSERT INTO `health_model_config` VALUES (1,NULL,'收缩压','心脏收缩时动脉血压最高值','blood-pressure','mmHg','SBP','90,140',1,'PUBLIC'),(2,NULL,'舒张压','心脏舒张时动脉血压最低值','blood-pressure','mmHg','DBP','60,90',1,'PUBLIC'),(3,NULL,'空腹血糖','空腹时血液中的葡萄糖浓度','blood-sugar','mmol/L','FPG','3.9,6.1',1,'PUBLIC'),(4,NULL,'体重指数','体重与身高的平方之比','bmi','kg/m²','BMI','18.5,24.9',1,'PERSONALIZED'),(5,NULL,'心率','每分钟心跳次数','heart-rate','次/分','HR','60,100',1,'PERSONALIZED');
/*!40000 ALTER TABLE `health_model_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `message`
--

DROP TABLE IF EXISTS `message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `content` varchar(500) DEFAULT NULL,
  `user_id` int DEFAULT NULL COMMENT '接收者ID',
  `send_id` int DEFAULT NULL COMMENT '发送者ID',
  `replier_id` int DEFAULT NULL,
  `is_read` tinyint(1) DEFAULT '0' COMMENT '未读:0;已读:1',
  `other` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `message`
--

LOCK TABLES `message` WRITE;
/*!40000 ALTER TABLE `message` DISABLE KEYS */;
INSERT INTO `message` VALUES (1,'欢迎使用智康云健康管理系统！您的健康档案已创建，建议每月更新一次健康数据。',3,1,NULL,0,NULL,'2026-08-19 09:00:00'),(2,'提醒：您订阅的「苯磺酸氨氯地平片」用药时间到了，请按时服药。',3,1,NULL,0,NULL,'2026-08-26 08:00:00');
/*!40000 ALTER TABLE `message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `news`
--

DROP TABLE IF EXISTS `news`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `news` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(200) DEFAULT NULL COMMENT '标题',
  `content` longtext COMMENT '内容',
  `tag_id` int DEFAULT NULL COMMENT '分类ID',
  `cover` varchar(255) DEFAULT NULL COMMENT '封面图',
  `reader_ids` text COMMENT '阅读者ID列表',
  `is_top` tinyint(1) DEFAULT NULL COMMENT '是否置顶',
  `is_banner` tinyint(1) DEFAULT NULL COMMENT '是否轮播图',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `news`
--

LOCK TABLES `news` WRITE;
/*!40000 ALTER TABLE `news` DISABLE KEYS */;
/*!40000 ALTER TABLE `news` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `news_save`
--

DROP TABLE IF EXISTS `news_save`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `news_save` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int DEFAULT NULL,
  `news_id` int DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_news_id` (`news_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `news_save`
--

LOCK TABLES `news_save` WRITE;
/*!40000 ALTER TABLE `news_save` DISABLE KEYS */;
/*!40000 ALTER TABLE `news_save` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `patient_profile`
--

LOCK TABLES `patient_profile` WRITE;
/*!40000 ALTER TABLE `patient_profile` DISABLE KEYS */;
INSERT INTO `patient_profile` VALUES (1,3,'男',34,'1992-03-15',175.00,72.50,23.67,'[\"高血压\"]','[\"青霉素\"]','[\"氨氯地平\"]','[]','[\"糖尿病\"]','{\"smoking\": false, \"drinking\": \"偶尔\", \"exercise\": \"每周3次\"}','[\"控制血压\", \"减重5kg\"]',5.40,7.80,4.80,1.50,1.40,2.90,138,88,72,'2026-08-28 12:19:41','2026-08-28 12:19:41');
/*!40000 ALTER TABLE `patient_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `system_config`
--

DROP TABLE IF EXISTS `system_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_config` (
  `id` int NOT NULL AUTO_INCREMENT,
  `config_group` varchar(50) NOT NULL COMMENT '配置分组',
  `config_key` varchar(100) NOT NULL COMMENT '配置键名',
  `config_value` text COMMENT '配置值',
  `description` varchar(255) DEFAULT '',
  `sensitive` tinyint(1) DEFAULT '0' COMMENT '是否敏感',
  `value_type` varchar(20) DEFAULT 'string' COMMENT '值类型',
  `default_value` varchar(500) DEFAULT '',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_key` (`config_group`,`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `system_config`
--

LOCK TABLES `system_config` WRITE;
/*!40000 ALTER TABLE `system_config` DISABLE KEYS */;
INSERT INTO `system_config` VALUES (1,'mysql','url','jdbc:mysql://localhost:3306/personal_health?characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true','数据库URL',1,'string','','2026-08-19 02:08:10','2026-08-19 02:08:10'),(2,'mysql','username','root','数据库用户名',1,'string','root','2026-08-19 02:08:10','2026-08-19 02:08:10'),(3,'mysql','password','1234','数据库密码',1,'string','','2026-08-19 02:08:10','2026-08-19 02:08:10'),(4,'mysql','driver','com.mysql.cj.jdbc.Driver','驱动类',0,'string','com.mysql.cj.jdbc.Driver','2026-08-19 02:08:10','2026-08-19 02:08:10'),(5,'mysql','pool-min-idle','5','最小空闲连接',0,'number','5','2026-08-19 02:08:10','2026-08-19 02:08:10'),(6,'mysql','pool-max-size','20','最大连接数',0,'number','20','2026-08-19 02:08:10','2026-08-19 02:08:10'),(7,'server','port','21090','服务端口',0,'number','21090','2026-08-19 02:08:10','2026-08-19 02:08:10'),(8,'server','context-path','/api/personal-health/v1.0','上下文路径',0,'string','/api/personal-health/v1.0','2026-08-19 02:08:10','2026-08-19 02:08:10'),(9,'websocket','enabled','true','WebSocket启用',0,'boolean','true','2026-08-19 02:08:10','2026-08-19 02:08:10'),(10,'websocket','port','21091','WebSocket端口',0,'number','21091','2026-08-19 02:08:10','2026-08-19 02:08:10'),(11,'websocket','max-connections','1000','最大连接数',0,'number','1000','2026-08-19 02:08:10','2026-08-19 02:08:10'),(12,'ota','enabled','false','OTA启用',0,'boolean','false','2026-08-19 02:08:10','2026-08-19 02:08:10'),(13,'ota','server-url','','OTA服务器',0,'string','','2026-08-19 02:08:10','2026-08-19 02:08:10'),(14,'ota','check-interval','3600','检查间隔',0,'number','3600','2026-08-19 02:08:10','2026-08-19 02:08:10'),(15,'sqlite','enabled','false','SQLite启用',0,'boolean','false','2026-08-19 02:08:10','2026-08-19 02:08:10'),(16,'sqlite','db-path','./data/local.db','SQLite路径',0,'string','./data/local.db','2026-08-19 02:08:10','2026-08-19 02:08:10'),(17,'ai','provider','deepseek','AI厂商',0,'string','deepseek','2026-08-19 02:08:10','2026-08-19 02:08:10'),(18,'ai','api-key','','AI API Key',1,'string','','2026-08-19 02:08:10','2026-08-19 02:08:10'),(19,'ai','reasoner-api-key','','深度思考Key',1,'string','','2026-08-19 02:08:10','2026-08-19 02:08:10'),(20,'ai','bocha-api-key','','博查搜索Key',1,'string','','2026-08-19 02:08:10','2026-08-19 02:08:10'),(21,'ai','max-tokens','4096','最大Token数',0,'number','4096','2026-08-19 02:08:10','2026-08-19 02:08:10'),(22,'jwt','secret','CHANGE_ME_JWT_SECRET_AT_LEAST_32BYTES','JWT密钥(生产必须用环境变量JWT_SECRET覆盖)',1,'string','CHANGE_ME_JWT_SECRET_AT_LEAST_32BYTES','2026-08-19 02:08:10','2026-08-19 02:08:10'),(23,'jwt','expiration','604800000','JWT过期时间(ms)',0,'number','604800000','2026-08-19 02:08:10','2026-08-19 02:08:10'),(24,'admin','password','admin123','管理员密码',1,'string','admin123','2026-08-19 02:08:10','2026-08-19 02:08:10');
/*!40000 ALTER TABLE `system_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tags`
--

DROP TABLE IF EXISTS `tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tags` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(50) DEFAULT NULL COMMENT '分类名称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tags`
--

LOCK TABLES `tags` WRITE;
/*!40000 ALTER TABLE `tags` DISABLE KEYS */;
INSERT INTO `tags` VALUES (1,'饮食健康','2026-08-19 02:08:10'),(2,'运动健身','2026-08-19 02:08:10'),(3,'心理健康','2026-08-19 02:08:10'),(4,'疾病预防','2026-08-19 02:08:10'),(5,'养生保健','2026-08-19 02:08:10');
/*!40000 ALTER TABLE `tags` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_account` varchar(50) NOT NULL COMMENT '用户账号',
  `user_name` varchar(50) DEFAULT NULL COMMENT '用户昵称',
  `user_pwd` varchar(100) NOT NULL COMMENT '密码(BCrypt)',
  `user_avatar` varchar(255) DEFAULT NULL COMMENT '头像',
  `user_email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `user_role` int DEFAULT '2' COMMENT '角色(1:管理员;2:用户)',
  `is_login` tinyint(1) DEFAULT '0' COMMENT '是否可登录(0:可登录;1:锁定禁止)',
  `is_word` tinyint(1) DEFAULT '0' COMMENT '禁言(0:正常;1:禁言)',
  `is_vip` tinyint(1) DEFAULT '0' COMMENT 'VIP(0:否;1:是)',
  `vip_expire_time` datetime DEFAULT NULL COMMENT 'VIP到期时间(NULL=永久)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_account` (`user_account`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (1,'admin','管理员','$2a$10$HEbR7/pgslM4n7FCsYUAuuLQhMmK3e.78I5A.WjNRwqozsO4rOT.u',NULL,NULL,1,0,0,0,NULL,'2026-08-19 02:08:10'),(2,'user','普通用户','$2a$10$HEbR7/pgslM4n7FCsYUAuuLQhMmK3e.78I5A.WjNRwqozsO4rOT.u',NULL,NULL,2,0,0,0,NULL,'2026-08-19 02:08:10'),(3,'demo','张小明','$2a$10$HEbR7/pgslM4n7FCsYUAuuLQhMmK3e.78I5A.WjNRwqozsO4rOT.u',NULL,'demo@example.com',2,0,0,0,NULL,'2026-08-28 12:19:41');
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_health`
--

DROP TABLE IF EXISTS `user_health`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_health` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int DEFAULT NULL,
  `health_model_config_id` int DEFAULT NULL,
  `value` varchar(100) DEFAULT NULL COMMENT '记录值',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_health_model_config_id` (`health_model_config_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_health`
--

LOCK TABLES `user_health` WRITE;
/*!40000 ALTER TABLE `user_health` DISABLE KEYS */;
INSERT INTO `user_health` VALUES (1,3,1,'138','2026-08-28 12:19:41'),(2,3,2,'88','2026-08-28 12:19:41'),(3,3,3,'5.4','2026-08-28 12:19:41'),(4,3,4,'23.67','2026-08-28 12:19:41'),(5,3,5,'72','2026-08-28 12:19:41');
/*!40000 ALTER TABLE `user_health` ENABLE KEYS */;
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

-- Dump completed on 2026-08-28 12:27:36
