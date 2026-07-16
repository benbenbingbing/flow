-- 配置迁移与发布包

CREATE TABLE `config_migration_asset` (
  `id` varchar(64) NOT NULL,
  `asset_type` varchar(20) NOT NULL COMMENT 'ENTITY/PROCESS',
  `business_key` varchar(100) NOT NULL COMMENT 'entityCode/processKey',
  `asset_name` varchar(200) NOT NULL,
  `source_history_id` varchar(64) NOT NULL,
  `source_version` int NOT NULL,
  `version_description` varchar(500) DEFAULT NULL,
  `migration_tag` varchar(100) NOT NULL,
  `mark_for_export` tinyint NOT NULL DEFAULT '1',
  `snapshot_completeness` varchar(20) NOT NULL DEFAULT 'COMPLETE',
  `snapshot_schema_version` int NOT NULL DEFAULT '1',
  `snapshot_json` longtext NOT NULL,
  `content_hash` varchar(64) NOT NULL,
  `dependencies_json` json DEFAULT NULL,
  `dependency_count` int NOT NULL DEFAULT '0',
  `missing_dependency_count` int NOT NULL DEFAULT '0',
  `export_status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `published_at` datetime DEFAULT NULL,
  `published_by` varchar(100) DEFAULT NULL,
  `last_export_at` datetime DEFAULT NULL,
  `export_count` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_migration_asset_history` (`asset_type`,`source_history_id`),
  KEY `idx_migration_asset_key` (`asset_type`,`business_key`,`source_version`),
  KEY `idx_migration_asset_tag` (`migration_tag`),
  KEY `idx_migration_asset_export` (`mark_for_export`,`export_status`,`snapshot_completeness`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移发布资产';

CREATE TABLE `config_export_package` (
  `id` varchar(64) NOT NULL,
  `package_no` varchar(100) NOT NULL,
  `migration_tag` varchar(100) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `checksum` varchar(64) NOT NULL,
  `signature_value` varchar(128) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'READY',
  `asset_count` int NOT NULL DEFAULT '0',
  `package_data` longblob NOT NULL,
  `created_by` varchar(100) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `download_count` int NOT NULL DEFAULT '0',
  `last_download_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_export_package_no` (`package_no`),
  KEY `idx_export_package_tag` (`migration_tag`),
  KEY `idx_export_package_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移导出包';

CREATE TABLE `config_export_package_item` (
  `id` varchar(64) NOT NULL,
  `package_id` varchar(64) NOT NULL,
  `asset_id` varchar(64) NOT NULL,
  `asset_type` varchar(20) NOT NULL,
  `business_key` varchar(100) NOT NULL,
  `source_version` int NOT NULL,
  `content_hash` varchar(64) NOT NULL,
  `selection_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_export_package_asset` (`package_id`,`asset_id`),
  KEY `idx_export_item_package` (`package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移导出包项目';

CREATE TABLE `config_import_package` (
  `id` varchar(64) NOT NULL,
  `package_no` varchar(100) NOT NULL,
  `source_environment` varchar(100) DEFAULT NULL,
  `migration_tag` varchar(100) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `checksum` varchar(64) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'UPLOADED',
  `validation_report_json` json DEFAULT NULL,
  `package_data` longblob NOT NULL,
  `imported_by` varchar(100) DEFAULT NULL,
  `imported_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `published_by` varchar(100) DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `error_message` text DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_import_checksum` (`checksum`),
  KEY `idx_import_package_tag` (`migration_tag`),
  KEY `idx_import_package_status` (`status`,`imported_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移导入包';

CREATE TABLE `config_import_item` (
  `id` varchar(64) NOT NULL,
  `import_package_id` varchar(64) NOT NULL,
  `asset_type` varchar(20) NOT NULL,
  `business_key` varchar(100) NOT NULL,
  `asset_name` varchar(200) NOT NULL,
  `source_version` int NOT NULL,
  `source_hash` varchar(64) NOT NULL,
  `target_before_version` int DEFAULT NULL,
  `target_before_hash` varchar(64) DEFAULT NULL,
  `target_after_version` int DEFAULT NULL,
  `target_after_hash` varchar(64) DEFAULT NULL,
  `comparison_status` varchar(30) NOT NULL DEFAULT 'NEW',
  `mapping_status` varchar(20) NOT NULL DEFAULT 'RESOLVED',
  `publish_status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `snapshot_json` longtext NOT NULL,
  `dependencies_json` json DEFAULT NULL,
  `error_message` text DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_import_item_asset` (`import_package_id`,`asset_type`,`business_key`,`source_version`),
  KEY `idx_import_item_package` (`import_package_id`),
  KEY `idx_import_item_compare` (`comparison_status`,`publish_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移导入项目';

CREATE TABLE `config_asset_baseline` (
  `id` varchar(64) NOT NULL,
  `asset_type` varchar(20) NOT NULL,
  `business_key` varchar(100) NOT NULL,
  `source_version` int NOT NULL,
  `source_hash` varchar(64) NOT NULL,
  `target_version` int DEFAULT NULL,
  `target_hash` varchar(64) NOT NULL,
  `import_package_id` varchar(64) NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_baseline` (`asset_type`,`business_key`),
  KEY `idx_asset_baseline_package` (`import_package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移资产基线';

CREATE TABLE `config_environment_mapping` (
  `id` varchar(64) NOT NULL,
  `source_type` varchar(30) NOT NULL,
  `source_key` varchar(200) NOT NULL,
  `target_key` varchar(200) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_environment_mapping` (`source_type`,`source_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移环境映射';

SET @system_manage_id = (
  SELECT id FROM sys_menu
  WHERE menu_name = '系统管理' AND menu_type = 'M' AND deleted = 0
  LIMIT 1
);

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, create_time, update_time, deleted
)
SELECT
  'config_migration_menu_001', @system_manage_id, '配置迁移', 'C', 'FolderOpened', 90,
  '/system/config-migration', 'system/ConfigMigration', 'config-migration:list',
  '0', '0', '0', '0', NOW(), NOW(), 0
WHERE @system_manage_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 'config_migration_menu_001' AND deleted = 0);

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, sort, path, component, perm,
  status, visible, is_frame, is_cache, create_time, update_time, deleted
)
SELECT button_id, 'config_migration_menu_001', button_name, 'F', button_sort, '', '', button_perm,
       '0', '0', '0', '0', NOW(), NOW(), 0
FROM (
  SELECT 'config_migration_export_001' button_id, '导出配置' button_name, 1 button_sort, 'config-migration:export' button_perm
  UNION ALL SELECT 'config_migration_download_001', '下载发布包', 2, 'config-migration:download'
  UNION ALL SELECT 'config_migration_import_001', '导入配置', 3, 'config-migration:import'
  UNION ALL SELECT 'config_migration_analyze_001', '分析配置', 4, 'config-migration:analyze'
  UNION ALL SELECT 'config_migration_publish_001', '发布配置', 5, 'config-migration:publish'
  UNION ALL SELECT 'config_migration_rollback_001', '回滚配置', 6, 'config-migration:rollback'
) buttons
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 'config_migration_menu_001' AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = button_id AND deleted = 0);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT REPLACE(UUID(), '-', ''), role.id, menu.id, NOW()
FROM sys_role role
JOIN sys_menu menu ON menu.id IN (
  'config_migration_menu_001',
  'config_migration_export_001',
  'config_migration_download_001',
  'config_migration_import_001',
  'config_migration_analyze_001',
  'config_migration_publish_001',
  'config_migration_rollback_001'
)
WHERE role.role_code IN ('super_admin', 'admin')
  AND role.deleted = 0
  AND menu.deleted = 0;
