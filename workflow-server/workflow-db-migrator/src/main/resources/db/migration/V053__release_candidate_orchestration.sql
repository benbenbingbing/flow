CREATE TABLE `release_candidate` (
  `id` varchar(64) NOT NULL COMMENT '发布候选ID',
  `candidate_no` varchar(64) NOT NULL COMMENT '发布候选编号',
  `candidate_name` varchar(200) NOT NULL COMMENT '发布候选名称',
  `description` varchar(1000) DEFAULT NULL COMMENT '说明',
  `source_import_id` varchar(64) NOT NULL COMMENT '绑定的配置迁移导入批次ID',
  `source_package_checksum` varchar(128) DEFAULT NULL COMMENT '冻结的导入包校验和',
  `migration_tag` varchar(100) NOT NULL COMMENT '冻结的迁移标记',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/BLOCKED/READY/PUBLISHING/PUBLISHED/FAILED/COMPENSATING/COMPENSATED/MANUAL_REQUIRED',
  `preflight_status` varchar(16) NOT NULL DEFAULT 'NOT_RUN' COMMENT 'NOT_RUN/PASS/FAIL',
  `revision` int NOT NULL DEFAULT 1 COMMENT '候选并发修订号',
  `candidate_hash` char(64) NOT NULL COMMENT '冻结内容SHA-256',
  `idempotency_key` varchar(128) DEFAULT NULL COMMENT '最近一次发布幂等键',
  `failure_step_id` varchar(64) DEFAULT NULL COMMENT '失败步骤ID',
  `created_by` varchar(100) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` varchar(100) DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `validated_by` varchar(100) DEFAULT NULL,
  `validated_at` datetime DEFAULT NULL,
  `published_by` varchar(100) DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_release_candidate_no` (`candidate_no`),
  KEY `idx_release_candidate_status` (`status`, `update_time`),
  KEY `idx_release_candidate_import` (`source_import_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用级发布候选';

CREATE TABLE `release_candidate_item` (
  `id` varchar(64) NOT NULL,
  `candidate_id` varchar(64) NOT NULL,
  `source_import_item_id` varchar(64) NOT NULL,
  `source_asset_id` varchar(64) DEFAULT NULL COMMENT '关联迁移资产索引ID',
  `asset_type` varchar(64) NOT NULL,
  `business_key` varchar(200) NOT NULL,
  `asset_name` varchar(300) DEFAULT NULL,
  `frozen_source_version` int DEFAULT NULL,
  `frozen_source_hash` varchar(128) DEFAULT NULL,
  `frozen_snapshot_hash` char(64) NOT NULL,
  `frozen_target_version` int DEFAULT NULL,
  `frozen_target_hash` varchar(128) DEFAULT NULL,
  `dependencies_json` longtext DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT 100,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_release_candidate_source_item` (`candidate_id`, `source_import_item_id`),
  KEY `idx_release_candidate_item_order` (`candidate_id`, `sort_order`, `business_key`),
  KEY `idx_release_candidate_item_asset` (`source_asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布候选资产条目';

CREATE TABLE `release_candidate_dependency` (
  `id` varchar(64) NOT NULL,
  `candidate_id` varchar(64) NOT NULL,
  `dependent_item_id` varchar(64) NOT NULL COMMENT '依赖方候选条目',
  `required_item_id` varchar(64) DEFAULT NULL COMMENT '候选内被依赖条目',
  `dependency_type` varchar(64) NOT NULL,
  `dependency_key` varchar(200) NOT NULL,
  `required` tinyint NOT NULL DEFAULT 1,
  `resolved` tinyint NOT NULL DEFAULT 0,
  `source_description` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_release_candidate_dependency` (`candidate_id`, `dependent_item_id`),
  KEY `idx_release_candidate_required` (`candidate_id`, `required_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布候选依赖边';

CREATE TABLE `release_candidate_validation` (
  `id` varchar(64) NOT NULL,
  `candidate_id` varchar(64) NOT NULL,
  `item_id` varchar(64) DEFAULT NULL,
  `validation_code` varchar(100) NOT NULL,
  `severity` varchar(16) NOT NULL COMMENT 'INFO/WARNING/BLOCKER',
  `message` varchar(1000) NOT NULL,
  `detail_json` longtext DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_release_candidate_validation` (`candidate_id`, `severity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布候选预检结果';

CREATE TABLE `release_candidate_step` (
  `id` varchar(64) NOT NULL,
  `candidate_id` varchar(64) NOT NULL,
  `item_id` varchar(64) DEFAULT NULL,
  `step_no` int NOT NULL,
  `step_key` varchar(300) NOT NULL,
  `step_type` varchar(64) NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'NOT_EXECUTED' COMMENT 'NOT_EXECUTED/RUNNING/COMPLETED/FAILED/COMPENSATED/MANUAL_REQUIRED',
  `input_json` longtext DEFAULT NULL,
  `output_json` longtext DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `operator` varchar(100) DEFAULT NULL,
  `error_message` varchar(2000) DEFAULT NULL,
  `recovery_action` varchar(1000) DEFAULT NULL,
  `attempt_count` int NOT NULL DEFAULT 0,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_release_candidate_step_no` (`candidate_id`, `step_no`),
  KEY `idx_release_candidate_step_status` (`candidate_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布候选执行步骤';

CREATE TABLE `release_candidate_report` (
  `id` varchar(64) NOT NULL,
  `candidate_id` varchar(64) NOT NULL,
  `report_no` varchar(100) NOT NULL,
  `report_json` longtext NOT NULL,
  `generated_by` varchar(100) DEFAULT NULL,
  `generated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_release_candidate_report` (`candidate_id`),
  UNIQUE KEY `uk_release_candidate_report_no` (`report_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布候选审计报告';

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, query, keep_alive, breadcrumb,
  remark, deleted, create_by, create_time, update_by, update_time,
  entity_code, resource_type, list_key
) VALUES (
  'release_candidate_menu_001', '0', '发布候选', 'C', 'Promotion', 77,
  '/system/release-candidates', 'system/ReleaseCandidateManagement',
  'release-candidate:list', '0', '0', '0', '0', NULL, '0', '1',
  '应用级配置发布候选、统一预检、执行与恢复', 0, NULL,
  CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, NULL, NULL, NULL
);

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, query, keep_alive, breadcrumb,
  remark, deleted, create_by, create_time, update_by, update_time,
  entity_code, resource_type, list_key
) VALUES
  ('release_candidate_create_001', 'release_candidate_menu_001', '创建发布候选', 'F', NULL, 1, '', '', 'release-candidate:create', '0', '0', '0', '0', NULL, '0', '1', '创建和预检发布候选', 0, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, NULL, NULL, NULL),
  ('release_candidate_publish_001', 'release_candidate_menu_001', '执行发布候选', 'F', NULL, 2, '', '', 'release-candidate:publish', '0', '0', '0', '0', NULL, '0', '1', '执行已通过预检的发布候选', 0, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, NULL, NULL, NULL),
  ('release_candidate_recover_001', 'release_candidate_menu_001', '恢复发布候选', 'F', NULL, 3, '', '', 'release-candidate:recover', '0', '0', '0', '0', NULL, '0', '1', '续跑、补偿或标记人工恢复', 0, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, NULL, NULL, NULL);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT DISTINCT MD5(CONCAT(grant_row.role_id, ':release_candidate_menu_001')),
       grant_row.role_id, 'release_candidate_menu_001', CURRENT_TIMESTAMP
FROM sys_role_menu grant_row
JOIN sys_menu source_menu ON source_menu.id = grant_row.menu_id
WHERE source_menu.perm = 'config-migration:list'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = grant_row.role_id
      AND existing.menu_id = 'release_candidate_menu_001'
  );

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT DISTINCT MD5(CONCAT(grant_row.role_id, ':release_candidate_create_001')),
       grant_row.role_id, 'release_candidate_create_001', CURRENT_TIMESTAMP
FROM sys_role_menu grant_row
JOIN sys_menu source_menu ON source_menu.id = grant_row.menu_id
WHERE source_menu.perm = 'config-migration:export'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = grant_row.role_id
      AND existing.menu_id = 'release_candidate_create_001'
  );

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT DISTINCT MD5(CONCAT(grant_row.role_id, ':release_candidate_publish_001')),
       grant_row.role_id, 'release_candidate_publish_001', CURRENT_TIMESTAMP
FROM sys_role_menu grant_row
JOIN sys_menu source_menu ON source_menu.id = grant_row.menu_id
WHERE source_menu.perm = 'config-migration:publish'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = grant_row.role_id
      AND existing.menu_id = 'release_candidate_publish_001'
  );

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT DISTINCT MD5(CONCAT(grant_row.role_id, ':release_candidate_recover_001')),
       grant_row.role_id, 'release_candidate_recover_001', CURRENT_TIMESTAMP
FROM sys_role_menu grant_row
JOIN sys_menu source_menu ON source_menu.id = grant_row.menu_id
WHERE source_menu.perm = 'config-migration:rollback'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = grant_row.role_id
      AND existing.menu_id = 'release_candidate_recover_001'
  );
