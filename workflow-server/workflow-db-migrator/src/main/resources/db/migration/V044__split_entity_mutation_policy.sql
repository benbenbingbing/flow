CREATE TABLE `entity_mutation_policy_config` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更策略配置ID',
  `entity_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体定义ID',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码',
  `enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用实体变更策略',
  `draft_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '规则、步骤和变更目标草稿JSON',
  `active_release_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前运行发布快照ID',
  `revision` int NOT NULL DEFAULT '1' COMMENT '草稿修订号',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED',
  `migration_state` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NATIVE' COMMENT 'NATIVE/REVIEW_REQUIRED/MIGRATED',
  `create_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_mutation_policy_code` (`entity_code`,`deleted`),
  KEY `idx_entity_mutation_policy_release` (`active_release_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体变更策略草稿';

CREATE TABLE `entity_mutation_policy_release` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发布快照ID',
  `config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更策略配置ID',
  `version` int NOT NULL COMMENT '发布版本号',
  `config_document` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '不可变变更策略JSON',
  `published_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `published_by_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `publish_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_mutation_policy_release` (`config_id`,`version`),
  KEY `idx_entity_mutation_policy_release_time` (`config_id`,`publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体变更策略不可变发布快照';

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`)
SELECT 'entity_mutation_policy_management_001','0','实体变更策略','C','Operation',75,'/system/entity-mutation-policies','system/EntityMutationPolicyManagement','entity:mutation:config:list','0','0','0','0',NULL,'0','1','实体写入步骤与跨实体变更目标配置',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 'entity_mutation_policy_management_001');

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`)
SELECT 'entity_mutation_policy_list_001','entity_mutation_policy_management_001','查看实体变更策略','F',NULL,1,'','','entity:mutation:config:list','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 'entity_mutation_policy_list_001');

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`)
SELECT 'entity_mutation_policy_update_001','entity_mutation_policy_management_001','维护实体变更策略','F',NULL,2,'','','entity:mutation:config:update','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 'entity_mutation_policy_update_001');

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`)
SELECT 'entity_mutation_policy_publish_001','entity_mutation_policy_management_001','发布实体变更策略','F',NULL,3,'','','entity:mutation:config:publish','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 'entity_mutation_policy_publish_001');

-- 旧数据版本配置中包含的写入步骤和变更目标迁移到独立页面后，
-- 已有角色沿用原有查看/维护/发布授权，避免升级后突然失去管理入口。
INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`)
SELECT SHA2(CONCAT(`role_id`, ':entity_mutation_policy_management_001'), 256),
       `role_id`, 'entity_mutation_policy_management_001', CURRENT_TIMESTAMP
FROM `sys_role_menu`
WHERE `menu_id` = 'entity_version_config_list_001';

INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`)
SELECT SHA2(CONCAT(`role_id`, ':entity_mutation_policy_list_001'), 256),
       `role_id`, 'entity_mutation_policy_list_001', CURRENT_TIMESTAMP
FROM `sys_role_menu`
WHERE `menu_id` = 'entity_version_config_list_001';

INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`)
SELECT SHA2(CONCAT(`role_id`, ':entity_mutation_policy_update_001'), 256),
       `role_id`, 'entity_mutation_policy_update_001', CURRENT_TIMESTAMP
FROM `sys_role_menu`
WHERE `menu_id` = 'entity_version_config_update_001';

INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`)
SELECT SHA2(CONCAT(`role_id`, ':entity_mutation_policy_publish_001'), 256),
       `role_id`, 'entity_mutation_policy_publish_001', CURRENT_TIMESTAMP
FROM `sys_role_menu`
WHERE `menu_id` = 'entity_version_config_publish_001';
