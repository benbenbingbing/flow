-- Flattened business schema baseline.
-- Flowable engine tables are managed by Flowable.
-- All persisted audit timestamps use create_time/update_time.

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_asset_baseline` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `asset_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `business_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_version` int NOT NULL,
  `source_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_version` int DEFAULT NULL,
  `target_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `import_package_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_baseline` (`asset_type`,`business_key`),
  KEY `idx_asset_baseline_package` (`import_package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移资产基线';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_environment_mapping` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_key` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_key` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_environment_mapping` (`source_type`,`source_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移环境映射';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_export_package` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `package_no` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `migration_tag` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `signature_value` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'READY',
  `asset_count` int NOT NULL DEFAULT '0',
  `package_data` longblob NOT NULL,
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `download_count` int NOT NULL DEFAULT '0',
  `last_download_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_export_package_no` (`package_no`),
  KEY `idx_export_package_tag` (`migration_tag`),
  KEY `idx_export_package_created` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移导出包';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_export_package_item` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `package_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `asset_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `asset_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `business_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_version` int NOT NULL,
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `selection_json` longtext COLLATE utf8mb4_unicode_ci,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_export_package_asset` (`package_id`,`asset_id`),
  KEY `idx_export_item_package` (`package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移导出包项目';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_import_item` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `import_package_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `asset_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `business_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `asset_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_version` int NOT NULL,
  `source_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_before_version` int DEFAULT NULL,
  `target_before_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_after_version` int DEFAULT NULL,
  `target_after_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `comparison_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NEW',
  `mapping_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RESOLVED',
  `publish_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `snapshot_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `dependencies_json` longtext COLLATE utf8mb4_unicode_ci,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_import_item_asset` (`import_package_id`,`asset_type`,`business_key`,`source_version`),
  KEY `idx_import_item_package` (`import_package_id`),
  KEY `idx_import_item_compare` (`comparison_status`,`publish_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移导入项目';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_import_package` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `package_no` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_environment` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `migration_tag` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UPLOADED',
  `validation_report_json` longtext COLLATE utf8mb4_unicode_ci,
  `package_data` longblob NOT NULL,
  `imported_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `imported_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `published_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_import_checksum` (`checksum`),
  KEY `idx_import_package_tag` (`migration_tag`),
  KEY `idx_import_package_status` (`status`,`imported_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移导入包';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_migration_asset` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `asset_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ENTITY/PROCESS',
  `business_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'entityCode/processKey',
  `asset_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_history_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_version` int NOT NULL,
  `version_description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `migration_tag` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `mark_for_export` tinyint NOT NULL DEFAULT '1',
  `snapshot_completeness` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMPLETE',
  `snapshot_schema_version` int NOT NULL DEFAULT '1',
  `snapshot_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `dependencies_json` longtext COLLATE utf8mb4_unicode_ci,
  `dependency_count` int NOT NULL DEFAULT '0',
  `missing_dependency_count` int NOT NULL DEFAULT '0',
  `export_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `published_at` datetime DEFAULT NULL,
  `published_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_export_at` datetime DEFAULT NULL,
  `export_count` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_migration_asset_history` (`asset_type`,`source_history_id`),
  KEY `idx_migration_asset_key` (`asset_type`,`business_key`,`source_version`),
  KEY `idx_migration_asset_tag` (`migration_tag`),
  KEY `idx_migration_asset_export` (`mark_for_export`,`export_status`,`snapshot_completeness`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移发布资产';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_migration_asset_dependency` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `asset_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `dependency_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `dependency_key` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `required` tinyint NOT NULL DEFAULT '1',
  `source_description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dependency_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '依赖扩展JSON文档',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_asset_dependency` (`asset_id`,`dependency_type`,`dependency_key`),
  KEY `idx_config_dependency_lookup` (`dependency_type`,`dependency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置迁移资产依赖';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_code_rule` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `entity_code` varchar(100) NOT NULL COMMENT '实体编码',
  `prefix` varchar(20) DEFAULT '' COMMENT '编码前缀，如：CG、DD',
  `date_format` varchar(20) DEFAULT 'yyyyMMdd' COMMENT '日期格式，如：yyyyMMdd、yyyy-MM-dd',
  `seq_length` int DEFAULT '6' COMMENT '序列号位数，如：6表示000001',
  `seq_type` varchar(20) DEFAULT 'DAY' COMMENT '序列号重置周期：DAY按天、MONTH按月、YEAR按年、NEVER不重置',
  `current_seq` int DEFAULT '0' COMMENT '当前序列号值',
  `seq_date` varchar(20) DEFAULT '' COMMENT '当前序列号对应的日期（用于判断重置）',
  `example` varchar(100) DEFAULT '' COMMENT '编码示例',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_code` (`entity_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体编码规则配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `entity_code` varchar(100) NOT NULL COMMENT '实体编码',
  `entity_name` varchar(200) NOT NULL COMMENT '实体名称',
  `description` varchar(500) DEFAULT NULL COMMENT '实体描述',
  `process_definition_id` varchar(64) DEFAULT NULL COMMENT '关联流程定义ID',
  `status` varchar(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿/PUBLISHED已发布/DISABLED已禁用',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `table_name` varchar(100) DEFAULT NULL COMMENT '数据库表名',
  `lifecycle_mode` varchar(20) NOT NULL DEFAULT 'STANDALONE' COMMENT '实体生命周期模式：STANDALONE/WORKFLOW',
  `storage_mode` varchar(20) NOT NULL DEFAULT 'DYNAMIC' COMMENT '存储模式：DYNAMIC/SYSTEM',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `team_visibility_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否允许数据参与团队查看记录',
  `team_visibility_level` varchar(30) NOT NULL DEFAULT 'ADDITIVE' COMMENT '参与团队权限级别：ADDITIVE/OVERRIDE_SCOPE/ABSOLUTE',
  PRIMARY KEY (`id`),
  UNIQUE KEY `entity_code` (`entity_code`),
  KEY `idx_entity_code` (`entity_code`),
  KEY `idx_status` (`status`),
  KEY `idx_lifecycle_mode` (`lifecycle_mode`),
  KEY `idx_storage_mode` (`storage_mode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_field` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `entity_id` bigint NOT NULL COMMENT '所属实体ID',
  `field_code` varchar(100) NOT NULL COMMENT '字段编码',
  `field_name` varchar(200) NOT NULL COMMENT '字段名称',
  `field_type` varchar(50) NOT NULL COMMENT '字段类型',
  `db_type` varchar(50) DEFAULT NULL COMMENT '数据库字段类型',
  `field_length` int DEFAULT NULL COMMENT '字段长度',
  `is_required` tinyint(1) DEFAULT '0' COMMENT '是否必填',
  `is_unique` tinyint(1) DEFAULT '0' COMMENT '是否唯一',
  `default_value` varchar(500) DEFAULT NULL COMMENT '默认值',
  `options_json` text COMMENT '选项配置JSON',
  `validate_rules` text COMMENT '验证规则JSON',
  `sort_order` int DEFAULT '0' COMMENT '排序顺序',
  `is_system` tinyint(1) DEFAULT '0' COMMENT '是否系统字段：0-否 1-是（系统自动添加的字段，不可删除）',
  `is_published` tinyint DEFAULT '0' COMMENT '是否已发布到数据库表',
  `editable` tinyint(1) DEFAULT '1' COMMENT '是否可编辑：0-否 1-是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `field_precision` int DEFAULT NULL COMMENT '小数位数（精度）',
  `db_column_name` varchar(100) DEFAULT NULL COMMENT '数据库列名（下划线命名）',
  `file_types` varchar(500) DEFAULT NULL COMMENT '文件类型限制（用于附件类型，如：.jpg,.png,.pdf）',
  `file_max_size` int DEFAULT NULL COMMENT '文件大小限制（MB，用于附件类型）',
  `file_max_count` int DEFAULT NULL COMMENT '文件数量限制（用于附件类型）',
  `ref_entity_type` varchar(20) DEFAULT NULL COMMENT '引用实体类型（CUSTOM/USER/DEPT/ROLE/GROUP）',
  `ref_entity_id` varchar(64) DEFAULT NULL COMMENT '关联实体ID',
  `display_mode` varchar(20) DEFAULT NULL COMMENT '显示方式：embedded-嵌入, tab-Tab页',
  `ref_field_code` varchar(100) DEFAULT NULL COMMENT '关联字段编码',
  `field_id` varchar(100) DEFAULT NULL COMMENT '旧字段编码（兼容保留）',
  `dict_type` varchar(100) DEFAULT NULL COMMENT '绑定的系统代码表编码',
  `value_storage` varchar(20) DEFAULT 'SCALAR' COMMENT '字段值存储：SCALAR/MULTI_TABLE',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_field` (`entity_id`,`field_code`),
  KEY `idx_entity_id` (`entity_id`),
  KEY `idx_field_code` (`field_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体字段表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_field_file_item` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `field_id` varchar(64) NOT NULL COMMENT '关联字段ID（entity_field.id）',
  `item_name` varchar(200) NOT NULL COMMENT '附件项名称',
  `file_types` varchar(500) DEFAULT NULL COMMENT '允许的文件类型',
  `max_size` int DEFAULT NULL COMMENT '单文件大小限制（MB）',
  `max_count` int DEFAULT NULL COMMENT '文件数量限制',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_field_id` (`field_id`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体字段附件项配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_field_option` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `field_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `option_value` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `option_label` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `style_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `disabled` tinyint NOT NULL DEFAULT '0',
  `sort_order` int NOT NULL DEFAULT '0',
  `option_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '选项扩展JSON文档',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_field_option` (`field_id`,`option_value`),
  KEY `idx_entity_field_option_sort` (`field_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体字段静态选项';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_form` (
  `id` varchar(64) NOT NULL COMMENT 'è¡¨å•ID',
  `entity_id` varchar(64) NOT NULL COMMENT 'å®žä½“ID',
  `form_name` varchar(100) NOT NULL COMMENT 'è¡¨å•åç§°',
  `form_key` varchar(100) NOT NULL COMMENT 'è¡¨å•æ ‡è¯†',
  `description` varchar(500) DEFAULT '' COMMENT 'æè¿°',
  `layout_type` varchar(20) DEFAULT 'vertical' COMMENT 'å¸ƒå±€ç±»åž‹ï¼švertical-åž‚ç›´ horizontal-æ°´å¹³ grid-ç½‘æ ¼',
  `status` tinyint DEFAULT '1' COMMENT 'çŠ¶æ€ï¼š0-ç¦ç”¨ 1-å¯ç”¨',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `deleted` tinyint DEFAULT '0' COMMENT 'åˆ é™¤æ ‡å¿—',
  `is_default` tinyint(1) DEFAULT '0' COMMENT '是否默认表单',
  `custom_component` varchar(100) DEFAULT NULL COMMENT '自定义表单组件注册名',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `init_config` longtext COMMENT '表单初始化配置',
  `view_config` longtext COMMENT '表单视图配置JSON：布局、自定义组件参数',
  `revision` int NOT NULL DEFAULT '1' COMMENT '草稿元数据修订号',
  `active_release_id` varchar(64) DEFAULT NULL COMMENT '当前激活发布快照ID',
  `draft_hash` varchar(64) DEFAULT NULL COMMENT '当前草稿内容哈希',
  `custom_component_version` int DEFAULT NULL COMMENT '自定义整页表单组件锁定版本',
  `custom_component_snapshot_version` int DEFAULT NULL COMMENT '自定义整页表单配置快照版本',
  `data_source_bindings_document` longtext COMMENT '表单级统一数据源绑定JSON文档',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_form_key` (`entity_id`,`form_key`),
  KEY `idx_entity_id` (`entity_id`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å®žä½“è¡¨å•å®šä¹‰è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_form_field` (
  `id` varchar(64) NOT NULL COMMENT 'ID',
  `form_id` varchar(64) NOT NULL COMMENT 'è¡¨å•ID',
  `field_id` varchar(64) NOT NULL COMMENT 'å­—æ®µIDï¼ˆå¯¹åº”entity_fieldï¼‰',
  `field_code` varchar(100) DEFAULT NULL COMMENT '字段编码（对应 entity_field 的 field_code）',
  `field_name` varchar(100) NOT NULL COMMENT 'å­—æ®µåç§°',
  `field_label` varchar(100) NOT NULL COMMENT 'æ˜¾ç¤ºæ ‡ç­¾',
  `field_type` varchar(50) NOT NULL COMMENT 'å­—æ®µç±»åž‹',
  `sort_order` int DEFAULT '0' COMMENT 'æŽ’åº',
  `is_required` tinyint DEFAULT '0' COMMENT 'æ˜¯å¦å¿…å¡«ï¼š0-å¦ 1-æ˜¯',
  `is_readonly` tinyint DEFAULT '0' COMMENT 'æ˜¯å¦åªè¯»ï¼š0-å¦ 1-æ˜¯',
  `is_hidden` tinyint DEFAULT '0' COMMENT 'æ˜¯å¦éšè—ï¼š0-å¦ 1-æ˜¯',
  `default_value` varchar(500) DEFAULT '' COMMENT 'é»˜è®¤å€¼',
  `placeholder` varchar(200) DEFAULT '' COMMENT 'å ä½æç¤º',
  `validation_rules` longtext COMMENT '结构化校验规则JSON',
  `component_type` varchar(50) DEFAULT 'input' COMMENT 'ç»„ä»¶ç±»åž‹ï¼šinput/select/date/numberç­‰',
  `component_props` text COMMENT 'ç»„ä»¶é¢å¤–é…ç½®JSON',
  `grid_span` int DEFAULT '12' COMMENT 'æ …æ ¼å®½åº¦ï¼ˆ1-24ï¼‰',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `extension_config` longtext COMMENT '字段模式权限及扩展配置JSON',
  PRIMARY KEY (`id`),
  KEY `idx_form_id` (`form_id`),
  KEY `idx_field_id` (`field_id`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='è¡¨å•å­—æ®µé…ç½®è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_form_node` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定节点ID',
  `form_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表单ID',
  `parent_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父节点ID',
  `node_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表单内稳定节点编码',
  `active_node_key` varchar(100) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when (`deleted` = 0) then `node_key` else NULL end)) STORED COMMENT '仅活动节点参与表单内节点编码唯一约束',
  `node_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SECTION/GRID/TAB_SET/TAB/COLLAPSE/TEXT/FIELD/SUB_FORM/REPEATER/ACTION_SLOT',
  `binding_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE' COMMENT 'ENTITY_FIELD/RELATION/COMPUTED/CONTEXT/NONE',
  `binding_ref` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '字段、关系或上下文引用',
  `props_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '节点显式属性JSON文档',
  `rules_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '校验、显隐和权限规则JSON文档',
  `data_source_bindings_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '节点数据源绑定JSON文档',
  `legacy_props_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '无法识别的历史属性JSON文档',
  `order_key` bigint NOT NULL DEFAULT '1000000' COMMENT '稀疏排序键',
  `revision` int NOT NULL DEFAULT '1' COMMENT '节点草稿修订号',
  `template_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源模板ID',
  `template_version` int DEFAULT NULL COMMENT '锁定模板版本',
  `local_overrides_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '模板实例本地覆盖JSON文档',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `component_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节点扩展组件注册名',
  `component_version` int DEFAULT NULL COMMENT '节点扩展组件锁定版本',
  `snapshot_version` int DEFAULT NULL COMMENT '节点扩展配置快照版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_form_node_key` (`form_id`,`node_key`,`deleted`),
  UNIQUE KEY `uk_entity_form_node_active_key` (`form_id`,`active_node_key`),
  KEY `idx_entity_form_node_tree` (`form_id`,`parent_id`,`order_key`,`deleted`),
  KEY `idx_entity_form_node_binding` (`form_id`,`binding_type`,`binding_ref`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体表单递归草稿节点';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_action` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `list_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '列表配置ID',
  `position` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'TOOLBAR/ROW',
  `button_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定按钮编码',
  `button_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'built-in' COMMENT 'built-in/custom',
  `button_label` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '按钮名称',
  `icon` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标',
  `style_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '按钮样式',
  `link_mode` tinyint NOT NULL DEFAULT '0' COMMENT '是否链接按钮',
  `custom_mode` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'handler/component/open-list',
  `handler_code` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器或组件编码',
  `permission_code` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '功能权限码',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `unavailable_behavior` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'HIDE/DISABLE',
  `action_params_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '按钮扩展参数JSON文档',
  `availability_rule_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '按钮适用条件JSON文档',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `revision` int NOT NULL DEFAULT '1' COMMENT '按钮草稿修订号',
  `order_key` bigint NOT NULL DEFAULT '1000000' COMMENT '稀疏排序键',
  `template_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源模板ID',
  `template_version` int DEFAULT NULL COMMENT '锁定模板版本',
  `local_overrides_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '模板实例本地覆盖JSON文档',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_list_action` (`list_config_id`,`position`,`button_key`,`deleted`),
  KEY `idx_entity_list_action_runtime` (`list_config_id`,`position`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表按钮配置';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_config` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `entity_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体定义ID',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码',
  `list_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '列表标识（唯一，如：default、myList）',
  `list_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '列表名称',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明',
  `is_default` tinyint DEFAULT '0' COMMENT '是否默认列表',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `custom_component` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '自定义列表组件注册名',
  `toolbar_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '工具栏按钮配置JSON',
  `row_action_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '操作列按钮配置JSON',
  `view_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '列表视图配置JSON：查询区、表格、分页、自定义组件参数',
  `data_scope_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INHERIT' COMMENT '数据范围模式：INHERIT/NARROW/OVERRIDE',
  `access_permission_code` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '列表访问权限码，空时继承 entity:{code}:list',
  `allowed_scenes` longtext COLLATE utf8mb4_unicode_ci COMMENT '允许场景：MENU/PAGE/DIALOG/DRAWER/EMBEDDED/FORM_PICKER/SUB_TABLE',
  `selection_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '单选、多选和返回映射配置',
  `fixed_filter_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '服务端固定查询条件',
  `context_binding_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '来源记录上下文绑定配置',
  `query_provider_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '自定义安全查询提供者编码',
  `published_version` int NOT NULL DEFAULT '1' COMMENT '列表发布版本',
  `revision` int NOT NULL DEFAULT '1' COMMENT '草稿元数据修订号',
  `active_release_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前激活发布快照ID',
  `draft_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前草稿内容哈希',
  `query_data_source_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '统一列表查询数据源ID',
  `query_operation_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '统一列表查询操作编码',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_list_key` (`entity_id`,`list_key`,`deleted`),
  KEY `idx_entity_id` (`entity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_field` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `list_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属列表配置ID',
  `field_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体字段ID（关联entity_field）',
  `field_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段编码',
  `field_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段名称（快照）',
  `sort_order` int DEFAULT '0' COMMENT '列排序号',
  `width` int DEFAULT '0' COMMENT '列宽度（0表示自适应）',
  `show_in_list` tinyint DEFAULT '1' COMMENT '是否显示在列表',
  `is_query` tinyint DEFAULT '1' COMMENT '是否作为查询条件',
  `query_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'LIKE' COMMENT '查询方式：EQ/NE/LIKE/GT/LT/BETWEEN/IN',
  `align` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'left' COMMENT '对齐方式：left/center/right',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `data_source_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT 'ENTITY_FIELD' COMMENT '数据源类型：ENTITY_FIELD(实体字段)/REFERENCE(关联查询)/AGGREGATE(聚合统计)/CUSTOM_PROVIDER(自定义处理器)',
  `data_source_config` text COLLATE utf8mb4_unicode_ci COMMENT '数据源配置JSON',
  `render_component` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前端渲染组件名',
  `formatter` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '简单格式化表达式（如 yyyy-MM-dd、#0.00）',
  `column_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '列展示配置JSON',
  `query_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '查询组件配置JSON',
  `render_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '单元格渲染配置JSON',
  `revision` int NOT NULL DEFAULT '1' COMMENT '字段草稿修订号',
  `order_key` bigint NOT NULL DEFAULT '1000000' COMMENT '稀疏排序键',
  `data_source_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '统一数据源ID',
  `template_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源模板ID',
  `template_version` int DEFAULT NULL COMMENT '锁定模板版本',
  `local_overrides_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '模板实例本地覆盖JSON文档',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_list_field` (`list_config_id`,`field_id`,`deleted`),
  KEY `idx_list_config_id` (`list_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表字段配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_scene` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `list_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scene_code` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `revision` int NOT NULL DEFAULT '1' COMMENT '场景草稿修订号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_list_scene` (`list_config_id`,`scene_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表允许场景';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_scope_audit_log` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码',
  `list_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '列表编码',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作或被校验用户',
  `operation` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SAVE/PUBLISH/ROLLBACK/SIMULATE/BYPASS/DENY',
  `result` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SUCCESS/FAILED/DENIED',
  `detail_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '结构化详情',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_entity_list_scope_audit` (`entity_code`,`list_key`,`create_time`),
  KEY `idx_entity_list_scope_audit_user` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表数据范围审计日志';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_scope_binding` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码',
  `policy_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据范围方案ID',
  `list_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '列表编码，空表示实体默认范围',
  `match_config` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '适用用户结构化条件',
  `rule_effect` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ALLOW' COMMENT 'ALLOW/DENY',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `effective_start_time` datetime DEFAULT NULL COMMENT '生效时间',
  `effective_end_time` datetime DEFAULT NULL COMMENT '失效时间',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_entity_list_scope_binding_runtime` (`entity_code`,`list_key`,`enabled`,`deleted`),
  KEY `idx_entity_list_scope_binding_policy` (`policy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表数据范围适用对象绑定';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_scope_delegation` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实体编码，空表示全部实体',
  `from_user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '委托方用户ID',
  `to_user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '受托方用户ID',
  `delegate_scope` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PERSONAL' COMMENT 'PERSONAL/CREATED/SUBMITTED/CURRENT_TASK/POLICY/CONDITION',
  `policy_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '指定方案ID',
  `delegate_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '附加结构化条件',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_entity_list_scope_delegate_runtime` (`to_user_id`,`entity_code`,`enabled`,`deleted`),
  KEY `idx_entity_list_scope_delegate_from` (`from_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表数据范围委托';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_scope_policy` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码',
  `policy_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方案稳定编码',
  `policy_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方案名称',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '方案说明',
  `preset_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内置模板编码',
  `filter_config` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结构化数据条件',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `version` int NOT NULL DEFAULT '1' COMMENT '配置版本',
  `review_required` tinyint NOT NULL DEFAULT '0' COMMENT '旧复杂规则是否需要人工确认',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_list_scope_policy` (`entity_code`,`policy_key`,`deleted`),
  KEY `idx_entity_list_scope_policy_runtime` (`entity_code`,`status`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表数据范围方案';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_scope_release` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码',
  `version` int NOT NULL COMMENT '发布版本',
  `snapshot_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方案、绑定和列表模式完整快照',
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SHA-256',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布说明',
  `published_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布人',
  `published_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_list_scope_release` (`entity_code`,`version`),
  KEY `idx_entity_list_scope_release_active` (`entity_code`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表数据范围发布快照';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_publish_history` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `entity_id` varchar(64) NOT NULL COMMENT '实体定义ID',
  `entity_code` varchar(100) NOT NULL COMMENT '实体编码',
  `entity_name` varchar(200) NOT NULL COMMENT '实体名称',
  `version` int NOT NULL COMMENT '版本号',
  `version_description` varchar(500) DEFAULT NULL COMMENT '版本说明',
  `fields_snapshot` longtext COMMENT '字段定义快照JSON',
  `table_ddl` longtext COMMENT '表结构DDL',
  `publish_type` varchar(20) DEFAULT 'CREATE' COMMENT '发布类型：CREATE首次创建/ALTER修改结构',
  `changes_description` varchar(500) DEFAULT NULL COMMENT '变更内容描述',
  `published_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `published_by` varchar(64) DEFAULT NULL COMMENT '发布人ID',
  `published_by_name` varchar(100) DEFAULT NULL COMMENT '发布人姓名',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE有效/ROLLBACK已回滚',
  `process_definition_id` varchar(64) DEFAULT NULL COMMENT '发布时绑定流程定义ID',
  `lifecycle_mode` varchar(20) NOT NULL DEFAULT 'STANDALONE' COMMENT '发布时实体生命周期模式',
  `team_visibility_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '发布时是否允许数据参与团队查看记录',
  `team_visibility_level` varchar(30) NOT NULL DEFAULT 'ADDITIVE' COMMENT '发布时参与团队权限级别',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_version` (`entity_id`,`version`),
  KEY `idx_entity_code` (`entity_code`),
  KEY `idx_publish_type` (`publish_type`),
  KEY `idx_status` (`status`),
  KEY `idx_published_at` (`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='【实体发布版本历史表】记录实体每次发布的版本信息和表结构快照';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_relation` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `parent_entity_id` varchar(64) NOT NULL COMMENT '主实体ID',
  `parent_entity_code` varchar(100) NOT NULL COMMENT '主实体编码',
  `parent_field_id` varchar(64) DEFAULT NULL COMMENT '主实体关系字段ID',
  `parent_field_code` varchar(100) NOT NULL COMMENT '主实体关系字段编码',
  `relation_code` varchar(100) NOT NULL COMMENT '关系编码',
  `relation_name` varchar(200) DEFAULT NULL COMMENT '关系名称',
  `child_entity_id` varchar(64) NOT NULL COMMENT '子实体ID',
  `child_entity_code` varchar(100) NOT NULL COMMENT '子实体编码',
  `child_ref_field_code` varchar(100) NOT NULL COMMENT '子实体回填主数据ID字段',
  `relation_type` varchar(20) NOT NULL DEFAULT 'ONE_TO_MANY' COMMENT '关系类型：ONE_TO_ONE/ONE_TO_MANY',
  `cascade_delete` tinyint DEFAULT '1' COMMENT '主数据删除时是否级联删除子数据',
  `required` tinyint DEFAULT '0' COMMENT '是否必填',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `enabled` tinyint DEFAULT '1' COMMENT '是否启用',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_parent_field` (`parent_entity_id`,`parent_field_code`),
  KEY `idx_parent_entity` (`parent_entity_id`,`enabled`,`deleted`),
  KEY `idx_parent_code` (`parent_entity_code`,`enabled`,`deleted`),
  KEY `idx_child_entity` (`child_entity_id`),
  KEY `idx_relation_code` (`relation_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体关系定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_status` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码',
  `status_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态编码（系统标识）',
  `status_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态名称（显示用）',
  `status_category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态分类：NEW-新建、PROCESSING-审批中、COMPLETED-已完成、TERMINATED-终止',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态说明',
  `color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态颜色（如：#67C23A）',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_status` (`entity_code`,`status_code`,`deleted`),
  KEY `idx_entity_code` (`entity_code`),
  KEY `idx_status_category` (`status_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体状态定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_status_history` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `entity_data_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体数据ID',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码',
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID',
  `from_status` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '变更前状态',
  `to_status` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更后状态',
  `from_node_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源节点ID',
  `to_node_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标节点ID',
  `operator_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人姓名',
  `operation_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作类型：AUTO-自动流转, MANUAL-人工审批',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_entity_data` (`entity_data_id`),
  KEY `idx_entity_code` (`entity_code`),
  KEY `idx_process_instance` (`process_instance_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体数据状态历史记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_version_config` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置ID',
  `entity_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体定义ID',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码',
  `enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用数据版本',
  `active_release_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前运行时发布快照ID',
  `revision` int NOT NULL DEFAULT '1' COMMENT '草稿修订号',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED',
  `create_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_version_config_code` (`entity_code`,`deleted`),
  KEY `idx_entity_version_config_release` (`active_release_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体数据版本配置草稿';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_version_scenario` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '场景ID',
  `config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本配置ID',
  `scenario_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定场景编码',
  `scenario_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '场景中文名称',
  `source_types_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '匹配的变更入口JSON数组',
  `operation_types_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '匹配的操作类型JSON数组',
  `business_intents_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '匹配的业务意图JSON数组',
  `condition_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '可选条件表达式JSON文档',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级，数值越大越先匹配',
  `version_title_template` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本标题模板',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_version_scenario` (`config_id`,`scenario_code`),
  KEY `idx_entity_version_scenario_runtime` (`config_id`,`enabled`,`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体数据版本场景草稿';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_version_step` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '步骤ID',
  `config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本配置ID',
  `scenario_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '限定场景ID，为空表示实体级步骤',
  `phase` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BEFORE_WRITE' COMMENT 'PREPARE/BEFORE_WRITE/AFTER_WRITE/AFTER_COMMIT',
  `step_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BUILT_IN_RULE/EXPRESSION/FIELD_MAPPING/MANAGED_INTERFACE/JAVA_PROVIDER',
  `step_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '步骤名称',
  `provider_code` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '接口操作或Java Provider编码',
  `config_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '步骤参数JSON文档',
  `sort_order` int NOT NULL DEFAULT '0',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_entity_version_step_runtime` (`config_id`,`phase`,`enabled`,`sort_order`),
  KEY `idx_entity_version_step_scenario` (`scenario_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体变更前置操作草稿';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_change_target_binding` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '绑定ID',
  `config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标实体版本配置ID',
  `binding_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '绑定编码',
  `binding_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '绑定名称',
  `source_entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更申请实体编码',
  `target_entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标实体编码',
  `resolver_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'FIELD/RELATION/JAVA_PROVIDER',
  `resolver_code` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引用字段、关系编码或Provider编码',
  `resolver_config_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '目标解析参数JSON文档',
  `mapping_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '申请字段到目标字段映射JSON文档',
  `apply_strategy` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MERGE' COMMENT 'MERGE/REPLACE',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_change_target_binding` (`config_id`,`binding_code`),
  KEY `idx_entity_change_target_source` (`source_entity_code`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='变更申请目标解析配置';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_version_config_release` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发布快照ID',
  `config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本配置ID',
  `version` int NOT NULL COMMENT '发布版本号',
  `config_document` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '完整不可变配置JSON文档',
  `published_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `published_by_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `publish_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_version_config_release` (`config_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体版本配置发布快照';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_change_target_instance` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标实例ID',
  `binding_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '绑定编码',
  `source_entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更申请实体',
  `source_record_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更申请记录ID',
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标实体',
  `target_record_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标记录ID',
  `baseline_version_no` int DEFAULT NULL COMMENT '目标冻结时版本号',
  `target_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '解析时目标与映射快照',
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FROZEN' COMMENT 'FROZEN/APPLIED/CONFLICT/FAILED',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_change_target_instance` (`source_entity_code`,`source_record_id`,`process_instance_id`,`binding_code`,`target_entity_code`,`target_record_id`),
  KEY `idx_entity_change_target_process` (`process_instance_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='变更流程实际目标记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_mutation_receipt` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更回执ID',
  `idempotency_key` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '全局幂等键',
  `command_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更命令摘要',
  `operation_id` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作ID',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `record_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operation_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS',
  `result_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '首次成功执行结果JSON',
  `version_no` int DEFAULT NULL,
  `version_scenario_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `changed` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_mutation_receipt_key` (`idempotency_key`),
  KEY `idx_entity_mutation_receipt_record` (`entity_code`,`record_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体变更持久化幂等回执';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_record_version` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务版本ID',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `record_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `version_no` int NOT NULL COMMENT '同一记录从1递增',
  `version_title` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `scenario_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scenario_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_id` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `business_intent_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `business_intent_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_entity_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_record_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `process_definition_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operator_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operator_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `business_trace_key` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `idempotency_key` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_release_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entity_release_version` int DEFAULT NULL,
  `snapshot_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `snapshot_document` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_record_version_no` (`entity_code`,`record_id`,`version_no`),
  UNIQUE KEY `uk_entity_record_version_idempotent` (`entity_code`,`record_id`,`scenario_code`,`idempotency_key`),
  KEY `idx_entity_record_version_time` (`entity_code`,`record_id`,`create_time`),
  KEY `idx_entity_record_version_process` (`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务实体完整数据版本';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_action` (
  `id` varchar(64) NOT NULL,
  `process_config_id` varchar(64) NOT NULL COMMENT '流程定义配置ID',
  `sequence_flow_id` varchar(64) NOT NULL COMMENT '顺序流ID（bpmn元素ID）',
  `scope_type` varchar(20) DEFAULT 'SEQUENCE_FLOW' COMMENT '作用域：PROCESS/NODE/SEQUENCE_FLOW',
  `element_id` varchar(100) DEFAULT NULL COMMENT 'BPMN元素ID，流程级为空',
  `trigger_timing` varchar(50) DEFAULT 'TRANSITION_TAKEN' COMMENT '业务触发时机',
  `execution_mode` varchar(30) DEFAULT 'IN_TRANSACTION' COMMENT '执行方式：IN_TRANSACTION/AFTER_COMMIT',
  `failure_policy` varchar(20) DEFAULT 'ROLLBACK' COMMENT '失败策略：ROLLBACK/CONTINUE/RETRY/IGNORE',
  `retry_config` text COMMENT '重试配置JSON',
  `action_definition_id` varchar(64) DEFAULT NULL COMMENT '动作定义目录ID',
  `action_name` varchar(100) NOT NULL COMMENT '动作名称',
  `description` varchar(500) DEFAULT NULL COMMENT '动作描述',
  `interface_name` varchar(200) NOT NULL COMMENT '接口名称（Spring Bean或类名）',
  `method_name` varchar(50) DEFAULT 'execute' COMMENT '方法名',
  `params_json` text COMMENT '参数JSON',
  `sort_order` int DEFAULT '0' COMMENT '执行顺序',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `status` varchar(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/DISABLED',
  `version_id` varchar(64) DEFAULT NULL COMMENT '所属版本ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `deleted` int DEFAULT '0' COMMENT 'æ˜¯å¦åˆ é™¤ 0-æœªåˆ é™¤ 1-å·²åˆ é™¤',
  PRIMARY KEY (`id`),
  KEY `idx_process_config` (`process_config_id`),
  KEY `idx_sequence_flow` (`process_config_id`,`sequence_flow_id`),
  KEY `idx_version` (`version_id`),
  KEY `idx_status` (`status`),
  KEY `idx_process_action_binding` (`process_config_id`,`scope_type`,`element_id`,`trigger_timing`,`status`,`deleted`),
  KEY `idx_process_action_version_binding` (`version_id`,`scope_type`,`element_id`,`trigger_timing`,`status`,`deleted`),
  KEY `idx_process_action_definition_id` (`action_definition_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程动作配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_action_definition` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `action_code` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定动作编码，默认使用Spring Bean名称',
  `display_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作中文名称',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '动作用途说明',
  `handler_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'FlowActionHandler Bean名称',
  `visibility_scope` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENTITY' COMMENT '可见范围：GLOBAL/ENTITY',
  `entity_codes_json` text COLLATE utf8mb4_unicode_ci COMMENT 'ENTITY范围可见的实体编码JSON数组',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否允许在流程设计器中选择',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_action_definition_code` (`action_code`),
  UNIQUE KEY `uk_process_action_definition_handler` (`handler_name`),
  KEY `idx_process_action_definition_scope` (`visibility_scope`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程动作处理器目录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_action_definition_entity` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `action_definition_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_action_definition_entity` (`action_definition_id`,`entity_code`),
  KEY `idx_process_action_entity_code` (`entity_code`,`action_definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程动作可见实体';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_action_execution` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `action_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `action_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '动作名称快照',
  `handler_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器Bean名称快照',
  `handler_display_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器中文名称快照',
  `version_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `process_definition_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `execution_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实体编码快照',
  `scope_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `element_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trigger_timing` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_json` text COLLATE utf8mb4_unicode_ci,
  `resolved_params_json` text COLLATE utf8mb4_unicode_ci COMMENT '表达式解析后的动作参数',
  `result_json` text COLLATE utf8mb4_unicode_ci COMMENT '动作执行结果',
  `execution_trace_json` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '结构化执行步骤轨迹',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `retry_count` int DEFAULT '0',
  `max_retries` int DEFAULT '5',
  `next_retry_time` datetime DEFAULT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `error_stack` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '异常堆栈',
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL COMMENT '执行耗时毫秒',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_action_execution_idempotency` (`idempotency_key`),
  KEY `idx_process_action_execution_ready` (`status`,`next_retry_time`,`create_time`),
  KEY `idx_process_action_execution_process` (`process_instance_id`,`create_time`),
  KEY `idx_process_action_execution_action` (`action_id`,`create_time`),
  KEY `idx_process_action_execution_entity` (`entity_code`,`process_instance_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程动作执行记录与Outbox';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_cc_record` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `process_instance_id` varchar(64) NOT NULL COMMENT '流程实例ID',
  `process_definition_id` varchar(64) DEFAULT NULL COMMENT '流程定义ID',
  `process_key` varchar(100) DEFAULT NULL COMMENT '流程Key',
  `process_name` varchar(200) DEFAULT NULL COMMENT '流程名称',
  `business_key` varchar(200) DEFAULT NULL COMMENT '业务Key',
  `node_id` varchar(100) DEFAULT NULL COMMENT '节点ID',
  `node_name` varchar(200) DEFAULT NULL COMMENT '节点名称',
  `cc_user_id` varchar(64) DEFAULT NULL COMMENT '抄送人ID',
  `cc_user_name` varchar(100) DEFAULT NULL COMMENT '抄送人名称',
  `cc_type` varchar(20) DEFAULT 'AUTO' COMMENT '抄送类型：AUTO自动/MANUAL手动',
  `cc_timing` varchar(20) DEFAULT NULL COMMENT '抄送时机',
  `operator_id` varchar(64) DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人名称',
  `comment` varchar(1000) DEFAULT NULL COMMENT '知会备注',
  `source_task_id` varchar(64) DEFAULT NULL COMMENT '来源任务ID',
  `source_type` varchar(20) DEFAULT NULL COMMENT '来源类型',
  `recipient_rule_snapshot` text COMMENT '收件人规则快照',
  `unique_key` varchar(255) DEFAULT NULL COMMENT '幂等键',
  `read_status` varchar(20) DEFAULT 'UNREAD' COMMENT '阅读状态：UNREAD未读/READ已读',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_cc_unique_key` (`unique_key`),
  KEY `idx_process_instance` (`process_instance_id`),
  KEY `idx_cc_user` (`cc_user_id`,`read_status`),
  KEY `idx_process_key` (`process_key`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_process_cc_source_task` (`source_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程抄送记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_common_opinion` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID',
  `opinion_content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '意见内容',
  `opinion_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'APPROVE' COMMENT '意见类型：APPROVE同意/REJECT驳回/TRANSFER转办',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `use_count` int DEFAULT '0' COMMENT '使用次数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='常用审批意见';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_definition_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `process_key` varchar(100) NOT NULL COMMENT '流程标识',
  `process_name` varchar(200) NOT NULL COMMENT '流程名称',
  `description` varchar(500) DEFAULT NULL COMMENT '流程描述',
  `category` varchar(100) DEFAULT NULL COMMENT '流程分类',
  `version` int DEFAULT '1' COMMENT '版本号',
  `status` varchar(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿/PUBLISHED已发布/DISABLED已禁用',
  `bpmn_xml` text COMMENT 'BPMN XML内容',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT 'æ˜¯å¦åˆ é™¤ 0-æœªåˆ é™¤ 1-å·²åˆ é™¤',
  `entity_id` varchar(64) DEFAULT NULL COMMENT '绑定实体ID',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `process_key` (`process_key`),
  KEY `idx_process_key` (`process_key`),
  KEY `idx_status` (`status`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程定义配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_draft` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `draft_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '草稿编码',
  `process_definition_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID',
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程名称',
  `entity_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联实体编码',
  `entity_data_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联实体数据ID（临时数据）',
  `business_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务主键',
  `form_data` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表单数据',
  `draft_title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '草稿标题',
  `draft_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '草稿摘要',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人ID',
  `user_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE有效/SUBMITTED已提交/DELETED已删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `draft_code` (`draft_code`),
  KEY `idx_user` (`user_id`,`status`,`update_time`),
  KEY `idx_process` (`process_definition_id`),
  KEY `idx_entity` (`entity_code`,`entity_data_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程草稿箱';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_entity_status_mapping` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `process_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程定义配置ID',
  `process_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程标识',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码',
  `sequence_flow_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '连线ID（BPMN中的sequenceFlowId）',
  `source_node_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '源节点ID',
  `source_node_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '源节点名称',
  `target_node_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标节点ID',
  `target_node_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标节点名称',
  `entity_status_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体状态编码（关联entity_status表）',
  `condition_expression` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '条件表达式',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明描述',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `entity_status` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体数据状态值（如:审批中、已通过、已驳回）',
  `status_category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态分类：NEW-新建流程状态、PROCESSING-审批中流程状态、COMPLETED-已完成流程状态、TERMINATED-终止流程状态',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_source_target` (`process_config_id`,`source_node_id`,`target_node_id`,`deleted`),
  KEY `idx_process_config` (`process_config_id`),
  KEY `idx_process_key` (`process_key`),
  KEY `idx_entity_code` (`entity_code`),
  KEY `idx_source_node` (`source_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体流程状态映射表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_form_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `node_config_id` bigint NOT NULL COMMENT '所属节点配置ID',
  `form_name` varchar(200) NOT NULL COMMENT '表单名称',
  `form_key` varchar(100) NOT NULL COMMENT '表单标识',
  `description` varchar(500) DEFAULT NULL COMMENT '表单描述',
  `is_readonly` tinyint(1) DEFAULT '0' COMMENT '是否只读',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `entity_form_id` varchar(64) DEFAULT NULL COMMENT '实体表单ID',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_node_config_id` (`node_config_id`),
  KEY `idx_form_key` (`form_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='表单配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_form_field_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `form_config_id` bigint NOT NULL COMMENT '所属表单配置ID',
  `field_name` varchar(200) NOT NULL COMMENT '字段名称',
  `field_key` varchar(100) NOT NULL COMMENT '字段标识',
  `field_type` varchar(50) NOT NULL COMMENT '字段类型',
  `is_required` tinyint(1) DEFAULT '0' COMMENT '是否必填',
  `default_value` varchar(500) DEFAULT NULL COMMENT '默认值',
  `options_json` text COMMENT '选项配置JSON',
  `validate_rules` text COMMENT '验证规则JSON',
  `sort_order` int DEFAULT '0' COMMENT '排序顺序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_form_config_id` (`form_config_id`),
  KEY `idx_field_key` (`field_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='表单字段配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_node_approval` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `process_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `node_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `node_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` tinyint(1) DEFAULT '1',
  `comment_label` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT '审批意见',
  `options_json` text COLLATE utf8mb4_unicode_ci,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_process_node` (`process_config_id`,`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_node_approval_option` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `approval_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `option_value` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `option_label` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `style_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `show_comment` tinyint NOT NULL DEFAULT '1',
  `remark_required` tinyint NOT NULL DEFAULT '0',
  `sort_order` int NOT NULL DEFAULT '0',
  `option_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '审批项扩展JSON文档',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_approval_option` (`approval_config_id`,`option_value`),
  KEY `idx_process_approval_option_sort` (`approval_config_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程节点审批选项';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_node_assignee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `node_config_id` bigint NOT NULL COMMENT '所属节点配置ID',
  `assignee_type` varchar(50) NOT NULL COMMENT '审批人类型',
  `assignee_value` varchar(200) NOT NULL COMMENT '审批人值',
  `assignee_name` varchar(200) DEFAULT NULL COMMENT '审批人显示名称',
  `priority` int DEFAULT '0' COMMENT '优先级',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_node_config_id` (`node_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批人配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_node_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `node_id` varchar(100) NOT NULL COMMENT '节点ID',
  `node_name` varchar(200) NOT NULL COMMENT '节点名称',
  `node_type` varchar(50) NOT NULL COMMENT '节点类型',
  `process_config_id` bigint NOT NULL COMMENT '所属流程配置ID',
  `config_json` text COMMENT '扩展配置JSON',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `skip_node` tinyint DEFAULT '0' COMMENT '是否跳过节点',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_process_config_id` (`process_config_id`),
  KEY `idx_node_id` (`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程节点配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_node_form` (
  `id` varchar(64) NOT NULL COMMENT 'ID',
  `process_config_id` varchar(64) NOT NULL COMMENT 'æµç¨‹é…ç½®ID',
  `node_id` varchar(100) NOT NULL COMMENT 'èŠ‚ç‚¹IDï¼ˆbpmnå…ƒç´ IDï¼‰',
  `node_name` varchar(100) DEFAULT '' COMMENT 'èŠ‚ç‚¹åç§°',
  `form_id` varchar(64) NOT NULL COMMENT 'è¡¨å•ID',
  `is_readonly` tinyint DEFAULT '0' COMMENT 'æ˜¯å¦åªè¯»ï¼š0-å¦ 1-æ˜¯',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_node` (`process_config_id`,`node_id`),
  KEY `idx_process_config_id` (`process_config_id`),
  KEY `idx_form_id` (`form_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æµç¨‹èŠ‚ç‚¹è¡¨å•ç»‘å®šè¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_operation_log` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operation_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型：START/CLAIM/COMPLETE/TRANSFER/DELEGATE/REJECT/RETURN/CC',
  `operator_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operation_time` datetime DEFAULT NULL COMMENT '操作时间',
  `operation_comment` text COLLATE utf8mb4_unicode_ci,
  `old_value` text COLLATE utf8mb4_unicode_ci COMMENT '旧值（JSON）',
  `new_value` text COLLATE utf8mb4_unicode_ci COMMENT '新值（JSON）',
  `ip_address` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_agent` text COLLATE utf8mb4_unicode_ci,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `old_value_format` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'JSON' COMMENT 'JSON/PLAIN_TEXT',
  `new_value_format` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'JSON' COMMENT 'JSON/PLAIN_TEXT',
  PRIMARY KEY (`id`),
  KEY `idx_process` (`process_instance_id`,`operation_time`),
  KEY `idx_operator` (`operator_id`,`operation_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程操作日志';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_person_resolver_definition` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '人员解析器定义ID',
  `resolver_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定解析器编码',
  `display_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '中文名称',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用途说明',
  `bean_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Spring Bean名称',
  `implementation_version` int NOT NULL DEFAULT '1' COMMENT '实现版本',
  `contract_version` int NOT NULL DEFAULT '1' COMMENT '平台契约版本',
  `supported_usages_document` text COLLATE utf8mb4_unicode_ci COMMENT 'ASSIGNEE/CANDIDATE/MULTI_INSTANCE/CC',
  `extra_param_schema_document` longtext COLLATE utf8mb4_unicode_ci COMMENT 'extraParams Schema',
  `dynamic_extra_params` tinyint NOT NULL DEFAULT '0' COMMENT '是否允许动态extraParams',
  `enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否允许在流程配置中选择',
  `revision` int NOT NULL DEFAULT '1' COMMENT '目录修订号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_person_resolver_code` (`resolver_code`,`deleted`),
  KEY `idx_person_resolver_enabled` (`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='受控人员解析器目录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®ID',
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æµç¨‹å®žä¾‹ID',
  `process_definition_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æµç¨‹å®šä¹‰ID',
  `process_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æµç¨‹æ ‡è¯†',
  `process_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æµç¨‹åç§°',
  `node_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'èŠ‚ç‚¹ID',
  `node_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'èŠ‚ç‚¹åç§°',
  `node_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'èŠ‚ç‚¹ç±»åž‹',
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Flowableä»»åŠ¡ID',
  `business_key` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ä¸šåŠ¡ä¸»é”®',
  `entity_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å®žä½“ç¼–ç ',
  `entity_data_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å®žä½“æ•°æ®ID',
  `assignee_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æ‰§è¡ŒäººID',
  `assignee_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æ‰§è¡Œäººå§“å',
  `assignee_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æ‰§è¡Œäººç±»åž‹: user/group/role',
  `form_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'è¡¨å•æ ‡è¯†',
  `form_data` longtext COLLATE utf8mb4_unicode_ci COMMENT 'è¡¨å•æ•°æ®',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'todo' COMMENT '状态：todo待办/done已办/transfer已转办/skip已跳过/withdrawn已撤回',
  `action` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æ“ä½œ: approve/reject/transfer/skip',
  `action_label` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作显示文本，如"同意，需要会签"',
  `comment` text COLLATE utf8mb4_unicode_ci COMMENT 'å®¡æ‰¹æ„è§',
  `start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'ä»»åŠ¡å¼€å§‹æ—¶é—´',
  `end_time` datetime DEFAULT NULL COMMENT 'ä»»åŠ¡ç»“æŸæ—¶é—´',
  `due_time` datetime DEFAULT NULL COMMENT 'æˆªæ­¢æ—¶é—´',
  `duration` bigint DEFAULT NULL COMMENT 'å¤„ç†è€—æ—¶(æ¯«ç§’)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `deleted` tinyint DEFAULT '0' COMMENT 'åˆ é™¤æ ‡è®°: 0-æ­£å¸¸ 1-åˆ é™¤',
  `timeout_hours` int DEFAULT NULL COMMENT '超时时间',
  `timeout_action` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '超时策略',
  `timeout_handled` tinyint DEFAULT '0' COMMENT '是否已处理超时',
  `priority` int DEFAULT '0' COMMENT '优先级',
  PRIMARY KEY (`id`),
  UNIQUE KEY `task_id` (`task_id`),
  KEY `idx_process_instance` (`process_instance_id`),
  KEY `idx_assignee` (`assignee_id`,`status`),
  KEY `idx_status` (`status`),
  KEY `idx_business_key` (`business_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='æµç¨‹èŠ‚ç‚¹å¾…åŠžè¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task_add_sign` (
  `id` varchar(64) NOT NULL,
  `process_instance_id` varchar(64) NOT NULL,
  `source_task_id` varchar(64) NOT NULL,
  `node_id` varchar(100) DEFAULT NULL,
  `operation_type` varchar(20) NOT NULL DEFAULT 'PARALLEL',
  `operator_id` varchar(64) NOT NULL,
  `comment` varchar(1000) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `engine_execution_id` varchar(64) DEFAULT NULL,
  `source_completed` tinyint NOT NULL DEFAULT '0' COMMENT '原任务是否已提交',
  `source_action` varchar(100) DEFAULT NULL COMMENT '原任务提交动作',
  `source_action_label` varchar(200) DEFAULT NULL COMMENT '原任务动作名称',
  `source_comment` varchar(1000) DEFAULT NULL COMMENT '原任务审批意见',
  `source_form_data` longtext COMMENT '原任务表单数据JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `complete_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_add_sign_source_task` (`source_task_id`,`status`),
  KEY `idx_add_sign_process` (`process_instance_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行时加签记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task_add_sign_user` (
  `id` varchar(64) NOT NULL,
  `add_sign_id` varchar(64) NOT NULL,
  `user_id` varchar(64) NOT NULL,
  `user_name_snapshot` varchar(100) DEFAULT NULL,
  `generated_task_id` varchar(64) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'TODO',
  `sort_order` int NOT NULL DEFAULT '0',
  `complete_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_add_sign_user` (`add_sign_id`,`user_id`),
  UNIQUE KEY `uk_add_sign_generated_task` (`generated_task_id`),
  KEY `idx_add_sign_user_status` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行时加签人员';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task_candidate_group` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `group_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_task_candidate_group` (`task_instance_id`,`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程任务候选组';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task_candidate_user` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_task_candidate_user` (`task_instance_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程任务候选用户';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task_instance` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务实例ID',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程实例ID',
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Flowable任务ID',
  `task_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务节点Key',
  `task_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务名称',
  `process_definition_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID',
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程名称',
  `entity_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联实体编码',
  `entity_data_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联实体数据ID',
  `business_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务主键',
  `assignee_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '被指派人ID',
  `assignee_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '被指派人姓名',
  `owner_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务所有人ID',
  `candidate_users` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '候选人ID列表（JSON）',
  `candidate_groups` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '候选组列表（JSON）',
  `task_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务类型：TODO待办/DONE已办/DRAFT草稿/CC抄送',
  `action_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作类型：SUBMIT/APPROVE/REJECT/TRANSFER/RETURN/DELEGATE',
  `action_comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '处理意见',
  `form_data` longtext COLLATE utf8mb4_unicode_ci COMMENT '表单数据快照',
  `due_time` datetime DEFAULT NULL COMMENT '截止时间',
  `priority` int DEFAULT '50' COMMENT '优先级 0-100',
  `is_read` tinyint DEFAULT '0' COMMENT '是否已读',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `start_time` datetime DEFAULT NULL COMMENT '任务开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '任务结束时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '处理耗时（毫秒）',
  `parent_task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父任务ID（用于会签）',
  `root_task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '根任务ID',
  `delegation_state` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '委托状态：PENDING/RESOLVED',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_process_instance` (`process_instance_id`),
  KEY `idx_assignee_type` (`assignee_id`,`task_type`),
  KEY `idx_entity` (`entity_code`,`entity_data_id`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_due_time` (`due_time`),
  KEY `idx_task_type` (`task_type`,`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程任务实例表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_ui_release_binding` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '绑定记录ID',
  `process_version_history_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程发布历史ID',
  `process_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程配置ID',
  `process_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程标识',
  `process_version` int NOT NULL COMMENT '流程版本号',
  `deployment_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Flowable部署ID',
  `node_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程节点ID',
  `node_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程节点名称',
  `config_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FORM' COMMENT 'FORM/LIST',
  `config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表单或列表配置ID',
  `pinned_release_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程发布时固定的UI发布ID',
  `pinned_release_version` int NOT NULL COMMENT '流程发布时固定的UI版本号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_ui_release_binding` (`process_version_history_id`,`node_id`,`config_type`,`config_id`),
  KEY `idx_process_ui_binding_config` (`config_type`,`config_id`,`process_version_history_id`),
  KEY `idx_process_ui_binding_release` (`pinned_release_id`,`process_version_history_id`),
  KEY `idx_process_ui_binding_deployment` (`deployment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程发布版本与UI发布快照绑定';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_version_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®ID',
  `process_config_id` bigint NOT NULL COMMENT 'æµç¨‹å®šä¹‰ID',
  `process_key` varchar(100) NOT NULL COMMENT 'æµç¨‹æ ‡è¯†',
  `process_name` varchar(200) NOT NULL COMMENT 'æµç¨‹åç§°',
  `version` int NOT NULL COMMENT 'ç‰ˆæœ¬å·',
  `version_description` varchar(500) DEFAULT NULL COMMENT 'ç‰ˆæœ¬æè¿°/å‘å¸ƒè¯´æ˜Ž',
  `bpmn_xml` text COMMENT 'BPMN XMLå†…å®¹',
  `published_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'å‘å¸ƒæ—¶é—´',
  `published_by` varchar(64) DEFAULT NULL COMMENT 'å‘å¸ƒäººID',
  `deployment_id` varchar(64) DEFAULT NULL COMMENT 'Flowableéƒ¨ç½²ID',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT 'çŠ¶æ€ï¼šACTIVE-æœ‰æ•ˆï¼ŒARCHIVED-å·²å½’æ¡£',
  `deleted` int DEFAULT '0' COMMENT 'æ˜¯å¦åˆ é™¤ 0-æœªåˆ é™¤ 1-å·²åˆ é™¤',
  `node_forms_snapshot` longtext COMMENT '节点表单绑定快照JSON',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_version` (`process_config_id`,`version`),
  KEY `idx_process_config_id` (`process_config_id`),
  KEY `idx_process_key` (`process_key`),
  KEY `idx_version` (`version`),
  KEY `idx_deployment_id` (`deployment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æµç¨‹ç‰ˆæœ¬åŽ†å²è¡¨';
CREATE TABLE `sys_dict` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `dict_code` varchar(100) NOT NULL COMMENT '字典编码',
  `dict_name` varchar(100) NOT NULL COMMENT '字典名称',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `status` char(1) DEFAULT '0' COMMENT '状态：0-启用 1-禁用',
  `sort` int DEFAULT '0' COMMENT '排序',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_code` (`dict_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_item` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `dict_id` varchar(64) NOT NULL COMMENT '所属字典ID',
  `dict_code` varchar(100) NOT NULL COMMENT '冗余：字典编码（便于直接查询）',
  `parent_id` varchar(64) DEFAULT '0' COMMENT '父项ID，0表示顶级',
  `item_code` varchar(100) NOT NULL COMMENT '项编码',
  `item_label` varchar(100) NOT NULL COMMENT '项标签/显示文本',
  `item_value` varchar(200) NOT NULL COMMENT '项值',
  `sort` int DEFAULT '0' COMMENT '排序',
  `status` char(1) DEFAULT '0' COMMENT '状态：0-启用 1-禁用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dict_id` (`dict_id`),
  KEY `idx_dict_code` (`dict_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_dict_item_lookup` (`dict_code`,`item_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_group` (
  `id` varchar(64) NOT NULL COMMENT 'ç»„ID',
  `group_name` varchar(50) NOT NULL COMMENT 'ç»„åç§°',
  `group_code` varchar(50) NOT NULL COMMENT 'ç»„ç¼–ç ',
  `description` varchar(200) DEFAULT '' COMMENT 'æè¿°',
  `sort` int DEFAULT '0' COMMENT 'æŽ’åº',
  `status` char(1) DEFAULT '0' COMMENT 'çŠ¶æ€ï¼ˆ0å¯ç”¨ 1ç¦ç”¨ï¼‰',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `deleted` tinyint DEFAULT '0' COMMENT 'åˆ é™¤æ ‡å¿—',
  `parent_id` varchar(64) DEFAULT NULL COMMENT '父组ID',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_code` (`group_code`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”¨æˆ·ç»„è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` varchar(64) NOT NULL COMMENT '菜单ID',
  `parent_id` varchar(64) DEFAULT '0' COMMENT '父菜单ID，0为顶级菜单',
  `menu_name` varchar(100) NOT NULL COMMENT '菜单名称',
  `menu_type` char(1) DEFAULT 'M' COMMENT '菜单类型：M-目录 C-菜单 F-按钮',
  `icon` varchar(100) DEFAULT NULL COMMENT '菜单图标',
  `sort` int DEFAULT '0' COMMENT '显示排序',
  `path` varchar(200) DEFAULT NULL COMMENT '路由地址',
  `component` varchar(255) DEFAULT NULL COMMENT '组件路径',
  `perm` varchar(200) DEFAULT NULL COMMENT '权限标识，如：system:user:list',
  `status` char(1) DEFAULT '1' COMMENT '状态：0-禁用 1-启用',
  `visible` char(1) DEFAULT '1' COMMENT '显示状态：0-隐藏 1-显示',
  `keep_alive` char(1) DEFAULT '0' COMMENT '是否缓存：0-不缓存 1-缓存',
  `breadcrumb` char(1) DEFAULT '1' COMMENT '是否显示面包屑：0-否 1-是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` int DEFAULT '0' COMMENT '是否删除：0-未删除 1-已删除',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_frame` char(1) DEFAULT '0' COMMENT 'æ˜¯å¦å¤–é“¾ï¼š0-å¦ 1-æ˜¯',
  `is_cache` char(1) DEFAULT '0' COMMENT 'æ˜¯å¦ç¼“å­˜ï¼š0-ç¼“å­˜ 1-ä¸ç¼“å­˜',
  `query` varchar(255) DEFAULT '' COMMENT 'è·¯ç”±å‚æ•°',
  `entity_code` varchar(100) DEFAULT NULL COMMENT '关联实体编码，当菜单类型为C且配置了此字段时，点击菜单将跳转到对应实体的数据列表',
  `resource_type` varchar(30) DEFAULT NULL COMMENT '菜单资源类型，ENTITY_LIST 表示动态实体列表',
  `list_key` varchar(100) DEFAULT NULL COMMENT '实体列表稳定编码',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_sort` (`sort`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_entity_code` (`entity_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单权限表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_organization` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `org_code` varchar(100) NOT NULL COMMENT '组织编码（唯一）',
  `org_name` varchar(100) NOT NULL COMMENT '组织名称',
  `type` varchar(20) NOT NULL COMMENT '类型：org-组织，dept-部门',
  `parent_id` varchar(64) DEFAULT '0' COMMENT '父级ID（顶级为0）',
  `level` int DEFAULT '0' COMMENT '层级（0为顶级）',
  `path` varchar(500) DEFAULT '/' COMMENT '完整路径，如：/0/1/5/10/',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `leader_id` varchar(64) DEFAULT NULL COMMENT '负责人ID',
  `leader_name` varchar(100) DEFAULT NULL COMMENT '负责人名称（冗余）',
  `phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `address` varchar(200) DEFAULT NULL COMMENT '地址',
  `status` varchar(10) DEFAULT '0' COMMENT '状态：0-启用，1-禁用',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '是否删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_code` (`org_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_type` (`type`),
  KEY `idx_path` (`path`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组织部门表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` varchar(64) NOT NULL COMMENT 'è§’è‰²ID',
  `role_name` varchar(50) NOT NULL COMMENT 'è§’è‰²åç§°',
  `role_code` varchar(50) NOT NULL COMMENT 'è§’è‰²ç¼–ç ',
  `description` varchar(200) DEFAULT '' COMMENT 'æè¿°',
  `sort` int DEFAULT '0' COMMENT 'æ˜¾ç¤ºæŽ’åº',
  `status` char(1) DEFAULT '0' COMMENT 'çŠ¶æ€ï¼ˆ0å¯ç”¨ 1ç¦ç”¨ï¼‰',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT 'åˆ é™¤æ ‡å¿—ï¼ˆ0æ­£å¸¸ 1åˆ é™¤ï¼‰',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='è§’è‰²è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `id` varchar(64) NOT NULL COMMENT 'ID',
  `role_id` varchar(64) NOT NULL COMMENT 'è§’è‰²ID',
  `menu_id` varchar(64) NOT NULL COMMENT 'èœå•ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`,`menu_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='è§’è‰²èœå•å…³è”è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` varchar(64) NOT NULL COMMENT 'ç”¨æˆ·ID',
  `username` varchar(50) NOT NULL COMMENT 'ç”¨æˆ·å',
  `nickname` varchar(50) DEFAULT '' COMMENT 'æ˜µç§°',
  `password` varchar(100) NOT NULL COMMENT 'å¯†ç ',
  `email` varchar(100) DEFAULT '' COMMENT 'é‚®ç®±',
  `phone` varchar(20) DEFAULT '' COMMENT 'æ‰‹æœºå·',
  `avatar` varchar(255) DEFAULT '' COMMENT 'å¤´åƒ',
  `status` char(1) DEFAULT '0' COMMENT 'çŠ¶æ€ï¼ˆ0å¯ç”¨ 1ç¦ç”¨ï¼‰',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT 'åˆ é™¤æ ‡å¿—ï¼ˆ0æ­£å¸¸ 1åˆ é™¤ï¼‰',
  `org_id` varchar(64) DEFAULT NULL COMMENT '组织ID',
  `dept_id` varchar(64) DEFAULT NULL COMMENT '部门ID',
  `password_reset_required` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_org_id` (`org_id`),
  KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”¨æˆ·è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_group` (
  `id` varchar(64) NOT NULL COMMENT 'ID',
  `user_id` varchar(64) NOT NULL COMMENT 'ç”¨æˆ·ID',
  `group_id` varchar(64) NOT NULL COMMENT 'ç»„ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_group` (`user_id`,`group_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”¨æˆ·ç»„å…³è”è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` varchar(64) NOT NULL COMMENT 'ID',
  `user_id` varchar(64) NOT NULL COMMENT 'ç”¨æˆ·ID',
  `role_id` varchar(64) NOT NULL COMMENT 'è§’è‰²ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”¨æˆ·è§’è‰²å…³è”è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_operation_log` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `module_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `risk_level` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operator_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operator_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operator_ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_agent` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_method` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_path` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `summary` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `before_json` longtext COLLATE utf8mb4_unicode_ci,
  `after_json` longtext COLLATE utf8mb4_unicode_ci,
  `changed_fields_json` longtext COLLATE utf8mb4_unicode_ci,
  `payload_truncated` tinyint NOT NULL DEFAULT '0',
  `error_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_operation_event` (`event_id`),
  KEY `idx_system_operation_created` (`create_time`),
  KEY `idx_system_operation_operator` (`operator_id`,`create_time`),
  KEY `idx_system_operation_module` (`module_code`,`operation_code`,`create_time`),
  KEY `idx_system_operation_target` (`target_type`,`target_id`),
  KEY `idx_system_operation_result` (`result`,`create_time`),
  KEY `idx_system_operation_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统关键操作审计日志';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_component_template` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板ID',
  `template_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定模板编码',
  `template_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `template_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'FIELD_GROUP/FORM_SECTION/SUB_FORM/LIST_COLUMN_GROUP/BUTTON_GROUP',
  `current_version` int NOT NULL DEFAULT '1' COMMENT '当前版本',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_component_template_key` (`template_key`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='版本化UI组件模板';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_component_template_version` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板版本ID',
  `template_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板ID',
  `version` int NOT NULL COMMENT '版本号',
  `snapshot_document` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '不可变模板快照JSON文档',
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SHA-256内容哈希',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本说明',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_component_template_version` (`template_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UI组件模板不可变版本';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_config_hotfix_target` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '热修复目标ID',
  `hotfix_release_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '热修复发布ID',
  `config_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'FORM/LIST',
  `config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置ID',
  `process_version_history_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标流程发布历史ID',
  `pinned_release_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标原始钉定发布ID',
  `pinned_release_version` int NOT NULL COMMENT '目标原始钉定版本号',
  `previous_target_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上一有效热修复目标ID',
  `effective_snapshot_document` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标有效完整快照JSON文档',
  `effective_content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标有效快照SHA-256',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUPERSEDED/ROLLED_BACK',
  `active_slot` tinyint GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'ACTIVE') then 1 else NULL end)) STORED COMMENT '保证同一流程版本只有一个有效目标',
  `activated_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '激活人',
  `activated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '激活时间',
  `rolled_back_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '撤回人',
  `rolled_back_at` datetime DEFAULT NULL COMMENT '撤回时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_hotfix_target_active` (`config_type`,`config_id`,`process_version_history_id`,`active_slot`),
  KEY `idx_ui_hotfix_target_release` (`hotfix_release_id`,`status`),
  KEY `idx_ui_hotfix_target_pinned` (`pinned_release_id`,`status`),
  KEY `idx_ui_hotfix_target_process` (`process_version_history_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UI热修复流程版本目标快照';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_config_release` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发布快照ID',
  `config_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'FORM/LIST',
  `config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表单或列表配置ID',
  `version` int NOT NULL COMMENT '不可变版本号',
  `snapshot_document` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '完整运行时快照JSON文档',
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SHA-256内容哈希',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `active_slot` tinyint GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'ACTIVE') then 1 else NULL end)) STORED COMMENT '保证同一配置只有一个激活版本',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布说明',
  `published_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布人',
  `published_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `release_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STANDARD' COMMENT 'STANDARD/HOTFIX',
  `base_release_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '热修复基线发布ID',
  `risk_level` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SAFE' COMMENT 'SAFE/REVIEW/BLOCKED',
  `rollout_scope` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ACTIVE_AND_FUTURE',
  `patch_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '稳定ID语义补丁JSON文档',
  `override_risk` tinyint NOT NULL DEFAULT '0' COMMENT '是否经授权覆盖REVIEW风险',
  `override_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '风险覆盖原因',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_config_release_version` (`config_type`,`config_id`,`version`),
  UNIQUE KEY `uk_ui_config_release_active` (`config_type`,`config_id`,`active_slot`),
  KEY `idx_ui_config_release_active` (`config_type`,`config_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表单与列表不可变发布快照';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_config_release_audit` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '审计ID',
  `config_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'FORM/LIST',
  `config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置ID',
  `release_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联发布ID',
  `operation` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PREVIEW/PUBLISH_STANDARD/PUBLISH_HOTFIX/ROLLBACK_HOTFIX/OVERRIDE',
  `risk_level` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SAFE/REVIEW/BLOCKED',
  `actor_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人ID',
  `actor_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人名称',
  `reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布或覆盖原因',
  `trace_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务追踪ID',
  `detail_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '影响范围与差异JSON文档',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ui_release_audit_config` (`config_type`,`config_id`,`create_time`),
  KEY `idx_ui_release_audit_release` (`release_id`,`create_time`),
  KEY `idx_ui_release_audit_operation` (`operation`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UI发布与热修复审计日志';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_data_source_definition` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据源ID',
  `source_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定编码',
  `source_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称',
  `source_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ENTITY_QUERY/DICTIONARY/STATIC_OPTIONS/REGISTERED_PROVIDER/INTEGRATION_CONNECTOR/RUNTIME_CONTEXT/STRUCTURED_COMPUTE',
  `provider_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Provider或Connector注册编码',
  `scope_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GLOBAL' COMMENT 'GLOBAL/ENTITY/FORM/LIST',
  `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域资源ID',
  `config_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '受控配置JSON文档',
  `input_schema_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '输入Schema JSON文档',
  `output_schema_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '输出Schema JSON文档',
  `execution_policy_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '分页、超时、缓存和失败策略JSON文档',
  `operations_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '接口服务操作定义JSON数组',
  `revision` int NOT NULL DEFAULT '1' COMMENT '修订号',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_data_source_code` (`source_code`,`deleted`),
  KEY `idx_ui_data_source_catalog` (`source_type`,`scope_type`,`scope_id`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='受控UI数据源目录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_event_binding` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件绑定链ID',
  `owner_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ENTITY/FORM/LIST',
  `owner_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属实体、表单或列表ID',
  `target_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OWNER' COMMENT 'OWNER/FIELD/BUTTON',
  `target_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字段节点或按钮稳定编码，OWNER为空串',
  `event_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '统一业务事件编码',
  `inheritance_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INHERIT' COMMENT 'INHERIT/REPLACE/DISABLE',
  `steps_document` longtext COLLATE utf8mb4_unicode_ci COMMENT 'BEFORE/REPLACE/AFTER有序步骤JSON数组',
  `revision` int NOT NULL DEFAULT '1' COMMENT '草稿修订号',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_event_binding_scope` (`owner_type`,`owner_id`,`target_type`,`target_key`,`event_code`,`deleted`),
  KEY `idx_ui_event_binding_owner` (`owner_type`,`owner_id`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一UI事件绑定链';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ui_extension_definition` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '扩展定义ID',
  `extension_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'FORM/NODE/FIELD/LIST',
  `extension_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '前端或后端稳定注册名',
  `display_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '显示名称',
  `version` int NOT NULL COMMENT '扩展实现版本',
  `snapshot_version` int NOT NULL DEFAULT '1' COMMENT '配置快照协议版本',
  `supported_modes_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '支持的运行模式JSON数组',
  `supported_node_types_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '支持的节点类型JSON数组',
  `supported_bindings_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '支持的绑定类型JSON数组',
  `config_schema_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '配置Schema JSON文档',
  `capabilities_document` longtext COLLATE utf8mb4_unicode_ci COMMENT '扩展能力声明JSON文档',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `revision` int NOT NULL DEFAULT '1' COMMENT '定义修订号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ui_extension_version` (`extension_type`,`extension_key`,`version`,`deleted`),
  KEY `idx_ui_extension_catalog` (`extension_type`,`extension_key`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='受控UI扩展组件清单';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workbench_config` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置名称',
  `config_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置编码',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户ID（为空表示系统默认）',
  `layout_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'GRID' COMMENT '布局类型：GRID/FREE',
  `layout_config` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '布局配置',
  `widgets_config` longtext COLLATE utf8mb4_unicode_ci COMMENT '组件配置列表',
  `is_default` tinyint DEFAULT '0' COMMENT '是否默认',
  `is_system` tinyint DEFAULT '0' COMMENT '是否系统预设',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_code` (`config_code`),
  KEY `idx_user` (`user_id`),
  KEY `idx_default` (`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作台配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workbench_shortcut` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `shortcut_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `shortcut_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '类型：MENU/URL/ENTITY',
  `target_id` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标ID（菜单ID或URL）',
  `icon` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='快捷入口表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workflow_outbox_event` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `topic` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_key` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `aggregate_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `aggregate_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload_document` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT '0',
  `max_retries` int NOT NULL DEFAULT '8',
  `next_retry_time` datetime DEFAULT NULL,
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `processed_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_outbox_topic_event` (`topic`,`event_key`),
  KEY `idx_workflow_outbox_ready`
    (`status`,`next_retry_time`,`create_time`),
  KEY `idx_workflow_outbox_aggregate`
    (`aggregate_type`,`aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='通用事务Outbox事件';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Built-in system entity catalog.
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (315408474423554179,'sys_dict','字典类型','平台系统表目录：sys_dict',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_dict','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (319550136508222867,'sys_menu','菜单权限','平台系统表目录：sys_menu',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_menu','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (429188770482109985,'sys_user','系统用户','平台系统表目录：sys_user',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_user','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (540477245371031459,'sys_role_menu','角色菜单关系','平台系统表目录：sys_role_menu',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_role_menu','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (543966161995942233,'sys_user_role','用户角色关系','平台系统表目录：sys_user_role',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_user_role','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (687980787977785014,'sys_dict_item','字典明细','平台系统表目录：sys_dict_item',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_dict_item','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (695805941702569049,'sys_role','系统角色','平台系统表目录：sys_role',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_role','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (704307435534855320,'sys_user_group','用户组成员关系','平台系统表目录：sys_user_group',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_user_group','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (869084506871349004,'sys_organization','组织部门','平台系统表目录：sys_organization',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_organization','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_definition` (`id`, `entity_code`, `entity_name`, `description`, `process_definition_id`, `status`, `created_by`, `create_time`, `update_time`, `table_name`, `lifecycle_mode`, `storage_mode`, `deleted`, `updated_by`, `team_visibility_enabled`, `team_visibility_level`) VALUES (924525185085388686,'sys_group','用户组','平台系统表目录：sys_group',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_group','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (302983935847141633,315408474423554179,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (463024980901796770,315408474423554179,'deleted','逻辑删除','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (349900140693140919,315408474423554179,'description','描述','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (668047102303117425,315408474423554179,'dict_code','字典编码','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (683413866831122108,315408474423554179,'dict_name','字典名称','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (660514776571799460,315408474423554179,'id','主键ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1041515214726648952,315408474423554179,'sort','排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (835260617712650971,315408474423554179,'status','状态：0-启用 1-禁用','STRING','char(1)',1,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (392848417810309094,315408474423554179,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (343176658012303195,319550136508222867,'breadcrumb','是否显示面包屑：0-否 1-是','STRING','char(1)',1,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'breadcrumb',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (558990094873955059,319550136508222867,'component','组件路径','STRING','varchar(255)',255,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'component',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (175753738162967087,319550136508222867,'create_by','创建者','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,16,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_by',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (950655553882382674,319550136508222867,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,17,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (205676883257074073,319550136508222867,'deleted','是否删除：0-未删除 1-已删除','INTEGER','int',NULL,0,0,NULL,NULL,NULL,15,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (138193370562438585,319550136508222867,'entity_code','关联实体编码，当菜单类型为C且配置了此字段时，点击菜单将跳转到对应实体的数据列表','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,23,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'entity_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (407584881042156273,319550136508222867,'icon','菜单图标','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'icon',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (58574189954682540,319550136508222867,'id','菜单ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1138396868793173976,319550136508222867,'is_cache','是否缓存：0-缓存 1-不缓存','STRING','char(1)',1,0,0,NULL,NULL,NULL,21,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'is_cache',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (776238513957782207,319550136508222867,'is_frame','是否外链：0-否 1-是','STRING','char(1)',1,0,0,NULL,NULL,NULL,20,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'is_frame',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (111764993240200811,319550136508222867,'keep_alive','是否缓存：0-不缓存 1-缓存','STRING','char(1)',1,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'keep_alive',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (119578562633124551,319550136508222867,'list_key','实体列表稳定编码','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,25,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'list_key',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1002235088452601204,319550136508222867,'menu_name','菜单名称','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'menu_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (198475160699508098,319550136508222867,'menu_type','菜单类型：M-目录 C-菜单 F-按钮','STRING','char(1)',1,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'menu_type',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (573255979784869818,319550136508222867,'parent_id','父菜单ID，0为顶级菜单','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (426434390452136951,319550136508222867,'path','路由地址','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'path',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (908207397228493680,319550136508222867,'perm','权限标识，如：system:user:list','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'perm',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (544552761361520586,319550136508222867,'query','路由参数','STRING','varchar(255)',255,0,0,NULL,NULL,NULL,22,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'query',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (158131531689092062,319550136508222867,'remark','备注','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,14,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'remark',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (509277996761328404,319550136508222867,'resource_type','菜单资源类型，ENTITY_LIST 表示动态实体列表','STRING','varchar(30)',30,0,0,NULL,NULL,NULL,24,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'resource_type',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (323682773251959664,319550136508222867,'sort','显示排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (628997735940863871,319550136508222867,'status','状态：0-禁用 1-启用','STRING','char(1)',1,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (658300988401276707,319550136508222867,'update_by','更新者','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,18,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_by',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (531863267937602399,319550136508222867,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,19,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (555037528202687605,319550136508222867,'visible','显示状态：0-隐藏 1-显示','STRING','char(1)',1,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'visible',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (852378610239470992,429188770482109985,'avatar','头像','STRING','varchar(255)',255,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'avatar',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (23541322679314285,429188770482109985,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (361679855788775973,429188770482109985,'deleted','删除标志（0正常 1删除）','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (10101577987500861,429188770482109985,'dept_id','部门ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dept_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (326932484964000364,429188770482109985,'email','邮箱','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'email',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1134826313312664922,429188770482109985,'id','用户ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (745051415557763972,429188770482109985,'nickname','昵称','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'nickname',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1148301704392575944,429188770482109985,'org_id','组织ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'org_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (317101019809934504,429188770482109985,'password','密码','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'password',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1039751570117789095,429188770482109985,'password_reset_required','password_reset_required','BOOLEAN','tinyint',NULL,1,0,NULL,NULL,NULL,14,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'password_reset_required',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (475192751198870445,429188770482109985,'phone','手机号','STRING','varchar(20)',20,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'phone',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (59576534536847010,429188770482109985,'status','状态（0启用 1禁用）','STRING','char(1)',1,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (736587934404834291,429188770482109985,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (184566779876080086,429188770482109985,'username','用户名','STRING','varchar(50)',50,1,1,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'username',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (761157259593380615,540477245371031459,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (480775900607645235,540477245371031459,'id','ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (561900151098621684,540477245371031459,'menu_id','菜单ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'menu_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (919996913633728290,540477245371031459,'role_id','角色ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (979647368025597245,543966161995942233,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (728367720794403925,543966161995942233,'id','ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (75963321277264675,543966161995942233,'role_id','角色ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1082238976243929409,543966161995942233,'user_id','用户ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'user_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (290109203980610604,687980787977785014,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (902908459287977551,687980787977785014,'deleted','逻辑删除','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1112820714573490757,687980787977785014,'dict_code','冗余：字典编码（便于直接查询）','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (845110899938467101,687980787977785014,'dict_id','所属字典ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (85937474892350588,687980787977785014,'id','主键ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (978970058829541345,687980787977785014,'item_code','项编码','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'item_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (458794463769860611,687980787977785014,'item_label','项标签/显示文本','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'item_label',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (293542286014999959,687980787977785014,'item_value','项值','STRING','varchar(200)',200,1,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'item_value',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (160051157971634527,687980787977785014,'parent_id','父项ID，0表示顶级','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1042513892081176326,687980787977785014,'remark','备注','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'remark',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (290497113002851889,687980787977785014,'sort','排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (23540013034190091,687980787977785014,'status','状态：0-启用 1-禁用','STRING','char(1)',1,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (541437194327279494,687980787977785014,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (125004887893384772,695805941702569049,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (50302947400312544,695805941702569049,'deleted','删除标志（0正常 1删除）','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (464945732470729299,695805941702569049,'description','描述','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (39605060466058778,695805941702569049,'id','角色ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (582801574103340315,695805941702569049,'role_code','角色编码','STRING','varchar(50)',50,1,1,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (846037797095146960,695805941702569049,'role_name','角色名称','STRING','varchar(50)',50,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (469870220014538885,695805941702569049,'sort','显示排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (511818241961063640,695805941702569049,'sort_order','排序号','INTEGER','int',NULL,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort_order',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (249397106466806541,695805941702569049,'status','状态（0启用 1禁用）','STRING','char(1)',1,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (990484312511420209,695805941702569049,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (106998218957706418,704307435534855320,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (957745245096590288,704307435534855320,'group_id','组ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'group_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (460851437342127427,704307435534855320,'id','ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1137657801046065632,704307435534855320,'user_id','用户ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'user_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (933634782907475287,869084506871349004,'address','地址','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'address',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (730434704510267566,869084506871349004,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,16,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (79125910432577094,869084506871349004,'deleted','是否删除：0-未删除 1-已删除','INTEGER','int',NULL,0,0,NULL,NULL,NULL,18,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (78753772494339085,869084506871349004,'description','描述','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,15,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (378782243158581413,869084506871349004,'email','邮箱','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'email',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (971589645354457971,869084506871349004,'id','主键ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (978131954777805791,869084506871349004,'leader_id','负责人ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'leader_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (507492914926553510,869084506871349004,'leader_name','负责人名称（冗余）','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'leader_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (538220663657061167,869084506871349004,'level','层级（0为顶级）','INTEGER','int',NULL,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'level',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (49047951767170137,869084506871349004,'org_code','组织编码（唯一）','STRING','varchar(100)',100,1,1,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'org_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (103503443431748381,869084506871349004,'org_name','组织名称','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'org_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (756767862978652736,869084506871349004,'parent_id','父级ID（顶级为0）','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (689704407809393946,869084506871349004,'path','完整路径，如：/0/1/5/10/','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'path',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (34676674937238359,869084506871349004,'phone','联系电话','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'phone',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (799314663885053329,869084506871349004,'sort_order','排序号','INTEGER','int',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort_order',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1046028949155177888,869084506871349004,'status','状态：0-启用，1-禁用','STRING','varchar(10)',10,0,0,NULL,NULL,NULL,14,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (46123295334207598,869084506871349004,'type','类型：org-组织，dept-部门','STRING','varchar(20)',20,1,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'type',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (923457855008240877,869084506871349004,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,17,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (221052828124990695,924525185085388686,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (276971412500557125,924525185085388686,'deleted','删除标志','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (147276096293704262,924525185085388686,'description','描述','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (954151322232061889,924525185085388686,'group_code','组编码','STRING','varchar(50)',50,1,1,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'group_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (324844729973447637,924525185085388686,'group_name','组名称','STRING','varchar(50)',50,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'group_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (1039028238930600397,924525185085388686,'id','组ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (22985037036471449,924525185085388686,'parent_id','父组ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (332274980659204946,924525185085388686,'sort','排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (836913778987896460,924525185085388686,'sort_order','排序号','INTEGER','int',NULL,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort_order',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (370552990036906948,924525185085388686,'status','状态（0启用 1禁用）','STRING','char(1)',1,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO `entity_field` (`id`, `entity_id`, `field_code`, `field_name`, `field_type`, `db_type`, `field_length`, `is_required`, `is_unique`, `default_value`, `options_json`, `validate_rules`, `sort_order`, `is_system`, `is_published`, `editable`, `create_time`, `update_time`, `field_precision`, `db_column_name`, `file_types`, `file_max_size`, `file_max_count`, `ref_entity_type`, `ref_entity_id`, `display_mode`, `ref_field_code`, `field_id`, `dict_type`, `value_storage`, `deleted`) VALUES (335370829110629664,924525185085388686,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);

-- Built-in process action handlers.
INSERT INTO `process_action_definition` (`id`, `action_code`, `display_name`, `description`, `handler_name`, `visibility_scope`, `entity_codes_json`, `enabled`, `created_by`, `create_time`, `update_time`, `deleted`) VALUES ('ede18e6a898811f19f80b2ac4965237c','demoSimpleActionHandler','演示：通用流程动作','开发演示处理器，生产环境默认禁用。','demoSimpleActionHandler','ENTITY','[]',0,'system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);
INSERT INTO `process_action_definition` (`id`, `action_code`, `display_name`, `description`, `handler_name`, `visibility_scope`, `entity_codes_json`, `enabled`, `created_by`, `create_time`, `update_time`, `deleted`) VALUES ('ede18ea6898811f19f80b2ac4965237c','demoTypedActionHandler','演示：类型化流程动作','类型化参数开发演示，生产环境默认禁用。','demoTypedActionHandler','ENTITY','[]',0,'system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);
INSERT INTO `process_action_definition` (`id`, `action_code`, `display_name`, `description`, `handler_name`, `visibility_scope`, `entity_codes_json`, `enabled`, `created_by`, `create_time`, `update_time`, `deleted`) VALUES ('ede18eba898811f19f80b2ac4965237c','demoFailingActionHandler','演示：失败流程动作','失败与重试测试处理器，生产环境默认禁用。','demoFailingActionHandler','ENTITY','[]',0,'system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);
INSERT INTO `process_action_definition` (`id`, `action_code`, `display_name`, `description`, `handler_name`, `visibility_scope`, `entity_codes_json`, `enabled`, `created_by`, `create_time`, `update_time`, `deleted`) VALUES ('flow_action_definition_notify','sendNotificationHandler','发送流程通知','发送待办、完成、撤回等流程通知；推荐使用提交后执行。','sendNotificationHandler','GLOBAL',NULL,1,'system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);

-- Built-in navigation and permission resources.
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_analyze_001','config_migration_menu_001','分析配置','F',NULL,4,'','','config-migration:analyze','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_download_001','config_migration_menu_001','下载发布包','F',NULL,2,'','','config-migration:download','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_export_001','config_migration_menu_001','导出配置','F',NULL,1,'','','config-migration:export','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_import_001','config_migration_menu_001','导入配置','F',NULL,3,'','','config-migration:import','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_list_001','config_migration_menu_001','查看配置迁移','F',NULL,0,'','','config-migration:list','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_menu_001','0','配置迁移','C','FolderOpened',90,'/system/config-migration','system/ConfigMigration','config-migration:list','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_publish_001','config_migration_menu_001','发布配置','F',NULL,5,'','','config-migration:publish','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('config_migration_rollback_001','config_migration_menu_001','回滚配置','F',NULL,6,'','','config-migration:rollback','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('dev_guide_dir','0','定制开发','M','Document',5,'/dev',NULL,NULL,'0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_ui_hotfix_override_permission','0','UI配置热修复风险覆盖','F',NULL,91,NULL,NULL,'entity:ui-config:hotfix:override','0','1','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_ui_hotfix_publish_permission','0','UI配置兼容热修复','F',NULL,90,NULL,NULL,'entity:ui-config:hotfix','0','1','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('extension_list_permission_001','extension_management_menu_001','查看扩展','F',NULL,1,'','','system:extension:list','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('extension_management_menu_001','0','扩展管理','C','Setting',70,'/system/extensions','system/ExtensionManagement',NULL,'0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('extension_test_permission_001','extension_management_menu_001','测试扩展','F',NULL,3,'','','system:extension:test','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('extension_update_permission_001','extension_management_menu_001','维护扩展','F',NULL,2,'','','system:extension:update','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('interface_service_list_001','interface_service_menu_001','查看接口服务','F',NULL,1,'','','system:interface-service:list','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('interface_service_menu_001','0','接口服务','C','Connection',72,'/system/interface-services','system/InterfaceServices','system:interface-service:list','0','0','0','0',NULL,'0','1','统一接口服务与事件绑定管理',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('interface_service_test_001','interface_service_menu_001','测试接口服务','F',NULL,3,'','','system:interface-service:test','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('interface_service_update_001','interface_service_menu_001','维护接口服务','F',NULL,2,'','','system:interface-service:update','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_version_config_list_001','entity_version_management_001','查看数据版本配置','F',NULL,1,'','','entity:version:config:list','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_version_config_update_001','entity_version_management_001','维护数据版本配置','F',NULL,2,'','','entity:version:config:update','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_version_config_publish_001','entity_version_management_001','发布数据版本配置','F',NULL,3,'','','entity:version:config:publish','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('entity_version_management_001','0','数据版本','C','Clock',74,'/system/entity-versions','system/EntityVersionManagement','entity:version:config:list','0','0','0','0',NULL,'0','1','实体数据版本策略、发布与比较',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('flow_action_guide_menu_001','flow_setting_menu_001','流程动作','C','Notebook',1,'/system/flow-action-guide','system/FlowActionGuide','system:flowAction:view','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('flow_setting_menu_001','dev_guide_dir','流程配置','M','Connection',1,'','',NULL,'0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('system_audit_detail_perm_001','system_audit_menu_001','系统日志详情','F','',990,'','','system:audit:detail','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('system_audit_export_perm_001','system_audit_menu_001','系统日志导出','F','',990,'','','system:audit:export','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('system_audit_list_perm_001','system_audit_menu_001','系统日志查询','F','',990,'','','system:audit:list','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('system_audit_menu_001','0','系统日志','C','Document',80,'/system/audit-logs','system/SystemAudit',NULL,'0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('user_manual_dir_001','0','用户手册','M','Notebook',6,'/manual',NULL,NULL,'0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('user_manual_entity_001','user_manual_dir_001','实体配置','C','Document',1,'/manual/entity','manual/EntityManual','user-manual:entity:view','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES ('user_manual_process_001','user_manual_dir_001','流程管理','C','Connection',2,'/manual/process','manual/ProcessManual','user-manual:process:view','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);

-- Fresh-install bootstrap account: admin/admin. The first login must change
-- this temporary password before protected APIs can be used.
INSERT INTO `sys_role` (
  `id`, `role_name`, `role_code`, `description`, `sort_order`,
  `status`, `create_time`, `update_time`, `deleted`
) VALUES (
  '1', '超级管理员', 'super_admin', '系统内置超级管理员角色', 0,
  '0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
);

INSERT INTO `sys_user` (
  `id`, `username`, `nickname`, `password`, `email`, `phone`,
  `avatar`, `status`, `create_time`, `update_time`, `deleted`,
  `org_id`, `dept_id`, `password_reset_required`
) VALUES (
  '1', 'admin', '超级管理员',
  '$2y$10$VPL8vj30niywnU1gYVZGNOiPqQVACc8gG2n81hbOKQlH/.gxI8ZF6',
  'admin@workflow.com', NULL, NULL, '0',
  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, NULL, NULL, 1
);

INSERT INTO `sys_user_role` (
  `id`, `user_id`, `role_id`, `create_time`
) VALUES (
  'bootstrap_admin_role_001', '1', '1', CURRENT_TIMESTAMP
);

INSERT INTO `sys_role_menu` (
  `id`, `role_id`, `menu_id`, `create_time`
)
SELECT
  MD5(CONCAT('1:', `id`)), '1', `id`, CURRENT_TIMESTAMP
FROM `sys_menu`;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
