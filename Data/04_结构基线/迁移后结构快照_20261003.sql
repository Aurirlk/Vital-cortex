/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
SET @MYSQLDUMP_TEMP_LOG_BIN = @@SESSION.SQL_LOG_BIN;
SET @@SESSION.SQL_LOG_BIN= 0;
SET @@GLOBAL.GTID_PURGED=/*!80000 '+'*/ '68c8e41b-479b-11f1-98c8-005056c00001:1-49047';
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
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `target_type` varchar(20) NOT NULL COMMENT '目标类型：NEWS内容 / QUIZ题目',
  `target_id` bigint unsigned NOT NULL COMMENT '目标ID（多态）',
  `user_id` int unsigned NOT NULL COMMENT '评论人',
  `reply_to_id` int unsigned DEFAULT NULL COMMENT '被回复人',
  `parent_id` bigint unsigned DEFAULT NULL COMMENT '父评论ID（树形）',
  `content` text NOT NULL COMMENT '评论内容',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '点赞数',
  `upvote_list` json DEFAULT NULL COMMENT '点赞用户ID列表',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0已删除 1正常',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_comment_target` (`target_type`,`target_id`,`status`,`create_time`),
  KEY `idx_comment_user` (`user_id`),
  KEY `idx_comment_parent` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一评论表（多态）';
/*!40101 SET character_set_client = @saved_cs_client */;
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
  `parent_id` int unsigned NOT NULL DEFAULT '0' COMMENT '父科室ID，0=顶级科室',
  `code` varchar(30) DEFAULT NULL COMMENT '科室编码（唯一）如 CARDIO',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '层级深度，1=一级科室',
  `leader_id` int unsigned DEFAULT NULL COMMENT '科主任（hospital_doctor.id）',
  `phone` varchar(30) DEFAULT NULL COMMENT '科室电话',
  `location` varchar(120) DEFAULT NULL COMMENT '科室位置（楼栋/楼层）',
  `intro` text COMMENT '科室简介',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_dept_parent` (`parent_id`,`status`),
  KEY `idx_dept_status` (`status`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='科室表';
/*!40101 SET character_set_client = @saved_cs_client */;
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
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
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
  `legacy_user_id` int unsigned DEFAULT NULL COMMENT '迁移前关联的 user.id（仅历史追溯）',
  `username` varchar(50) DEFAULT NULL COMMENT '医生登录账号（唯一）',
  `password` varchar(100) DEFAULT NULL COMMENT '密码（BCrypt，口径与 user 表一致）',
  `salt` varchar(32) DEFAULT NULL COMMENT '密码盐值（保留字段，当前 BCrypt 不依赖盐）',
  `title_level` varchar(20) NOT NULL DEFAULT 'ATTENDING' COMMENT '职称：TITLE主任/ASSOC副主任/ATTENDING主治/RESIDENT住院医',
  `specialties` json DEFAULT NULL COMMENT '擅长领域（JSON字符串数组）',
  `visit_count` int NOT NULL DEFAULT '0' COMMENT '累计接诊数',
  `rating` decimal(3,2) NOT NULL DEFAULT '5.00' COMMENT '评分 0~5',
  `gender` varchar(10) DEFAULT NULL COMMENT '性别',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(30) DEFAULT NULL COMMENT '联系电话',
  `reg_no` varchar(50) DEFAULT NULL COMMENT '执业证号',
  `dept_ids` json DEFAULT NULL COMMENT '所属科室ID数组（支持多科室）',
  `need_init_password` tinyint NOT NULL DEFAULT '1' COMMENT '是否需初始化密码：1首次登录须设置密码',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doctor_username` (`username`),
  KEY `idx_department_id` (`department_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_doctor_dept` (`department_id`,`status`),
  KEY `idx_doctor_title` (`title_level`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医生表';
/*!40101 SET character_set_client = @saved_cs_client */;
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
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `model_announcement` (
  `id` int NOT NULL AUTO_INCREMENT,
  `model_key` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '妯″瀷鏍囪瘑锛堝? zhikangyun-local, deepseek 绛夛級',
  `model_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '妯″瀷灞曠ず鍚嶇О',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '妯?箙鏍囬?锛堝? "鏈?崏澶фā鍨嬪凡涓婄嚎"锛',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '妯?箙鎻忚堪鏂囧瓧',
  `bg_color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '#409EFF' COMMENT '妯?箙鑳屾櫙鑹',
  `icon` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'MagicStick' COMMENT '鍥炬爣鍚',
  `is_online` tinyint DEFAULT '0' COMMENT '鏄?惁涓婄嚎 0=涓嬬嚎 1=涓婄嚎',
  `is_active` tinyint DEFAULT '0' COMMENT '鏄?惁褰撳墠灞曠ず 0=鍚?1=鏄',
  `sort_order` int DEFAULT '0' COMMENT '鎺掑簭',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_key` (`model_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='妯″瀷鍏?憡/妯?箙閫氱煡琛';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `news` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `content_type` varchar(20) NOT NULL DEFAULT 'NEWS' COMMENT '内容类型：NEWS资讯 / POST帖子 / ARTICLE文章',
  `user_id` int unsigned DEFAULT NULL COMMENT '作者用户ID（资讯可为空）',
  `title` varchar(200) DEFAULT NULL COMMENT '标题（统一字段，迁移期与 name 并存）',
  `name` varchar(200) DEFAULT NULL COMMENT '标题',
  `content` longtext COMMENT '内容',
  `summary` varchar(500) DEFAULT NULL COMMENT '摘要/导读',
  `view_count` int NOT NULL DEFAULT '0' COMMENT '浏览量',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '点赞数',
  `favorite_count` int NOT NULL DEFAULT '0' COMMENT '收藏数',
  `comment_count` int NOT NULL DEFAULT '0' COMMENT '评论数',
  `share_count` int NOT NULL DEFAULT '0' COMMENT '分享数',
  `hot_score` decimal(10,4) NOT NULL DEFAULT '0.0000' COMMENT '热度分（定时任务维护）',
  `tag_id` int DEFAULT NULL COMMENT '分类ID',
  `cover` varchar(255) DEFAULT NULL COMMENT '封面图',
  `reader_ids` text COMMENT '阅读者ID列表',
  `is_top` tinyint(1) DEFAULT NULL COMMENT '是否置顶',
  `is_banner` tinyint(1) DEFAULT NULL COMMENT '是否轮播图',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0草稿 1已发布 2已下架',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `create_time` datetime DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tag_name_snapshot` varchar(50) DEFAULT NULL COMMENT '迁移期标签名快照（便于回溯）',
  PRIMARY KEY (`id`),
  KEY `idx_news_list` (`content_type`,`status`,`is_top`,`published_at`),
  KEY `idx_news_user` (`user_id`),
  KEY `idx_news_tag` (`tag_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
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
  `source` varchar(30) NOT NULL DEFAULT 'SYSTEM' COMMENT '来源：SYSTEM系统/USER用户/AI助手/ORDER订单',
  `sender_id` int unsigned DEFAULT NULL COMMENT '发送者ID（系统消息为空）',
  `link_url` varchar(255) DEFAULT NULL COMMENT '点击跳转地址',
  `biz_type` varchar(30) DEFAULT NULL COMMENT '业务类型：APPOINTMENT/FOLLOWUP/MALL/SYSTEM',
  `biz_id` bigint unsigned DEFAULT NULL COMMENT '业务ID',
  PRIMARY KEY (`id`),
  KEY `idx_notif_user` (`user_id`,`is_read`,`create_time`),
  KEY `idx_notif_biz` (`biz_type`,`biz_id`)
) ENGINE=InnoDB AUTO_INCREMENT=14037 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知表';
/*!40101 SET character_set_client = @saved_cs_client */;
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
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tags` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(50) DEFAULT NULL COMMENT '分类名称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序权重（小在前）',
  `type` varchar(20) NOT NULL DEFAULT 'GENERAL' COMMENT 'GENERAL通用 / POST论坛 / NEWS资讯',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0禁用 1启用',
  PRIMARY KEY (`id`),
  KEY `idx_tags_list` (`type`,`status`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
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
) ENGINE=InnoDB AUTO_INCREMENT=11079 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
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
SET @@SESSION.SQL_LOG_BIN = @MYSQLDUMP_TEMP_LOG_BIN;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

