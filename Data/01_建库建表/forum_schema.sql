-- ============================================================
-- 智康云 - 论坛模块数据库表结构
-- 版本: v1.0
-- 整合到现有资讯模块，与news表关联
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 第一部分：论坛帖子表
-- ============================================================

-- 1. 论坛帖子表（用户发帖，整合到资讯模块）
CREATE TABLE IF NOT EXISTS `post` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '发帖用户ID',
  `title` VARCHAR(200) NOT NULL COMMENT '帖子标题',
  `content` LONGTEXT COMMENT '帖子内容(支持Markdown)',
  `cover` VARCHAR(500) DEFAULT NULL COMMENT '封面图',
  `tag_id` INT DEFAULT NULL COMMENT '分类ID(关联tags表)',
  `view_count` INT UNSIGNED DEFAULT 0 COMMENT '浏览数',
  `like_count` INT UNSIGNED DEFAULT 0 COMMENT '点赞数',
  `favorite_count` INT UNSIGNED DEFAULT 0 COMMENT '收藏数',
  `comment_count` INT UNSIGNED DEFAULT 0 COMMENT '评论数',
  `share_count` INT UNSIGNED DEFAULT 0 COMMENT '分享数',
  `hot_score` DOUBLE DEFAULT 0 COMMENT '热度分(views*1+likes*3+favs*2+comments*4+shares*5)',
  `status` TINYINT(1) DEFAULT 1 COMMENT '状态(0:草稿;1:已发布;2:已锁定)',
  `is_top` TINYINT(1) DEFAULT 0 COMMENT '是否置顶(0:否;1:是)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_tag_id` (`tag_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_hot_score` (`hot_score` DESC),
  INDEX `idx_create_time` (`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛帖子表';

-- 2. 帖子回复表
CREATE TABLE IF NOT EXISTS `post_reply` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `post_id` INT NOT NULL COMMENT '帖子ID',
  `user_id` INT NOT NULL COMMENT '回复用户ID',
  `parent_id` INT DEFAULT NULL COMMENT '父回复ID(支持嵌套)',
  `content` VARCHAR(2000) NOT NULL COMMENT '回复内容',
  `like_count` INT UNSIGNED DEFAULT 0 COMMENT '点赞数',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_post_id` (`post_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子回复表';

-- 3. 帖子点赞表（防重复：联合唯一索引）
CREATE TABLE IF NOT EXISTS `post_like` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '点赞用户ID',
  `post_id` INT NOT NULL COMMENT '帖子ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_post_like` (`user_id`, `post_id`),
  INDEX `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子点赞表';

-- 4. 帖子收藏表（防重复：联合唯一索引）
CREATE TABLE IF NOT EXISTS `post_favorite` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '收藏用户ID',
  `post_id` INT NOT NULL COMMENT '帖子ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_post_fav` (`user_id`, `post_id`),
  INDEX `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子收藏表';

-- 5. 用户关注表（防重复：联合唯一索引）
CREATE TABLE IF NOT EXISTS `user_follow` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `follower_id` INT NOT NULL COMMENT '关注者ID',
  `followee_id` INT NOT NULL COMMENT '被关注者ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follower_followee` (`follower_id`, `followee_id`),
  INDEX `idx_followee_id` (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注表';

-- 6. 帖子举报表
CREATE TABLE IF NOT EXISTS `post_report` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '举报用户ID',
  `post_id` INT NOT NULL COMMENT '帖子ID',
  `reply_id` INT DEFAULT NULL COMMENT '回复ID(举报回复时)',
  `reason` VARCHAR(500) NOT NULL COMMENT '举报原因',
  `status` TINYINT(1) DEFAULT 0 COMMENT '状态(0:待处理;1:已处理;2:已驳回)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_post_id` (`post_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子举报表';

-- 7. 论坛标签表
CREATE TABLE IF NOT EXISTS `post_tag` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛标签表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 默认标签数据
-- ============================================================
INSERT IGNORE INTO `post_tag` (`id`, `name`) VALUES
(1, '健康生活'), (2, '疾病求助'), (3, '用药经验'), (4, '心理交流'), (5, '运动分享'), (6, '养生保健');

SELECT '论坛模块表结构创建完成！共7张表。' AS status;
