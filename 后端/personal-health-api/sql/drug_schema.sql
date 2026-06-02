-- ============================================================
-- 药品表初始化脚本
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------
-- 药品信息表 (drug)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `drug` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '药品ID',
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '药品名称',
  `generic_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '通用名',
  `category` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '药品分类',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '药品说明',
  `price` decimal(10,2) NULL DEFAULT NULL COMMENT '价格',
  `unit` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '单位',
  `specification` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '规格',
  `manufacturer` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '生产厂家',
  `cover` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '药品图片',
  `is_otc` tinyint(1) NULL DEFAULT 1 COMMENT '是否OTC(0:处方药;1:OTC)',
  `stock` int(11) NULL DEFAULT 0 COMMENT '库存',
  `status` tinyint(1) NULL DEFAULT 1 COMMENT '状态(0:下架;1:上架)',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_name`(`name`) USING BTREE,
  INDEX `idx_category`(`category`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 药品订阅表 (drug_subscription)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `drug_subscription` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订阅ID',
  `user_id` int(11) NOT NULL COMMENT '用户ID',
  `drug_id` int(11) NOT NULL COMMENT '药品ID',
  `quantity` int(11) NULL DEFAULT 1 COMMENT '订阅数量',
  `status` tinyint(1) NULL DEFAULT 1 COMMENT '状态(0:取消;1:有效)',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '订阅时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_drug`(`user_id`, `drug_id`) USING BTREE,
  INDEX `idx_drug_id`(`drug_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- 插入示例药品数据
INSERT INTO `drug` (`name`, `generic_name`, `category`, `description`, `price`, `unit`, `specification`, `manufacturer`, `cover`, `is_otc`, `stock`, `status`) VALUES
('阿莫西林胶囊', '阿莫西林', '抗生素', '适用于敏感菌所致的感染', 12.50, '盒', '0.25g*24粒', '哈药集团制药总厂', NULL, 1, 100, 1),
('布洛芬缓释胶囊', '布洛芬', '解热镇痛', '用于缓解轻至中度疼痛及感冒引起的发热', 25.00, '盒', '0.3g*20粒', '中美天津史克制药有限公司', NULL, 1, 200, 1),
('蒙脱石散', '蒙脱石', '止泻药', '用于成人及儿童急、慢性腹泻', 28.50, '盒', '3g*10袋', '博福-益普生(天津)制药有限公司', NULL, 1, 150, 1),
('复方甘草片', '复方甘草', '止咳化痰', '用于镇咳祛痰', 8.00, '瓶', '100片', '北京同仁堂制药有限公司', NULL, 1, 300, 1),
('维生素C片', '维生素C', '维生素', '用于预防坏血病', 6.50, '瓶', '0.1g*100片', '东北制药集团沈阳第一制药有限公司', NULL, 1, 500, 1),
('六味地黄丸', '六味地黄', '中成药', '用于肾阴亏损，头晕耳鸣', 35.00, '瓶', '200丸', '北京同仁堂科技发展股份有限公司', NULL, 1, 180, 1),
('感冒灵颗粒', '感冒灵', '感冒用药', '用于风热感冒，发热，头痛', 15.00, '盒', '10g*9袋', '华润三九医药股份有限公司', NULL, 1, 250, 1),
('健胃消食片', '健胃消食', '消化系统', '用于脾胃虚弱所致的食积', 22.00, '盒', '0.8g*32片', '江中药业股份有限公司', NULL, 1, 120, 1),
('双黄连口服液', '双黄连', '中成药', '用于外感风热所致的感冒', 18.00, '盒', '10ml*6支', '哈药集团三精制药有限公司', NULL, 1, 160, 1),
('藿香正气水', '藿香正气', '中成药', '用于外感风寒、内伤湿滞', 12.00, '盒', '10ml*10支', '太极集团重庆涪陵制药厂', NULL, 1, 200, 1),
('开塞露', '甘油', '泻药', '用于小儿、老年便秘', 3.50, '支', '20ml*2支', '上海运佳黄浦制药有限公司', NULL, 1, 400, 1),
('红霉素软膏', '红霉素', '外用抗生素', '用于脓疱疮等化脓性皮肤病', 5.00, '支', '1g:10mg*15g', '马应龙药业集团股份有限公司', NULL, 1, 350, 1),
('创可贴', '创可贴', '外科用药', '用于小创伤、擦伤等', 15.00, '盒', '100片', '云南白药集团股份有限公司', NULL, 1, 600, 1),
('风油精', '风油精', '中成药', '用于伤风感冒引起的头痛', 8.50, '瓶', '3ml', '漳州水仙药业股份有限公司', NULL, 1, 280, 1),
('板蓝根颗粒', '板蓝根', '中成药', '用于肺胃热盛所致的咽喉肿痛', 16.00, '盒', '10g*20袋', '广州白云山和记黄埔中药有限公司', NULL, 1, 220, 1);

SET FOREIGN_KEY_CHECKS = 1;

SELECT '药品表初始化完成！' AS status;
