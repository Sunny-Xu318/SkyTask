-- SkyTask Database Schema (MySQL 8, InnoDB, utf8mb4)

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `role_permission`;
DROP TABLE IF EXISTS `user_role`;
DROP TABLE IF EXISTS `user_token`;
DROP TABLE IF EXISTS `audit_log`;
DROP TABLE IF EXISTS `task_retry`;
DROP TABLE IF EXISTS `task_log`;
DROP TABLE IF EXISTS `task_instance`;
DROP TABLE IF EXISTS `executor_node`;
DROP TABLE IF EXISTS `task_dependency`;
DROP TABLE IF EXISTS `task_param`;
DROP TABLE IF EXISTS `task`;
DROP TABLE IF EXISTS `permission`;
DROP TABLE IF EXISTS `role`;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `tenant`;

-- =========================
--  Tenant & Security Tables
-- =========================

CREATE TABLE `tenant` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `username` VARCHAR(128) NOT NULL,
  `password_hash` VARCHAR(256) NOT NULL,
  `display_name` VARCHAR(128) DEFAULT NULL,
  `email` VARCHAR(256) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tenant_username` (`tenant_id`,`username`),
  CONSTRAINT `fk_user_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `role` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `description` VARCHAR(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_tenant_name` (`tenant_id`,`name`),
  CONSTRAINT `fk_role_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `permission` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED DEFAULT NULL,
  `name` VARCHAR(128) NOT NULL,
  `resource` VARCHAR(256) DEFAULT NULL,
  `action` VARCHAR(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_tenant_name` (`tenant_id`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_role` (
  `user_id` BIGINT UNSIGNED NOT NULL,
  `role_id` BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `role_permission` (
  `role_id` BIGINT UNSIGNED NOT NULL,
  `permission_id` BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (`role_id`,`permission_id`),
  CONSTRAINT `fk_role_permission_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_role_permission_permission` FOREIGN KEY (`permission_id`) REFERENCES `permission` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_token` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `refresh_token` VARCHAR(256) NOT NULL,
  `expires_at` TIMESTAMP NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_token` (`refresh_token`),
  CONSTRAINT `fk_user_token_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `audit_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `user_id` BIGINT UNSIGNED DEFAULT NULL,
  `action` VARCHAR(256) NOT NULL,
  `resource` VARCHAR(512) DEFAULT NULL,
  `ip` VARCHAR(64) DEFAULT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_tenant` (`tenant_id`),
  CONSTRAINT `fk_audit_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_audit_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ====================
--  Scheduler Core Data
-- ====================

CREATE TABLE `task` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `biz_group` VARCHAR(128) DEFAULT NULL,
  `description` VARCHAR(1024) DEFAULT NULL,
  `type` VARCHAR(32) NOT NULL COMMENT 'JAVA|SHELL|HTTP|SQL|CUSTOM',
  `handler` VARCHAR(512) DEFAULT NULL COMMENT '执行器处理器标识，例如类名或脚本路径或URL',
  `cron_expr` VARCHAR(128) DEFAULT NULL,
  `timezone` VARCHAR(64) DEFAULT 'UTC',
  `shard_count` INT UNSIGNED NOT NULL DEFAULT 1,
  `retry_max` INT UNSIGNED NOT NULL DEFAULT 3,
  `retry_backoff_ms` INT UNSIGNED NOT NULL DEFAULT 60000,
  `concurrency_policy` VARCHAR(32) DEFAULT 'PARALLEL',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_by` VARCHAR(64) DEFAULT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_tenant_name` (`tenant_id`,`name`),
  CONSTRAINT `fk_task_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `task_param` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `task_id` BIGINT UNSIGNED NOT NULL,
  `param_key` VARCHAR(128) NOT NULL,
  `param_value` TEXT,
  PRIMARY KEY (`id`),
  KEY `idx_task_param_task` (`task_id`),
  KEY `idx_task_param_tenant` (`tenant_id`),
  CONSTRAINT `fk_taskparam_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_taskparam_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `task_dependency` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `task_id` BIGINT UNSIGNED NOT NULL,
  `depends_on_task_id` BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_task_dependency_task` (`task_id`),
  KEY `idx_task_dependency_depends` (`depends_on_task_id`),
  KEY `idx_task_dependency_tenant` (`tenant_id`),
  CONSTRAINT `fk_task_dependency_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_task_dependency_depends` FOREIGN KEY (`depends_on_task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_task_dependency_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `executor_node` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `node_id` VARCHAR(128) NOT NULL,
  `host` VARCHAR(128) DEFAULT NULL,
  `port` INT UNSIGNED DEFAULT NULL,
  `metadata` JSON DEFAULT NULL,
  `capacity` INT UNSIGNED DEFAULT 100,
  `status` VARCHAR(32) DEFAULT 'ONLINE',
  `last_heartbeat` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_node_tenant` (`tenant_id`,`node_id`),
  KEY `idx_node_status` (`status`),
  KEY `idx_node_tenant` (`tenant_id`),
  CONSTRAINT `fk_executor_node_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `task_instance` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `task_id` BIGINT UNSIGNED NOT NULL,
  `instance_id` VARCHAR(64) NOT NULL COMMENT '外部可见的业务ID，例如UUID',
  `scheduled_time` DATETIME NOT NULL,
  `triggered_at` DATETIME DEFAULT NULL,
  `triggered_by` VARCHAR(32) DEFAULT 'SCHEDULER',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `attempts` INT UNSIGNED NOT NULL DEFAULT 0,
  `executor_node_id` BIGINT UNSIGNED DEFAULT NULL,
  `shard_index` INT DEFAULT NULL,
  `timeout_ms` INT DEFAULT NULL,
  `result` TEXT DEFAULT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_instance` (`instance_id`),
  KEY `idx_task_instance_task_status` (`task_id`,`status`),
  KEY `idx_task_instance_tenant` (`tenant_id`),
  KEY `idx_task_instance_sched` (`scheduled_time`),
  CONSTRAINT `fk_instance_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_instance_node` FOREIGN KEY (`executor_node_id`) REFERENCES `executor_node` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_instance_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `task_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `instance_id` VARCHAR(64) NOT NULL,
  `log_path` VARCHAR(1024) DEFAULT NULL COMMENT '日志存储位置或索引',
  `short_msg` VARCHAR(512) DEFAULT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_log_instance` (`instance_id`),
  KEY `idx_task_log_tenant` (`tenant_id`),
  CONSTRAINT `fk_task_log_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `task_retry` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `instance_id` VARCHAR(64) NOT NULL,
  `retry_no` INT UNSIGNED NOT NULL,
  `scheduled_retry_time` DATETIME NOT NULL,
  `status` VARCHAR(32) DEFAULT 'SCHEDULED',
  PRIMARY KEY (`id`),
  KEY `idx_task_retry_instance` (`instance_id`),
  KEY `idx_task_retry_tenant` (`tenant_id`),
  CONSTRAINT `fk_task_retry_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===================
--  Sample Seeded Data
-- ===================

INSERT INTO `tenant` (`id`, `code`, `name`, `status`) VALUES
  (1, 'default', 'Default Tenant', 'ACTIVE'),
  (2, 'tenant-alpha', 'Alpha Research Ltd.', 'ACTIVE'),
  (3, 'tenant-beta', 'Beta Logistics Co.', 'ACTIVE');

-- 密码: admin123 的bcrypt hash
INSERT INTO `user` (`id`, `tenant_id`, `username`, `password_hash`, `display_name`, `email`, `status`) VALUES
  (1, 1, 'admin', '{bcrypt}$2a$10$Dow1uP04R8CYU8AmHqAmJ.hn0pV3hokW2N.O1srO2Qd6hw2yY7jW2', '系统管理员', 'admin@skytask.com', 'ACTIVE'),
  (2, 2, 'ops-admin', '{bcrypt}$2a$10$Dow1uP04R8CYU8AmHqAmJ.hn0pV3hokW2N.O1srO2Qd6hw2yY7jW2', 'Alpha Ops Admin', 'ops-admin@alpha.example.com', 'ACTIVE'),
  (3, 2, 'dev-user', '{bcrypt}$2a$10$EixZaYVK1fsbw1ZfbX3OXetG8V6mW6IhXH8VEWilR92pT9bkHYO6G', 'Alpha Developer', 'dev-user@alpha.example.com', 'ACTIVE'),
  (4, 3, 'beta-admin', '{bcrypt}$2a$10$Dow1uP04R8CYU8AmHqAmJ.hn0pV3hokW2N.O1srO2Qd6hw2yY7jW2', 'Beta Administrator', 'admin@beta.example.com', 'ACTIVE');

INSERT INTO `role` (`id`, `tenant_id`, `name`, `description`) VALUES
  (1, 1, 'ADMIN', 'Default tenant administrator'),
  (2, 2, 'SYSTEM_ADMIN', 'Alpha tenant super administrator'),
  (3, 2, 'DEVELOPER', 'Alpha tenant developer with limited write permissions'),
  (4, 3, 'TENANT_ADMIN', 'Beta tenant administrator'),
  (5, 3, 'TENANT_VIEWER', 'Beta tenant read-only user');

INSERT INTO `permission` (`id`, `tenant_id`, `name`, `resource`, `action`) VALUES
  (1, NULL, 'task:read', 'task', 'READ'),
  (2, NULL, 'task:write', 'task', 'WRITE'),
  (3, NULL, 'task:trigger', 'task', 'TRIGGER'),
  (4, NULL, 'task:delete', 'task', 'DELETE'),
  (5, NULL, 'task:export', 'task', 'EXPORT'),
  (6, NULL, 'node:read', 'node', 'READ'),
  (7, NULL, 'node:manage', 'node', 'MANAGE'),
  (8, NULL, 'config:read', 'config', 'READ'),
  (9, NULL, 'config:write', 'config', 'WRITE'),
  (10, NULL, 'tenant:manage', 'tenant', 'MANAGE');

INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
  -- ADMIN (role_id=1): 所有权限
  (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8), (1, 9), (1, 10),
  -- SYSTEM_ADMIN (role_id=2): 所有权限
  (2, 1), (2, 2), (2, 3), (2, 4), (2, 5), (2, 6), (2, 7), (2, 8), (2, 9), (2, 10),
  -- DEVELOPER (role_id=3): 读、写、触发任务，查看节点
  (3, 1), (3, 2), (3, 3), (3, 6),
  -- TENANT_ADMIN (role_id=4): 租户管理员权限
  (4, 1), (4, 2), (4, 3), (4, 4), (4, 5), (4, 6), (4, 7), (4, 8), (4, 9),
  -- TENANT_VIEWER (role_id=5): 只读权限
  (5, 1), (5, 6), (5, 8);

INSERT INTO `user_role` (`user_id`, `role_id`) VALUES
  (1, 1),
  (2, 2),
  (3, 3),
  (4, 4);

INSERT INTO `task` (`id`, `tenant_id`, `name`, `biz_group`, `description`, `type`, `handler`, `cron_expr`, `timezone`, `shard_count`, `retry_max`, `retry_backoff_ms`, `concurrency_policy`, `enabled`, `created_by`) VALUES
  (1, 1, 'alpha-nightly-report', 'ALPHA_REPORT', 'Generate nightly analytics report', 'CRON', 'com.alpha.task.ReportTask', '0 0 1 * * ?', 'Asia/Shanghai', 1, 3, 60000, 'PARALLEL', 1, 'ops-admin'),
  (2, 1, 'alpha-cache-refresh', 'ALPHA_CACHE', 'Refresh application cache shards', 'FIXED_RATE', 'https://alpha.example.com/cache/refresh', NULL, 'Asia/Shanghai', 4, 3, 60000, 'PARALLEL', 1, 'dev-user'),
  (3, 2, 'beta-order-sync', 'BETA_ORDER', 'Synchronize orders from ERP', 'CRON', 'com.beta.task.OrderSyncTask', '0 */30 * * * ?', 'UTC', 2, 5, 90000, 'SERIAL', 1, 'beta-admin');

INSERT INTO `task_param` (`tenant_id`, `task_id`, `param_key`, `param_value`) VALUES
  (1, 1, 'owner', 'ops-team'),
  (1, 1, 'retryPolicy', 'EXP_BACKOFF'),
  (1, 2, 'owner', 'cache-team'),
  (1, 2, 'retryPolicy', 'FIXED_INTERVAL'),
  (2, 3, 'owner', 'beta-ops'),
  (2, 3, 'retryPolicy', 'EXP_BACKOFF');

INSERT INTO `task_dependency` (`tenant_id`, `task_id`, `depends_on_task_id`) VALUES
  (1, 1, 2);

INSERT INTO `executor_node` (`tenant_id`, `node_id`, `host`, `port`, `metadata`, `capacity`, `status`) VALUES
  (1, 'alpha-worker-1', '10.10.0.11', 8089, JSON_OBJECT('zone','shanghai-1'), 200, 'ONLINE'),
  (2, 'beta-worker-1', '172.16.1.21', 8089, JSON_OBJECT('zone','beijing-2'), 150, 'ONLINE');

-- audit_log, task_instance, etc. remain empty by default

SET FOREIGN_KEY_CHECKS = 1;
