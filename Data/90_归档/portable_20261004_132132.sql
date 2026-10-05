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

SET @@GLOBAL.GTID_PURGED=/*!80000 '+'*/ '68c8e41b-479b-11f1-98c8-005056c00001:1-49292';

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
) ENGINE=InnoDB AUTO_INCREMENT=183 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_record`
--

LOCK TABLES `ai_chat_record` WRITE;
/*!40000 ALTER TABLE `ai_chat_record` DISABLE KEYS */;
INSERT INTO `ai_chat_record` VALUES (1,1,3,'user','医生你好，我确诊高血压半年了，日常饮食有什么需要特别注意的吗？','nutritionist','2026-08-20 10:28:05'),(2,1,3,'assistant','高血压患者的饮食核心原则是“低钠、高钾、控脂”。建议：1）每日食盐不超过5克，少吃腌制食品和加工肉；2）多吃富含钾的蔬果如香蕉、菠菜；3）减少动物脂肪，优选橄榄油等不饱和脂肪酸；4）限制饮酒。配合目前用药，定期监测血压会更稳妥。','nutritionist','2026-08-20 10:29:10'),(3,1,3,'user','那每天盐具体吃多少合适？酱油算不算盐？','nutritionist','2026-08-20 10:31:00'),(4,1,3,'assistant','每天食盐总量建议不超过5克（约一啤酒瓶盖平铺量）。酱油、蚝油、豆瓣酱含盐都很高，10毫升酱油约含1.5克盐，需计入总量。建议做菜少放盐、起锅前再放，味道更明显也更省盐。','nutritionist','2026-08-20 10:32:00'),(5,2,3,'user','这是我最近的体检报告，能帮我看看吗？','analyst','2026-08-22 15:00:05'),(6,2,3,'assistant','根据您的报告：收缩压138/舒张压88处于正常高值，空腹血糖5.4 mmol/L正常，低密度脂蛋白（LDL）2.9 mmol/L略偏高，BMI 23.67正常。整体看血压和血脂是需要关注的重点，建议加强生活方式干预并复查。','analyst','2026-08-22 15:02:00'),(7,2,3,'user','低密度脂蛋白偏高要紧吗？需要吃药吗？','analyst','2026-08-22 15:05:00'),(8,2,3,'assistant','LDL 2.9属于边缘升高。对于已确诊高血压的人群，一般建议把LDL控制在2.6以下以护血管。目前可先通过饮食（减少饱和脂肪）+运动观察3个月，若仍偏高再考虑他汀类药物。请以主治医生意见为准。','analyst','2026-08-22 15:07:00'),(9,2,3,'user','好的，那运动方面有什么建议？','analyst','2026-08-22 15:09:00'),(10,2,3,'assistant','推荐中等强度有氧运动，如快走、慢跑、游泳，每周5次、每次30分钟。运动时心率控制在（220-年龄）×60%~70%，即约112~130次/分，微微出汗、能说话但不能唱歌的强度最合适。','analyst','2026-08-22 15:10:00'),(11,3,3,'user','最近工作压力大，总是失眠，半夜醒了好几次。','psychologist','2026-08-25 21:40:05'),(12,3,3,'assistant','长期失眠会放大压力，形成恶性循环。建议先建立“睡眠卫生”：固定作息、睡前1小时远离手机蓝光、卧室只用于睡觉。可试试4-7-8呼吸法（吸气4秒、屏息7秒、呼气8秒）帮助放松。','psychologist','2026-08-25 21:42:00'),(13,3,3,'user','有没有什么快速放松的方法？','psychologist','2026-08-25 21:44:00'),(14,3,3,'assistant','可以试试“渐进式肌肉放松”：从脚趾开始，依次绷紧再放松每个部位，到头顶约10分钟；或听白噪音/轻音乐。若两周仍无改善，建议到睡眠门诊评估，排除焦虑因素。','psychologist','2026-08-25 21:45:00'),(99,26,2,'user','医生你好，我有高血压，平时饮食需要注意什么？','nutritionist','2026-05-12 20:00:00'),(100,26,2,'assistant','高血压患者建议低盐饮食，每日食盐摄入控制在5克以内，多吃富含钾的蔬果如香蕉、菠菜，减少腌制食品与加工肉制品。','nutritionist','2026-05-12 20:02:00'),(101,26,2,'user','那酱油和味精还能吃吗？','nutritionist','2026-05-12 20:04:00'),(102,26,2,'assistant','酱油含盐量不低，建议少量使用；可用柠檬汁、醋、香辛料替代部分咸味。味精适量无碍，但整体仍要控钠。','nutritionist','2026-05-12 20:06:00'),(103,27,2,'user','帮我看看最近的体检报告，低密度脂蛋白偏高','analyst','2026-06-12 20:07:00'),(104,27,2,'assistant','您的LDL-C为3.8 mmol/L，略高于理想值(<3.4)。结合甘油三酯也偏高，提示血脂代谢需关注，建议先生活方式干预。','analyst','2026-06-12 20:09:00'),(105,27,2,'user','需要马上吃药吗？','analyst','2026-06-12 20:11:00'),(106,27,2,'assistant','若无明显心血管病史，可先3个月饮食运动干预后复查；若持续偏高或合并糖尿病/高血压，再考虑他汀类药物。','analyst','2026-06-12 20:13:00'),(107,28,2,'user','我想开始跑步锻炼，怎么安排比较科学？','consultant','2026-07-12 20:14:00'),(108,28,2,'assistant','建议从快走+慢跑结合起步，每周3-4次、每次30分钟，配速以\"能说话但不能唱歌\"为宜，循序渐进增量（每周不超过10%）。','consultant','2026-07-12 20:16:00'),(109,28,2,'user','跑步后膝盖有点酸正常吗？','consultant','2026-07-12 20:18:00'),(110,28,2,'assistant','轻度酸胀多为肌肉适应，注意跑前热身、选缓震跑鞋；若关节刺痛或持续肿胀应暂停并就医评估。','consultant','2026-07-12 20:20:00'),(111,29,3,'user','医生你好，我有高血压，平时饮食需要注意什么？','nutritionist','2026-05-13 20:00:00'),(112,29,3,'assistant','高血压患者建议低盐饮食，每日食盐摄入控制在5克以内，多吃富含钾的蔬果如香蕉、菠菜，减少腌制食品与加工肉制品。','nutritionist','2026-05-13 20:02:00'),(113,29,3,'user','那酱油和味精还能吃吗？','nutritionist','2026-05-13 20:04:00'),(114,29,3,'assistant','酱油含盐量不低，建议少量使用；可用柠檬汁、醋、香辛料替代部分咸味。味精适量无碍，但整体仍要控钠。','nutritionist','2026-05-13 20:06:00'),(115,30,3,'user','帮我看看最近的体检报告，低密度脂蛋白偏高','analyst','2026-06-13 20:07:00'),(116,30,3,'assistant','您的LDL-C为3.8 mmol/L，略高于理想值(<3.4)。结合甘油三酯也偏高，提示血脂代谢需关注，建议先生活方式干预。','analyst','2026-06-13 20:09:00'),(117,30,3,'user','需要马上吃药吗？','analyst','2026-06-13 20:11:00'),(118,30,3,'assistant','若无明显心血管病史，可先3个月饮食运动干预后复查；若持续偏高或合并糖尿病/高血压，再考虑他汀类药物。','analyst','2026-06-13 20:13:00'),(119,31,3,'user','最近工作压力大，连续一周入睡困难','psychologist','2026-07-13 20:14:00'),(120,31,3,'assistant','可以从睡眠卫生入手：固定作息、睡前1小时远离屏幕、卧室保持黑暗安静；白天适度日晒也有助于褪黑素分泌。','psychologist','2026-07-13 20:16:00'),(121,31,3,'user','有没有具体的放松训练？','psychologist','2026-07-13 20:18:00'),(122,31,3,'assistant','推荐渐进式肌肉放松法：从脚趾开始依次紧张-放松各肌群，配合腹式呼吸，每次10-15分钟，睡前练习效果明显。','psychologist','2026-07-13 20:20:00'),(123,32,4,'user','医生你好，我有高血压，平时饮食需要注意什么？','nutritionist','2026-05-14 20:00:00'),(124,32,4,'assistant','高血压患者建议低盐饮食，每日食盐摄入控制在5克以内，多吃富含钾的蔬果如香蕉、菠菜，减少腌制食品与加工肉制品。','nutritionist','2026-05-14 20:02:00'),(125,32,4,'user','那酱油和味精还能吃吗？','nutritionist','2026-05-14 20:04:00'),(126,32,4,'assistant','酱油含盐量不低，建议少量使用；可用柠檬汁、醋、香辛料替代部分咸味。味精适量无碍，但整体仍要控钠。','nutritionist','2026-05-14 20:06:00'),(127,33,4,'user','我想三个月减脂5斤，可行吗？','general_assistant','2026-06-14 20:07:00'),(128,33,4,'assistant','这是合理且可持续的目标。建议每周运动150分钟中等强度+适度热量缺口(约300-500 kcal/天)，配合蛋白质摄入与睡眠，避免极端节食。','general_assistant','2026-06-14 20:09:00'),(129,34,4,'user','我想开始跑步锻炼，怎么安排比较科学？','consultant','2026-07-14 20:14:00'),(130,34,4,'assistant','建议从快走+慢跑结合起步，每周3-4次、每次30分钟，配速以\"能说话但不能唱歌\"为宜，循序渐进增量（每周不超过10%）。','consultant','2026-07-14 20:16:00'),(131,34,4,'user','跑步后膝盖有点酸正常吗？','consultant','2026-07-14 20:18:00'),(132,34,4,'assistant','轻度酸胀多为肌肉适应，注意跑前热身、选缓震跑鞋；若关节刺痛或持续肿胀应暂停并就医评估。','consultant','2026-07-14 20:20:00'),(133,35,5,'user','喉咙痛伴低烧两天了，需要吃药吗？','doctor','2026-05-15 20:00:00'),(134,35,5,'assistant','若体温<38.5℃可先多休息、多饮水，用淡盐水漱口缓解咽痛；多为病毒自限性，通常5-7天好转。','doctor','2026-05-15 20:02:00'),(135,35,5,'user','什么情况必须去医院？','doctor','2026-05-15 20:04:00'),(136,35,5,'assistant','持续高热超过3天、呼吸困难、胸痛或吞咽困难，应及时就诊排除细菌感染或并发症。','doctor','2026-05-15 20:06:00'),(137,36,5,'user','帮我看看最近的体检报告，低密度脂蛋白偏高','analyst','2026-06-15 20:07:00'),(138,36,5,'assistant','您的LDL-C为3.8 mmol/L，略高于理想值(<3.4)。结合甘油三酯也偏高，提示血脂代谢需关注，建议先生活方式干预。','analyst','2026-06-15 20:09:00'),(139,36,5,'user','需要马上吃药吗？','analyst','2026-06-15 20:11:00'),(140,36,5,'assistant','若无明显心血管病史，可先3个月饮食运动干预后复查；若持续偏高或合并糖尿病/高血压，再考虑他汀类药物。','analyst','2026-06-15 20:13:00'),(141,37,5,'user','医生你好，我有高血压，平时饮食需要注意什么？','nutritionist','2026-07-15 20:14:00'),(142,37,5,'assistant','高血压患者建议低盐饮食，每日食盐摄入控制在5克以内，多吃富含钾的蔬果如香蕉、菠菜，减少腌制食品与加工肉制品。','nutritionist','2026-07-15 20:16:00'),(143,37,5,'user','那酱油和味精还能吃吗？','nutritionist','2026-07-15 20:18:00'),(144,37,5,'assistant','酱油含盐量不低，建议少量使用；可用柠檬汁、醋、香辛料替代部分咸味。味精适量无碍，但整体仍要控钠。','nutritionist','2026-07-15 20:20:00'),(145,38,5,'user','最近工作压力大，连续一周入睡困难','psychologist','2026-05-15 20:21:00'),(146,38,5,'assistant','可以从睡眠卫生入手：固定作息、睡前1小时远离屏幕、卧室保持黑暗安静；白天适度日晒也有助于褪黑素分泌。','psychologist','2026-05-15 20:23:00'),(147,38,5,'user','有没有具体的放松训练？','psychologist','2026-05-15 20:25:00'),(148,38,5,'assistant','推荐渐进式肌肉放松法：从脚趾开始依次紧张-放松各肌群，配合腹式呼吸，每次10-15分钟，睡前练习效果明显。','psychologist','2026-05-15 20:27:00'),(149,39,6,'user','医生你好，我有高血压，平时饮食需要注意什么？','nutritionist','2026-05-16 20:00:00'),(150,39,6,'assistant','高血压患者建议低盐饮食，每日食盐摄入控制在5克以内，多吃富含钾的蔬果如香蕉、菠菜，减少腌制食品与加工肉制品。','nutritionist','2026-05-16 20:02:00'),(151,39,6,'user','那酱油和味精还能吃吗？','nutritionist','2026-05-16 20:04:00'),(152,39,6,'assistant','酱油含盐量不低，建议少量使用；可用柠檬汁、醋、香辛料替代部分咸味。味精适量无碍，但整体仍要控钠。','nutritionist','2026-05-16 20:06:00'),(153,40,6,'user','帮我看看最近的体检报告，低密度脂蛋白偏高','analyst','2026-06-16 20:07:00'),(154,40,6,'assistant','您的LDL-C为3.8 mmol/L，略高于理想值(<3.4)。结合甘油三酯也偏高，提示血脂代谢需关注，建议先生活方式干预。','analyst','2026-06-16 20:09:00'),(155,40,6,'user','需要马上吃药吗？','analyst','2026-06-16 20:11:00'),(156,40,6,'assistant','若无明显心血管病史，可先3个月饮食运动干预后复查；若持续偏高或合并糖尿病/高血压，再考虑他汀类药物。','analyst','2026-06-16 20:13:00'),(157,41,6,'user','喉咙痛伴低烧两天了，需要吃药吗？','doctor','2026-07-16 20:14:00'),(158,41,6,'assistant','若体温<38.5℃可先多休息、多饮水，用淡盐水漱口缓解咽痛；多为病毒自限性，通常5-7天好转。','doctor','2026-07-16 20:16:00'),(159,41,6,'user','什么情况必须去医院？','doctor','2026-07-16 20:18:00'),(160,41,6,'assistant','持续高热超过3天、呼吸困难、胸痛或吞咽困难，应及时就诊排除细菌感染或并发症。','doctor','2026-07-16 20:20:00'),(161,42,7,'user','我想开始跑步锻炼，怎么安排比较科学？','consultant','2026-05-10 20:00:00'),(162,42,7,'assistant','建议从快走+慢跑结合起步，每周3-4次、每次30分钟，配速以\"能说话但不能唱歌\"为宜，循序渐进增量（每周不超过10%）。','consultant','2026-05-10 20:02:00'),(163,42,7,'user','跑步后膝盖有点酸正常吗？','consultant','2026-05-10 20:04:00'),(164,42,7,'assistant','轻度酸胀多为肌肉适应，注意跑前热身、选缓震跑鞋；若关节刺痛或持续肿胀应暂停并就医评估。','consultant','2026-05-10 20:06:00'),(165,43,7,'user','帮我看看最近的体检报告，低密度脂蛋白偏高','analyst','2026-06-10 20:07:00'),(166,43,7,'assistant','您的LDL-C为3.8 mmol/L，略高于理想值(<3.4)。结合甘油三酯也偏高，提示血脂代谢需关注，建议先生活方式干预。','analyst','2026-06-10 20:09:00'),(167,43,7,'user','需要马上吃药吗？','analyst','2026-06-10 20:11:00'),(168,43,7,'assistant','若无明显心血管病史，可先3个月饮食运动干预后复查；若持续偏高或合并糖尿病/高血压，再考虑他汀类药物。','analyst','2026-06-10 20:13:00'),(169,44,7,'user','最近工作压力大，连续一周入睡困难','psychologist','2026-07-10 20:14:00'),(170,44,7,'assistant','可以从睡眠卫生入手：固定作息、睡前1小时远离屏幕、卧室保持黑暗安静；白天适度日晒也有助于褪黑素分泌。','psychologist','2026-07-10 20:16:00'),(171,44,7,'user','有没有具体的放松训练？','psychologist','2026-07-10 20:18:00'),(172,44,7,'assistant','推荐渐进式肌肉放松法：从脚趾开始依次紧张-放松各肌群，配合腹式呼吸，每次10-15分钟，睡前练习效果明显。','psychologist','2026-07-10 20:20:00'),(173,45,8,'user','我想三个月减脂5斤，可行吗？','general_assistant','2026-05-11 20:00:00'),(174,45,8,'assistant','这是合理且可持续的目标。建议每周运动150分钟中等强度+适度热量缺口(约300-500 kcal/天)，配合蛋白质摄入与睡眠，避免极端节食。','general_assistant','2026-05-11 20:02:00'),(175,46,8,'user','医生你好，我有高血压，平时饮食需要注意什么？','nutritionist','2026-06-11 20:07:00'),(176,46,8,'assistant','高血压患者建议低盐饮食，每日食盐摄入控制在5克以内，多吃富含钾的蔬果如香蕉、菠菜，减少腌制食品与加工肉制品。','nutritionist','2026-06-11 20:09:00'),(177,46,8,'user','那酱油和味精还能吃吗？','nutritionist','2026-06-11 20:11:00'),(178,46,8,'assistant','酱油含盐量不低，建议少量使用；可用柠檬汁、醋、香辛料替代部分咸味。味精适量无碍，但整体仍要控钠。','nutritionist','2026-06-11 20:13:00'),(179,47,8,'user','最近工作压力大，连续一周入睡困难','psychologist','2026-07-11 20:14:00'),(180,47,8,'assistant','可以从睡眠卫生入手：固定作息、睡前1小时远离屏幕、卧室保持黑暗安静；白天适度日晒也有助于褪黑素分泌。','psychologist','2026-07-11 20:16:00'),(181,47,8,'user','有没有具体的放松训练？','psychologist','2026-07-11 20:18:00'),(182,47,8,'assistant','推荐渐进式肌肉放松法：从脚趾开始依次紧张-放松各肌群，配合腹式呼吸，每次10-15分钟，睡前练习效果明显。','psychologist','2026-07-11 20:20:00');
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
) ENGINE=InnoDB AUTO_INCREMENT=158 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_config`
--

LOCK TABLES `ai_config` WRITE;
/*!40000 ALTER TABLE `ai_config` DISABLE KEYS */;
INSERT INTO `ai_config` VALUES (1,'provider','deepseek','AI厂商','2026-08-19 02:08:10','2026-10-03 21:24:10'),(2,'api_key','','普通对话API Key','2026-08-19 02:08:10','2026-10-03 21:24:10'),(3,'api_url','https://api.deepseek.com/v1/chat/completions','API地址','2026-08-19 02:08:10','2026-10-03 21:24:10'),(4,'model','deepseek-v4-flash','模型','2026-08-19 02:08:10','2026-10-03 21:24:10'),(5,'reasoner_api_key','','深度思考API Key','2026-08-19 02:08:10','2026-10-03 21:24:10'),(6,'reasoner_api_url','https://api.deepseek.com/v1/chat/completions','深度思考API地址','2026-08-19 02:08:10','2026-10-03 21:24:10'),(7,'reasoner_model','deepseek-v4-pro','深度思考模型','2026-08-19 02:08:10','2026-10-03 21:24:10'),(8,'embedding_api_key','','Embedding API Key','2026-08-19 02:08:10','2026-10-03 21:24:10'),(9,'embedding_api_url','https://api.deepseek.com/v1/embeddings','Embedding API地址','2026-08-19 02:08:10','2026-10-03 21:24:10'),(10,'embedding_model','text-embedding-3-small','Embedding模型','2026-08-19 02:08:10','2026-10-03 21:24:10'),(11,'web_search_enabled','true','联网搜索启用','2026-08-19 02:08:10','2026-10-03 21:24:10'),(12,'web_search_provider','auto','搜索引擎','2026-08-19 02:08:10','2026-10-03 21:24:10'),(13,'connect_timeout','30000','连接超时','2026-08-19 02:08:10','2026-10-03 21:24:10'),(14,'read_timeout','60000','读取超时','2026-08-19 02:08:10','2026-10-03 21:24:10'),(15,'max_tokens','4096','最大Token数','2026-08-19 02:08:10','2026-10-03 21:24:10'),(16,'max_history_rounds','10','最大历史轮数','2026-08-19 02:08:10','2026-10-03 21:24:10'),(17,'bocha_api_key','','博查API Key','2026-08-28 17:11:12','2026-10-03 21:24:10'),(18,'bocha_api_url','https://api.bochaai.com/v1/web-search','博查API地址','2026-08-28 17:11:12','2026-10-03 21:24:10'),(19,'tavily_api_key','','Tavily API Key','2026-08-28 17:11:12','2026-10-03 21:24:10'),(20,'tavily_api_url','https://api.tavily.com/search','Tavily API地址','2026-08-28 17:11:12','2026-10-03 21:24:10'),(21,'duckduckgo_api_url','https://api.duckduckgo.com/','DuckDuckGo API地址','2026-08-28 17:11:12','2026-10-03 21:24:10'),(22,'serper_api_key','','Serper API Key','2026-08-28 17:11:12','2026-10-03 21:24:10'),(23,'serper_api_url','https://google.serper.dev/search','Serper API地址','2026-08-28 17:11:12','2026-10-03 21:24:10'),(24,'serpapi_api_key','','SerpAPI Key','2026-08-28 17:11:12','2026-10-03 21:24:10'),(25,'serpapi_api_url','https://serpapi.com/search','SerpAPI地址','2026-08-28 17:11:12','2026-10-03 21:24:10'),(26,'dify_api_key','','Dify API Key','2026-08-28 17:11:12','2026-10-03 21:24:10'),(27,'dify_base_url','http://localhost:5001/v1','Dify基础URL','2026-08-28 17:11:12','2026-10-03 21:24:10'),(28,'dify_keyword_workflow','/workflows/run','Dify关键词端点','2026-08-28 17:11:12','2026-10-03 21:24:10');
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
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_conversation`
--

LOCK TABLES `ai_conversation` WRITE;
/*!40000 ALTER TABLE `ai_conversation` DISABLE KEYS */;
INSERT INTO `ai_conversation` VALUES (1,3,'高血压日常饮食需要注意什么？','nutritionist',4,'2026-08-20 10:32:00','2026-08-20 10:28:00','2026-08-20 10:32:00'),(2,3,'帮我解读一下体检报告','analyst',6,'2026-08-22 15:10:00','2026-08-22 15:00:00','2026-08-22 15:10:00'),(3,3,'最近总是失眠睡不好','psychologist',4,'2026-08-25 21:45:00','2026-08-25 21:40:00','2026-08-25 21:45:00'),(26,2,'高血压饮食咨询','nutritionist',4,'2026-05-12 20:08:00','2026-05-12 20:00:00','2026-05-12 20:08:00'),(27,2,'体检报告解读','analyst',4,'2026-06-12 20:15:00','2026-06-12 20:07:00','2026-06-12 20:15:00'),(28,2,'运动计划咨询','consultant',4,'2026-07-12 20:22:00','2026-07-12 20:14:00','2026-07-12 20:22:00'),(29,3,'高血压饮食咨询','nutritionist',4,'2026-05-13 20:08:00','2026-05-13 20:00:00','2026-05-13 20:08:00'),(30,3,'体检报告解读','analyst',4,'2026-06-13 20:15:00','2026-06-13 20:07:00','2026-06-13 20:15:00'),(31,3,'失眠与压力管理','psychologist',4,'2026-07-13 20:22:00','2026-07-13 20:14:00','2026-07-13 20:22:00'),(32,4,'高血压饮食咨询','nutritionist',4,'2026-05-14 20:08:00','2026-05-14 20:00:00','2026-05-14 20:08:00'),(33,4,'健康目标设定','general_assistant',2,'2026-06-14 20:11:00','2026-06-14 20:07:00','2026-06-14 20:11:00'),(34,4,'运动计划咨询','consultant',4,'2026-07-14 20:22:00','2026-07-14 20:14:00','2026-07-14 20:22:00'),(35,5,'感冒症状咨询','doctor',4,'2026-05-15 20:08:00','2026-05-15 20:00:00','2026-05-15 20:08:00'),(36,5,'体检报告解读','analyst',4,'2026-06-15 20:15:00','2026-06-15 20:07:00','2026-06-15 20:15:00'),(37,5,'高血压饮食咨询','nutritionist',4,'2026-07-15 20:22:00','2026-07-15 20:14:00','2026-07-15 20:22:00'),(38,5,'失眠与压力管理','psychologist',4,'2026-05-15 20:29:00','2026-05-15 20:21:00','2026-05-15 20:29:00'),(39,6,'高血压饮食咨询','nutritionist',4,'2026-05-16 20:08:00','2026-05-16 20:00:00','2026-05-16 20:08:00'),(40,6,'体检报告解读','analyst',4,'2026-06-16 20:15:00','2026-06-16 20:07:00','2026-06-16 20:15:00'),(41,6,'感冒症状咨询','doctor',4,'2026-07-16 20:22:00','2026-07-16 20:14:00','2026-07-16 20:22:00'),(42,7,'运动计划咨询','consultant',4,'2026-05-10 20:08:00','2026-05-10 20:00:00','2026-05-10 20:08:00'),(43,7,'体检报告解读','analyst',4,'2026-06-10 20:15:00','2026-06-10 20:07:00','2026-06-10 20:15:00'),(44,7,'失眠与压力管理','psychologist',4,'2026-07-10 20:22:00','2026-07-10 20:14:00','2026-07-10 20:22:00'),(45,8,'健康目标设定','general_assistant',2,'2026-05-11 20:04:00','2026-05-11 20:00:00','2026-05-11 20:04:00'),(46,8,'高血压饮食咨询','nutritionist',4,'2026-06-11 20:15:00','2026-06-11 20:07:00','2026-06-11 20:15:00'),(47,8,'失眠与压力管理','psychologist',4,'2026-07-11 20:22:00','2026-07-11 20:14:00','2026-07-11 20:22:00');
/*!40000 ALTER TABLE `ai_conversation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_usage`
--

DROP TABLE IF EXISTS `ai_usage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `user_id` int DEFAULT NULL COMMENT '鐢ㄦ埛ID锛堝彲涓虹┖锛岄潪鐧诲綍鎬佽皟鐢?級',
  `scene` varchar(50) DEFAULT 'chat' COMMENT '鍦烘櫙锛歝hat/stream/keyword/websearch/rag_eval',
  `model` varchar(100) DEFAULT '' COMMENT '妯″瀷鍚',
  `prompt_tokens` int DEFAULT '0' COMMENT '杈撳叆token鏁',
  `completion_tokens` int DEFAULT '0' COMMENT '杈撳嚭token鏁',
  `total_tokens` int DEFAULT '0' COMMENT '鎬籺oken鏁',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_ai_usage_user` (`user_id`),
  KEY `idx_ai_usage_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI token 鐢ㄩ噺璁板綍';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_usage`
--

LOCK TABLES `ai_usage` WRITE;
/*!40000 ALTER TABLE `ai_usage` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_usage` ENABLE KEYS */;
UNLOCK TABLES;

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
INSERT INTO `appointment` VALUES (5001,3,3001,4001,1,'2026-07-07','morning',1,'头晕、血压偏高，近一周晨起明显',NULL,2,'2026-07-02 10:00:00','2026-10-04 13:21:17'),(5002,4,3001,4001,1,'2026-07-07','morning',2,'体检发现血糖偏高，家族有糖尿病史',NULL,1,'2026-07-02 14:00:00','2026-10-04 13:21:17'),(5003,5,3002,4003,2,'2026-07-07','morning',1,'反复上腹隐痛，肝功能指标异常',NULL,0,'2026-07-03 09:00:00','2026-10-04 13:21:17'),(5004,6,3003,4005,3,'2026-07-07','afternoon',1,'绝经后骨密度下降，需评估干预方案',NULL,1,'2026-07-03 16:00:00','2026-10-04 13:21:17'),(5005,7,3001,4002,1,'2026-07-07','afternoon',1,'血脂多项升高，询问饮食调理',NULL,0,'2026-07-04 11:00:00','2026-10-04 13:21:17');
/*!40000 ALTER TABLE `appointment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_log`
--

DROP TABLE IF EXISTS `audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int DEFAULT NULL COMMENT '操作用户ID',
  `user_name` varchar(50) DEFAULT NULL COMMENT '操作用户名',
  `action` varchar(100) NOT NULL COMMENT '操作类型(login/create/update/delete/export)',
  `resource` varchar(100) NOT NULL COMMENT '资源类型(user/ai_config/drug/news等)',
  `resource_id` varchar(50) DEFAULT NULL COMMENT '资源ID',
  `description` varchar(500) DEFAULT NULL COMMENT '操作描述',
  `ip_address` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `request_method` varchar(10) DEFAULT NULL COMMENT '请求方法',
  `request_url` varchar(500) DEFAULT NULL COMMENT '请求URL',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:失败;1:成功)',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_action` (`action`),
  KEY `idx_resource` (`resource`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全审计日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_log`
--

LOCK TABLES `audit_log` WRITE;
/*!40000 ALTER TABLE `audit_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `department`
--

DROP TABLE IF EXISTS `department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `department` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '科室名称',
  `description` varchar(500) DEFAULT NULL COMMENT '科室描述',
  `cover` varchar(500) DEFAULT NULL COMMENT '科室图片',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:停用;1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='科室表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `department`
--

LOCK TABLES `department` WRITE;
/*!40000 ALTER TABLE `department` DISABLE KEYS */;
INSERT INTO `department` VALUES (1,'内科','内脏疾病诊治',NULL,1,1,'2026-08-28 13:35:55'),(2,'外科','手术治疗',NULL,2,1,'2026-08-28 13:35:55'),(3,'儿科','儿童疾病',NULL,3,1,'2026-08-28 13:35:55'),(4,'妇产科','妇科及产科',NULL,4,1,'2026-08-28 13:35:55'),(5,'眼科','眼病诊治',NULL,5,1,'2026-08-28 13:35:55'),(6,'耳鼻喉科','耳鼻喉疾病',NULL,6,1,'2026-08-28 13:35:55'),(7,'皮肤科','皮肤病诊治',NULL,7,1,'2026-08-28 13:35:55'),(8,'中医科','中医诊疗',NULL,8,1,'2026-08-28 13:35:55'),(9,'骨科','骨关节疾病',NULL,9,1,'2026-08-28 13:35:55'),(10,'神经内科','神经系统疾病',NULL,10,1,'2026-08-28 13:35:55');
/*!40000 ALTER TABLE `department` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `doctor_schedule`
--

DROP TABLE IF EXISTS `doctor_schedule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `doctor_schedule` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `doctor_id` int NOT NULL COMMENT '医生ID',
  `schedule_date` date NOT NULL COMMENT '排班日期',
  `time_slot` varchar(20) NOT NULL COMMENT '时间段(morning/afternoon/evening)',
  `max_patients` int unsigned NOT NULL DEFAULT '30' COMMENT '号源总数',
  `booked_count` int unsigned DEFAULT '0' COMMENT '已预约数',
  `version` int unsigned DEFAULT '0' COMMENT '乐观锁版本号',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:停诊;1:正常)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doctor_date_slot` (`doctor_id`,`schedule_date`,`time_slot`),
  KEY `idx_schedule_date` (`schedule_date`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=4007 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医生排班表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `doctor_schedule`
--

LOCK TABLES `doctor_schedule` WRITE;
/*!40000 ALTER TABLE `doctor_schedule` DISABLE KEYS */;
INSERT INTO `doctor_schedule` VALUES (4001,3001,'2026-07-07','morning',30,12,0,1,'2026-07-01 10:00:00','2026-08-28 13:36:25'),(4002,3001,'2026-07-07','afternoon',25,8,0,1,'2026-07-01 10:00:00','2026-08-28 13:36:25'),(4003,3002,'2026-07-07','morning',20,15,0,1,'2026-07-01 10:00:00','2026-08-28 13:36:25'),(4004,3002,'2026-07-08','morning',20,5,0,1,'2026-07-01 10:00:00','2026-08-28 13:36:25'),(4005,3003,'2026-07-07','afternoon',30,20,0,1,'2026-07-01 10:00:00','2026-08-28 13:36:25'),(4006,3003,'2026-07-08','morning',30,10,0,1,'2026-07-01 10:00:00','2026-08-28 13:36:25');
/*!40000 ALTER TABLE `doctor_schedule` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `evaluations`
--

LOCK TABLES `evaluations` WRITE;
/*!40000 ALTER TABLE `evaluations` DISABLE KEYS */;
INSERT INTO `evaluations` VALUES (1,NULL,3,1,'news',1,'讲得很清楚，正好最近在控盐，收藏了！','[2, 4, 5]','2026-07-02 11:00:00'),(2,1,4,1,'news',1,'同感，减盐确实不容易但坚持下来血压稳多了。','[3]','2026-07-02 12:00:00'),(3,NULL,5,1,'news',4,'终于看懂体检单上的箭头了，谢谢。','[2, 6]','2026-07-05 15:00:00'),(4,NULL,6,1,'news',7,'甲减要终身吃药吗？有点担心。','[8]','2026-07-08 10:00:00'),(5,NULL,4,1,'news',5,'食谱很实用，照着吃了一周血糖好多了。','[3, 5]','2026-07-10 14:00:00'),(6,NULL,7,1,'news',2,'每天下班跟着做拉伸，肩颈舒服很多。','[2]','2026-07-12 18:00:00'),(7,NULL,8,1,'news',9,'焦虑自助方法试了呼吸法，确实有用。','[4, 6]','2026-07-15 20:00:00'),(8,NULL,2,1,'news',3,'失眠清单已打印贴床头，希望能改善。','[3, 7]','2026-07-18 21:00:00'),(9,NULL,3,1,'news',8,'上次痛风发作就是没管住嘴，受教了。','[5]','2026-07-20 09:00:00'),(10,NULL,5,1,'news',10,'妈妈骨质疏松，这篇文章正好给她看。','[6, 8]','2026-07-22 16:00:00');
/*!40000 ALTER TABLE `evaluations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `followup_record`
--

DROP TABLE IF EXISTS `followup_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `followup_record` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `task_id` int NOT NULL COMMENT '任务ID',
  `patient_id` int NOT NULL COMMENT '患者ID',
  `content` varchar(1000) DEFAULT NULL COMMENT '打卡内容/备注',
  `proof` json DEFAULT NULL COMMENT '证明材料(图片/视频JSON)',
  `status` tinyint(1) DEFAULT '0' COMMENT '状态(0:正常;1:异常)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_patient_id` (`patient_id`)
) ENGINE=InnoDB AUTO_INCREMENT=13005 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='随访打卡记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `followup_record`
--

LOCK TABLES `followup_record` WRITE;
/*!40000 ALTER TABLE `followup_record` DISABLE KEYS */;
INSERT INTO `followup_record` VALUES (13001,12001,1006,'今日血压130/82mmHg，正常范围',NULL,0,'2026-07-02 08:00:00'),(13002,12001,1006,'今日血压128/80mmHg，正常',NULL,0,'2026-07-03 08:00:00'),(13003,12003,1007,'空腹血糖6.8mmol/L，偏高',NULL,1,'2026-07-03 07:30:00'),(13004,12003,1007,'空腹血糖6.5mmol/L，有所下降',NULL,0,'2026-07-04 07:30:00');
/*!40000 ALTER TABLE `followup_record` ENABLE KEYS */;
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
-- Table structure for table `hospital_doctor`
--

DROP TABLE IF EXISTS `hospital_doctor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hospital_doctor` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int DEFAULT NULL COMMENT '关联用户表ID(可为空)',
  `name` varchar(50) NOT NULL COMMENT '医生姓名',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像',
  `title` varchar(50) DEFAULT NULL COMMENT '职称(主任医师/副主任医师/主治医师/住院医师)',
  `department_id` int NOT NULL COMMENT '所属科室ID',
  `introduction` text COMMENT '个人简介',
  `expertise` varchar(500) DEFAULT NULL COMMENT '擅长领域',
  `qualifications` varchar(500) DEFAULT NULL COMMENT '资质信息',
  `is_online` tinyint(1) DEFAULT '0' COMMENT '在线状态(0:离线;1:在线)',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:停用;1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_department_id` (`department_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医生表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `hospital_doctor`
--

LOCK TABLES `hospital_doctor` WRITE;
/*!40000 ALTER TABLE `hospital_doctor` DISABLE KEYS */;
INSERT INTO `hospital_doctor` VALUES (3001,1001,'张医生',NULL,'主任医师',1,'从事内科临床工作20年，擅长高血压、糖尿病等慢性病诊治。','高血压、糖尿病、冠心病',NULL,1,0,1,'2026-01-15 10:00:00','2026-08-28 13:36:25'),(3002,1002,'李医生',NULL,'副主任医师',2,'从事外科临床工作15年，擅长普外科、骨科手术。','普外科、骨科、微创手术',NULL,1,0,1,'2026-01-20 14:30:00','2026-08-28 13:36:25'),(3003,1003,'王医生',NULL,'主治医师',3,'从事儿科临床工作10年，擅长儿童常见病诊治。','小儿感冒、肺炎、腹泻',NULL,1,0,1,'2026-02-01 09:15:00','2026-08-28 13:36:25');
/*!40000 ALTER TABLE `hospital_doctor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mall_order`
--

DROP TABLE IF EXISTS `mall_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mall_order` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `order_no` varchar(50) NOT NULL COMMENT '订单号',
  `user_id` int NOT NULL COMMENT '用户ID',
  `total_amount` decimal(10,2) NOT NULL COMMENT '订单总额',
  `actual_amount` decimal(10,2) NOT NULL COMMENT '实付金额',
  `status` tinyint(1) DEFAULT '0' COMMENT '状态(0:待付款;1:已付款;2:已发货;3:已收货;4:已完成;5:已取消)',
  `payment_method` varchar(50) DEFAULT NULL COMMENT '支付方式(模拟)',
  `payment_time` datetime DEFAULT NULL COMMENT '支付时间',
  `shipping_address_id` int DEFAULT NULL COMMENT '收货地址ID',
  `remark` varchar(500) DEFAULT NULL COMMENT '订单备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=7006 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商城订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mall_order`
--

LOCK TABLES `mall_order` WRITE;
/*!40000 ALTER TABLE `mall_order` DISABLE KEYS */;
INSERT INTO `mall_order` VALUES (7001,'ORD20260701001',1006,136.00,136.00,4,'模拟支付','2026-07-01 10:30:00',NULL,NULL,'2026-07-01 10:00:00','2026-08-28 13:36:25'),(7002,'ORD20260702001',1007,299.00,299.00,4,'模拟支付','2026-07-02 14:30:00',NULL,NULL,'2026-07-02 14:00:00','2026-08-28 13:36:25'),(7003,'ORD20260703001',1008,199.00,199.00,3,'模拟支付','2026-07-03 09:30:00',NULL,NULL,'2026-07-03 09:00:00','2026-08-28 13:36:25'),(7004,'ORD20260704001',1009,106.00,106.00,2,'模拟支付','2026-07-04 16:30:00',NULL,NULL,'2026-07-04 16:00:00','2026-08-28 13:36:25'),(7005,'ORD20260705001',1010,258.00,258.00,1,NULL,NULL,NULL,NULL,'2026-07-05 11:00:00','2026-08-28 13:36:25');
/*!40000 ALTER TABLE `mall_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mall_product`
--

DROP TABLE IF EXISTS `mall_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mall_product` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `category_id` int DEFAULT NULL COMMENT '分类ID',
  `product_type` varchar(20) DEFAULT 'health' COMMENT '商品类型: drug=药品, device=医疗器械, health=保健品',
  `name` varchar(200) NOT NULL COMMENT '商品名称',
  `description` text COMMENT '商品描述',
  `cover` varchar(500) DEFAULT NULL COMMENT '商品图片',
  `price` decimal(10,2) NOT NULL COMMENT '售价',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价',
  `stock` int unsigned DEFAULT '0' COMMENT '库存',
  `sales_count` int unsigned DEFAULT '0' COMMENT '销量',
  `unit` varchar(50) DEFAULT '件' COMMENT '单位',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:下架;1:上架)',
  `is_hot` tinyint(1) DEFAULT '0' COMMENT '是否热销',
  `is_new` tinyint(1) DEFAULT '0' COMMENT '是否新品',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`),
  KEY `idx_is_hot` (`is_hot`),
  KEY `idx_is_new` (`is_new`)
) ENGINE=InnoDB AUTO_INCREMENT=7106 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商城商品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mall_product`
--

LOCK TABLES `mall_product` WRITE;
/*!40000 ALTER TABLE `mall_product` DISABLE KEYS */;
INSERT INTO `mall_product` VALUES (6001,1,'drug','降压茶','天然草本降压茶，辅助控制血压',NULL,68.00,98.00,500,120,'盒',1,0,0,'2026-03-01 10:00:00','2026-08-28 13:46:34'),(6002,1,'drug','血糖仪','家用血糖仪，精准测量',NULL,299.00,399.00,200,85,'台',1,0,0,'2026-03-05 14:00:00','2026-08-28 13:46:34'),(6003,2,'device','血压计','电子血压计，一键测量',NULL,199.00,259.00,300,150,'台',1,0,0,'2026-03-10 09:00:00','2026-08-28 13:46:28'),(6004,3,'health','维生素C','天然维生素C，增强免疫力',NULL,48.00,68.00,1000,320,'瓶',1,0,0,'2026-03-15 16:00:00','2026-08-28 13:46:34'),(6005,3,'health','钙片','补钙片，预防骨质疏松',NULL,58.00,78.00,800,210,'瓶',1,0,0,'2026-03-20 11:00:00','2026-08-28 13:46:34'),(6006,4,'health','燕麦片','即食燕麦片，健康早餐',NULL,32.00,45.00,2000,450,'袋',1,0,1,'2026-04-01 10:00:00','2026-08-28 13:46:34'),(6007,4,'health','蜂蜜','天然蜂蜜，润肠通便',NULL,88.00,128.00,500,180,'瓶',1,0,1,'2026-04-05 14:00:00','2026-08-28 13:36:25'),(6008,5,'health','瑜伽垫','加厚瑜伽垫，防滑耐磨',NULL,128.00,168.00,300,95,'张',1,0,0,'2026-04-10 09:00:00','2026-08-28 13:36:25'),(7001,1,'drug','苯磺酸氨氯地平片','钙通道阻滞剂，用于高血压、心绞痛。；通用名：氨氯地平；规格：5mg*7片；厂家：辉瑞制药',NULL,28.50,28.50,120,0,'盒',1,0,0,'2026-08-28 12:19:41','2026-08-28 16:42:57'),(7002,1,'drug','二甲双胍缓释片','2型糖尿病一线用药，改善胰岛素敏感性。；通用名：二甲双胍；规格：0.5g*30片；厂家：中美上海施贵宝',NULL,35.00,35.00,80,0,'盒',1,0,0,'2026-08-28 12:19:41','2026-08-28 16:42:57'),(7003,1,'drug','阿司匹林肠溶片','抑制血小板聚集，用于心脑血管疾病预防。；通用名：阿司匹林；规格：100mg*30片；厂家：拜耳医药',NULL,15.80,15.80,200,0,'盒',1,0,0,'2026-08-28 12:19:41','2026-08-28 16:42:57'),(7004,1,'drug','维生素D3软胶囊','促进钙吸收，维护骨骼健康。；通用名：维生素D3；规格：400IU*60粒；厂家：汤臣倍健；非处方药(OTC)',NULL,49.90,49.90,150,0,'瓶',1,0,0,'2026-08-28 12:19:41','2026-08-28 16:42:57'),(7005,1,'drug','二甲双胍缓释片','用于2型糖尿病，改善胰岛素敏感性；通用名：Metformin；规格：0.5g*30片；厂家：中美上海施贵宝；非处方药(OTC)','',38.00,38.00,120,0,'盒',1,0,0,'2026-08-28 12:29:38','2026-08-28 16:42:57'),(7006,1,'drug','氨氯地平片','二氢吡啶类钙拮抗剂，用于高血压；通用名：Amlodipine；规格：5mg*28片；厂家：辉瑞；非处方药(OTC)','',25.50,25.50,200,0,'盒',1,0,0,'2026-08-28 12:29:38','2026-08-28 16:42:57'),(7007,1,'drug','阿托伐他汀钙片','他汀类，降低低密度脂蛋白胆固醇；通用名：Atorvastatin；规格：20mg*7片；厂家：辉瑞；非处方药(OTC)','',42.00,42.00,150,0,'盒',1,0,0,'2026-08-28 12:29:38','2026-08-28 16:42:57'),(7008,1,'drug','左甲状腺素钠片','用于甲状腺功能减退替代治疗；通用名：Levothyroxine；规格：50μg*100片；厂家：德国默克；非处方药(OTC)','',30.00,30.00,90,0,'盒',1,0,0,'2026-08-28 12:29:38','2026-08-28 16:42:57'),(7009,1,'drug','布洛芬缓释胶囊','用于缓解轻中度疼痛及发热；通用名：Ibuprofen；规格：0.3g*20粒；厂家：中美天津史克；非处方药(OTC)','',18.00,18.00,300,0,'盒',1,0,0,'2026-08-28 12:29:38','2026-08-28 16:42:57'),(7010,1,'drug','维生素D3滴剂','促进钙吸收，维护骨骼健康；通用名：Vitamin D3；规格：400IU*60粒；厂家：星鲨；非处方药(OTC)','',56.00,56.00,180,0,'瓶',1,0,0,'2026-08-28 12:29:38','2026-08-28 16:42:57'),(7011,1,'drug','非布司他片','用于痛风高尿酸血症；通用名：Febuxostat；规格：40mg*28片；厂家：江苏恒瑞；非处方药(OTC)','',68.00,68.00,80,0,'盒',1,0,0,'2026-08-28 12:29:38','2026-08-28 16:42:57'),(7012,1,'drug','氯雷他定片','用于缓解过敏性鼻炎症状；通用名：Loratadine；规格：10mg*6片；厂家：西安杨森；非处方药(OTC)','',22.00,22.00,260,0,'盒',1,0,0,'2026-08-28 12:29:38','2026-08-28 16:42:57'),(7013,1,'drug','枸橼酸铋钾片','用于慢性胃炎及缓解胃酸过多引起的胃痛、胃灼热感（烧心）、反酸。；通用名：枸橼酸铋钾；规格：0.3g×40片；厂家：丽珠集团丽珠制药厂',NULL,35.00,35.00,180,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7014,1,'drug','乳酸菌素片','用于肠内异常发酵、消化不良、肠炎和小儿腹泻。；通用名：乳酸菌素片；规格：0.4g×30片；厂家：江中药业股份有限公司；非处方药(OTC)',NULL,12.00,12.00,500,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7015,1,'drug','硝苯地平缓释片','用于治疗高血压和心绞痛。；通用名：硝苯地平；规格：10mg×30片；厂家：拜耳医药保健有限公司',NULL,32.00,32.00,250,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7016,1,'drug','阿司匹林肠溶片','用于预防一过性脑缺血发作、心肌梗死、心房颤动、人工心脏瓣膜、动静脉瘘或其他手术后的血栓形成。；通用名：阿司匹林；规格：100mg×30片；厂家：拜耳医药保健有限公司',NULL,15.00,15.00,400,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7017,1,'drug','缬沙坦胶囊','用于治疗轻、中度原发性高血压。；通用名：缬沙坦；规格：80mg×7粒；厂家：北京诺华制药有限公司',NULL,38.00,38.00,200,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7018,1,'drug','复方丹参滴丸','活血化瘀，理气止痛。用于气滞血瘀所致的胸痹，症见胸闷、心前区刺痛；冠心病心绞痛见上述证候者。；通用名：复方丹参滴丸；规格：27mg×180丸；厂家：天士力医药集团股份有限公司；非处方药(OTC)',NULL,25.00,25.00,300,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7019,1,'drug','氯吡格雷片','用于预防动脉粥样硬化血栓形成事件，如心肌梗死、缺血性卒中等。；通用名：硫酸氢氯吡格雷；规格：75mg×7片；厂家：赛诺菲(杭州)制药有限公司',NULL,68.00,68.00,150,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7020,1,'drug','维生素C片','用于预防坏血病，也可用于各种急慢性传染疾病及紫癜等的辅助治疗。；通用名：维生素C；规格：100mg×100片；厂家：东北制药集团股份有限公司；非处方药(OTC)',NULL,5.00,5.00,1000,0,'瓶',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7021,1,'drug','维生素B族片','用于预防和治疗B族维生素缺乏所致的营养不良、厌食、脚气病、糙皮病等。；通用名：复合维生素B；规格：100片；厂家：东北制药集团股份有限公司；非处方药(OTC)',NULL,12.00,12.00,800,0,'瓶',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7022,1,'drug','维生素E软胶囊','用于心、脑血管疾病及习惯性流产、不孕症的辅助治疗。；通用名：维生素E；规格：100mg×60粒；厂家：浙江医药股份有限公司；非处方药(OTC)',NULL,18.00,18.00,500,0,'瓶',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7023,1,'drug','维生素D滴剂','用于预防和治疗维生素D缺乏症，如佝偻病等。；通用名：维生素D3；规格：400IU×36粒；厂家：青岛双鲸药业有限公司；非处方药(OTC)',NULL,35.00,35.00,300,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7024,1,'drug','多维元素片','用于预防和治疗因维生素与矿物质缺乏所引起的各种疾病。；通用名：多维元素片；规格：100片；厂家：惠氏制药有限公司；非处方药(OTC)',NULL,68.00,68.00,250,0,'瓶',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7025,1,'drug','阿莫西林胶囊','用于敏感菌所致的呼吸道感染、泌尿生殖道感染、皮肤软组织感染等。；通用名：阿莫西林；规格：0.5g×24粒；厂家：珠海联邦制药股份有限公司',NULL,12.00,12.00,500,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7026,1,'drug','头孢克洛胶囊','用于敏感菌所致的呼吸道感染、泌尿系统感染、耳鼻喉科感染及皮肤软组织感染等。；通用名：头孢克洛；规格：0.25g×12粒；厂家：礼来苏州制药有限公司',NULL,22.00,22.00,300,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7027,1,'drug','阿奇霉素片','用于敏感细菌所引起的呼吸道感染、皮肤和软组织感染、耳鼻喉感染等。；通用名：阿奇霉素；规格：0.25g×6片；厂家：辉瑞制药有限公司',NULL,18.50,18.50,350,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7028,1,'drug','左氧氟沙星片','用于敏感细菌引起的呼吸道感染、泌尿系统感染、生殖系统感染、皮肤软组织感染等。；通用名：左氧氟沙星；规格：0.5g×6片；厂家：第一三共制药(北京)有限公司',NULL,15.00,15.00,400,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7029,1,'drug','甲硝唑片','用于治疗肠道和肠外阿米巴病，还可用于治疗阴道滴虫病、小袋虫病和皮肤利什曼病等。；通用名：甲硝唑；规格：0.2g×21片；厂家：华北制药股份有限公司',NULL,8.00,8.00,600,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7030,1,'drug','罗红霉素分散片','适用于敏感菌株引起的呼吸道感染、耳鼻喉感染、泌尿生殖道感染、皮肤软组织感染等。；通用名：罗红霉素；规格：0.15g×6片；厂家：哈药集团制药总厂',NULL,16.00,16.00,280,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7031,1,'drug','红霉素软膏','用于脓疱疮等化脓性皮肤病、小面积烧伤、溃疡面的感染和寻常痤疮。；通用名：红霉素；规格：1%×10g；厂家：马应龙药业集团股份有限公司；非处方药(OTC)',NULL,3.50,3.50,800,0,'支',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7032,1,'drug','云南白药气雾剂','活血散瘀，消肿止痛。用于跌打损伤，瘀血肿痛，肌肉酸痛及风湿性关节疼痛等。；通用名：云南白药气雾剂；规格：50g+85g；厂家：云南白药集团股份有限公司；非处方药(OTC)',NULL,58.00,58.00,200,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7033,1,'drug','创可贴','用于小创伤、擦伤等患处的止血、护创。；通用名：苯扎氯铵贴；规格：100片；厂家：云南白药集团股份有限公司；非处方药(OTC)',NULL,5.00,5.00,1000,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7034,1,'drug','皮炎平软膏','用于局限性瘙痒症、神经性皮炎、接触性皮炎、脂溢性皮炎以及慢性湿疹。；通用名：复方醋酸地塞米松乳膏；规格：20g；厂家：华润三九医药股份有限公司；非处方药(OTC)',NULL,12.00,12.00,500,0,'支',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7035,1,'drug','碘伏消毒液','用于皮肤、黏膜的消毒，如手术前刷手、注射部位皮肤消毒等。；通用名：聚维酮碘溶液；规格：100ml；厂家：上海利康消毒高科技有限公司；非处方药(OTC)',NULL,8.00,8.00,600,0,'瓶',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7036,1,'drug','达克宁乳膏','由皮真菌、酵母菌及其他真菌引起的皮肤、指（趾）甲感染，如体股癣、手足癣、花斑癣等。；通用名：硝酸咪康唑乳膏；规格：2%×20g；厂家：西安杨森制药有限公司；非处方药(OTC)',NULL,16.00,16.00,400,0,'支',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7037,1,'drug','活血止痛膏','活血止痛，舒筋通络。用于筋骨疼痛，肌肉麻痹，痰核流注，关节酸痛。；通用名：活血止痛膏；规格：6贴；厂家：黄石市力健药业有限公司；非处方药(OTC)',NULL,22.00,22.00,300,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7038,1,'drug','六味地黄丸','滋阴补肾。用于肾阴亏损，头晕耳鸣，腰膝酸软，骨蒸潮热，盗汗遗精。；通用名：六味地黄丸；规格：200丸；厂家：河南宛西制药股份有限公司；非处方药(OTC)',NULL,18.00,18.00,500,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7039,1,'drug','逍遥丸','疏肝健脾，养血调经。用于肝郁脾虚所致的郁闷不舒、胸胁胀痛、头晕目眩、食欲减退、月经不调。；通用名：逍遥丸；规格：200丸；厂家：河南宛西制药股份有限公司；非处方药(OTC)',NULL,15.00,15.00,400,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7040,1,'drug','补中益气丸','补中益气，升阳举陷。用于脾胃虚弱、中气下陷所致的体倦乏力、食少腹胀、便溏久泻、肛门下坠。；通用名：补中益气丸；规格：200丸；厂家：河南宛西制药股份有限公司；非处方药(OTC)',NULL,16.00,16.00,350,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7041,1,'drug','牛黄解毒片','清热解毒。用于火热内盛，咽喉肿痛，牙龈肿痛，口舌生疮，目赤肿痛。；通用名：牛黄解毒片；规格：0.3g×36片；厂家：北京同仁堂科技发展股份有限公司；非处方药(OTC)',NULL,8.00,8.00,600,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7042,1,'drug','藿香正气水','解表化湿，理气和中。用于外感风寒、内伤湿滞或夏伤暑湿所致的感冒，症见头痛昏重、胸膈痞闷、脘腹胀痛、呕吐泄泻。；通用名：藿香正气水；规格：10ml×10支；厂家：太极集团重庆涪陵制药厂；非处方药(OTC)',NULL,10.00,10.00,500,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7043,1,'drug','安宫牛黄丸','清热解毒，镇惊开窍。用于热病，邪入心包，高热惊厥，神昏谵语；中风昏迷及脑炎、脑膜炎、中毒性脑病、脑出血、败血症见上述证候者。；通用名：安宫牛黄丸；规格：3g×1丸；厂家：北京同仁堂股份有限公司',NULL,780.00,780.00,50,0,'丸',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7044,1,'drug','感冒清热颗粒','疏风散寒，解表清热。用于风寒感冒，头痛发热，恶寒身痛，鼻流清涕，咳嗽咽干。；通用名：感冒清热颗粒；规格：12g×10袋；厂家：北京同仁堂科技发展股份有限公司；非处方药(OTC)',NULL,14.00,14.00,450,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7045,1,'drug','复方甘草片','用于镇咳祛痰。；通用名：复方甘草片；规格：100片；厂家：华北制药股份有限公司',NULL,5.00,5.00,700,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7046,1,'drug','法莫替丁片','用于缓解胃酸过多所致的胃痛、胃灼热感（烧心）、反酸。；通用名：法莫替丁；规格：20mg×30片；厂家：安斯泰来制药(中国)有限公司；非处方药(OTC)',NULL,18.00,18.00,300,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7047,1,'drug','美托洛尔缓释片','用于治疗高血压、心绞痛、心肌梗死、肥厚型心肌病、主动脉夹层、心律失常、甲状腺功能亢进、心脏神经官能症等。；通用名：酒石酸美托洛尔；规格：47.5mg×7片；厂家：阿斯利康制药有限公司',NULL,28.00,28.00,200,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7048,1,'drug','碳酸钙D3片','用于妊娠和哺乳期妇女、更年期妇女、老年人等的钙补充剂，并帮助防治骨质疏松症。；通用名：碳酸钙D3；规格：60片；厂家：惠氏制药有限公司；非处方药(OTC)',NULL,55.00,55.00,300,0,'瓶',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7049,1,'drug','克拉霉素片','用于敏感菌引起的呼吸道感染、皮肤软组织感染、耳鼻喉感染等。；通用名：克拉霉素；规格：0.25g×6片；厂家：雅培贸易(上海)有限公司',NULL,25.00,25.00,250,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7050,1,'drug','风油精','清凉，止痛，驱风，止痒。用于蚊虫叮咬及伤风感冒引起的头痛，头晕，晕车不适。；通用名：风油精；规格：3ml；厂家：漳州水仙药业股份有限公司；非处方药(OTC)',NULL,6.00,6.00,900,0,'瓶',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7051,1,'drug','金匮肾气丸','温补肾阳，化气行水。用于肾虚水肿，腰膝酸软，小便不利，畏寒肢冷。；通用名：金匮肾气丸；规格：200丸；厂家：河南宛西制药股份有限公司；非处方药(OTC)',NULL,20.00,20.00,350,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7052,1,'drug','蒙脱石散（儿童装）','用于儿童急、慢性腹泻。；通用名：蒙脱石散；规格：3g×10袋（草莓味）；厂家：博福-益普生(天津)制药有限公司；非处方药(OTC)',NULL,32.00,32.00,350,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7053,1,'drug','双黄连口服液','疏风解表，清热解毒。用于外感风热所致的感冒，症见发热、咳嗽、咽痛。；通用名：双黄连口服液；规格：10ml×10支；厂家：哈药集团三精制药有限公司；非处方药(OTC)',NULL,22.00,22.00,400,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7054,1,'drug','布洛芬缓释胶囊','用于缓解轻至中度疼痛如头痛、关节痛、偏头痛、牙痛、肌肉痛、神经痛、痛经。也用于普通感冒或流行性感冒引起的发热。；通用名：布洛芬；规格：0.3g×20粒；厂家：中美天津史克制药有限公司；非处方药(OTC)',NULL,16.00,16.00,500,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7055,1,'drug','通宣理肺丸','解表散寒，宣肺止嗽。用于风寒感冒咳嗽，咯痰不畅，发热恶寒，鼻塞流涕，头痛无汗，肢体酸痛。；通用名：通宣理肺丸；规格：200丸；厂家：北京同仁堂股份有限公司；非处方药(OTC)',NULL,15.00,15.00,300,0,'盒',1,0,0,'2026-06-01 21:02:10','2026-08-28 16:42:57'),(7056,1,'drug','阿莫西林胶囊','适用于敏感菌所致的感染，如中耳炎、鼻窦炎、咽炎、扁桃体炎等上呼吸道感染；通用名：阿莫西林；规格：0.25g*24粒；厂家：哈药集团制药总厂；非处方药(OTC)',NULL,12.50,12.50,100,0,'盒',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7057,1,'drug','布洛芬缓释胶囊','用于缓解轻至中度疼痛及感冒引起的发热，如头痛、关节痛、偏头痛、牙痛、肌肉痛等；通用名：布洛芬；规格：0.3g*20粒；厂家：中美天津史克制药有限公司；非处方药(OTC)',NULL,25.00,25.00,200,0,'盒',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7058,1,'drug','蒙脱石散','用于成人及儿童急、慢性腹泻；通用名：蒙脱石；规格：3g*10袋；厂家：博福-益普生(天津)制药有限公司；非处方药(OTC)',NULL,28.50,28.50,150,0,'盒',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7059,1,'drug','复方甘草片','用于镇咳祛痰；通用名：复方甘草；规格：100片；厂家：北京同仁堂制药有限公司',NULL,8.00,8.00,300,0,'瓶',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7060,1,'drug','维生素C片','用于预防坏血病，也可用于各种急慢性传染疾病及紫癜等的辅助治疗；通用名：维生素C；规格：0.1g*100片；厂家：东北制药集团沈阳第一制药有限公司；非处方药(OTC)',NULL,6.50,6.50,500,0,'瓶',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7061,1,'drug','六味地黄丸','用于肾阴亏损，头晕耳鸣，腰膝酸软，骨蒸潮热，盗汗遗精；通用名：六味地黄；规格：200丸；厂家：北京同仁堂科技发展股份有限公司；非处方药(OTC)',NULL,35.00,35.00,180,0,'瓶',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7062,1,'drug','感冒灵颗粒','用于风热感冒，发热，头痛，鼻塞，流涕，咽痛；通用名：感冒灵；规格：10g*9袋；厂家：华润三九医药股份有限公司；非处方药(OTC)',NULL,15.00,15.00,250,0,'盒',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7063,1,'drug','健胃消食片','用于脾胃虚弱所致的食积，症见不思饮食、嗳腐酸臭、脘腹胀满；通用名：健胃消食；规格：0.8g*32片；厂家：江中药业股份有限公司；非处方药(OTC)',NULL,22.00,22.00,120,0,'盒',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7064,1,'drug','双黄连口服液','用于外感风热所致的感冒，症见发热、咳嗽、咽痛；通用名：双黄连；规格：10ml*6支；厂家：哈药集团三精制药有限公司；非处方药(OTC)',NULL,18.00,18.00,160,0,'盒',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7065,1,'drug','藿香正气水','用于外感风寒、内伤湿滞或夏伤暑湿所致的感冒；通用名：藿香正气；规格：10ml*10支；厂家：太极集团重庆涪陵制药厂；非处方药(OTC)',NULL,12.00,12.00,200,0,'盒',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7066,1,'drug','开塞露','用于小儿、老年便秘；通用名：甘油；规格：20ml*2支；厂家：上海运佳黄浦制药有限公司；非处方药(OTC)',NULL,3.50,3.50,400,0,'支',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7067,1,'drug','红霉素软膏','用于脓疱疮等化脓性皮肤病、小面积烧伤、溃疡面的感染和寻常痤疮；通用名：红霉素；规格：1g:10mg*15g；厂家：马应龙药业集团股份有限公司；非处方药(OTC)',NULL,5.00,5.00,350,0,'支',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7068,1,'drug','创可贴','用于小创伤、擦伤等；通用名：创可贴；规格：100片；厂家：云南白药集团股份有限公司；非处方药(OTC)',NULL,15.00,15.00,600,0,'盒',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7069,1,'drug','风油精','用于伤风感冒引起的头痛、头晕以及关节痛、牙痛、腹部胀痛；通用名：风油精；规格：3ml；厂家：漳州水仙药业股份有限公司；非处方药(OTC)',NULL,8.50,8.50,280,0,'瓶',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7070,1,'drug','板蓝根颗粒','用于肺胃热盛所致的咽喉肿痛、口咽干燥、急性扁桃体炎；通用名：板蓝根；规格：10g*20袋；厂家：广州白云山和记黄埔中药有限公司；非处方药(OTC)',NULL,16.00,16.00,220,0,'盒',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7071,1,'drug','复方氨酚烷胺胶囊','用于缓解普通感冒及流行性感冒引起的发热、头痛、四肢酸痛、打喷嚏、流鼻涕、鼻塞、咽痛等症状；通用名：氨酚烷胺；规格：10粒；厂家：哈药集团制药总厂；非处方药(OTC)',NULL,18.50,18.50,180,0,'盒',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7072,1,'drug','奥美拉唑肠溶胶囊','用于胃溃疡、十二指肠溃疡、应激性溃疡、反流性食管炎和卓-艾综合征；通用名：奥美拉唑；规格：20mg*14粒；厂家：阿斯利康制药有限公司',NULL,32.00,32.00,150,0,'盒',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7073,1,'drug','硝苯地平缓释片','用于各种类型的高血压及心绞痛；通用名：硝苯地平；规格：10mg*30片；厂家：拜耳医药保健有限公司',NULL,28.00,28.00,200,0,'盒',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7074,1,'drug','阿托伐他汀钙片','用于高胆固醇血症和混合型高脂血症；冠心病和脑中风的防治；通用名：阿托伐他汀；规格：20mg*7片；厂家：辉瑞制药有限公司',NULL,45.00,45.00,180,0,'盒',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7075,1,'drug','二甲双胍片','用于单纯饮食控制及体育锻炼治疗无效的2型糖尿病；通用名：二甲双胍；规格：0.25g*48片；厂家：中美上海施贵宝制药有限公司',NULL,15.00,15.00,250,0,'盒',1,0,0,'2026-06-21 21:25:04','2026-08-28 16:42:57'),(7076,1,'drug','阿莫西林胶囊','适用于敏感菌所致的感染；通用名：阿莫西林；规格：0.25g*24粒；厂家：哈药集团制药总厂；非处方药(OTC)',NULL,12.50,12.50,100,0,'盒',1,0,0,'2026-06-22 11:12:13','2026-08-28 16:42:57'),(7077,1,'drug','布洛芬缓释胶囊','用于缓解轻至中度疼痛及感冒引起的发热；通用名：布洛芬；规格：0.3g*20粒；厂家：中美天津史克制药有限公司；非处方药(OTC)',NULL,25.00,25.00,200,0,'盒',1,0,0,'2026-06-22 11:12:13','2026-08-28 16:42:57'),(7078,1,'drug','蒙脱石散','用于成人及儿童急、慢性腹泻；通用名：蒙脱石；规格：3g*10袋；厂家：博福-益普生(天津)制药有限公司；非处方药(OTC)',NULL,28.50,28.50,150,0,'盒',1,0,0,'2026-06-22 11:12:13','2026-08-28 16:42:57'),(7079,1,'drug','复方甘草片','用于镇咳祛痰；通用名：复方甘草；规格：100片；厂家：北京同仁堂制药有限公司；非处方药(OTC)',NULL,8.00,8.00,300,0,'瓶',1,0,0,'2026-06-22 11:12:13','2026-08-28 16:42:57'),(7080,1,'drug','维生素C片','用于预防坏血病；通用名：维生素C；规格：0.1g*100片；厂家：东北制药集团沈阳第一制药有限公司；非处方药(OTC)',NULL,6.50,6.50,500,0,'瓶',1,0,0,'2026-06-22 11:12:13','2026-08-28 16:42:57'),(7081,1,'drug','六味地黄丸','用于肾阴亏损，头晕耳鸣；通用名：六味地黄；规格：200丸；厂家：北京同仁堂科技发展股份有限公司；非处方药(OTC)',NULL,35.00,35.00,180,0,'瓶',1,0,0,'2026-06-22 11:12:13','2026-08-28 16:42:57'),(7082,1,'drug','感冒灵颗粒','用于风热感冒，发热，头痛；通用名：感冒灵；规格：10g*9袋；厂家：华润三九医药股份有限公司；非处方药(OTC)',NULL,15.00,15.00,250,0,'盒',1,0,0,'2026-06-22 11:12:13','2026-08-28 16:42:57'),(7083,1,'drug','健胃消食片','用于脾胃虚弱所致的食积；通用名：健胃消食；规格：0.8g*32片；厂家：江中药业股份有限公司；非处方药(OTC)',NULL,22.00,22.00,120,0,'盒',1,0,0,'2026-06-22 11:12:13','2026-08-28 16:42:57'),(7084,1,'drug','双黄连口服液','用于外感风热所致的感冒；通用名：双黄连；规格：10ml*6支；厂家：哈药集团三精制药有限公司；非处方药(OTC)',NULL,18.00,18.00,160,0,'盒',1,0,0,'2026-06-22 11:12:13','2026-08-28 16:42:57'),(7085,1,'drug','藿香正气水','用于外感风寒、内伤湿滞；通用名：藿香正气；规格：10ml*10支；厂家：太极集团重庆涪陵制药厂；非处方药(OTC)',NULL,12.00,12.00,200,0,'盒',1,0,0,'2026-06-22 11:12:13','2026-08-28 16:42:57'),(7086,1,'drug','开塞露','用于小儿、老年便秘；通用名：甘油；规格：20ml*2支；厂家：上海运佳黄浦制药有限公司；非处方药(OTC)',NULL,3.50,3.50,400,0,'支',1,0,0,'2026-06-22 11:12:13','2026-08-28 16:42:57'),(7087,1,'drug','红霉素软膏','用于脓疱疮等化脓性皮肤病；通用名：红霉素；规格：1g:10mg*15g；厂家：马应龙药业集团股份有限公司；非处方药(OTC)',NULL,5.00,5.00,350,0,'支',1,0,0,'2026-06-22 11:12:13','2026-08-28 16:42:57'),(7088,1,'drug','创可贴','用于小创伤、擦伤等；通用名：创可贴；规格：100片；厂家：云南白药集团股份有限公司；非处方药(OTC)',NULL,15.00,15.00,600,0,'盒',1,0,0,'2026-06-22 11:12:13','2026-08-28 16:42:57'),(7089,1,'drug','风油精','用于伤风感冒引起的头痛；通用名：风油精；规格：3ml；厂家：漳州水仙药业股份有限公司；非处方药(OTC)',NULL,8.50,8.50,280,0,'瓶',1,0,0,'2026-06-22 11:12:13','2026-08-28 16:42:57'),(7090,1,'drug','板蓝根颗粒','用于肺胃热盛所致的咽喉肿痛；通用名：板蓝根；规格：10g*20袋；厂家：广州白云山和记黄埔中药有限公司；非处方药(OTC)',NULL,16.00,16.00,220,0,'盒',1,0,0,'2026-06-22 11:12:13','2026-08-28 16:42:57'),(7091,1,'drug','阿莫西林胶囊','适用于敏感菌所致的感染；通用名：阿莫西林；规格：0.25g*24粒；厂家：哈药集团制药总厂；非处方药(OTC)',NULL,12.50,12.50,100,0,'盒',1,0,0,'2026-06-22 11:12:15','2026-08-28 16:42:57'),(7092,1,'drug','布洛芬缓释胶囊','用于缓解轻至中度疼痛及感冒引起的发热；通用名：布洛芬；规格：0.3g*20粒；厂家：中美天津史克制药有限公司；非处方药(OTC)',NULL,25.00,25.00,200,0,'盒',1,0,0,'2026-06-22 11:12:15','2026-08-28 16:42:57'),(7093,1,'drug','蒙脱石散','用于成人及儿童急、慢性腹泻；通用名：蒙脱石；规格：3g*10袋；厂家：博福-益普生(天津)制药有限公司；非处方药(OTC)',NULL,28.50,28.50,150,0,'盒',1,0,0,'2026-06-22 11:12:15','2026-08-28 16:42:57'),(7094,1,'drug','复方甘草片','用于镇咳祛痰；通用名：复方甘草；规格：100片；厂家：北京同仁堂制药有限公司；非处方药(OTC)',NULL,8.00,8.00,300,0,'瓶',1,0,0,'2026-06-22 11:12:15','2026-08-28 16:42:57'),(7095,1,'drug','维生素C片','用于预防坏血病；通用名：维生素C；规格：0.1g*100片；厂家：东北制药集团沈阳第一制药有限公司；非处方药(OTC)',NULL,6.50,6.50,500,0,'瓶',1,0,0,'2026-06-22 11:12:15','2026-08-28 16:42:57'),(7096,1,'drug','六味地黄丸','用于肾阴亏损，头晕耳鸣；通用名：六味地黄；规格：200丸；厂家：北京同仁堂科技发展股份有限公司；非处方药(OTC)',NULL,35.00,35.00,180,0,'瓶',1,0,0,'2026-06-22 11:12:15','2026-08-28 16:42:57'),(7097,1,'drug','感冒灵颗粒','用于风热感冒，发热，头痛；通用名：感冒灵；规格：10g*9袋；厂家：华润三九医药股份有限公司；非处方药(OTC)',NULL,15.00,15.00,250,0,'盒',1,0,0,'2026-06-22 11:12:15','2026-08-28 16:42:57'),(7098,1,'drug','健胃消食片','用于脾胃虚弱所致的食积；通用名：健胃消食；规格：0.8g*32片；厂家：江中药业股份有限公司；非处方药(OTC)',NULL,22.00,22.00,120,0,'盒',1,0,0,'2026-06-22 11:12:15','2026-08-28 16:42:57'),(7099,1,'drug','双黄连口服液','用于外感风热所致的感冒；通用名：双黄连；规格：10ml*6支；厂家：哈药集团三精制药有限公司；非处方药(OTC)',NULL,18.00,18.00,160,0,'盒',1,0,0,'2026-06-22 11:12:15','2026-08-28 16:42:57'),(7100,1,'drug','藿香正气水','用于外感风寒、内伤湿滞；通用名：藿香正气；规格：10ml*10支；厂家：太极集团重庆涪陵制药厂；非处方药(OTC)',NULL,12.00,12.00,200,0,'盒',1,0,0,'2026-06-22 11:12:15','2026-08-28 16:42:57'),(7101,1,'drug','开塞露','用于小儿、老年便秘；通用名：甘油；规格：20ml*2支；厂家：上海运佳黄浦制药有限公司；非处方药(OTC)',NULL,3.50,3.50,400,0,'支',1,0,0,'2026-06-22 11:12:15','2026-08-28 16:42:57'),(7102,1,'drug','红霉素软膏','用于脓疱疮等化脓性皮肤病；通用名：红霉素；规格：1g:10mg*15g；厂家：马应龙药业集团股份有限公司；非处方药(OTC)',NULL,5.00,5.00,350,0,'支',1,0,0,'2026-06-22 11:12:15','2026-08-28 16:42:57'),(7103,1,'drug','创可贴','用于小创伤、擦伤等；通用名：创可贴；规格：100片；厂家：云南白药集团股份有限公司；非处方药(OTC)',NULL,15.00,15.00,600,0,'盒',1,0,0,'2026-06-22 11:12:15','2026-08-28 16:42:57'),(7104,1,'drug','风油精','用于伤风感冒引起的头痛；通用名：风油精；规格：3ml；厂家：漳州水仙药业股份有限公司；非处方药(OTC)',NULL,8.50,8.50,280,0,'瓶',1,0,0,'2026-06-22 11:12:15','2026-08-28 16:42:57'),(7105,1,'drug','板蓝根颗粒','用于肺胃热盛所致的咽喉肿痛；通用名：板蓝根；规格：10g*20袋；厂家：广州白云山和记黄埔中药有限公司；非处方药(OTC)',NULL,16.00,16.00,220,0,'盒',1,0,0,'2026-06-22 11:12:15','2026-08-28 16:42:57');
/*!40000 ALTER TABLE `mall_product` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `message`
--

LOCK TABLES `message` WRITE;
/*!40000 ALTER TABLE `message` DISABLE KEYS */;
INSERT INTO `message` VALUES (1,'欢迎使用智康云健康管理系统！您的健康档案已创建，建议每月更新一次健康数据。',3,1,NULL,0,NULL,'2026-08-19 09:00:00'),(2,'提醒：您订阅的「苯磺酸氨氯地平片」用药时间到了，请按时服药。',3,1,NULL,0,NULL,'2026-08-26 08:00:00'),(3,'欢迎使用智康云健康管理系统，您的健康档案已建立，可随时在「健康记录」中查看指标趋势。',2,1,NULL,0,NULL,'2026-04-01 09:00:00'),(4,'温馨提示：您订阅的用药提醒已生效，请按时服药并定期复诊。',2,1,NULL,0,NULL,'2026-07-21 09:00:00'),(5,'欢迎使用智康云健康管理系统，您的健康档案已建立，可随时在「健康记录」中查看指标趋势。',3,1,NULL,0,NULL,'2026-04-01 09:00:00'),(6,'温馨提示：您订阅的用药提醒已生效，请按时服药并定期复诊。',3,1,NULL,0,NULL,'2026-07-21 09:00:00'),(7,'欢迎使用智康云健康管理系统，您的健康档案已建立，可随时在「健康记录」中查看指标趋势。',4,1,NULL,0,NULL,'2026-04-01 09:00:00'),(8,'温馨提示：您订阅的用药提醒已生效，请按时服药并定期复诊。',4,1,NULL,0,NULL,'2026-07-21 09:00:00'),(9,'欢迎使用智康云健康管理系统，您的健康档案已建立，可随时在「健康记录」中查看指标趋势。',5,1,NULL,0,NULL,'2026-04-01 09:00:00'),(10,'温馨提示：您订阅的用药提醒已生效，请按时服药并定期复诊。',5,1,NULL,0,NULL,'2026-07-21 09:00:00'),(11,'欢迎使用智康云健康管理系统，您的健康档案已建立，可随时在「健康记录」中查看指标趋势。',6,1,NULL,0,NULL,'2026-04-01 09:00:00'),(12,'温馨提示：您订阅的用药提醒已生效，请按时服药并定期复诊。',6,1,NULL,0,NULL,'2026-07-21 09:00:00'),(13,'欢迎使用智康云健康管理系统，您的健康档案已建立，可随时在「健康记录」中查看指标趋势。',7,1,NULL,0,NULL,'2026-04-01 09:00:00'),(14,'温馨提示：您订阅的用药提醒已生效，请按时服药并定期复诊。',7,1,NULL,0,NULL,'2026-07-21 09:00:00'),(15,'欢迎使用智康云健康管理系统，您的健康档案已建立，可随时在「健康记录」中查看指标趋势。',8,1,NULL,0,NULL,'2026-04-01 09:00:00'),(16,'温馨提示：您订阅的用药提醒已生效，请按时服药并定期复诊。',8,1,NULL,0,NULL,'2026-07-21 09:00:00');
/*!40000 ALTER TABLE `message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `model_announcement`
--

DROP TABLE IF EXISTS `model_announcement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `model_announcement` (
  `id` int NOT NULL AUTO_INCREMENT,
  `model_key` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '妯″瀷鏍囪瘑锛堝? zhikangyun-local, deepseek 绛夛級',
  `model_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '妯″瀷灞曠ず鍚嶇О',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '妯?箙鏍囬?锛堝? "鏈?崏澶фā鍨嬪凡涓婄嚎"锛',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '妯?箙鎻忚堪鏂囧瓧',
  `bg_color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '#409EFF' COMMENT '妯?箙鑳屾櫙鑹',
  `icon` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'MagicStick' COMMENT '鍥炬爣鍚',
  `is_online` tinyint DEFAULT '0' COMMENT '鏄?惁涓婄嚎 0=涓嬬嚎 1=涓婄嚎',
  `is_active` tinyint DEFAULT '0' COMMENT '鏄?惁褰撳墠灞曠ず 0=鍚?1=鏄',
  `sort_order` int DEFAULT '0' COMMENT '鎺掑簭',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_key` (`model_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='妯″瀷鍏?憡/妯?箙閫氱煡琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `model_announcement`
--

LOCK TABLES `model_announcement` WRITE;
/*!40000 ALTER TABLE `model_announcement` DISABLE KEYS */;
/*!40000 ALTER TABLE `model_announcement` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `news`
--

LOCK TABLES `news` WRITE;
/*!40000 ALTER TABLE `news` DISABLE KEYS */;
INSERT INTO `news` VALUES (1,'春季养生指南：中医教你调理肝气','<p>春季养生指南：中医教你调理肝气</p>',1,NULL,'[2, 3, 4]',0,0,'2026-07-01 10:00:00'),(2,'有氧运动如何保护心血管','规律的中等强度有氧运动可改善心肺功能、降低血脂。文章给出每周运动时长建议与居家可执行的训练方案。',2,NULL,'[2, 3, 4, 5]',1,1,'2026-07-02 10:00:00'),(3,'失眠人群的睡眠卫生清单','从作息、环境到睡前习惯，系统梳理影响睡眠的因素，并提供可操作的改善清单。',3,NULL,'[2, 3, 4, 5, 6]',1,1,'2026-07-03 10:00:00'),(4,'体检报告里的血脂指标怎么看','解读总胆固醇、甘油三酯、高低密度脂蛋白的意义，以及何时需要就医干预。',4,NULL,'[2, 3, 4, 5, 6, 7]',1,1,'2026-07-04 10:00:00'),(5,'糖尿病患者日常饮食搭配','围绕升糖指数(GI)讲解主食、蛋白与蔬菜的科学搭配，附带一周参考食谱。',1,NULL,'[2, 3]',1,1,'2026-07-05 10:00:00'),(6,'办公室人群肩颈放松操','针对久坐人群设计的一套简易拉伸动作，每天10分钟缓解肩颈僵硬。',2,NULL,'[2, 3, 4]',1,1,'2026-07-06 10:00:00'),(7,'认识甲状腺功能减退','介绍甲减的常见症状、诊断方式与规范用药，强调定期复查的重要性。',5,NULL,'[2, 3, 4, 5]',0,1,'2026-07-07 10:00:00'),(8,'痛风急性发作怎么办','从饮食、饮水到药物，说明急性发作期的处理原则与长期尿酸管理。',4,NULL,'[2, 3, 4, 5, 6]',1,1,'2026-07-08 10:00:00'),(9,'心理健康：识别焦虑信号','帮助大众识别日常焦虑与焦虑障碍的边界，并介绍自助调节方法。',3,NULL,'[2, 3, 4, 5, 6, 7]',1,1,'2026-07-09 10:00:00'),(10,'中老年人骨密度养护','讲解钙与维生素D的补充策略、负重运动对骨骼的好处，预防骨质疏松。',5,NULL,'[2, 3]',0,1,'2026-07-10 10:00:00'),(11,'保健养生从现在做起','<p>为了家人，为了亲朋，保健养生从现在做起！</p><p>养生小贴士：</p><p>1. 多喝水</p><p>2. 少熬夜</p><p>3. 多运动</p><p>4. 保持好心情</p>',5,'http://localhost:21090/api/personal-health/v1.0/file/getFile?fileName=articles/e7bcce46.png',NULL,0,0,'2024-07-20 15:00:00'),(12,'健康饮食的重要性','<p>健康饮食是身体健康的基础。</p><p>建议：</p><p>1. 多吃蔬菜水果</p><p>2. 控制盐糖摄入</p><p>3. 适量蛋白质</p><p>4. 少吃加工食品</p><p>5. 规律饮食时间</p>',1,'http://localhost:21090/api/personal-health/v1.0/file/getFile?fileName=articles/ba3b54812.png',NULL,0,0,'2024-07-21 11:00:00'),(13,'慢性阻塞性肺疾病(COPD)诊疗规范','<h1><strong>一、定义与分型</strong></h1><p>COPD是一种以持续性气流受限为特征的常见、可预防和可治疗的慢性呼吸系统疾病。典型表型包括：慢性支气管炎型（咳嗽咳痰为主）和肺气肿型（呼吸困难为主）。</p><h1><strong>二、诊断标准</strong></h1><p>1. 危险因素：吸烟史（吸烟指数=包/天×年数&gt;10包年）、生物燃料暴露、职业粉尘等</p><p>2. 临床症状：慢性咳嗽/咳痰+进行性呼吸困难（mMRC分级评估）</p><p>3. 肺功能：支气管扩张剂吸入后FEV1/FVC &lt; 0.70（不可逆气流受限）</p><h1><strong>三、气流受限严重度分级（GOLD分期）</strong></h1><table style=\\\"width: auto;\\\"><tbody><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">GOLD分级</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">FEV1(%预计值)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">严重度</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">典型表现</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">GOLD 1</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">≥80%</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">轻度</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">可能无症状</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">GOLD 2</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">50-79%</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">中度</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">劳力后气短</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">GOLD 3</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">30-49%</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">重度</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">日常活动即气短</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">GOLD 4</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">&lt;30%</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">极重度</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"288\\\">静息时亦气短，常伴呼吸衰竭</td></tr></tbody></table><h1><strong>四、GOLD ABCD综合评估（症状+急性加重风险）</strong></h1><table style=\\\"width: auto;\\\"><tbody><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">分组</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">特征</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">初始治疗</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">A组</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">mMRC 0-1/CAT&lt;10 急性加重≤1次(无住院)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">短效支气管扩张剂 prn</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">B组</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">mMRC≥2/CAT≥10 急性加重≤1次(无住院)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">LAMA 或 LABA 单药</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">C组</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">mMRC 0-1/CAT&lt;10 急性加重≥2次或≥1次住院</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">LAMA 单药</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">D组</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">mMRC≥2/CAT≥10 急性加重≥2次或≥1次住院</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">LAMA+LABA；若血EOS≥300→ICS+LABA+LAMA</td></tr></tbody></table><h1><strong>五、药物治疗</strong></h1><h2><strong>1. 支气管扩张剂（核心治疗）</strong></h2><p>SABA（短效β2激动剂）：沙丁胺醇，按需使用，作用维持4-6小时</p><p>SAMA（短效抗胆碱能）：异丙托溴铵，按需使用</p><p>LABA（长效β2激动剂）：福莫特罗、沙美特罗、茚达特罗，作用维持12-24小时，qd/bid</p><p>LAMA（长效抗胆碱能）：噻托溴铵、乌美溴铵，作用维持24小时，qd</p><h2><strong>2. 吸入性糖皮质激素(ICS)</strong></h2><p>血嗜酸性粒细胞(EOS)≥300个/μL → ICS联合治疗获益最大</p><p>EOS 100-299个/μL → ICS联合治疗有一定获益</p><p>EOS&lt;100个/μL → ICS获益小，不推荐常规使用</p><p>常见不良反应：口腔念珠菌感染（每次吸入后漱口）、声音嘶哑</p><h1><strong>六、非药物治疗</strong></h1><p>1. 戒烟：唯一被证实可延缓FEV1下降速度的干预措施</p><p>2. 肺康复：运动训练+营养指导+疾病自我管理教育，疗程6-12周</p><p>3. 长期氧疗(LTOT)：静息PaO2≤55mmHg或SaO2≤88%，每天吸氧≥15小时</p><p>4. 无创正压通气(NPPV)：稳定期高碳酸血症(PaCO2≥52mmHg)</p><p>5. 外科手术：肺减容术(特定肺气肿患者)、肺移植(终末期)</p><h1><strong>七、急性加重期管理</strong></h1><p>诱因：呼吸道感染（病毒&gt;细菌）、空气污染、治疗中断</p><p>诊断依据：呼吸困难加重+痰量增多+痰变脓性（满足≥2项为中度，全部满足为重度）</p><h2><strong>分级治疗</strong></h2><p>轻度加重：增加SABA/SAMA使用频率</p><p>中度加重：口服泼尼松30-40mg/天×5-7天 + 抗生素（满足痰变脓性条件时）</p><p>重度加重（需住院）：雾化吸入支气管扩张剂 + 全身糖皮质激素 + 必要时无创通气或气管插管</p><h2><strong>抗生素使用指征</strong></h2><p>呼吸困难加重+痰量增多+痰变脓性 → 三联征全满足或只有后面两项</p><p>首选：阿莫西林/克拉维酸、头孢呋辛、呼吸喹诺酮（莫西沙星/左氧氟沙星）</p><p>有铜绿假单胞菌感染风险者：环丙沙星或抗假单胞β内酰胺类</p><h1><strong>八、合并症筛查与管理</strong></h1><p>COPD常见肺外合并症：</p><p>1. 心血管疾病（COPD死亡首要原因之一）：缺血性心脏病、心衰、房颤</p><p>2. 骨质疏松：糖皮质激素使用+吸烟+营养不良+低体重</p><p>3. 焦虑抑郁：约40%COPD患者合并，影响治疗依从性和生活质量</p><p>4. 胃食管反流：加重咳嗽症状，PPI试验性治疗</p><p>5. 肺癌：COPD是肺癌独立危险因素，推荐LDCT筛查</p><p><br></p><p>声明：本文仅供医学专业人员参考学习，具体诊疗请遵医嘱。</p><p><br></p>',4,'https://picsum.photos/seed/health1/400/250',NULL,1,1,'2026-06-01 20:33:41'),(14,'地中海饮食与心血管疾病的预防','<ol><li>什么是地中海饮食？</li></ol><p>地中海饮食（Mediterranean Diet）并非一种严格的、限制性的减肥食谱，而是一种泛指希腊、意大利南部、西班牙等环地中海国家传统的饮食习惯和生活方式。多次被美国《新闻与世界报道》（U.S. News & World Report）评为全球最佳整体饮食模式。</p><h2>2. 地中海饮食的核心构成</h2><p>地中海饮食金字塔强调以下几个层级：</p><ol><li>每日大量摄入（基础）： 蔬菜、水果、全谷物（燕麦、糙米、全麦）、豆类、坚果和种子。 特级初榨橄榄油作为主要的脂肪来源（替代黄油和猪油）。 大量的水。</li><li>每周适量摄入（中层）： 鱼类和海鲜（每周至少两次，特别是富含Omega-3的深海鱼如三文鱼、沙丁鱼）。 禽肉、鸡蛋和适量的乳制品（主要是酸奶和奶酪）。</li><li>极少量摄入（顶层）： 红肉（猪肉、牛肉、羊肉）和加工肉类（香肠、培根）。 甜点和含糖饮料。</li><li>生活方式的配合： 与家人朋友共享膳食，保持规律的体育活动。 成年人可适量饮用红酒（通常建议随餐饮用，女性每天不超过一杯，男性不超过两杯），但这并非强制要求。</li></ol><h2>3. 对心血管健康的保护机制</h2><p>地中海饮食在预防心脏病和中风方面有着强大的临床证据支持，其生理机制主要包括：</p><ul><li>改善血脂代谢：富含单不饱和脂肪酸（橄榄油）和多不饱和脂肪酸（深海鱼），有助于降低“坏胆固醇”（LDL-C）水平，升高“好胆固醇”（HDL-C）水平。</li><li>抗炎与抗氧化作用：大量的蔬菜、水果和坚果提供了丰富的维生素、矿物质、膳食纤维和植物化学物质（如多酚类），能有效中和体内的自由基，减轻系统性炎症反应（炎症是动脉粥样硬化的关键驱动因素）。</li><li>改善内皮功能：保持血管弹性，有助于稳定血压。</li><li>调节肠道菌群：高膳食纤维促进有益肠道细菌的生长，这些细菌产生的短链脂肪酸对代谢健康有益。</li></ul><h2>4. 常见问答 (Q&A)</h2><ul><li>Q：如果买不到特级初榨橄榄油，可以用什么代替？ A：如果条件受限，可以选择其他富含单不饱和脂肪酸的植物油，如茶籽油（被称为“东方橄榄油”）、芥花籽油（Canola oil）或花生油，但尽量避免使用油炸等高温烹饪方式。</li><li>Q：地中海饮食适合减肥吗？ A：虽然地中海饮食的初衷不是减肥，但由于其富含膳食纤维且提倡健康脂肪，能提供良好的饱腹感。如果在遵循地中海饮食原则的同时，适度控制总热量摄入，它可以成为一种非常健康、不易反弹的长期减重方式。</li></ul>',1,'https://picsum.photos/seed/health2/400/250',NULL,NULL,NULL,'2026-06-01 20:35:01'),(15,'常见急症鉴别诊断与院前处理','<h1><strong>一、急性胸痛</strong></h1><p>急性胸痛是急诊科最常见的危重症状之一，病因涵盖心源性、肺源性、消化源性等多个系统。快速鉴别至关重要。</p><h2><strong>1. 急性心肌梗死</strong></h2><p>典型表现：胸骨后压榨性疼痛，持续&gt;20分钟，可放射至左肩、左臂内侧、下颌或背部，常伴大汗、濒死感。含服硝酸甘油不缓解。</p><p>院前处理：立即停止一切活动，取半卧位，舌下含服硝酸甘油0.5mg（血压不低时），嚼服阿司匹林300mg（无过敏及出血禁忌），呼叫120。</p><p>关键检查：心电图（18导联）+ 心肌标志物（hs-cTnI、CK-MB）。</p><h2><strong>2. 肺栓塞</strong></h2><p>典型表现：突发胸痛+呼吸困难+咯血三联征，可伴晕厥、低血压。常有深静脉血栓危险因素（长期卧床、手术、肿瘤、妊娠等）。</p><p>关键检查：D-二聚体筛查 + CT肺动脉造影(CTPA)确诊。</p><h2><strong>3. 主动脉夹层</strong></h2><p>典型表现：突发撕裂样剧痛，疼痛部位可随夹层扩展而迁移，双上肢血压差&gt;20mmHg。</p><p>紧急处理：控制心率&lt;60次/分，收缩压降至100-120mmHg，立即转送血管外科。</p><h1><strong>二、急性腹痛</strong></h1><h2><strong>1. 急性阑尾炎</strong></h2><p>典型表现：转移性右下腹痛（脐周→右下腹），伴麦氏点压痛、反跳痛。可有低热、恶心呕吐、白细胞升高。</p><p>鉴别：需排除右侧输尿管结石、宫外孕破裂（女性）、肠系膜淋巴结炎（儿童）。</p><h2><strong>2. 急性胰腺炎</strong></h2><p>典型表现：暴饮暴食或饮酒后突发上腹剧烈持续性疼痛，向腰背部放射，屈曲体位可缓解。血清淀粉酶升高&gt;正常上限3倍。</p><p>严重度评估：Ranson评分≥3或CTSI≥4分为重症，死亡率可达30%。治疗核心：禁食+液体复苏+镇痛。</p><h2><strong>3. 肠梗阻</strong></h2><p>四大典型症状：腹痛、呕吐、腹胀、停止排便排气。腹部立位平片可见阶梯状气液平面。</p><p>紧急情况：绞窄性肠梗阻（持续剧烈腹痛+腹膜刺激征+发热）需紧急手术。</p><h1><strong>三、急性脑血管病（卒中）</strong></h1><p>时间就是大脑！溶栓时间窗：发病4.5小时内。</p><h2><strong>FAST快速识别法</strong></h2><table style=\\\"width: auto;\\\"><tbody><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">字母</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">含义</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">检查方法</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">F (Face)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">面部</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">微笑时口角是否歪斜</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">A (Arm)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">手臂</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">双上肢平举是否一侧下垂</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">S (Speech)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">语言</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">说话是否含糊不清或无法理解</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">T (Time)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">时间</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">出现任一症状立即拨打120</td></tr></tbody></table><h1><strong>四、过敏性休克</strong></h1><p>诱因：药物（青霉素最常见）、食物（坚果、海鲜）、昆虫叮咬。</p><p>识别：接触过敏原后数分钟至数小时内出现皮肤潮红/荨麻疹+呼吸窘迫+低血压，可伴喉头水肿、支气管痉挛。</p><p>急救：肾上腺素0.3-0.5mg大腿外侧肌注（1:1000），每5-15分钟可重复。同时建立静脉通路，快速补液。糖皮质激素和抗组胺药作为二线辅助治疗。</p><h1><strong>五、院前急救通用原则</strong></h1><p>1. DRABC评估：危险(Danger)→反应(Response)→气道(Airway)→呼吸(Breathing)→循环(Circulation)</p><p>2. 心肺复苏：按压频率100-120次/分，深度5-6cm，按压:通气=30:2</p><p>3. 止血：直接压迫止血，止血带仅用于危及生命的四肢大出血</p><p>4. 搬运：疑似脊柱损伤者需轴位翻身，颈椎损伤需颈托固定</p><p>5. 心理支持：对清醒患者进行安抚，告知已呼叫急救，减少恐惧和焦虑</p><p> </p><p>声明：本文仅供医学专业人员参考学习，具体诊疗请遵医嘱。紧急情况请立即拨打120。</p><p><br></p>',4,'https://picsum.photos/seed/health4/400/250',NULL,NULL,NULL,'2026-06-01 20:35:45'),(16,'儿童常见病家庭护理汇总','<p><br></p><p>一、小儿发热</p><p>1. 体温分级</p><p> &nbsp; - 低热：37.3-38.0℃</p><p> &nbsp; - 中度发热：38.1-39.0℃</p><p> &nbsp; - 高热：39.1-41.0℃</p><p> &nbsp; - 超高热：&gt;41.0℃</p><p><br></p><p>2. 退热药物选择</p><p> &nbsp; - 对乙酰氨基酚（扑热息痛）：10-15mg/kg/次，间隔4-6小时，每日不超过5次</p><p> &nbsp; - 布洛芬：5-10mg/kg/次，间隔6-8小时，每日不超过4次</p><p> &nbsp; - 不推荐交替使用，除非单一药物效果不佳且遵医嘱</p><p> &nbsp; - 3个月以下婴儿发热禁用退热药，立即就医</p><p><br></p><p>3. 物理降温</p><p> &nbsp; - 保持室内通风，室温22-25℃</p><p> &nbsp; - 减少衣物包裹，利于散热</p><p> &nbsp; - 温水擦浴（32-34℃）颈、腋、腹股沟，禁用酒精擦浴</p><p> &nbsp; - 多饮水，观察尿量（婴幼儿&gt;1ml/kg/h为正常）</p><p><br></p><p>4. 立即就医的警示信号</p><p> &nbsp; - 3月龄以下婴儿体温&gt;38℃</p><p> &nbsp; - 持续高热&gt;72小时</p><p> &nbsp; - 精神萎靡、嗜睡、烦躁不安</p><p> &nbsp; - 出现惊厥（热性惊厥）</p><p> &nbsp; - 呼吸急促（&lt;1岁&gt;50次/分，1-5岁&gt;40次/分）</p><p> &nbsp; - 皮肤出现紫癜或瘀斑（警惕脑膜炎球菌败血症）</p><p><br></p><p>二、小儿腹泻</p><p>1. 脱水程度判断</p><p> &nbsp; 轻度：口唇稍干，尿量略少，前囟/眼窝稍凹陷</p><p> &nbsp; 中度：皮肤弹性差，尿量明显减少，前囟/眼窝明显凹陷</p><p> &nbsp; 重度：意识改变，无尿&gt;6小时，四肢凉，脉搏细弱（立即就医）</p><p><br></p><p>2. 口服补液盐(ORS)</p><p> &nbsp; - 低渗ORS配方：氯化钠2.6g + 枸橼酸钠2.9g + 氯化钾1.5g + 无水葡萄糖13.5g + 水1000ml</p><p> &nbsp; - 原则：拉多少补多少，少量多次喂服</p><p> &nbsp; - 轻度脱水：50ml/kg，4小时内分次喝完</p><p> &nbsp; - 母乳喂养继续，辅食暂停生冷油腻</p><p><br></p><p>3. 常用药物</p><p> &nbsp; - 蒙脱石散：&lt;1岁 1g tid，1-2岁 1.5g tid，&gt;2岁 3g tid（空腹，与其它药物隔1h）</p><p> &nbsp; - 益生菌：双歧杆菌/布拉氏酵母菌，辅助恢复肠道菌群</p><p> &nbsp; - 锌制剂：6个月以下 10mg/天，6个月以上 20mg/天，连服10-14天</p><p> &nbsp; - 禁用成人止泻药（如洛哌丁胺，有中枢神经毒性风险）</p><p><br></p><p>三、手足口病</p><p>1. 病原：肠道病毒（柯萨奇A16、EV71最常见）</p><p>2. 典型表现：发热+手足臀丘疹/疱疹+口腔疱疹/溃疡</p><p>3. 高峰季节：5-7月，好发年龄：5岁以下</p><p>4. 隔离期：症状消失后1周，约10-14天</p><p>5. 重症预警（EV71感染）：持续高热&gt;3天 + 精神差/呕吐/肢体抖动 + 心率呼吸增快</p><p>6. 治疗：对症支持，无特效抗病毒药，EV71疫苗可预防</p><p><br></p><p>四、急性上呼吸道感染（感冒）</p><p>1. 90%为病毒感染，7-10天自愈</p><p>2. 不常规使用抗生素</p><p>3. 对症：鼻塞用生理盐水喷鼻，咳嗽&gt;1岁可服蜂蜜2-5ml</p><p>4. 6岁以下禁用复方感冒药（含减充血剂/抗组胺成分）</p><p>5. 并发症警示：耳痛（中耳炎）、呼吸急促（肺炎）、持续发热&gt;3天</p><p><br></p>',4,'https://picsum.photos/seed/health3/400/250',NULL,NULL,NULL,'2026-06-01 20:40:54'),(17,'一本小小的病理书','<p>头痛，发热，鼻塞，流涕，咽痛是由风寒感冒引起的1，需要开感冒灵颗粒</p>',4,'https://picsum.photos/seed/health3/400/250',NULL,NULL,NULL,'2026-06-01 20:44:20'),(18,'肥胖症的临床营养干预与减重策略','<h2>1. 肥胖症的定义与危害</h2><p>肥胖症是一种由多因素引起的慢性代谢性疾病，表现为体内脂肪过度积聚。临床上通常使用身体质量指数（BMI）来衡量：</p><ul><li>BMI = 体重(kg) / 身高(m)的平方</li><li>根据中国标准：BMI 24.0-27.9 为超重，BMI ≥ 28.0 为肥胖。</li></ul><p>肥胖是2型糖尿病、高血压、冠心病、阻塞性睡眠呼吸暂停综合征（OSAS）以及某些癌症（如乳腺癌、结肠癌）的独立危险因素。</p><h2>2. 减重的核心原理：能量负平衡</h2><p>所有成功的减重饮食干预，其底层逻辑都是创造<strong>能量缺口（Caloric Deficit）</strong>，即：<strong>总能量消耗 &gt; 总能量摄入</strong>。<br>通常建议每天创造 500-750 千卡的能量缺口，这样可以实现每周减轻 0.5-1.0 公斤体重的稳健目标。</p><h2>3. 常见的医学减重饮食模式</h2><h3>3.1 限能量平衡膳食 (CRD)</h3><ul><li>特点：在限制总热量摄入的同时，保持营养素比例平衡。碳水化合物占50%-60%，蛋白质占15%-20%，脂肪占20%-30%。</li><li>优势：安全性高，容易长期坚持，适合绝大多数肥胖患者。</li></ul><h3>3.2 高蛋白饮食 (HPD)</h3><ul><li>特点：提高蛋白质的摄入比例（通常占总热量的20%-30%，或每天1.5-2.0g/kg体重）。</li><li>机制：蛋白质的食物热效应（TEF）最高，消化吸收需要消耗更多热量；同时能增加饱腹感，并在减重期间最大限度地保留瘦体重（肌肉）。</li><li>禁忌：慢性肾脏病患者慎用。</li></ul><h3>3.3 间歇性禁食 (Intermittent Fasting, IF)</h3><ul><li>特点：不严格限制食物种类，而是限制进食的“时间窗口”。最流行的是 16:8 方案（每天禁食16小时，在8小时的窗口期内进食）。</li><li>机制：通过延长空腹时间，降低胰岛素水平，促使身体动用脂肪储备作为能量。</li><li>注意事项：进食窗口期内仍需控制总热量，不能暴饮暴食。孕妇、儿童及有进食障碍史者不宜采用。</li></ul><h2>4. 减重过程中的行为干预</h2><p>仅仅改变饮食内容是不够的，行为习惯的改变决定了减重能否长期维持：</p><ul><li>细嚼慢咽：大脑接收到饱腹信号通常需要20分钟，减慢进食速度可以减少总摄入量。</li><li>规律作息：睡眠不足会导致“瘦素”（抑制食欲）分泌减少，“饥饿素”分泌增加，导致食欲亢进，尤其是对高糖高脂食物的渴望。</li><li>情绪管理：识别并克服“情绪性进食”（因压力、焦虑或无聊而进食，而非真正的生理饥饿）。</li></ul><h2>5. 常见问答 (Q&A)</h2><ul><li>Q：为什么我减肥初期掉秤很快，后来就不动了（平台期）？ A：初期掉的体重很大一部分是水分和糖原。随着体重下降，身体的基础代谢率也会适应性降低（身体在“节能”），导致原先的能量缺口消失。打破平台期需要重新评估热量需求，或增加运动强度和力量训练。</li><li>Q：可以用果汁代替水果来减肥吗？ A：不建议。榨汁过程破坏了水果中的膳食纤维，并且果汁通常含有浓缩的果糖，消化吸收极快，不仅容易导致血糖飙升，而且饱腹感差，极易造成热量超标。</li></ul>',4,'https://picsum.photos/seed/health4/400/250',NULL,NULL,NULL,'2026-06-01 20:45:19'),(19,'高尿酸血症与痛风饮食管理指南','<p><br></p><p><br></p><p>一、诊断标准</p><p>- 高尿酸血症：非同日2次空腹血尿酸 &gt;420μmol/L(男性) 或 &gt;360μmol/L(女性)</p><p>- 痛风：高尿酸血症+关节红肿热痛+关节液检出尿酸盐结晶</p><p><br></p><p>二、降尿酸目标值</p><p>- 普通高尿酸血症：&lt;360μmol/L</p><p>- 痛风患者（无痛风石）：&lt;360μmol/L</p><p>- 痛风患者（有痛风石）：&lt;300μmol/L</p><p>- 不推荐长期 &lt;180μmol/L</p><p><br></p><p>三、嘌呤含量分级与食物选择</p><p><br></p><p>高嘌呤食物（150-1000mg/100g，急性期禁食，缓解期严格限制）</p><p> &nbsp;- 动物内脏：猪肝(229)、猪肾(226)、鸡肝(243)</p><p> &nbsp;- 海鲜：沙丁鱼(295)、鲭鱼(210)、贝类、虾蟹、鱼籽</p><p> &nbsp;- 浓肉汤、火锅汤底(嘌呤溶于水，熬煮后极高)</p><p> &nbsp;- 红肉：牛肉、猪肉、羊肉（缓解期&lt;100g/天）</p><p><br></p><p>中嘌呤食物（50-150mg/100g，缓解期可适量）</p><p> &nbsp;- 白肉：去皮鸡肉、鸭肉</p><p> &nbsp;- 部分鱼类：三文鱼、鳕鱼、鲤鱼</p><p> &nbsp;- 豆类及豆制品：豆腐、豆浆、豌豆（植物嘌呤对血尿酸影响远小于动物嘌呤）</p><p> &nbsp;- 菌菇类：香菇、金针菇</p><p><br></p><p>低嘌呤食物（&lt;50mg/100g，可自由选择）</p><p> &nbsp;- 主食类：米饭、面条、馒头、玉米、土豆</p><p> &nbsp;- 蔬菜类：绝大多数叶菜、瓜果类蔬菜</p><p> &nbsp;- 水果类：苹果、梨、桃（但应限制高果糖水果：西瓜、荔枝）</p><p> &nbsp;- 奶制品：低脂/脱脂牛奶、酸奶（促进尿酸排泄）</p><p> &nbsp;- 蛋类：鸡蛋、鸭蛋（极低嘌呤优质蛋白）</p><p><br></p><p>四、饮水管理</p><p>- 每日饮水量 2000-3000ml，保证尿量&gt;2000ml/天</p><p>- 推荐：白开水、淡茶、苏打水（弱碱性）</p><p>- 禁忌：含糖饮料、果汁（果糖代谢产生尿酸）、啤酒（含鸟嘌呤核苷）</p><p><br></p><p>五、药物干预指征</p><p>启动降尿酸药物治疗的条件（满足任一）：</p><p>1. 痛风发作≥2次/年</p><p>2. 有痛风石或关节尿酸沉积证据</p><p>3. 合并慢性肾脏病(CKD 3期以上)</p><p>4. 血尿酸持续&gt;540μmol/L(9mg/dL)</p><p><br></p><p>六、常用降尿酸药物</p><p>1. 别嘌醇：抑制尿酸生成。起始100mg/天，最大600mg/天。HLA-B*5801阳性者禁用（亚裔高发，可致严重超敏反应综合征）</p><p>2. 非布司他：抑制尿酸生成。起始20-40mg/天，最大80mg/天。心血管疾病患者慎用</p><p>3. 苯溴马隆：促进尿酸排泄。eGFR&lt;20ml/min禁用。服药期间需大量饮水并碱化尿液</p><p><br></p><p>七、急性痛风发作处理</p><p>- 尽早用药（发作24小时内效果最佳）</p><p>- 一线：秋水仙碱（首剂1mg，1小时后再服0.5mg，之后0.5mg bid/tid）</p><p>- 二线：非甾体抗炎药（如依托考昔、塞来昔布）</p><p>- 三线：糖皮质激素（如泼尼松30-40mg/天）</p><p>- 发作期不启动/不调整降尿酸药（避免尿酸盐波动诱发加重）</p><p><br></p>',1,'https://picsum.photos/seed/health5/400/250',NULL,NULL,NULL,'2026-06-01 20:46:39'),(20,'高血压患者日常管理指南','<p>一、血压分级标准</p><p>正常血压：收缩压 &lt; 120 mmHg 且 舒张压 &lt; 80 mmHg</p><p>正常高值：收缩压 120-139 mmHg 或 舒张压 80-89 mmHg</p><p>高血压1级：收缩压 140-159 mmHg 或 舒张压 90-99 mmHg</p><p>高血压2级：收缩压 160-179 mmHg 或 舒张压 100-109 mmHg</p><p>高血压3级：收缩压 &gt;= 180 mmHg 或 舒张压 &gt;= 110 mmHg</p><p><br></p><p>二、生活方式干预</p><p>1. 限盐：每日食盐摄入量 &lt; 6g，避免腌制食品和高钠调味品</p><p>2. 减重：BMI 控制在 18.5-23.9 kg/m²，腰围男性 &lt; 90cm，女性 &lt; 85cm</p><p>3. 运动：每周至少 150 分钟中等强度有氧运动（快走、游泳、骑车）</p><p>4. 戒烟限酒：彻底戒烟，酒精摄入男性 &lt; 25g/天，女性 &lt; 15g/天</p><p>5. DASH饮食：增加蔬菜、水果、全谷物、低脂乳制品摄入</p><p><br></p><p>三、常用降压药物分类</p><p>1. 钙通道阻滞剂(CCB)：如氨氯地平、硝苯地平控释片</p><p>2. ACEI类：如依那普利、培哚普利</p><p>3. ARB类：如缬沙坦、厄贝沙坦</p><p>4. 利尿剂：如氢氯噻嗪、吲达帕胺</p><p>5. β受体阻滞剂：如美托洛尔、比索洛尔</p><p><br></p><p>四、监测与随访</p><p>- 家庭自测血压每日早晚各1次，每次测2-3遍取平均值</p><p>- 每3-6个月复查血脂、血糖、肾功能、电解质</p><p>- 每年做一次超声心动图、颈动脉超声</p><p><br></p><p>五、危险信号（需立即就医）</p><p>- 收缩压 &gt; 180 mmHg 或舒张压 &gt; 120 mmHg 伴有头痛、视物模糊</p><p>- 突发胸痛、呼吸困难、一侧肢体无力、言语不清</p><p><br></p>',5,'https://picsum.photos/seed/health1/400/250',NULL,NULL,NULL,'2026-06-01 20:47:11'),(21,'骨质疏松症防治指南','<h1><strong>一、定义</strong></h1><p>骨质疏松症是一种以骨量减少、骨微结构破坏、骨脆性增加、易发生骨折为特征的全身性骨病。分为：原发性（绝经后骨质疏松I型、老年性骨质疏松II型）和继发性（药物、疾病导致）。</p><h1><strong>二、诊断标准（WHO标准）</strong></h1><p>基于双能X线吸收法(DXA)测定骨密度T值：</p><table style=\\\"width: auto;\\\"><tbody><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">诊断</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">T值</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">处理建议</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">正常</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">T ≥ -1.0</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">保持健康生活方式</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">骨量减少</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">-2.5 &lt; T &lt; -1.0</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">补充钙+维生素D，生活方式干预</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">骨质疏松</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">T ≤ -2.5</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">启动药物治疗</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">严重骨质疏松</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">T ≤ -2.5 + 脆性骨折</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">强化治疗方案</td></tr></tbody></table><h1><strong>三、骨转换标志物监测</strong></h1><p>骨形成标志物：PINP（I型原胶原N端前肽，最敏感）、ALP（骨性碱性磷酸酶）</p><p>骨吸收标志物：β-CTX（I型胶原C末端肽交联，最常用）、NTX（I型胶原N末端肽）</p><p>临床意义：抗骨吸收治疗3-6个月后，β-CTX应下降&gt;30%，否则需评估依从性或调整方案</p><h1><strong>四、基础治疗：钙与维生素D</strong></h1><p>推荐每日摄入量：</p><p>钙：成人800-1200mg/天（分次口服，单次≤500mg吸收最佳），饮食+补充剂总量</p><p>高钙食物：牛奶(120mg/100ml)、酸奶(150mg/100g)、豆腐(164mg/100g)、芝麻酱(1170mg/100g)、虾皮(991mg/100g)、深绿色蔬菜(油菜、芥蓝)</p><p>维生素D3：800-1200 IU/天，维持血清25(OH)D≥30ng/mL（75nmol/L）</p><p>活性维生素D及其类似物：骨化三醇/阿法骨化醇，适用于肝肾功能不全或老年人</p><h1><strong>五、抗骨质疏松药物</strong></h1><h2><strong>1. 双膦酸盐类（一线首选）</strong></h2><p>作用机制：抑制破骨细胞活性，降低骨吸收</p><p>阿仑膦酸钠：70mg 每周1次，晨起空腹&gt;200ml白水送服，服药后保持直立30分钟</p><p>唑来膦酸：5mg 静脉滴注 每年1次（方便性最好，依从性最佳）</p><p>警示：严重肾损害(eGFR&lt;35)禁用；双膦酸盐使用前需检查口腔，治疗期间避免拔牙等口腔手术（颌骨坏死风险）</p><h2><strong>2. RANKL抑制剂</strong></h2><p>地舒单抗：60mg 皮下注射 每6个月1次</p><p>优势：肾功能不全者无需调整剂量；可提高骨密度幅度优于双膦酸盐</p><p>注意：停药后骨吸收反弹性升高(反弹效应)，不可随意停药/推迟给药</p><h2><strong>3. 骨形成促进剂（合成代谢剂）</strong></h2><p>特立帕肽（重组人PTH 1-34）：20μg 皮下注射 qd，疗程≤24个月</p><p>适应症：严重骨质疏松(T≤-3.5)、椎体骨折≥2处、双膦酸盐治疗效果欠佳</p><p>禁忌：Paget骨病、既往骨骼放疗史、骨转移、高钙血症、妊娠/哺乳</p><h1><strong>六、康复与运动</strong></h1><p>核心目标：增强肌力→改善平衡→降低跌倒率→减少骨折</p><p>1. 负重运动（刺激骨形成）：快走、慢跑、爬楼梯、跳舞，每周3-5次，每次30分钟</p><p>2. 抗阻运动（增强肌肉）：弹力带、哑铃训练，每周2-3次</p><p>3. 平衡训练（防跌倒）：太极拳、单腿站立练习，每天10-15分钟</p><p>4. 姿势训练：避免前屈运动（如仰卧起坐）、剧烈扭转动作</p><p>5. 骨折急性期：伤后1-2周绝对卧床→2-4周床上四肢活动→4-8周坐起→3个月后助行器行走</p><h1><strong>七、防跌倒家庭改造</strong></h1><p>1. 移除地面障碍物、散落的电线</p><p>2. 浴室安装扶手+防滑垫，马桶座圈适当加高</p><p>3. 夜间照明充足，床头到卫生间沿途设置小夜灯</p><p>4. 穿防滑鞋，避免穿拖鞋</p><p>5. 视力矫正：定期眼科检查，白内障及早手术</p><h1><strong>八、药物治疗疗程与药物假期</strong></h1><p>口服双膦酸盐：治疗3-5年后评估是否进入药物假期（骨折风险不高时可停药1-2年观察）</p><p>静脉唑来膦酸：治疗3年后评估，高风险者继续至6年</p><p>地舒单抗：无药物假期概念，需持续给药或转换为双膦酸盐过渡</p><p>特立帕肽：疗程上限24个月，停药后序贯双膦酸盐/地舒单抗维持疗效</p><p><br></p><p>声明：本文仅供医学专业人员参考学习，具体诊疗请遵医嘱。</p><p><br></p>',4,'https://picsum.photos/seed/health4/400/250',NULL,NULL,NULL,'2026-06-01 20:48:07'),(22,'冠心病康复与二级预防指南','<p>一、冠心病分型</p><p>1. 慢性冠脉综合征(CCS)：稳定型心绞痛、缺血性心肌病</p><p>2. 急性冠脉综合征(ACS)：ST段抬高型心梗(STEMI)、非ST段抬高型心梗(NSTEMI)、不稳定型心绞痛</p><p><br></p><p>二、二级预防核心策略(ABCDE)</p><p>A — 抗血小板与ACEI/ARB</p><p> &nbsp;- 阿司匹林 100mg qd 终身服用（无禁忌），PCI术后需联合氯吡格雷/替格瑞洛双抗</p><p> &nbsp;- ACEI/ARB：改善心室重构，目标血压&lt;130/80mmHg</p><p>B — 血压与β受体阻滞剂</p><p> &nbsp;- 美托洛尔/比索洛尔：降低心肌氧耗，目标静息心率55-60次/分</p><p> &nbsp;- 血压目标&lt;130/80mmHg，老年人可放宽至&lt;140/90mmHg</p><p>C — 胆固醇管理与戒烟</p><p> &nbsp;- LDL-C目标：极高危&lt;1.4mmol/L(55mg/dL)，高危&lt;1.8mmol/L(70mg/dL)</p><p> &nbsp;- 他汀类药物+必要时联合依折麦布/PCSK9抑制剂</p><p> &nbsp;- 彻底戒烟，避免二手烟</p><p>D — 饮食与糖尿病管理</p><p> &nbsp;- 地中海饮食：橄榄油、坚果、深海鱼、蔬果为主</p><p> &nbsp;- 糖化血红蛋白 HbA1c&lt;7.0%</p><p>E — 运动与教育</p><p> &nbsp;- 每周≥150min中等强度有氧运动</p><p> &nbsp;- 心肺康复评估：6分钟步行试验、心肺运动试验</p><p><br></p><p>三、心绞痛分级(CCS分级)</p><p>I级：重体力活动诱发心绞痛</p><p>II级：日常活动轻度受限（快走、爬坡时发作）</p><p>III级：日常活动明显受限（慢走1-2条街即发作）</p><p>IV级：轻微活动或静息时即发作</p><p><br></p><p>四、硝酸酯类药物使用规范</p><p>- 舌下含服硝酸甘油0.5mg，5分钟后不缓解可重复1次，最多3次</p><p>- 15分钟仍不缓解+持续胸痛，高度怀疑急性心梗，立即呼叫120</p><p>- 硝酸酯类24小时内应有10-12小时无药间歇，避免耐药</p><p><br></p><p>五、康复分期</p><p>1. 住院期(I期)：床上被动活动→坐起→床边站立→室内慢走</p><p>2. 恢复期(II期)：出院后1-6个月，每周3-5次有氧运动，心率=静息心率+20~30次/分</p><p>3. 维持期(III期)：长期规律运动，回归社会角色</p><p><br></p><p>六、危险信号</p><p>- 静息心绞痛或心绞痛频率/程度突然加重</p><p>- 含服硝酸甘油无效的胸痛持续&gt;15分钟</p><p>- 突发呼吸困难、端坐呼吸、双下肢水肿（警惕心衰）</p>',4,'https://picsum.photos/seed/health2/400/250',NULL,NULL,NULL,'2026-06-01 20:48:57'),(23,'临床合理用药基本原则','<h2>一、合理用药五大原则</h2><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">原则</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">内涵</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\"><strong>安全性</strong></td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">用药风险小于获益。注意禁忌症、不良反应监测、特殊人群剂量调整</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\"><strong>有效性</strong></td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">选择循证医学证据充分的药物和方案</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\"><strong>经济性</strong></td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">在安全有效前提下，优选成本效果比最优的方案</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\"><strong>适当性</strong></td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">选对药、用对量、走对途径、给对疗程</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\"><strong>个体化</strong></td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">结合患者基因型、肝肾功能、年龄、合并用药制定方案</td></tr></tbody></table><hr/><p><br></p><h2>二、特殊人群用药</h2><h3>老年人用药</h3><ul><li>起始剂量为成人剂量的1/2-2/3，缓慢滴定</li><li>五种以上药物联用时评估药物相互作用（Beers标准筛查潜在不合理用药）</li><li>慎用苯二氮卓类、抗胆碱能药、NSAIDs（肾损害+消化道出血风险高）</li></ul><h3>妊娠期用药</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">FDA分级</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">定义</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">示例</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">A级</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">对照研究证实对胎儿无风险</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">左甲状腺素、叶酸</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">B级</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">动物实验中未见风险或无人类数据</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">青霉素类、头孢菌素、胰岛素</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">C级</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">动物实验中显示不良反应，缺乏人类数据</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">沙丁胺醇、钙通道阻滞剂</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">D级</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">人类数据显示对胎儿有风险，但获益可能大于风险</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">卡马西平、丙戊酸钠、华法林</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">X级</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">动物和人类已证实致畸，妊娠期绝对禁用</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">异维A酸、沙利度胺、他汀类</td></tr></tbody></table><h3>哺乳期用药</h3><ul><li>大多数药物以低浓度进入乳汁（&lt;母体剂量的1-2%）</li><li>哺乳后立即服药可减少乳汁中药物浓度峰值</li><li>安全选项：对乙酰氨基酚、布洛芬、青霉素类</li></ul><hr/><p><br></p><h2>三、肝肾功能不全剂量调整</h2><h3>肾功能不全（以eGFR为参考）</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">eGFR (ml/min/1.73m²)</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">调整方案</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&gt;60</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">正常剂量</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">30-60</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">部分药物需减量25-50%或延长给药间隔</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">15-30</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">多数药物需显著减量</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&lt;15 或透析</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">参照透析用药指南调整</td></tr></tbody></table><p><strong>常见需要肾功能调整的药物：</strong></p><ul><li>抗生素：头孢类、喹诺酮类、氨基糖苷类（肾毒性，CKD慎用）、万古霉素</li><li>抗病毒药：阿昔洛韦、更昔洛韦</li><li>降糖药：二甲双胍（eGFR&lt;30禁用，eGFR 30-45减量不超过1g/天）</li><li>NSAIDs：CKD 3期以上尽量避免</li><li>低分子肝素：治疗剂量时需监测抗Xa因子活性</li></ul><h3>肝功能不全（Child-Pugh分级）</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">分级</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">A级（轻度）</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">B级（中度）</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">C级（重度）</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">胆红素(mg/dL)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&lt;2</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">2-3</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&gt;3</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">白蛋白(g/L)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&gt;35</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">28-35</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&lt;28</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">PT延长(秒)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&lt;4</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">4-6</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&gt;6</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">腹水</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">无</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">可控</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">难治</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">脑病</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">无</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">1-2级</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">3-4级</td></tr></tbody></table><hr/><p><br></p><h2>四、药物相互作用警示</h2><h3>药效学相互作用（同侧效应叠加）</h3><ul><li>非甾体抗炎药+糖皮质激素 → 消化道出血风险倍增</li><li>镇静催眠药+酒精 → 呼吸抑制</li><li>ACEI+保钾利尿剂 → 高钾血症</li></ul><h3>药代动力学相互作用（代谢酶CYP450介导）</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">机制</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">代表药</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">联用后果</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">CYP3A4抑制剂+他汀</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">酮康唑/克拉霉素+阿托伐他汀</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">横纹肌溶解风险</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">CYP2C19抑制剂+氯吡格雷</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">奥美拉唑+氯吡格雷</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">抗血小板效果减弱</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">CYP2C9诱导剂+华法林</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">利福平+华法林</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">抗凝不足 → 血栓</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">P-gp抑制剂+地高辛</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">胺碘酮+地高辛</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">地高辛中毒(心律失常)</td></tr></tbody></table><h3>常用CYP酶诱导剂和抑制剂</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">CYP酶</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">诱导剂</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">抑制剂</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">CYP3A4</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">利福平、卡马西平、苯妥英钠、圣约翰草</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">酮康唑、伊曲康唑、克拉霉素、葡萄柚汁</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">CYP2C9</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">利福平</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">氟康唑、胺碘酮</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">CYP2C19</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">利福平</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">奥美拉唑、氟西汀</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">CYP2D6</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">—</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">氟西汀、帕罗西汀、奎尼丁</td></tr></tbody></table><hr/><p><br></p><h2>五、抗生素合理使用</h2><h3>抗菌药物分级管理</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">级别</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">特点</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">代表药</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">非限制使用</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">安全有效、经济</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">青霉素G、阿莫西林、头孢氨苄</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">限制使用</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">针对特定病原菌</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">头孢曲松、左氧氟沙星、万古霉素</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">特殊使用</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">最后防线</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">碳青霉烯类、替加环素、利奈唑胺</td></tr></tbody></table><h3>抗生素使用六不原则</h3><ol><li>病毒感染不用（普通感冒90%为病毒性）</li><li>不明原因发热不用（除非有明确感染灶）</li><li>预防性使用不滥用（仅限于围手术期等明确指征）</li><li>能用窄谱不用广谱</li><li>能用单药不用联合（除非混合感染或协同需求）</li><li>疗程足而不超（一般7-14天，特殊感染按指南延长）</li></ol><p><br></p>',4,'https://picsum.photos/seed/health2/400/250',NULL,NULL,NULL,'2026-06-01 20:49:50'),(24,'营养学基础：宏量营养素与微量营养素','<h2>1. 概述</h2><p>营养学是研究食物中对人体有益的成分（营养素）以及它们在体内消化、吸收、代谢和排泄过程的科学。人体需要的营养素主要分为两大部分：宏量营养素（Macronutrients）和微量营养素（Micronutrients），此外还包括水和膳食纤维。</p><h2>2. 宏量营养素</h2><p>宏量营养素是人体需求量较大、能够提供能量的营养物质。</p><h3>2.1 碳水化合物 (Carbohydrates)</h3><ul><li>功能：人体最主要、最经济的能量来源，尤其是大脑和神经系统的主要燃料。1克碳水化合物提供约 4 千卡能量。</li><li>分类： 简单碳水化合物：单糖（如葡萄糖、果糖）和双糖（如蔗糖、乳糖），消化吸收快，易引起血糖波动。 复杂碳水化合物：多糖（如淀粉、糖原），消化吸收较慢，提供持久能量。</li><li>食物来源：谷物、薯类、豆类、水果等。</li></ul><h3>2.2 蛋白质 (Proteins)</h3><ul><li>功能：构成和修复身体组织（肌肉、骨骼、皮肤等），合成酶、激素和抗体，维持酸碱平衡。1克蛋白质提供约 4 千卡能量。</li><li>组成：由氨基酸组成。人体有9种必需氨基酸无法自行合成，必须通过食物获取。</li><li>食物来源： 优质蛋白：肉类、家禽、鱼类、蛋类、奶制品、大豆及其制品。 非完全蛋白：部分谷物和蔬菜。</li></ul><h3>2.3 脂肪 (Fats/Lipids)</h3><ul><li>功能：提供高密度能量（1克脂肪提供约 9 千卡能量），保护内脏器官，维持体温，促进脂溶性维生素（A、D、E、K）的吸收。</li><li>分类： 饱和脂肪酸：多存在于动物脂肪、椰子油中，摄入过多与心血管疾病风险增加有关。 不饱和脂肪酸：包括单不饱和脂肪酸（橄榄油、坚果）和多不饱和脂肪酸（鱼油中的Omega-3，植物油中的Omega-6），对心血管健康有益。 反式脂肪酸：存在于部分加工食品中，对健康极为有害，应尽量避免。</li></ul><h2>3. 微量营养素</h2><p>微量营养素虽然人体需求量极小，但对维持正常生理功能至关重要，几乎不提供能量。</p><h3>3.1 维生素 (Vitamins)</h3><ul><li>脂溶性维生素：维生素A（视力、免疫）、维生素D（骨骼健康、钙吸收）、维生素E（抗氧化）、维生素K（凝血功能）。</li><li>水溶性维生素：维生素C（抗氧化、胶原蛋白合成）、B族维生素（参与能量代谢和神经系统功能）。</li></ul><h3>3.2 矿物质 (Minerals)</h3><ul><li>常量元素：钙（骨骼和牙齿）、磷、钾、钠、镁。</li><li>微量元素：铁（氧气运输）、锌（免疫功能、伤口愈合）、碘（甲状腺功能）、硒（抗氧化）。</li></ul><h2>4. 常见问答 (Q&A)</h2><ul><li>Q：什么是“空热量”食物？ A：指那些含有高热量（通常来自添加糖和固体脂肪）但几乎不含维生素、矿物质、蛋白质或膳食纤维等必需营养素的食物，如碳酸饮料、薯片和糖果。</li><li>Q：为什么膳食纤维很重要，虽然它不能被消化？ A：膳食纤维可以增加饱腹感，促进肠道蠕动，预防便秘；可溶性纤维还有助于降低血液胆固醇水平和稳定血糖。</li></ul>',1,'https://picsum.photos/seed/health3/400/250',NULL,NULL,NULL,'2026-06-01 20:50:32'),(25,'孕期营养与围产保健管理','<h1><strong>一、孕期体重增长推荐（IOM标准）</strong></h1><table style=\\\"width: auto;\\\"><tbody><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">孕前BMI(kg/m²)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">推荐总增重(kg)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">孕中晚期增重速率(kg/周)</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">低体重 &lt;18.5</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">12.5 - 18</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">0.44 - 0.58</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">正常体重 18.5-24.9</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">11.5 - 16</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">0.35 - 0.50</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">超重 25-29.9</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">7 - 11.5</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">0.23 - 0.33</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">肥胖 ≥30</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">5 - 9</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">0.17 - 0.27</td></tr></tbody></table><h1><strong>二、关键营养素补充</strong></h1><h2><strong>1. 叶酸（预防神经管缺陷）</strong></h2><p>剂量：孕前3个月至孕早期(12周) 400-800μg/天</p><p>高危人群（既往NTD妊娠史/抗癫痫药）增至4-5mg/天</p><p>食物来源：深绿色叶菜(菠菜、芦笋)、豆类、动物肝脏、强化谷物</p><h2><strong>2. 铁（预防缺铁性贫血）</strong></h2><p>妊娠期铁需求量：约1000mg（红细胞增加+胎儿+胎盘+分娩失血储备）</p><p>孕中期开始常规补铁30-60mg/天（与维生素C同服促进吸收，避免与钙/茶同服）</p><p>缺铁性贫血诊断：Hb&lt;110g/L(孕期)，&lt;100g/L(产后)，血清铁蛋白&lt;15μg/L</p><p>食物来源：红肉、动物肝脏(每周1-2次&lt;100g)、动物血、黑木耳</p><h2><strong>3. 钙</strong></h2><p>推荐量：孕期/哺乳期 1000-1200mg/天</p><p>低钙摄入地区额外补钙1500-2000mg/天可降低子痫前期风险</p><p>食物来源：牛奶及奶制品(首选)、豆腐、芝麻酱(钙含量极高)、虾皮</p><h2><strong>4. 碘</strong></h2><p>孕期甲状腺激素合成增加50%，需额外补碘150μg/天</p><p>使用碘盐是基础，孕期建议含碘复合维生素</p><h2><strong>5. DHA（二十二碳六烯酸）</strong></h2><p>每日≥200mg，促进胎儿大脑和视网膜发育</p><p>食物来源：深海鱼(三文鱼、沙丁鱼，每周2次)、藻油DHA补充剂</p><p>注意事项：避免含汞高的鱼类(鲨鱼、旗鱼、方头鱼、大耳马鲛)</p><h1><strong>三、产前检查时间表</strong></h1><table style=\\\"width: auto;\\\"><tbody><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">孕周</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">检查项目</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">要点</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">6-8周</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">超声确认宫内孕、胎心</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">排除宫外孕</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">11-13+6周</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">NT超声+早期唐筛(NIPT可选)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">NT&lt;2.5mm为正常</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">16-20周</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">中孕期血清学筛查(唐筛)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">三联/四联筛查</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">20-24周</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">系统超声大排畸</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">全面筛查胎儿结构异常</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">24-28周</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">OGTT口服糖耐量试验</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">筛查妊娠期糖尿病GDM</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">28-32周</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">血常规+肝肾功能</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">筛查贫血、胆汁淤积</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">36周后</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">每周胎心监护(NST)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"384\\\">评估胎盘功能</td></tr></tbody></table><h1><strong>四、妊娠期常见问题处理</strong></h1><h2><strong>1. 妊娠剧吐</strong></h2><p>轻度：少食多餐、晨起前吃苏打饼干、生姜茶、维生素B6 10-25mg tid</p><p>重度（体重下降&gt;5%+酮症+电解质紊乱）：静脉补液+维生素B1(预防Wernicke脑病)+止吐药</p><p>止吐药物：多西拉敏+维生素B6(一线)，昂丹司琼(二线，孕早期尽量避免)</p><h2><strong>2. 妊娠期糖尿病(GDM)</strong></h2><p>OGTT诊断标准：空腹&lt;5.1，1h&lt;10.0，2h&lt;8.5 mmol/L，任一超标即诊断</p><p>管理目标：空腹&lt;5.3，餐后1h&lt;7.8，餐后2h&lt;6.7 mmol/L</p><p>生活方式干预2周血糖不达标启动胰岛素治疗（二甲双胍/格列本脲也可选择）</p><h2><strong>3. 妊娠期高血压疾病</strong></h2><p>妊娠期高血压：孕20周后新发血压≥140/90，无蛋白尿</p><p>子痫前期：血压≥140/90 + 蛋白尿≥300mg/24h 或 尿蛋白/肌酐≥0.3</p><p>重度子痫前期（任一）：血压≥160/110 + 血小板减少 + 肝酶升高 + 肺水肿 + 持续头痛/视力障碍</p><p>处理原则：解痉(硫酸镁)+降压(拉贝洛尔/硝苯地平)+适时终止妊娠</p><p>阿司匹林100-150mg/天口服用于子痫前期高风险孕妇的预防（孕12-16周开始至36周）</p><p><br></p><p>声明：本文仅供医学专业人员参考学习，具体诊疗请遵医嘱。</p><p><br></p>',100,'https://picsum.photos/seed/health4/400/250',NULL,NULL,NULL,'2026-06-01 20:51:45'),(26,'常见肿瘤标志物临床解读手册','<p><br></p><blockquote>重要提示：肿瘤标志物升高≠癌症确诊，仅为辅助参考指标，确诊需依靠病理活检。单项轻度升高需结合影像学+临床症状综合判断，动态监测趋势比单次绝对值更有意义。</blockquote><hr/><p><br></p><h2>一、广谱肿瘤标志物</h2><h3>CEA（癌胚抗原）</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">项目</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">说明</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">正常值</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&lt;5 ng/mL（非吸烟者），&lt;10 ng/mL（吸烟者）</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">相关肿瘤</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">结直肠癌、胃癌、肺癌、胰腺癌、乳腺癌</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">非癌性升高</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">吸烟、肝炎、肝硬化、溃疡性结肠炎、胰腺炎</td></tr></tbody></table><p><strong>临床意义：</strong></p><ul><li>结直肠癌术后监测：术后6周应恢复正常，持续升高提示复发/转移</li><li>辅助化疗疗效评估：2周期化疗后下降&gt;30%提示有效</li><li>胸水/腹水CEA&gt;血清CEA提示恶性积液</li></ul><hr/><p><br></p><h3>CA19-9（糖类抗原19-9）</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">项目</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">说明</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">正常值</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&lt;37 U/mL</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">相关肿瘤</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">胰腺癌（敏感性70-80%）、胆管癌、胃癌、结直肠癌</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">非癌性升高</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">急性胰腺炎、胆管炎、肝硬化、糖尿病</td></tr></tbody></table><p><strong>临床意义：</strong></p><ul><li>胰腺癌首选肿瘤标志物，但特异性有限</li><li>联合CA125对卵巢癌、联合CA242对胰腺癌提高诊断价值</li><li>Lewis血型阴性人群（约5-10%）CA19-9始终不升高</li></ul><hr/><p><br></p><h2>二、器官特异性肿瘤标志物</h2><h3>AFP（甲胎蛋白）</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">项目</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">说明</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">正常值</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&lt;20 ng/mL</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">相关肿瘤</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">原发性肝细胞癌(HCC)、卵黄囊瘤、胚胎性癌</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">非癌性升高</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">妊娠、急慢性肝炎、肝硬化（通常&lt;400ng/mL）</td></tr></tbody></table><p><strong>诊断阈值：</strong></p><ul><li>AFP&gt;400 ng/mL持续&gt;4周 → 高度怀疑HCC</li><li>AFP&gt;200 ng/mL持续&gt;8周 → 高度怀疑HCC</li><li>肝硬化患者AFP逐步升高需每3个月超声+AFP监测</li></ul><hr/><p><br></p><h3>CA125（糖类抗原125）</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">项目</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">说明</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">正常值</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&lt;35 U/mL</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">相关肿瘤</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">卵巢上皮癌（敏感性80-85%）、子宫内膜癌</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">非癌性升高</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">月经期、妊娠、子宫内膜异位症、盆腔炎、腹水、心衰</td></tr></tbody></table><p><strong>临床应用：</strong></p><ul><li>卵巢癌术后监测金标准</li><li>联合HE4+ROMA指数提高卵巢癌预测准确率</li><li>胸腹水CA125升高也可见于结核性胸腹膜炎</li></ul><hr/><p><br></p><h3>PSA（前列腺特异性抗原）</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">项目</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">说明</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">正常值</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">tPSA&lt;4 ng/mL</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">灰区</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">tPSA 4-10 ng/mL（需结合fPSA/fPSA比值）</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">高风险</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">fPSA/tPSA&lt;0.15 → 前列腺癌风险&gt;50%</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">fPSA/tPSA&gt;0.25 → 良性增生可能性大</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\"></td></tr></tbody></table><p><strong>影响因素：</strong></p><ul><li>年龄增长PSA自然升高（40-49岁&lt;2.5，50-59岁&lt;3.5，60-69岁&lt;4.5）</li><li>前列腺按摩、导尿、膀胱镜检查后2周内禁测PSA</li><li>5α-还原酶抑制剂（非那雄胺）服药6个月后PSA降低约50%</li></ul><hr/><p><br></p><h2>三、肺癌常用标志物</h2><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">标志物</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">正常值</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">敏感性最高的病理类型</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">CYFRA21-1</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&lt;3.3 ng/mL</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">肺鳞癌</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">CEA</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&lt;5 ng/mL</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">肺腺癌</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">NSE</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&lt;16.3 ng/mL</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">小细胞肺癌(SCLC)</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">ProGRP</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&lt;50 pg/mL</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">小细胞肺癌(SCLC)，特异性更高</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">SCC-Ag</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&lt;1.5 ng/mL</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">肺鳞癌</td></tr></tbody></table><hr/><p><br></p><h2>四、随访监测原则</h2><ol><li><strong>肿瘤标志物单项轻度升高</strong>（&lt;正常值上限2倍）：1个月后复查，若继续升高安排影像学</li><li><strong>术后标志物下降后再次升高</strong>：高度警惕复发转移，立即安排增强CT/PET-CT</li><li><strong>化疗期间一过性升高</strong>：可能是肿瘤溶解释放，2-3周后复查</li><li><strong>多标志物联合监测</strong>优于单项：如结直肠癌CEA+CA19-9，卵巢癌CA125+HE4</li></ol>',4,'https://picsum.photos/seed/health6/400/250',NULL,0,0,'2026-06-01 20:52:51'),(27,'消化系统常见疾病诊疗要点','<h1></h1><h2>一、胃食管反流病(GERD)</h2><h3>典型症状</h3><ul><li>烧心（胸骨后烧灼感）+ 反流（胃内容物向上反流至咽部或口腔）</li><li>多在餐后1小时内、平卧或弯腰时加重</li></ul><h3>警报症状（需胃镜检查排除肿瘤）</h3><ul><li>年龄&gt;45岁首发</li><li>吞咽困难、进行性体重下降</li><li>上消化道出血（黑便、呕血）</li><li>贫血</li></ul><h3>治疗阶梯</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">阶梯</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">方案</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">疗程</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">一</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">PPI标准剂量 qd（奥美拉唑20mg/泮托拉唑40mg）</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">4-8周</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">二</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">PPI标准剂量 bid</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">8周</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">三</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">PPI+促动力药（莫沙必利5mg tid）</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">个体化</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">维持</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">PPI最低有效剂量 按需服用</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">长期</td></tr></tbody></table><h3>生活方式调整</h3><ul><li>床头抬高15-20cm（非垫高枕头，需整个床头抬高）</li><li>睡前3小时不进食</li><li>避免：咖啡、浓茶、巧克力、高脂饮食、辛辣刺激</li><li>减重（BMI&lt;25）</li></ul><hr/><p><br></p><h2>二、消化性溃疡</h2><h3>胃溃疡与十二指肠溃疡鉴别</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">特征</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">胃溃疡(GU)</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">十二指肠溃疡(DU)</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">好发部位</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">胃小弯侧胃角</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">十二指肠球部</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">疼痛节律</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">餐后0.5-1h出现→下次餐前缓解</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">空腹痛→进食缓解→下次餐前再痛</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">癌变风险</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">约1%</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">极少</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">胃酸分泌</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">正常或偏低</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">偏高</td></tr></tbody></table><h3>病因</h3><ol><li>幽门螺杆菌(Hp)感染（DU中&gt;90%，GU中70-80%）</li><li>NSAIDs/阿司匹林</li><li>应激、吸烟、饮酒、高盐饮食</li></ol><h3>Hp根除方案（四联疗法，14天）</h3><ul><li>方案A（铋剂四联）：PPI bid + 铋剂220mg bid + 阿莫西林1g bid + 克拉霉素500mg bid</li><li>方案B（伴同疗法）：PPI bid + 阿莫西林1g bid + 克拉霉素500mg bid + 甲硝唑400mg bid/tid</li><li>根除后4周复查C13/C14呼气试验</li></ul><h3>并发症</h3><ul><li><strong>上消化道出血</strong>：最常见，表现为呕血+黑便。内镜下Forrest分级指导止血</li><li><strong>穿孔</strong>：突发剧烈刀割样上腹痛→板状腹→X线见膈下游离气体→急诊手术</li><li><strong>幽门梗阻</strong>：反复呕吐隔夜宿食，振水音阳性</li><li><strong>癌变</strong>：GU术后残胃需定期随访</li></ul><hr/><p><br></p><h2>三、功能性消化不良</h2><h3>诊断（罗马IV标准）</h3><p>病程≥6个月，近3个月满足以下≥1条：</p><ul><li>餐后饱胀不适</li><li>早饱感</li><li>上腹痛或烧灼感</li><li>无器质性病变可解释</li></ul><h3>分型</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">亚型</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">主要症状</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">治疗侧重</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">上腹痛综合征(EPS)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">上腹痛、烧灼感</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">PPI/抗酸药</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">餐后不适综合征(PDS)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">饱胀、早饱</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">促动力药</td></tr></tbody></table><h3>治疗</h3><ul><li>一线：促动力药（莫沙必利/多潘立酮）+ 抑酸药</li><li>二线：三环类抗抑郁药（如阿米替林12.5-25mg qn）</li><li>辅助：益生菌、消化酶制剂、心理行为治疗</li></ul><hr/><p><br></p><h2>四、急性胃肠炎</h2><h3>病原学</h3><ul><li>病毒性（占70-80%）：诺如病毒、轮状病毒</li><li>细菌性：沙门菌、副溶血性弧菌、金黄色葡萄球菌（毒素）、大肠杆菌</li></ul><h3>紧急评估：脱水分度</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">程度</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">体征</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">处理</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">轻度</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">口唇干燥、尿量正常</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">口服补液</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">中度</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">皮肤弹性↓、眼窝凹陷、尿少</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">ORS+密切观察</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">重度</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">意识改变、无尿、休克</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">紧急静脉补液</td></tr></tbody></table><h3>药物选择</h3><ul><li>止泻：蒙脱石散（吸附毒素，不被吸收）</li><li>益生菌：辅助缩短病程</li><li>抗生素指征：高热+T&gt;38.5℃+脓血便+粪便镜检WBC&gt;15/HP</li><li>止吐：甲氧氯普胺10mg im（成人）</li></ul><h3>饮食建议</h3><ul><li>急性期：禁食4-6小时后逐步从流质→半流质→软食过渡</li><li>推荐：白粥、烂面条、蒸蛋羹、香蕉、苹果泥(BRAT饮食)</li><li>避免：牛奶（暂时性乳糖酶缺乏）、高纤维、辛辣、油腻</li></ul><hr/><p><br></p><h2>五、肠易激综合征(IBS)</h2><h3>诊断标准（罗马IV）</h3><p>反复腹痛，近3个月平均每周至少1次，伴以下≥2项：</p><ul><li>与排便相关</li><li>排便频率改变</li><li>粪便性状改变</li></ul><h3>分型</h3><ul><li>IBS-C（便秘型）：硬便/块状便≥25%，稀便/水样便&lt;25%</li><li>IBS-D（腹泻型）：稀便/水样便≥25%，硬便/块状便&lt;25%</li><li>IBS-M（混合型）：两者均≥25%</li><li>IBS-U（不定型）</li></ul><h3>治疗策略</h3><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">分型</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">药物</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">IBS-C</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">利那洛肽、鲁比前列酮、聚乙二醇</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">IBS-D</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">利福昔明（2周）、洛哌丁胺（按需）、5-HT3拮抗剂</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">腹痛</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">匹维溴铵、曲美布汀</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">辅助</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">低FODMAP饮食、益生菌、肠道菌群移植</td></tr></tbody></table><p><br></p>',4,'https://picsum.photos/seed/health4/400/250',NULL,NULL,NULL,'2026-06-01 20:53:31'),(28,'2型糖尿病营养干预方案','<p>一、血糖控制目标</p><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">指标</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">理想</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">一般</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">差</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">空腹血糖(mmol/L)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">4.4-6.1</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">6.2-7.0</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&gt;7.0</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">餐后2h血糖(mmol/L)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">4.4-8.0</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">8.1-10.0</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&gt;10.0</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">HbA1c(%)</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&lt;6.5</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">6.5-7.5</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">&gt;7.5</td></tr></tbody></table><h2>二、每日总热量计算</h2><ul><li>休息状态：25-30 kcal/kg</li><li>轻体力活动：30-35 kcal/kg</li><li>中体力活动：35-40 kcal/kg</li><li>重体力活动：&gt;40 kcal/kg</li></ul><h2>三、三大营养素配比</h2><table style=\\\"width: auto;\\\"><tbody><tr><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">营养素</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">供能占比</th><th colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">说明</th></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">碳水化合物</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">45%-60%</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">优先选择低GI食物</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">蛋白质</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">15%-20%</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">肾功能正常者1.0-1.5g/kg/d</td></tr><tr><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">脂肪</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">20%-35%</td><td colSpan=\\\"1\\\" rowSpan=\\\"1\\\" width=\\\"auto\\\">饱和脂肪&lt;7%，反式脂肪&lt;1%</td></tr></tbody></table><h2>四、食物选择红绿灯</h2><h3>绿灯区（推荐）</h3><ul><li>全谷物：燕麦、荞麦、糙米、藜麦</li><li>蔬菜：菠菜、西兰花、黄瓜、番茄、芹菜</li><li>优质蛋白：鱼肉、去皮禽肉、豆腐、蛋清</li></ul><h3>黄灯区（适量）</h3><ul><li>水果：苹果、柚子、草莓、蓝莓（每日&lt;200g）</li><li>根茎类：红薯、土豆、山药（替代主食）</li></ul><h3>红灯区（避免）</h3><ul><li>含糖饮料、果汁、甜点、蜜饯</li><li>精制主食：白米饭、白馒头、白面包（可少量搭配杂粮）</li><li>高GI水果：西瓜、荔枝、龙眼</li></ul><h2>五、运动处方</h2><ul><li>有氧运动：每周5次，每次30分钟，心率=(220-年龄)×60%-70%</li><li>抗阻训练：每周2-3次，每次8-10个动作，每组10-15次</li><li>运动禁忌：血糖&gt;16.7mmol/L时禁止运动</li></ul><h2>六、低血糖处理</h2><p>出现心慌、出汗、手抖、饥饿感时立即测血糖：</p><ul><li>血糖&lt;3.9mmol/L：口服15g葡萄糖或150ml果汁，15分钟后复测</li><li>意识不清：立即拨打120，不可强行喂食</li></ul><p><br></p>',1,'https://picsum.photos/seed/health2/400/250',NULL,NULL,NULL,'2026-06-01 20:54:15'),(29,'糖尿病患者的医学营养治疗与日常饮食指南','<ol><li>糖尿病与医学营养治疗 (MNT) 简介</li></ol><p>糖尿病（Diabetes Mellitus）是一种以高血糖为特征的代谢性疾病。医学营养治疗（Medical Nutrition Therapy, MNT）是糖尿病综合管理的基础，旨在通过合理的饮食调整，帮助患者控制血糖、血脂和血压，预防或延缓糖尿病并发症的发生。</p><h2>2. 核心饮食原则</h2><h3>2.1 碳水化合物的管理</h3><p>碳水化合物是影响餐后血糖的最主要因素。糖尿病患者不需要完全戒除碳水，而是需要“聪明地”选择。</p><ul><li>血糖生成指数 (GI)：建议多选择低GI（&lt;55）的食物，如燕麦、糙米、杂豆、全麦面包。这些食物消化吸收慢，血糖升高平缓。</li><li>控制总摄入量：使用“食物交换份”或碳水化合物计数法，保持每餐碳水化合物的摄入量相对稳定。</li><li>避免添加糖：尽量避免含糖饮料、果汁、甜点和精加工零食。</li></ul><h3>2.2 优质蛋白质的摄入</h3><ul><li>蛋白质不会引起血糖的剧烈波动，且能增加饱腹感。</li><li>建议选择瘦肉、去皮家禽、鱼类、蛋类以及豆制品。</li><li>对于已出现糖尿病肾病（DKD）的患者，需在医生指导下限制蛋白质的摄入量，以减轻肾脏负担。</li></ul><h3>2.3 脂肪的选择</h3><ul><li>限制饱和脂肪的摄入（如肥肉、黄油、猪油），占总热量的比例应低于 7%。</li><li>严禁反式脂肪（如人造奶油、部分氢化植物油）。</li><li>增加单不饱和脂肪和多不饱和脂肪的比例（如橄榄油、牛油果、深海鱼、坚果）。</li></ul><h2>3. 推荐的饮食模式</h2><ul><li>地中海饮食：以植物性食物为主，富含橄榄油和鱼类，被证明对改善胰岛素抵抗和心血管健康非常有效。</li><li>DASH饮食（终止高血压膳食疗法）：强调全谷物、蔬菜、水果和低脂奶制品，适合伴有高血压的糖尿病患者。</li></ul><h2>4. 常见误区与问答 (Q&A)</h2><ul><li>Q：糖尿病患者可以吃水果吗？ A：可以。但应在血糖控制相对稳定时食用，选择低GI的水果（如苹果、柚子、草莓、樱桃），并放在两餐之间作为加餐，而不是饭后立即食用。每次食用量应控制在拳头大小（约200克）。</li><li>Q：无糖食品就可以随便吃吗？ A：不可以。所谓的“无糖食品”通常只是不含蔗糖，但仍然可能含有碳水化合物（如面粉、淀粉）或脂肪，过量食用依然会导致血糖升高和热量超标。</li><li>Q：主食吃得越少，对控制血糖越好吗？ A：错误。过度限制主食可能导致能量不足，引发低血糖。长期碳水摄入不足还可能导致体内脂肪过度分解，产生酮体，甚至诱发酮症酸中毒。关键在于“控制总量，优化结构”。</li></ul>',1,'https://picsum.photos/seed/health5/400/250',NULL,NULL,NULL,'2026-06-01 20:54:45');
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
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `news_save`
--

LOCK TABLES `news_save` WRITE;
/*!40000 ALTER TABLE `news_save` DISABLE KEYS */;
INSERT INTO `news_save` VALUES (1,3,1,'2026-07-01 12:00:00'),(2,3,3,'2026-07-02 12:00:00'),(3,3,5,'2026-07-03 12:00:00'),(4,2,2,'2026-07-04 12:00:00'),(5,2,4,'2026-07-05 12:00:00'),(6,4,6,'2026-07-06 12:00:00'),(7,5,8,'2026-07-07 12:00:00'),(8,6,7,'2026-07-08 12:00:00'),(9,7,9,'2026-07-09 12:00:00'),(10,8,10,'2026-07-10 12:00:00'),(11,8,1,'2026-07-11 12:00:00');
/*!40000 ALTER TABLE `news_save` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `notification`
--

DROP TABLE IF EXISTS `notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL COMMENT '接收用户',
  `title` varchar(200) DEFAULT NULL,
  `content` text,
  `type` tinyint DEFAULT '0',
  `is_read` tinyint(1) DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=14006 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notification`
--

LOCK TABLES `notification` WRITE;
/*!40000 ALTER TABLE `notification` DISABLE KEYS */;
INSERT INTO `notification` VALUES (14001,1006,'预约成功','您已成功预约张医生7月7日上午的门诊',1,1,'2026-07-02 10:00:00'),(14002,1006,'随访任务提醒','您有新的随访任务：每日血压监测',2,0,'2026-07-01 10:00:00'),(14003,1007,'预约成功','您已成功预约张医生7月7日上午的门诊',1,1,'2026-07-02 14:00:00'),(14004,1007,'订单发货','您的订单ORD20260702001已发货',3,0,'2026-07-03 10:00:00'),(14005,1008,'预约确认','您的预约已确认，请按时就诊',0,0,'2026-07-03 09:00:00');
/*!40000 ALTER TABLE `notification` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `order_item`
--

DROP TABLE IF EXISTS `order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_item` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `order_id` int NOT NULL COMMENT '订单ID',
  `product_id` int NOT NULL COMMENT '商品ID',
  `product_name` varchar(200) NOT NULL COMMENT '商品名称(快照)',
  `product_price` decimal(10,2) NOT NULL COMMENT '商品单价(快照)',
  `product_cover` varchar(500) DEFAULT NULL COMMENT '商品图片(快照)',
  `quantity` int unsigned NOT NULL DEFAULT '1' COMMENT '数量',
  `subtotal` decimal(10,2) NOT NULL COMMENT '小计',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8008 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单商品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_item`
--

LOCK TABLES `order_item` WRITE;
/*!40000 ALTER TABLE `order_item` DISABLE KEYS */;
INSERT INTO `order_item` VALUES (8001,7001,6001,'降压茶',68.00,NULL,2,136.00,'2026-07-01 10:00:00'),(8002,7002,6002,'血糖仪',299.00,NULL,1,299.00,'2026-07-02 14:00:00'),(8003,7003,6003,'血压计',199.00,NULL,1,199.00,'2026-07-03 09:00:00'),(8004,7004,6004,'维生素C',48.00,NULL,1,48.00,'2026-07-04 16:00:00'),(8005,7004,6006,'燕麦片',32.00,NULL,2,64.00,'2026-07-04 16:00:00'),(8006,7005,6003,'血压计',199.00,NULL,1,199.00,'2026-07-05 11:00:00'),(8007,7005,6005,'钙片',58.00,NULL,1,58.00,'2026-07-05 11:00:00');
/*!40000 ALTER TABLE `order_item` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `patient_profile`
--

LOCK TABLES `patient_profile` WRITE;
/*!40000 ALTER TABLE `patient_profile` DISABLE KEYS */;
INSERT INTO `patient_profile` VALUES (1,3,'男',34,'1992-03-15',175.00,72.50,23.67,'[\"高血压\"]','[\"青霉素\"]','[\"氨氯地平\"]','[]','[\"糖尿病\"]','{\"smoking\": false, \"drinking\": \"偶尔\", \"exercise\": \"每周3次\"}','[\"控制血压\", \"减重5kg\"]',5.40,7.80,4.80,1.50,1.40,2.90,138,88,72,'2026-08-28 12:19:41','2026-08-28 12:19:41'),(8,2,'男',30,'1996-03-12',175.00,72.00,23.50,'[\"轻度脂肪肝\"]','[]','[]','[]','[\"父亲高血压\"]','{\"smoking\": false, \"drinking\": \"偶尔\", \"exercise\": \"每周2次\"}','[\"减重5kg\", \"规律作息\"]',5.40,7.20,4.80,1.40,1.30,2.90,128,82,76,'2026-10-04 13:21:16','2026-04-01 09:00:00'),(9,4,'女',28,'1998-07-21',163.00,52.00,19.60,'[]','[\"花粉\"]','[]','[]','[]','{\"smoking\": false, \"drinking\": \"否\", \"exercise\": \"每周4次瑜伽\"}','[\"保持体重\", \"改善睡眠\"]',4.90,6.30,4.20,0.90,1.50,2.20,110,70,72,'2026-10-04 13:21:16','2026-04-01 09:00:00'),(10,5,'男',45,'1981-02-09',178.00,88.00,27.80,'[\"2型糖尿病\", \"高尿酸\"]','[\"青霉素\"]','[\"二甲双胍\", \"别嘌醇\"]','[\"阑尾切除\"]','[\"母亲糖尿病\"]','{\"smoking\": false, \"drinking\": \"每日啤酒\", \"exercise\": \"很少\"}','[\"控糖\", \"戒烟\"]',8.60,12.40,5.60,2.80,1.00,3.60,146,94,82,'2026-10-04 13:21:16','2026-04-01 09:00:00'),(11,6,'女',52,'1974-11-03',160.00,65.00,25.40,'[\"高血压\", \"骨质疏松\"]','[\"磺胺类\"]','[\"氨氯地平\", \"钙片\"]','[\"子宫肌瘤切除\"]','[\"姐姐乳腺癌\"]','{\"smoking\": false, \"drinking\": \"否\", \"exercise\": \"每日散步\"}','[\"骨密度改善\", \"平稳血压\"]',5.80,7.80,5.20,1.70,1.20,3.30,138,86,78,'2026-10-04 13:21:16','2026-04-01 09:00:00'),(12,7,'男',36,'1990-05-18',180.00,80.00,24.70,'[\"高血脂\"]','[]','[]','[]','[]','{\"smoking\": false, \"drinking\": \"社交饮酒\", \"exercise\": \"每周3次健身\"}','[\"增肌\", \"降脂\"]',5.20,6.90,5.40,2.20,1.10,3.40,132,85,74,'2026-10-04 13:21:16','2026-04-01 09:00:00'),(13,8,'女',40,'1986-09-27',166.00,60.00,21.80,'[\"甲状腺功能减退\"]','[\"海鲜\"]','[\"左甲状腺素钠\"]','[]','[]','{\"smoking\": false, \"drinking\": \"否\", \"exercise\": \"瑜伽+游泳\"}','[\"体重管理\", \"精力提升\"]',5.00,6.50,4.60,1.10,1.40,2.60,118,76,70,'2026-10-04 13:21:16','2026-04-01 09:00:00');
/*!40000 ALTER TABLE `patient_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `post`
--

DROP TABLE IF EXISTS `post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL COMMENT '发帖用户ID',
  `title` varchar(200) NOT NULL COMMENT '帖子标题',
  `content` longtext COMMENT '帖子内容(支持Markdown)',
  `cover` varchar(500) DEFAULT NULL COMMENT '封面图',
  `tag_id` int DEFAULT NULL COMMENT '分类ID(关联tags表)',
  `view_count` int unsigned DEFAULT '0' COMMENT '浏览数',
  `like_count` int unsigned DEFAULT '0' COMMENT '点赞数',
  `favorite_count` int unsigned DEFAULT '0' COMMENT '收藏数',
  `comment_count` int unsigned DEFAULT '0' COMMENT '评论数',
  `share_count` int unsigned DEFAULT '0' COMMENT '分享数',
  `hot_score` double DEFAULT '0' COMMENT '热度分(views*1+likes*3+favs*2+comments*4+shares*5)',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:草稿;1:已发布;2:已锁定)',
  `is_top` tinyint(1) DEFAULT '0' COMMENT '是否置顶(0:否;1:是)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_tag_id` (`tag_id`),
  KEY `idx_status` (`status`),
  KEY `idx_hot_score` (`hot_score` DESC),
  KEY `idx_create_time` (`create_time` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=2011 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='论坛帖子表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `post`
--

LOCK TABLES `post` WRITE;
/*!40000 ALTER TABLE `post` DISABLE KEYS */;
INSERT INTO `post` VALUES (2001,1006,'高血压患者的日常饮食注意事项','高血压患者应该注意低盐饮食，每天食盐摄入量不超过6克。多吃富含钾的食物，如香蕉、土豆、菠菜等。避免高脂肪、高胆固醇的食物...',NULL,1,1250,89,45,23,12,0,1,1,'2026-03-01 10:00:00','2026-08-28 13:36:25'),(2002,1007,'糖尿病患者的运动指南','糖尿病患者适当运动有助于控制血糖。建议每周进行150分钟中等强度有氧运动，如快走、游泳、骑自行车等。运动前后要注意监测血糖...',NULL,2,980,67,38,19,8,0,0,0,'2026-03-05 14:30:00','2026-08-28 13:36:25'),(2003,1008,'春季养生：如何预防感冒','春季气温变化大，容易感冒。建议：1.适当增减衣物；2.多喝水；3.保持室内通风；4.适当运动增强免疫力；5.饮食均衡...',NULL,5,756,45,28,15,6,0,0,0,'2026-03-10 09:15:00','2026-08-28 13:36:25'),(2004,1009,'心理健康：如何缓解工作压力','现代人工作压力大，容易产生焦虑情绪。建议：1.合理安排工作时间；2.适当休息；3.培养兴趣爱好；4.与朋友交流；5.必要时寻求专业帮助...',NULL,3,654,38,22,12,5,0,0,0,'2026-03-15 16:45:00','2026-08-28 13:36:25'),(2005,1010,'颈椎病的预防和保健','长期伏案工作容易导致颈椎病。建议：1.保持正确坐姿；2.每工作1小时休息10分钟；3.做颈部保健操；4.选择合适的枕头...',NULL,4,543,32,18,10,4,0,0,0,'2026-03-20 11:20:00','2026-08-28 13:36:25'),(2006,1006,'高血压患者可以吃哪些水果','高血压患者适合吃的水果：1.香蕉（富含钾）；2.苹果（富含果胶）；3.橙子（富含维生素C）；4.猕猴桃（降血压）；5.山楂（扩张血管）...',NULL,1,432,28,15,8,3,0,0,0,'2026-03-25 10:30:00','2026-08-28 13:36:25'),(2007,1007,'糖尿病患者的饮食禁忌','糖尿病患者应该避免：1.高糖食物；2.高脂肪食物；3.高盐食物；4.酒精；5.含糖饮料。建议多吃蔬菜、全谷物、瘦肉...',NULL,1,398,25,12,7,2,0,0,0,'2026-04-01 14:00:00','2026-08-28 13:36:25'),(2008,1008,'如何提高睡眠质量','提高睡眠质量的方法：1.规律作息；2.睡前避免使用电子设备；3.保持卧室安静、黑暗；4.适当运动；5.避免睡前饮酒、喝咖啡...',NULL,3,367,22,10,6,2,0,0,0,'2026-04-05 09:00:00','2026-08-28 13:36:25'),(2009,1009,'老年人如何预防骨质疏松','预防骨质疏松：1.补充钙质和维生素D；2.适当运动；3.避免吸烟、饮酒；4.定期检查骨密度；5.避免摔倒...',NULL,4,312,18,8,5,1,0,0,0,'2026-04-10 16:00:00','2026-08-28 13:36:25'),(2010,1010,'儿童感冒的护理方法','儿童感冒护理：1.多喝水；2.注意休息；3.保持室内通风；4.适当增减衣物；5.饮食清淡。如症状严重，应及时就医...',NULL,5,278,15,6,4,1,0,0,0,'2026-04-15 11:00:00','2026-08-28 13:36:25');
/*!40000 ALTER TABLE `post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `post_favorite`
--

DROP TABLE IF EXISTS `post_favorite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_favorite` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL COMMENT '收藏用户ID',
  `post_id` int NOT NULL COMMENT '帖子ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_post_fav` (`user_id`,`post_id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子收藏表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `post_favorite`
--

LOCK TABLES `post_favorite` WRITE;
/*!40000 ALTER TABLE `post_favorite` DISABLE KEYS */;
/*!40000 ALTER TABLE `post_favorite` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `post_like`
--

DROP TABLE IF EXISTS `post_like`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_like` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL COMMENT '点赞用户ID',
  `post_id` int NOT NULL COMMENT '帖子ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_post_like` (`user_id`,`post_id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子点赞表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `post_like`
--

LOCK TABLES `post_like` WRITE;
/*!40000 ALTER TABLE `post_like` DISABLE KEYS */;
/*!40000 ALTER TABLE `post_like` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `post_reply`
--

DROP TABLE IF EXISTS `post_reply`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_reply` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `post_id` int NOT NULL COMMENT '帖子ID',
  `user_id` int NOT NULL COMMENT '回复用户ID',
  `parent_id` int DEFAULT NULL COMMENT '父回复ID(支持嵌套)',
  `content` varchar(2000) NOT NULL COMMENT '回复内容',
  `like_count` int unsigned DEFAULT '0' COMMENT '点赞数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子回复表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `post_reply`
--

LOCK TABLES `post_reply` WRITE;
/*!40000 ALTER TABLE `post_reply` DISABLE KEYS */;
/*!40000 ALTER TABLE `post_reply` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `post_report`
--

DROP TABLE IF EXISTS `post_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_report` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL COMMENT '举报用户ID',
  `post_id` int NOT NULL COMMENT '帖子ID',
  `reply_id` int DEFAULT NULL COMMENT '回复ID(举报回复时)',
  `reason` varchar(500) NOT NULL COMMENT '举报原因',
  `status` tinyint(1) DEFAULT '0' COMMENT '状态(0:待处理;1:已处理;2:已驳回)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子举报表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `post_report`
--

LOCK TABLES `post_report` WRITE;
/*!40000 ALTER TABLE `post_report` DISABLE KEYS */;
/*!40000 ALTER TABLE `post_report` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `post_tag`
--

DROP TABLE IF EXISTS `post_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_tag` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '标签名称',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='论坛标签表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `post_tag`
--

LOCK TABLES `post_tag` WRITE;
/*!40000 ALTER TABLE `post_tag` DISABLE KEYS */;
INSERT INTO `post_tag` VALUES (1,'健康生活',0,'2026-08-28 13:36:25'),(2,'疾病求助',0,'2026-08-28 13:36:25'),(3,'用药经验',0,'2026-08-28 13:36:25'),(4,'心理交流',0,'2026-08-28 13:36:25'),(5,'运动分享',0,'2026-08-28 13:36:25'),(6,'养生保健',0,'2026-08-28 13:36:25');
/*!40000 ALTER TABLE `post_tag` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_category`
--

DROP TABLE IF EXISTS `product_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_category` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '分类名称',
  `parent_id` int DEFAULT NULL COMMENT '父分类ID(NULL=顶级)',
  `icon` varchar(500) DEFAULT NULL COMMENT '分类图标',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:停用;1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品分类表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_category`
--

LOCK TABLES `product_category` WRITE;
/*!40000 ALTER TABLE `product_category` DISABLE KEYS */;
INSERT INTO `product_category` VALUES (1,'药品',NULL,NULL,1,1,'2026-08-28 13:35:54'),(2,'医疗器械',NULL,NULL,2,1,'2026-08-28 13:35:54'),(3,'保健品',NULL,NULL,3,1,'2026-08-28 13:35:54'),(4,'健康食品',NULL,NULL,4,1,'2026-08-28 13:35:54'),(5,'健身器材',NULL,NULL,5,1,'2026-08-28 13:35:54');
/*!40000 ALTER TABLE `product_category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quiz_answer`
--

DROP TABLE IF EXISTS `quiz_answer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_answer` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `record_id` int NOT NULL COMMENT '考试记录ID',
  `question_id` int NOT NULL COMMENT '题目ID',
  `answer` varchar(2000) DEFAULT NULL COMMENT '学生答案',
  `score` int unsigned DEFAULT '0' COMMENT '得分',
  `is_correct` tinyint(1) DEFAULT '0' COMMENT '是否正确(0:错;1:对)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_record_id` (`record_id`),
  KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='答题记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quiz_answer`
--

LOCK TABLES `quiz_answer` WRITE;
/*!40000 ALTER TABLE `quiz_answer` DISABLE KEYS */;
/*!40000 ALTER TABLE `quiz_answer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quiz_exam`
--

DROP TABLE IF EXISTS `quiz_exam`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_exam` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL COMMENT '试卷名称',
  `description` varchar(500) DEFAULT NULL COMMENT '试卷描述',
  `duration_minutes` int unsigned DEFAULT '60' COMMENT '考试时长(分钟)',
  `total_score` int unsigned DEFAULT '100' COMMENT '总分',
  `pass_score` int unsigned DEFAULT '60' COMMENT '及格分',
  `difficulty` tinyint(1) DEFAULT '2' COMMENT '难度(1:简单;2:中等;3:困难)',
  `question_count` int unsigned DEFAULT '0' COMMENT '题目数量',
  `status` tinyint(1) DEFAULT '0' COMMENT '状态(0:草稿;1:已发布)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=10003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quiz_exam`
--

LOCK TABLES `quiz_exam` WRITE;
/*!40000 ALTER TABLE `quiz_exam` DISABLE KEYS */;
INSERT INTO `quiz_exam` VALUES (10001,'健康基础知识测验','测试您的健康基础知识水平',30,100,60,1,10,1,'2026-04-01 10:00:00','2026-08-28 13:36:25'),(10002,'慢性病管理知识测验','测试您对慢性病管理的了解',20,50,30,2,5,1,'2026-04-05 14:00:00','2026-08-28 13:36:25');
/*!40000 ALTER TABLE `quiz_exam` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quiz_exam_question`
--

DROP TABLE IF EXISTS `quiz_exam_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_exam_question` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `exam_id` int NOT NULL COMMENT '试卷ID',
  `question_id` int NOT NULL COMMENT '题目ID',
  `score` int unsigned DEFAULT '1' COMMENT '该题分值',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_question` (`exam_id`,`question_id`),
  KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷题目关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quiz_exam_question`
--

LOCK TABLES `quiz_exam_question` WRITE;
/*!40000 ALTER TABLE `quiz_exam_question` DISABLE KEYS */;
/*!40000 ALTER TABLE `quiz_exam_question` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quiz_question`
--

DROP TABLE IF EXISTS `quiz_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_question` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `category_id` int DEFAULT NULL COMMENT '分类ID',
  `question_type` tinyint(1) NOT NULL COMMENT '题型(0:单选;1:多选;2:判断;3:填空;4:简答)',
  `title` varchar(1000) NOT NULL COMMENT '题目内容',
  `options` json DEFAULT NULL COMMENT '选项(JSON数组，仅选择题)',
  `answer` varchar(2000) NOT NULL COMMENT '正确答案',
  `analysis` varchar(2000) DEFAULT NULL COMMENT '解析',
  `difficulty` tinyint(1) DEFAULT '2' COMMENT '难度(1:简单;2:中等;3:困难)',
  `score` int unsigned DEFAULT '1' COMMENT '分值',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:草稿;1:发布)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_question_type` (`question_type`),
  KEY `idx_difficulty` (`difficulty`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=9011 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题库表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quiz_question`
--

LOCK TABLES `quiz_question` WRITE;
/*!40000 ALTER TABLE `quiz_question` DISABLE KEYS */;
INSERT INTO `quiz_question` VALUES (9001,1,0,'正常血压范围是多少？','[\"90-140/60-90mmHg\", \"100-160/70-100mmHg\", \"80-120/50-80mmHg\", \"110-150/70-95mmHg\"]','A','正常血压为收缩压90-140mmHg，舒张压60-90mmHg',1,10,1,'2026-03-01 10:00:00','2026-08-28 13:36:25'),(9002,1,0,'空腹血糖正常值是多少？','[\"3.9-6.1mmol/L\", \"4.0-7.0mmol/L\", \"3.0-5.0mmol/L\", \"5.0-8.0mmol/L\"]','A','空腹血糖正常值为3.9-6.1mmol/L',1,10,1,'2026-03-01 10:00:00','2026-08-28 13:36:25'),(9003,1,2,'高血压患者应该限制盐的摄入','[\"正确\", \"错误\"]','A','高血压患者应低盐饮食，每天不超过6克',1,10,1,'2026-03-01 10:00:00','2026-08-28 13:36:25'),(9004,2,0,'以下哪种运动最适合糖尿病患者？','[\"短跑\", \"快走\", \"举重\", \"拳击\"]','B','快走是有氧运动，适合糖尿病患者',1,10,1,'2026-03-05 14:00:00','2026-08-28 13:36:25'),(9005,2,2,'糖尿病患者可以随意吃水果','[\"正确\", \"错误\"]','B','糖尿病患者应选择低糖水果，适量食用',1,10,1,'2026-03-05 14:00:00','2026-08-28 13:36:25'),(9006,3,0,'成年人每天推荐睡眠时间是多久？','[\"4-5小时\", \"6-8小时\", \"9-10小时\", \"11-12小时\"]','B','成年人每天推荐睡眠6-8小时',1,10,1,'2026-03-10 09:00:00','2026-08-28 13:36:25'),(9007,3,2,'睡前喝咖啡有助于睡眠','[\"正确\", \"错误\"]','B','咖啡因会刺激神经，不利于睡眠',1,10,1,'2026-03-10 09:00:00','2026-08-28 13:36:25'),(9008,4,0,'预防骨质疏松应补充什么？','[\"维生素A\", \"钙和维生素D\", \"维生素C\", \"铁\"]','B','钙和维生素D有助于骨骼健康',1,10,1,'2026-03-15 16:00:00','2026-08-28 13:36:25'),(9009,5,0,'感冒最常见的病因是什么？','[\"细菌\", \"病毒\", \"真菌\", \"寄生虫\"]','B','感冒大多由病毒引起',1,10,1,'2026-03-20 11:00:00','2026-08-28 13:36:25'),(9010,5,2,'抗生素可以治疗感冒','[\"正确\", \"错误\"]','B','抗生素对病毒无效，感冒不建议使用抗生素',1,10,1,'2026-03-20 11:00:00','2026-08-28 13:36:25');
/*!40000 ALTER TABLE `quiz_question` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quiz_record`
--

DROP TABLE IF EXISTS `quiz_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_record` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `exam_id` int NOT NULL COMMENT '试卷ID',
  `user_id` int NOT NULL COMMENT '用户ID',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `score` int unsigned DEFAULT '0' COMMENT '得分',
  `total_score` int unsigned DEFAULT '0' COMMENT '试卷总分',
  `correct_count` int unsigned DEFAULT '0' COMMENT '正确题数',
  `question_count` int unsigned DEFAULT '0' COMMENT '题目总数',
  `status` tinyint(1) DEFAULT '0' COMMENT '状态(0:进行中;1:已提交;2:已批改)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_exam_id` (`exam_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考试记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quiz_record`
--

LOCK TABLES `quiz_record` WRITE;
/*!40000 ALTER TABLE `quiz_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `quiz_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `shipping_address`
--

DROP TABLE IF EXISTS `shipping_address`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shipping_address` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL COMMENT '用户ID',
  `receiver_name` varchar(50) NOT NULL COMMENT '收件人姓名',
  `receiver_phone` varchar(20) NOT NULL COMMENT '收件人电话',
  `province` varchar(50) DEFAULT NULL COMMENT '省',
  `city` varchar(50) DEFAULT NULL COMMENT '市',
  `district` varchar(50) DEFAULT NULL COMMENT '区',
  `detail_address` varchar(200) NOT NULL COMMENT '详细地址',
  `is_default` tinyint(1) DEFAULT '0' COMMENT '是否默认(0:否;1:是)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货地址表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `shipping_address`
--

LOCK TABLES `shipping_address` WRITE;
/*!40000 ALTER TABLE `shipping_address` DISABLE KEYS */;
/*!40000 ALTER TABLE `shipping_address` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `shopping_cart`
--

DROP TABLE IF EXISTS `shopping_cart`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shopping_cart` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL COMMENT '用户ID',
  `product_id` int NOT NULL COMMENT '商品ID',
  `quantity` int unsigned DEFAULT '1' COMMENT '数量',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`,`product_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `shopping_cart`
--

LOCK TABLES `shopping_cart` WRITE;
/*!40000 ALTER TABLE `shopping_cart` DISABLE KEYS */;
/*!40000 ALTER TABLE `shopping_cart` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=1011 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (1,'admin','管理员','$2a$10$HEbR7/pgslM4n7FCsYUAuuLQhMmK3e.78I5A.WjNRwqozsO4rOT.u',NULL,NULL,1,0,0,0,NULL,'2026-08-19 02:08:10'),(2,'user','普通用户','$2a$10$HEbR7/pgslM4n7FCsYUAuuLQhMmK3e.78I5A.WjNRwqozsO4rOT.u',NULL,NULL,2,0,0,0,NULL,'2026-08-19 02:08:10'),(3,'demo','张小明','$2a$10$HEbR7/pgslM4n7FCsYUAuuLQhMmK3e.78I5A.WjNRwqozsO4rOT.u',NULL,'demo@example.com',2,0,0,0,NULL,'2026-08-28 12:19:41'),(4,'lily','李莉','$2b$10$rNTZMrhatpx71aT/d5hWouV8wec6NZh4Z.W.fDmKRoLEgsvvHv.Hu','https://i.pravatar.cc/150?u=lily','lily@vitalcortex.cn',2,0,0,0,NULL,'2026-04-01 09:00:00'),(5,'wang','王强','$2b$10$rNTZMrhatpx71aT/d5hWouV8wec6NZh4Z.W.fDmKRoLEgsvvHv.Hu','https://i.pravatar.cc/150?u=wang','wang@vitalcortex.cn',2,0,0,0,NULL,'2026-04-01 09:00:00'),(6,'chen','陈静','$2b$10$rNTZMrhatpx71aT/d5hWouV8wec6NZh4Z.W.fDmKRoLEgsvvHv.Hu','https://i.pravatar.cc/150?u=chen','chen@vitalcortex.cn',2,0,0,0,NULL,'2026-04-01 09:00:00'),(7,'zhao','赵磊','$2b$10$rNTZMrhatpx71aT/d5hWouV8wec6NZh4Z.W.fDmKRoLEgsvvHv.Hu','https://i.pravatar.cc/150?u=zhao','zhao@vitalcortex.cn',2,0,0,0,NULL,'2026-04-01 09:00:00'),(8,'sun','孙悦','$2b$10$rNTZMrhatpx71aT/d5hWouV8wec6NZh4Z.W.fDmKRoLEgsvvHv.Hu','https://i.pravatar.cc/150?u=sun','sun@vitalcortex.cn',2,0,0,0,NULL,'2026-04-01 09:00:00'),(1001,'doctor_zhang','张医生','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',NULL,NULL,4,0,0,0,NULL,'2026-01-15 10:00:00'),(1002,'doctor_li','李医生','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',NULL,NULL,4,0,0,0,NULL,'2026-01-20 14:30:00'),(1003,'doctor_wang','王医生','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',NULL,NULL,4,0,0,0,NULL,'2026-02-01 09:15:00'),(1004,'merchant_shop','健康商城旗舰店','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',NULL,NULL,3,0,0,0,NULL,'2026-01-10 08:00:00'),(1005,'merchant_pharma','大药房官方店','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',NULL,NULL,3,0,0,0,NULL,'2026-01-12 11:00:00'),(1006,'user_test1','测试用户1','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',NULL,NULL,2,0,0,0,NULL,'2026-02-01 10:00:00'),(1007,'user_test2','测试用户2','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',NULL,NULL,2,0,0,0,NULL,'2026-02-05 14:00:00'),(1008,'user_test3','测试用户3','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',NULL,NULL,2,0,0,0,NULL,'2026-02-10 09:30:00'),(1009,'user_test4','测试用户4','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',NULL,NULL,2,0,0,0,NULL,'2026-02-15 16:45:00'),(1010,'user_test5','测试用户5','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',NULL,NULL,2,0,0,0,NULL,'2026-02-20 11:20:00');
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_follow`
--

DROP TABLE IF EXISTS `user_follow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_follow` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `follower_id` int NOT NULL COMMENT '关注者ID',
  `followee_id` int NOT NULL COMMENT '被关注者ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follower_followee` (`follower_id`,`followee_id`),
  KEY `idx_followee_id` (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户关注表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_follow`
--

LOCK TABLES `user_follow` WRITE;
/*!40000 ALTER TABLE `user_follow` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_follow` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=11016 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_health`
--

LOCK TABLES `user_health` WRITE;
/*!40000 ALTER TABLE `user_health` DISABLE KEYS */;
INSERT INTO `user_health` VALUES (1,3,1,'138','2026-08-28 12:19:41'),(2,3,2,'88','2026-08-28 12:19:41'),(3,3,3,'5.4','2026-08-28 12:19:41'),(4,3,4,'23.67','2026-08-28 12:19:41'),(5,3,5,'72','2026-08-28 12:19:41'),(111,2,1,'124.0','2026-05-15 08:00:00'),(112,2,1,'125.2','2026-06-15 08:00:00'),(113,2,1,'126.4','2026-07-15 08:00:00'),(114,2,2,'80.8','2026-05-15 08:01:00'),(115,2,2,'82.0','2026-06-15 08:01:00'),(116,2,2,'83.2','2026-07-15 08:01:00'),(117,2,3,'7.0','2026-05-15 08:02:00'),(118,2,3,'8.2','2026-06-15 08:02:00'),(119,2,3,'9.4','2026-07-15 08:02:00'),(120,2,4,'27.9','2026-05-15 08:03:00'),(121,2,4,'29.1','2026-06-15 08:03:00'),(122,2,4,'30.3','2026-07-15 08:03:00'),(123,2,5,'83.2','2026-05-15 08:04:00'),(124,2,5,'84.4','2026-06-15 08:04:00'),(125,2,5,'85.6','2026-07-15 08:04:00'),(126,3,1,'134.0','2026-05-15 08:00:00'),(127,3,1,'135.2','2026-06-15 08:00:00'),(128,3,1,'136.4','2026-07-15 08:00:00'),(129,3,2,'86.8','2026-05-15 08:01:00'),(130,3,2,'88.0','2026-06-15 08:01:00'),(131,3,2,'89.2','2026-07-15 08:01:00'),(132,3,3,'7.7','2026-05-15 08:02:00'),(133,3,3,'8.9','2026-06-15 08:02:00'),(134,3,3,'10.1','2026-07-15 08:02:00'),(135,3,4,'28.6','2026-05-15 08:03:00'),(136,3,4,'29.8','2026-06-15 08:03:00'),(137,3,4,'31.0','2026-07-15 08:03:00'),(138,3,5,'87.2','2026-05-15 08:04:00'),(139,3,5,'88.4','2026-06-15 08:04:00'),(140,3,5,'89.6','2026-07-15 08:04:00'),(141,4,1,'106.0','2026-05-15 08:00:00'),(142,4,1,'107.2','2026-06-15 08:00:00'),(143,4,1,'108.4','2026-07-15 08:00:00'),(144,4,2,'68.8','2026-05-15 08:01:00'),(145,4,2,'70.0','2026-06-15 08:01:00'),(146,4,2,'71.2','2026-07-15 08:01:00'),(147,4,3,'6.5','2026-05-15 08:02:00'),(148,4,3,'7.7','2026-06-15 08:02:00'),(149,4,3,'8.9','2026-07-15 08:02:00'),(150,4,4,'24.0','2026-05-15 08:03:00'),(151,4,4,'25.2','2026-06-15 08:03:00'),(152,4,4,'26.4','2026-07-15 08:03:00'),(153,4,5,'79.2','2026-05-15 08:04:00'),(154,4,5,'80.4','2026-06-15 08:04:00'),(155,4,5,'81.6','2026-07-15 08:04:00'),(156,5,1,'142.0','2026-05-15 08:00:00'),(157,5,1,'143.2','2026-06-15 08:00:00'),(158,5,1,'144.4','2026-07-15 08:00:00'),(159,5,2,'92.8','2026-05-15 08:01:00'),(160,5,2,'94.0','2026-06-15 08:01:00'),(161,5,2,'95.2','2026-07-15 08:01:00'),(162,5,3,'10.2','2026-05-15 08:02:00'),(163,5,3,'11.4','2026-06-15 08:02:00'),(164,5,3,'12.6','2026-07-15 08:02:00'),(165,5,4,'32.2','2026-05-15 08:03:00'),(166,5,4,'33.4','2026-06-15 08:03:00'),(167,5,4,'34.6','2026-07-15 08:03:00'),(168,5,5,'89.2','2026-05-15 08:04:00'),(169,5,5,'90.4','2026-06-15 08:04:00'),(170,5,5,'91.6','2026-07-15 08:04:00'),(171,6,1,'134.0','2026-05-15 08:00:00'),(172,6,1,'135.2','2026-06-15 08:00:00'),(173,6,1,'136.4','2026-07-15 08:00:00'),(174,6,2,'84.8','2026-05-15 08:01:00'),(175,6,2,'86.0','2026-06-15 08:01:00'),(176,6,2,'87.2','2026-07-15 08:01:00'),(177,6,3,'7.4','2026-05-15 08:02:00'),(178,6,3,'8.6','2026-06-15 08:02:00'),(179,6,3,'9.8','2026-07-15 08:02:00'),(180,6,4,'29.8','2026-05-15 08:03:00'),(181,6,4,'31.0','2026-06-15 08:03:00'),(182,6,4,'32.2','2026-07-15 08:03:00'),(183,6,5,'85.2','2026-05-15 08:04:00'),(184,6,5,'86.4','2026-06-15 08:04:00'),(185,6,5,'87.6','2026-07-15 08:04:00'),(186,7,1,'128.0','2026-05-15 08:00:00'),(187,7,1,'129.2','2026-06-15 08:00:00'),(188,7,1,'130.4','2026-07-15 08:00:00'),(189,7,2,'83.8','2026-05-15 08:01:00'),(190,7,2,'85.0','2026-06-15 08:01:00'),(191,7,2,'86.2','2026-07-15 08:01:00'),(192,7,3,'6.8','2026-05-15 08:02:00'),(193,7,3,'8.0','2026-06-15 08:02:00'),(194,7,3,'9.2','2026-07-15 08:02:00'),(195,7,4,'29.1','2026-05-15 08:03:00'),(196,7,4,'30.3','2026-06-15 08:03:00'),(197,7,4,'31.5','2026-07-15 08:03:00'),(198,7,5,'81.2','2026-05-15 08:04:00'),(199,7,5,'82.4','2026-06-15 08:04:00'),(200,7,5,'83.6','2026-07-15 08:04:00'),(201,8,1,'114.0','2026-05-15 08:00:00'),(202,8,1,'115.2','2026-06-15 08:00:00'),(203,8,1,'116.4','2026-07-15 08:00:00'),(204,8,2,'74.8','2026-05-15 08:01:00'),(205,8,2,'76.0','2026-06-15 08:01:00'),(206,8,2,'77.2','2026-07-15 08:01:00'),(207,8,3,'6.6','2026-05-15 08:02:00'),(208,8,3,'7.8','2026-06-15 08:02:00'),(209,8,3,'9.0','2026-07-15 08:02:00'),(210,8,4,'26.2','2026-05-15 08:03:00'),(211,8,4,'27.4','2026-06-15 08:03:00'),(212,8,4,'28.6','2026-07-15 08:03:00'),(213,8,5,'77.2','2026-05-15 08:04:00'),(214,8,5,'78.4','2026-06-15 08:04:00'),(215,8,5,'79.6','2026-07-15 08:04:00'),(11001,1006,1,'135','2026-07-01 08:00:00'),(11002,1006,2,'85','2026-07-01 08:00:00'),(11003,1006,3,'5.8','2026-07-01 08:00:00'),(11004,1006,4,'23.5','2026-07-01 08:00:00'),(11005,1006,5,'72','2026-07-01 08:00:00'),(11006,1007,1,'145','2026-07-02 09:00:00'),(11007,1007,2,'92','2026-07-02 09:00:00'),(11008,1007,3,'7.2','2026-07-02 09:00:00'),(11009,1007,4,'26.8','2026-07-02 09:00:00'),(11010,1007,5,'88','2026-07-02 09:00:00'),(11011,1008,1,'120','2026-07-03 10:00:00'),(11012,1008,2,'78','2026-07-03 10:00:00'),(11013,1008,3,'5.2','2026-07-03 10:00:00'),(11014,1008,4,'22.1','2026-07-03 10:00:00'),(11015,1008,5,'68','2026-07-03 10:00:00');
/*!40000 ALTER TABLE `user_health` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='就诊记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `visit_record`
--

LOCK TABLES `visit_record` WRITE;
/*!40000 ALTER TABLE `visit_record` DISABLE KEYS */;
INSERT INTO `visit_record` VALUES (1,5001,3,3001,'头晕、血压偏高','患者自述近一周晨起头晕，伴轻微心悸，无胸痛、无视物模糊。家中自测血压最高 150/95 mmHg。','原发性高血压 1 级（需复测确认）','苯磺酸氨氯地平片 5mg 口服 每日一次\n低盐饮食（<5g/日）\n每日快走 30 分钟','诊室血压 148/92 mmHg；心率 82 次/分；BMI 23.7','2 周后复测血压并记录家庭自测值；如收缩压持续 >140 则调整用药','2026-07-07 09:30:00','2026-07-07 09:30:00'),(2,5002,4,3001,'体检发现血糖偏高','体检空腹血糖 6.2 mmol/L，患者无明显多饮多尿消瘦。母亲有 2 型糖尿病史。','空腹血糖受损（IFG）','饮食控制为主，暂不予降糖药物\n减少精制碳水与含糖饮料','空腹血糖 6.2 mmol/L；BMI 19.6；血压 110/70 mmHg','3 个月后复查空腹血糖；建议记录每日主食摄入量','2026-07-07 10:15:00','2026-07-07 10:15:00'),(3,5003,5,3002,'反复上腹隐痛','上腹隐痛反复 2 月，餐后明显，无反酸嗳气。肝胆超声提示轻度脂肪肝。','轻度脂肪肝（考虑非酒精性）','避免熬夜与高脂饮食\n适度有氧运动减轻体重','ALT 68 U/L；AST 52 U/L；腹部超声提示肝内回声增强','6 个月后复查肝功能与腹部超声','2026-07-07 14:20:00','2026-07-07 14:20:00');
/*!40000 ALTER TABLE `visit_record` ENABLE KEYS */;
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

-- Dump completed on 2026-10-04 13:21:33
