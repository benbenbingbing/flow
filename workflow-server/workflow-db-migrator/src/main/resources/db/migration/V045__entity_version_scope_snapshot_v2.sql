ALTER TABLE `entity_version_config`
  ADD COLUMN `contract_version` int NOT NULL DEFAULT '1' COMMENT '配置契约版本：1/2' AFTER `enabled`,
  ADD COLUMN `draft_document` longtext COLLATE utf8mb4_unicode_ci COMMENT 'V2触发器、范围和比较策略草稿JSON' AFTER `contract_version`,
  ADD COLUMN `migration_state` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'REVIEW_REQUIRED' COMMENT 'NATIVE/REVIEW_REQUIRED/MIGRATED' AFTER `draft_document`;

ALTER TABLE `entity_version_config_release`
  ADD COLUMN `contract_version` int NOT NULL DEFAULT '1' COMMENT '配置契约版本：1/2' AFTER `version`,
  ADD COLUMN `scope_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布时冻结范围摘要' AFTER `config_document`;

ALTER TABLE `entity_record_version`
  ADD COLUMN `schema_version` int NOT NULL DEFAULT '1' COMMENT '快照契约版本：1/2' AFTER `entity_release_version`,
  ADD COLUMN `config_release_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '命中的版本策略发布ID' AFTER `schema_version`,
  ADD COLUMN `config_release_version` int DEFAULT NULL COMMENT '命中的版本策略发布版本' AFTER `config_release_id`,
  ADD COLUMN `data_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原始业务数据摘要' AFTER `config_release_version`,
  ADD COLUMN `presentation_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '冻结中文展示语义摘要' AFTER `data_hash`,
  ADD COLUMN `scope_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '固化范围摘要' AFTER `presentation_hash`,
  ADD COLUMN `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '幂等请求摘要' AFTER `scope_hash`,
  ADD COLUMN `dataset_count` int NOT NULL DEFAULT '0' COMMENT '关系数据集数量' AFTER `request_hash`,
  ADD COLUMN `snapshot_row_count` int NOT NULL DEFAULT '1' COMMENT '根记录与关系记录总数' AFTER `dataset_count`,
  ADD COLUMN `snapshot_size_bytes` bigint NOT NULL DEFAULT '0' COMMENT 'V2快照序列化字节数' AFTER `snapshot_row_count`,
  ADD COLUMN `completeness` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMPLETE' COMMENT 'COMPLETE；V2禁止截断' AFTER `snapshot_size_bytes`,
  ADD KEY `idx_entity_record_version_release` (`config_release_id`),
  ADD KEY `idx_entity_record_version_schema` (`schema_version`,`create_time`),
  ADD CONSTRAINT `fk_entity_record_version_config_release`
    FOREIGN KEY (`config_release_id`) REFERENCES `entity_version_config_release` (`id`);

UPDATE `entity_version_config`
SET `contract_version` = 1,
    `migration_state` = 'REVIEW_REQUIRED'
WHERE `draft_document` IS NULL;

UPDATE `entity_record_version`
SET `schema_version` = 1,
    `data_hash` = `snapshot_hash`,
    `completeness` = 'COMPLETE'
WHERE `schema_version` = 1;

CREATE TABLE `entity_record_version_dataset` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据集ID',
  `version_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务版本ID',
  `node_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定范围节点编码',
  `node_kind` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RELATION' COMMENT 'RELATION',
  `relation_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '冻结关系编码',
  `relation_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '冻结关系中文名称',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '子实体编码',
  `entity_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '冻结子实体中文名称',
  `entity_release_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entity_release_version` int DEFAULT NULL,
  `selector_document` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '冻结关系、过滤和排序选择器JSON',
  `presentation_document` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '冻结中文表单展示定义JSON',
  `data_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `presentation_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `row_count` int NOT NULL DEFAULT '0',
  `complete` tinyint NOT NULL DEFAULT '1' COMMENT 'V2必须完整，禁止静默截断',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_record_version_dataset_node` (`version_id`,`node_code`),
  KEY `idx_entity_record_version_dataset_relation` (`relation_code`,`entity_code`),
  CONSTRAINT `fk_entity_record_version_dataset_version`
    FOREIGN KEY (`version_id`) REFERENCES `entity_record_version` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体版本V2一层关系数据集';

CREATE TABLE `entity_record_version_dataset_row` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据集行ID',
  `dataset_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `record_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '子记录稳定ID',
  `record_title` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '冻结业务中文标题',
  `row_order` int NOT NULL DEFAULT '0' COMMENT '冻结顺序',
  `row_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `values_document` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'fieldCode到FrozenValue的JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_record_version_dataset_row` (`dataset_id`,`record_id`),
  KEY `idx_entity_record_version_dataset_order` (`dataset_id`,`row_order`,`record_id`),
  CONSTRAINT `fk_entity_record_version_dataset_row_dataset`
    FOREIGN KEY (`dataset_id`) REFERENCES `entity_record_version_dataset` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体版本V2关系数据集行';

CREATE TABLE `entity_record_version_counter` (
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `record_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_version_no` int NOT NULL DEFAULT '0',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`entity_code`,`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体记录版本号事务计数器';

INSERT INTO `entity_record_version_counter`
    (`entity_code`,`record_id`,`last_version_no`,`update_time`)
SELECT `entity_code`,`record_id`,MAX(`version_no`),CURRENT_TIMESTAMP
FROM `entity_record_version`
GROUP BY `entity_code`,`record_id`;

INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`icon`,`sort`,`path`,`component`,`perm`,`status`,`visible`,`is_frame`,`is_cache`,`query`,`keep_alive`,`breadcrumb`,`remark`,`deleted`,`create_by`,`create_time`,`update_by`,`update_time`,`entity_code`,`resource_type`,`list_key`)
SELECT 'entity_version_record_view_001','entity_version_management_001','查看记录版本','F',NULL,4,'','','entity:version:record:view','0','0','0','0',NULL,'0','1','查看有数据权限的记录历史版本',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 'entity_version_record_view_001');

INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`icon`,`sort`,`path`,`component`,`perm`,`status`,`visible`,`is_frame`,`is_cache`,`query`,`keep_alive`,`breadcrumb`,`remark`,`deleted`,`create_by`,`create_time`,`update_by`,`update_time`,`entity_code`,`resource_type`,`list_key`)
SELECT 'entity_version_record_capture_001','entity_version_management_001','手工固化记录版本','F',NULL,5,'','','entity:version:record:capture','0','0','0','0',NULL,'0','1','手工生成记录检查点',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 'entity_version_record_capture_001');

INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`)
SELECT SHA2(CONCAT(`role_id`, ':entity_version_record_view_001'), 256),
       `role_id`, 'entity_version_record_view_001', CURRENT_TIMESTAMP
FROM `sys_role_menu`
WHERE `menu_id` = 'entity_version_config_list_001';

INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`)
SELECT SHA2(CONCAT(`role_id`, ':entity_version_record_capture_001'), 256),
       `role_id`, 'entity_version_record_capture_001', CURRENT_TIMESTAMP
FROM `sys_role_menu`
WHERE `menu_id` = 'entity_version_config_update_001';
