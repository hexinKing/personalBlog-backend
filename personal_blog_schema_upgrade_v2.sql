/*
  personal_blog schema upgrade v2
  MySQL version: 8.0+

  使用说明：
  1. 执行前请先备份当前 personal_blog 数据库。
  2. 本脚本按“现有表增量优化 + 新增扩展表”的方式编写，不包含 DROP TABLE，不会主动清空数据。
  3. ALTER TABLE 部分按一次性迁移设计；如果重复执行，已存在字段或索引会报错。
  4. 当前后端代码尚未使用这些新增字段和新表，执行后仍需配套实体、DTO、Mapper、Service、权限逻辑等代码改造。
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =========================================================
-- 1. 用户表增强：角色权限、账号状态、安全登录、token 失效能力
-- =========================================================

ALTER TABLE `user`
  ADD COLUMN `nickname` varchar(50) NULL DEFAULT NULL COMMENT '昵称' AFTER `username`,
  ADD COLUMN `role` varchar(20) NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN-管理员, AUTHOR-作者, USER-普通用户' AFTER `avatar`,
  ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '账号状态: 0-禁用, 1-正常, 2-锁定' AFTER `role`,
  ADD COLUMN `bio` varchar(500) NULL DEFAULT NULL COMMENT '个人简介' AFTER `status`,
  ADD COLUMN `last_login_time` datetime NULL DEFAULT NULL COMMENT '最后登录时间' AFTER `bio`,
  ADD COLUMN `last_login_ip` varchar(64) NULL DEFAULT NULL COMMENT '最后登录IP' AFTER `last_login_time`,
  ADD COLUMN `login_fail_count` int NOT NULL DEFAULT 0 COMMENT '连续登录失败次数' AFTER `last_login_ip`,
  ADD COLUMN `locked_until` datetime NULL DEFAULT NULL COMMENT '锁定截止时间' AFTER `login_fail_count`,
  ADD COLUMN `password_version` int NOT NULL DEFAULT 1 COMMENT '密码版本，用于密码修改后使旧token失效' AFTER `locked_until`,
  ADD COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否, 1-是' AFTER `password_version`,
  ADD UNIQUE INDEX `uk_user_email` (`email`),
  ADD INDEX `idx_user_role_status` (`role`, `status`),
  ADD INDEX `idx_user_deleted` (`deleted`);

-- 如需指定现有管理员，可执行类似语句：
-- UPDATE `user` SET `role` = 'ADMIN' WHERE `username` = '你的管理员用户名';

-- =========================================================
-- 2. 文章表增强：发布工作流、SEO、封面、热度、统计冗余、全文搜索
-- =========================================================

ALTER TABLE `article`
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0-草稿, 1-已发布, 2-已下线, 3-已归档, 4-定时发布',
  ADD COLUMN `author_id` bigint NULL DEFAULT NULL COMMENT '作者用户ID' AFTER `category_id`,
  ADD COLUMN `cover_url` varchar(500) NULL DEFAULT NULL COMMENT '文章封面图URL' AFTER `summary`,
  ADD COLUMN `slug` varchar(150) NULL DEFAULT NULL COMMENT '文章访问别名，用于SEO友好URL' AFTER `title`,
  ADD COLUMN `seo_title` varchar(255) NULL DEFAULT NULL COMMENT 'SEO标题' AFTER `slug`,
  ADD COLUMN `seo_description` varchar(500) NULL DEFAULT NULL COMMENT 'SEO描述' AFTER `seo_title`,
  ADD COLUMN `reading_time` int NOT NULL DEFAULT 0 COMMENT '预计阅读时长，单位分钟' AFTER `seo_description`,
  ADD COLUMN `is_top` tinyint NOT NULL DEFAULT 0 COMMENT '是否置顶: 0-否, 1-是' AFTER `status`,
  ADD COLUMN `is_recommended` tinyint NOT NULL DEFAULT 0 COMMENT '是否推荐: 0-否, 1-是' AFTER `is_top`,
  ADD COLUMN `allow_comment` tinyint NOT NULL DEFAULT 1 COMMENT '是否允许评论: 0-否, 1-是' AFTER `is_recommended`,
  ADD COLUMN `comment_count` int NOT NULL DEFAULT 0 COMMENT '评论数冗余' AFTER `view_count`,
  ADD COLUMN `like_count` int NOT NULL DEFAULT 0 COMMENT '点赞数冗余' AFTER `comment_count`,
  ADD COLUMN `favorite_count` int NOT NULL DEFAULT 0 COMMENT '收藏数冗余' AFTER `like_count`,
  ADD COLUMN `hot_score` decimal(12,4) NOT NULL DEFAULT 0.0000 COMMENT '综合热度分' AFTER `favorite_count`,
  ADD COLUMN `publish_time` datetime NULL DEFAULT NULL COMMENT '发布时间' AFTER `hot_score`,
  ADD COLUMN `schedule_time` datetime NULL DEFAULT NULL COMMENT '定时发布时间' AFTER `publish_time`,
  ADD COLUMN `offline_time` datetime NULL DEFAULT NULL COMMENT '下线时间' AFTER `schedule_time`,
  ADD COLUMN `archive_time` datetime NULL DEFAULT NULL COMMENT '归档时间' AFTER `offline_time`,
  ADD COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否, 1-是' AFTER `archive_time`,
  ADD UNIQUE INDEX `uk_article_slug` (`slug`),
  ADD INDEX `idx_article_author_status` (`author_id`, `status`),
  ADD INDEX `idx_article_status_publish` (`status`, `publish_time`),
  ADD INDEX `idx_article_recommended` (`is_recommended`, `status`, `publish_time`),
  ADD INDEX `idx_article_hot` (`status`, `hot_score`, `publish_time`),
  ADD INDEX `idx_article_deleted` (`deleted`),
  ADD FULLTEXT INDEX `ft_article_search` (`title`, `summary`, `content`);

-- =========================================================
-- 3. 分类表增强：slug、排序、启停、文章数量
-- =========================================================

ALTER TABLE `category`
  ADD COLUMN `slug` varchar(100) NULL DEFAULT NULL COMMENT '分类别名，用于前台路由和SEO' AFTER `name`,
  ADD COLUMN `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前' AFTER `description`,
  ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0-停用, 1-启用' AFTER `sort_order`,
  ADD COLUMN `article_count` int NOT NULL DEFAULT 0 COMMENT '分类下文章数量冗余' AFTER `status`,
  ADD COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否, 1-是' AFTER `article_count`,
  ADD UNIQUE INDEX `uk_category_slug` (`slug`),
  ADD INDEX `idx_category_status_sort` (`status`, `sort_order`),
  ADD INDEX `idx_category_deleted` (`deleted`);

-- =========================================================
-- 4. 标签表增强：slug、描述、颜色、排序、启停、文章数量
-- =========================================================

ALTER TABLE `tag`
  ADD COLUMN `slug` varchar(100) NULL DEFAULT NULL COMMENT '标签别名，用于前台路由和SEO' AFTER `name`,
  ADD COLUMN `description` varchar(255) NULL DEFAULT NULL COMMENT '标签描述' AFTER `slug`,
  ADD COLUMN `color` varchar(20) NULL DEFAULT NULL COMMENT '标签颜色，如 #3B82F6' AFTER `description`,
  ADD COLUMN `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前' AFTER `color`,
  ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0-停用, 1-启用' AFTER `sort_order`,
  ADD COLUMN `article_count` int NOT NULL DEFAULT 0 COMMENT '标签下文章数量冗余' AFTER `status`,
  ADD COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否, 1-是' AFTER `article_count`,
  ADD UNIQUE INDEX `uk_tag_slug` (`slug`),
  ADD INDEX `idx_tag_status_sort` (`status`, `sort_order`),
  ADD INDEX `idx_tag_deleted` (`deleted`);

-- =========================================================
-- 5. 文章标签关联表增强：创建时间与反向查询索引
-- =========================================================

ALTER TABLE `article_tag`
  ADD COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER `tag_id`,
  ADD INDEX `idx_article_tag_tag_id` (`tag_id`);

-- =========================================================
-- 6. 评论表增强：树形评论、审核信息、风控字段、逻辑删除
-- =========================================================

ALTER TABLE `comment`
  ADD COLUMN `root_id` bigint NULL DEFAULT NULL COMMENT '根评论ID，便于树形评论查询' AFTER `parent_id`,
  ADD COLUMN `user_id` bigint NULL DEFAULT NULL COMMENT '评论用户ID，游客评论为空' AFTER `root_id`,
  ADD COLUMN `reply_to_user_id` bigint NULL DEFAULT NULL COMMENT '被回复用户ID' AFTER `user_id`,
  ADD COLUMN `ip_hash` varchar(128) NULL DEFAULT NULL COMMENT '评论IP哈希，用于风控，避免直接存储明文IP' AFTER `content`,
  ADD COLUMN `user_agent` varchar(500) NULL DEFAULT NULL COMMENT '提交评论的User-Agent' AFTER `ip_hash`,
  ADD COLUMN `like_count` int NOT NULL DEFAULT 0 COMMENT '评论点赞数' AFTER `status`,
  ADD COLUMN `audit_user_id` bigint NULL DEFAULT NULL COMMENT '审核人用户ID' AFTER `like_count`,
  ADD COLUMN `audit_time` datetime NULL DEFAULT NULL COMMENT '审核时间' AFTER `audit_user_id`,
  ADD COLUMN `reject_reason` varchar(255) NULL DEFAULT NULL COMMENT '拒绝原因' AFTER `audit_time`,
  ADD COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`,
  ADD COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否, 1-是' AFTER `update_time`,
  ADD INDEX `idx_comment_article_status_time` (`article_id`, `status`, `create_time`),
  ADD INDEX `idx_comment_root_time` (`root_id`, `create_time`),
  ADD INDEX `idx_comment_user_id` (`user_id`),
  ADD INDEX `idx_comment_audit` (`status`, `audit_time`),
  ADD INDEX `idx_comment_deleted` (`deleted`);

-- =========================================================
-- 7. 新增：文章版本历史表
-- =========================================================

CREATE TABLE IF NOT EXISTS `article_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `article_id` bigint NOT NULL COMMENT '文章ID',
  `version_no` int NOT NULL COMMENT '版本号，从1递增',
  `title` varchar(255) NOT NULL COMMENT '文章标题快照',
  `slug` varchar(150) NULL DEFAULT NULL COMMENT '文章slug快照',
  `content` longtext NOT NULL COMMENT '文章内容快照',
  `summary` varchar(500) NULL DEFAULT NULL COMMENT '文章摘要快照',
  `category_id` bigint NULL DEFAULT NULL COMMENT '分类ID快照',
  `cover_url` varchar(500) NULL DEFAULT NULL COMMENT '封面图快照',
  `seo_title` varchar(255) NULL DEFAULT NULL COMMENT 'SEO标题快照',
  `seo_description` varchar(500) NULL DEFAULT NULL COMMENT 'SEO描述快照',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '文章状态快照',
  `editor_id` bigint NULL DEFAULT NULL COMMENT '编辑人用户ID',
  `change_note` varchar(500) NULL DEFAULT NULL COMMENT '变更说明',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_version` (`article_id`, `version_no`),
  KEY `idx_article_version_article_time` (`article_id`, `create_time`),
  KEY `idx_article_version_editor` (`editor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章版本历史表';

-- =========================================================
-- 8. 新增：密码重置令牌表
-- =========================================================

CREATE TABLE IF NOT EXISTS `password_reset_token` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户ID',
  `email` varchar(100) NOT NULL COMMENT '邮箱',
  `token_hash` varchar(128) NOT NULL COMMENT '重置令牌或验证码哈希',
  `expires_at` datetime NOT NULL COMMENT '过期时间',
  `used_time` datetime NULL DEFAULT NULL COMMENT '使用时间',
  `fail_count` int NOT NULL DEFAULT 0 COMMENT '校验失败次数',
  `request_ip` varchar(64) NULL DEFAULT NULL COMMENT '请求IP',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_password_reset_token_hash` (`token_hash`),
  KEY `idx_password_reset_email` (`email`, `expires_at`),
  KEY `idx_password_reset_user` (`user_id`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='密码重置令牌表';

-- =========================================================
-- 9. 新增：认证令牌表，支持刷新token、退出登录、token黑名单
-- =========================================================

CREATE TABLE IF NOT EXISTS `auth_token` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `token_hash` varchar(128) NOT NULL COMMENT 'token哈希，不存储明文token',
  `token_type` tinyint NOT NULL COMMENT '令牌类型: 1-refresh token, 2-access token blacklist',
  `issued_at` datetime NOT NULL COMMENT '签发时间',
  `expires_at` datetime NOT NULL COMMENT '过期时间',
  `revoked_at` datetime NULL DEFAULT NULL COMMENT '吊销时间',
  `request_ip` varchar(64) NULL DEFAULT NULL COMMENT '请求IP',
  `user_agent` varchar(500) NULL DEFAULT NULL COMMENT 'User-Agent',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_token_hash` (`token_hash`),
  KEY `idx_auth_token_user_type` (`user_id`, `token_type`, `expires_at`),
  KEY `idx_auth_token_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='认证令牌表';

-- =========================================================
-- 10. 新增：媒体资源表
-- =========================================================

CREATE TABLE IF NOT EXISTS `media_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `uploader_id` bigint NULL DEFAULT NULL COMMENT '上传人用户ID',
  `file_name` varchar(255) NOT NULL COMMENT '存储文件名',
  `original_name` varchar(255) NULL DEFAULT NULL COMMENT '原始文件名',
  `url` varchar(500) NOT NULL COMMENT '访问URL',
  `object_key` varchar(500) NULL DEFAULT NULL COMMENT '对象存储key或本地路径',
  `storage_type` varchar(30) NOT NULL DEFAULT 'LOCAL' COMMENT '存储类型: LOCAL, OSS, S3, MINIO',
  `mime_type` varchar(100) NULL DEFAULT NULL COMMENT 'MIME类型',
  `file_size` bigint NOT NULL DEFAULT 0 COMMENT '文件大小，单位字节',
  `width` int NULL DEFAULT NULL COMMENT '图片宽度',
  `height` int NULL DEFAULT NULL COMMENT '图片高度',
  `usage_status` tinyint NOT NULL DEFAULT 0 COMMENT '使用状态: 0-未使用, 1-使用中',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-否, 1-是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_media_uploader` (`uploader_id`, `create_time`),
  KEY `idx_media_usage` (`usage_status`, `deleted`),
  KEY `idx_media_mime` (`mime_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='媒体资源表';

-- =========================================================
-- 11. 新增：用户文章互动表，支持点赞、收藏等轻互动
-- =========================================================

CREATE TABLE IF NOT EXISTS `user_article_interaction` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `article_id` bigint NOT NULL COMMENT '文章ID',
  `interaction_type` tinyint NOT NULL COMMENT '互动类型: 1-点赞, 2-收藏',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_article_interaction` (`user_id`, `article_id`, `interaction_type`),
  KEY `idx_article_interaction` (`article_id`, `interaction_type`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户文章互动表';

-- =========================================================
-- 12. 新增：通知表，支持评论回复、审核结果、系统通知
-- =========================================================

CREATE TABLE IF NOT EXISTS `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `receiver_user_id` bigint NOT NULL COMMENT '接收人用户ID',
  `sender_user_id` bigint NULL DEFAULT NULL COMMENT '发送人用户ID，系统通知为空',
  `type` varchar(50) NOT NULL COMMENT '通知类型: COMMENT_REPLY, COMMENT_AUDIT, SYSTEM',
  `title` varchar(255) NOT NULL COMMENT '通知标题',
  `content` varchar(1000) NULL DEFAULT NULL COMMENT '通知内容',
  `related_type` varchar(50) NULL DEFAULT NULL COMMENT '关联对象类型: ARTICLE, COMMENT 等',
  `related_id` bigint NULL DEFAULT NULL COMMENT '关联对象ID',
  `is_read` tinyint NOT NULL DEFAULT 0 COMMENT '是否已读: 0-否, 1-是',
  `read_time` datetime NULL DEFAULT NULL COMMENT '阅读时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_notification_receiver_read` (`receiver_user_id`, `is_read`, `create_time`),
  KEY `idx_notification_related` (`related_type`, `related_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知表';

-- =========================================================
-- 13. 新增：操作审计日志表
-- =========================================================

CREATE TABLE IF NOT EXISTS `operation_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `operator_id` bigint NULL DEFAULT NULL COMMENT '操作人用户ID',
  `operator_username` varchar(50) NULL DEFAULT NULL COMMENT '操作人用户名',
  `action` varchar(100) NOT NULL COMMENT '操作类型，如 ARTICLE_CREATE, COMMENT_AUDIT',
  `target_type` varchar(50) NULL DEFAULT NULL COMMENT '目标对象类型',
  `target_id` bigint NULL DEFAULT NULL COMMENT '目标对象ID',
  `request_method` varchar(20) NULL DEFAULT NULL COMMENT '请求方法',
  `request_path` varchar(255) NULL DEFAULT NULL COMMENT '请求路径',
  `request_ip` varchar(64) NULL DEFAULT NULL COMMENT '请求IP',
  `user_agent` varchar(500) NULL DEFAULT NULL COMMENT 'User-Agent',
  `detail` json NULL COMMENT '操作详情JSON',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '操作状态: 0-失败, 1-成功',
  `error_message` varchar(1000) NULL DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_operator_time` (`operator_id`, `create_time`),
  KEY `idx_audit_action_time` (`action`, `create_time`),
  KEY `idx_audit_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作审计日志表';

-- =========================================================
-- 14. 新增：文章每日统计表，支撑数据看板与趋势分析
-- =========================================================

CREATE TABLE IF NOT EXISTS `article_daily_stat` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `article_id` bigint NOT NULL COMMENT '文章ID',
  `stat_date` date NOT NULL COMMENT '统计日期',
  `view_count` int NOT NULL DEFAULT 0 COMMENT '当日阅读数',
  `comment_count` int NOT NULL DEFAULT 0 COMMENT '当日评论数',
  `like_count` int NOT NULL DEFAULT 0 COMMENT '当日点赞数',
  `favorite_count` int NOT NULL DEFAULT 0 COMMENT '当日收藏数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_daily_stat` (`article_id`, `stat_date`),
  KEY `idx_daily_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章每日统计表';

-- =========================================================
-- 15. 新增：敏感词表，支撑评论风控
-- =========================================================

CREATE TABLE IF NOT EXISTS `sensitive_word` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `word` varchar(100) NOT NULL COMMENT '敏感词',
  `level` tinyint NOT NULL DEFAULT 1 COMMENT '级别: 1-审核, 2-拒绝',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0-停用, 1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sensitive_word` (`word`),
  KEY `idx_sensitive_word_status` (`status`, `level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感词表';

-- =========================================================
-- 16. 新增：AI 生成记录表，支撑摘要、标签、SEO建议等能力
-- =========================================================

CREATE TABLE IF NOT EXISTS `ai_generation_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `article_id` bigint NULL DEFAULT NULL COMMENT '关联文章ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '操作用户ID',
  `task_type` varchar(50) NOT NULL COMMENT '任务类型: SUMMARY, TAGS, SEO, SHARE_COPY, POLISH',
  `provider` varchar(50) NULL DEFAULT NULL COMMENT 'AI服务提供方',
  `model_name` varchar(100) NULL DEFAULT NULL COMMENT '模型名称',
  `prompt` text NULL COMMENT '提示词',
  `result` mediumtext NULL COMMENT '生成结果',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0-失败, 1-成功',
  `error_message` varchar(1000) NULL DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_article_time` (`article_id`, `create_time`),
  KEY `idx_ai_user_time` (`user_id`, `create_time`),
  KEY `idx_ai_task_type` (`task_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI生成记录表';

SET FOREIGN_KEY_CHECKS = 1;

