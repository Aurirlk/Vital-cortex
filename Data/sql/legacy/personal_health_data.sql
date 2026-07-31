-- ============================================================
-- 个人健康管理系统 - 数据导入脚本（日期格式已修复）
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- user 表数据（使用 REPLACE INTO 避免主键冲突）
REPLACE INTO `user` (`id`, `user_account`, `user_name`, `user_pwd`, `user_avatar`, `user_email`, `user_role`, `is_login`, `is_word`, `create_time`) VALUES
('1', 'admin', '程序员辰星', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=3064c25pic_4.jpg', '1343243@qq.com', '1', '0', '0', '2024-07-09 12:53:05'),
('3', 'yangshu', '辰星的健康系统', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=150bb3dSnipaste_2024-11-22_00-47-16.png', '1134123@qq.com', '2', '0', '0', '2024-10-07 23:59:31'),
('8', 'aiqin', '深海', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=b024d50Snipaste_2024-11-29_18-23-12.png', '123@qq.coom', '2', '0', '1', '2024-10-07 23:59:31'),
('9', 'wanghai', '大春', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=b40ef5dSnipaste_2024-11-29_18-23-18.png', '1243@qq.com', '1', '1', '0', '2024-10-07 23:59:31'),
('10', 'meihua', '梅花', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=2c6fa89猫头鹰.png', '156456@qq.com', '2', '0', '0', '2024-10-07 23:59:31'),
('11', 'hupeng', '胡鹏', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=a778bc4Snipaste_2024-11-29_18-23-12.png', '789789@qq.com', '1', '1', '0', '2024-11-18 13:00:14'),
('12', 'zhanglan', '张兰', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=42bcf20Snipaste_2024-11-29_18-23-18.png', '43443@qq.com', '2', '0', '1', '2024-11-18 13:00:14'),
('13', 'chenhao', '陈浩', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=b2b19b9熊猫.png', '1567766@qq.com', '2', '0', '0', '2024-10-25 16:16:40'),
('14', 'liran', '李冉', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=f9275d7熊猫.png', '32323@qq.com', '1', '0', '0', '2024-11-18 13:00:14'),
('15', 'liran1', 'liran1', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=907317eSnipaste_2024-11-22_00-46-48.png', '2121@qq.com', '2', '0', '0', '2024-11-18 13:00:30'),
('16', 'zhenli', '陈立', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=3a7ef99Snipaste_2024-11-22_00-46-36.png', '32312@qq.com', '1', '0', '0', '2024-11-18 13:00:50'),
('17', 'guihua', '桂花', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=b6aed4apic_9.jpg', '1212@qq.com', '2', '0', '0', '2024-11-18 13:01:18'),
('18', 'chenghua', '橙黄橘', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=127ddf0pic_4.jpg', '432432@qq.com', '2', '0', '0', '2024-11-18 13:01:34'),
('19', 'lineng', '力能', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=d516551pic_3.jpg', '1232@qq.com', '2', '0', '0', '2024-11-18 14:15:19'),
('20', '1221gthjhg', 'yonghu', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=a7350bepic_2.jpg', '124354@qq.com', '2', '0', '0', '2024-11-18 14:16:24'),
('21', 'yingzi11', '43影子1212121', '14e1b600b1fd579f47433b88e8d85291', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=d829e54pic_1.jpg', '12432@qq.com', '2', '0', '0', '2024-11-18 14:17:35'),
('22', 'admin', '管理员', 'e10adc3949ba59abbe56e057f20f883e', NULL, NULL, '1', '0', '0', NULL),
('23', 'admin', '管理员', 'e10adc3949ba59abbe56e057f20f883e', NULL, NULL, '1', '0', '0', NULL),
('24', 'admin', '管理员', 'e10adc3949ba59abbe56e057f20f883e', NULL, NULL, '1', '0', '0', NULL),
('25', 'admin', '管理员', 'e10adc3949ba59abbe56e057f20f883e', NULL, NULL, '1', '0', '0', NULL),
('26', 'admin', '管理员', 'e10adc3949ba59abbe56e057f20f883e', NULL, NULL, '1', '0', '0', NULL),
('27', 'wkj', 'wkj', '$2a$10$NuilUx6733STBC00ZRfK1u0QA131vhZ8ZW6mLjbBk87k6XoVg5VP6', NULL, NULL, '2', '0', '0', '2026-05-29 14:03:22'),
('28', 'admin', '管理员', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', NULL, NULL, '1', '0', '0', NULL),
('29', 'user', '普通用户', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', NULL, NULL, '2', '0', '0', NULL),
('30', 'admin', '管理员', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', NULL, NULL, '1', '0', '0', NULL),
('31', 'user', '普通用户', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', NULL, NULL, '2', '0', '0', NULL);

-- health_model_config 表数据（使用 REPLACE INTO 避免主键冲突）
REPLACE INTO `health_model_config` (`id`, `user_id`, `name`, `detail`, `cover`, `unit`, `symbol`, `is_global`, `value_range`) VALUES
('1', '1', '身高', '身高模型，选择该模型可记录身高', '/api/personal-heath/v1.0/file/getFile?fileName=c8379f0身高.png', 'cm', 'Height', '1', '195,459'),
('2', '1', '体重', '该模型为体重参数，记录时选中即可', '/api/personal-heath/v1.0/file/getFile?fileName=e7e18c5体重.png', 'KG', 'Weight', '1', '126,548'),
('4', '1', '谷丙转氨酶', '专属记录转氨酶', '/api/personal-heath/v1.0/file/getFile?fileName=04d7bcf转氨酶.png', 'U/L', 'ALT', '1', '108,258'),
('7', '1', '运动心率', '运动时心率', '/api/personal-heath/v1.0/file/getFile?fileName=ae7a515心率.png', '次/分', 'Bpm', '1', '75,197'),
('8', '1', '夜间血压', '夜间血压测量值', '/api/personal-heath/v1.0/file/getFile?fileName=536807e血压.png', '毫米汞柱', 'mmHg', '1', '72,145'),
('9', '1', '夜跑步数', '记录夜跑步数', '/api/personal-heath/v1.0/file/getFile?fileName=851253d步数.png', '步', '暂无', '1', '422,17990'),
('10', '1', '晨跑步数', '记录晨跑时的步数', '/api/personal-heath/v1.0/file/getFile?fileName=1cd680d步数.png', '步', '暂无', '1', '3000,10000'),
('11', '3', '辰星-晨间血糖浓度', '这是测试的', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=bad20ee体重.png', 'mol', '摩尔/升', '0', '10,50'),
('12', '3', '辰星-夜间平均心率', '测试数据', 'http://localhost:21090/api/personal-heath/v1.0/file/getFile?fileName=a8e6967心率.png', '次', '暂无', '0', '80,110'),
('13', '1', '身高', '记录身高数据', NULL, 'cm', 'Height', '1', '100,220'),
('14', '1', '体重', '记录体重数据', NULL, 'KG', 'Weight', '1', '30,200'),
('15', '1', '收缩压', '记录收缩压数据', NULL, 'mmHg', 'Systolic', '1', '90,140'),
('16', '1', '舒张压', '记录舒张压数据', NULL, 'mmHg', 'Diastolic', '1', '60,90'),
('17', '1', '心率', '记录心率数据', NULL, '次/分', 'Heart Rate', '1', '60,100'),
('18', '1', '体温', '记录体温数据', NULL, '℃', 'Temperature', '1', '36.0,37.3'),
('19', '1', '血糖', '记录血糖数据', NULL, 'mmol/L', 'Blood Glucose', '1', '3.9,6.1'),
('20', '1', '血氧', '记录血氧饱和度', NULL, '%', 'SpO2', '1', '95,100');

-- tags 表数据（使用 REPLACE INTO 避免主键冲突）
REPLACE INTO `tags` (`id`, `name`, `create_time`) VALUES
('1', '饮食健康', '2024-07-10 15:32:08'),
('2', '运动健身', '2024-07-10 15:32:08'),
('3', '心理健康', '2024-07-10 15:32:08'),
('4', '疾病预防', '2024-07-10 15:32:08'),
('5', '养生保健', '2024-07-10 15:32:08');

SET FOREIGN_KEY_CHECKS = 1;

SELECT '数据导入完成！' AS status;
