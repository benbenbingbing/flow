CREATE TABLE IF NOT EXISTS `entity_version_config` (
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

CREATE TABLE IF NOT EXISTS `entity_version_scenario` (
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

CREATE TABLE IF NOT EXISTS `entity_version_step` (
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

CREATE TABLE IF NOT EXISTS `entity_change_target_binding` (
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

CREATE TABLE IF NOT EXISTS `entity_version_config_release` (
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

CREATE TABLE IF NOT EXISTS `entity_change_target_instance` (
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

CREATE TABLE IF NOT EXISTS `entity_mutation_receipt` (
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

CREATE TABLE IF NOT EXISTS `entity_record_version` (
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

CREATE TABLE IF NOT EXISTS `ui_event_binding` (
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

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`) VALUES
('interface_service_menu_001','0','接口服务','C','Connection',72,'/system/interface-services','system/InterfaceServices','system:interface-service:list','0','0','0','0',NULL,'0','1','统一接口服务与事件绑定管理',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL),
('interface_service_list_001','interface_service_menu_001','查看接口服务','F',NULL,1,'','','system:interface-service:list','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL),
('interface_service_update_001','interface_service_menu_001','维护接口服务','F',NULL,2,'','','system:interface-service:update','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL),
('interface_service_test_001','interface_service_menu_001','测试接口服务','F',NULL,3,'','','system:interface-service:test','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL),
('entity_version_management_001','0','数据版本','C','Clock',74,'/system/entity-versions','system/EntityVersionManagement','entity:version:config:list','0','0','0','0',NULL,'0','1','实体数据版本策略、发布与比较',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL),
('entity_version_config_list_001','entity_version_management_001','查看数据版本配置','F',NULL,1,'','','entity:version:config:list','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL),
('entity_version_config_update_001','entity_version_management_001','维护数据版本配置','F',NULL,2,'','','entity:version:config:update','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL),
('entity_version_config_publish_001','entity_version_management_001','发布数据版本配置','F',NULL,3,'','','entity:version:config:publish','0','0','0','0',NULL,'0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL);

UPDATE `sys_menu`
SET `menu_name` = '接口服务',
    `remark` = '统一接口服务与事件绑定管理',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'interface_service_menu_001';

UPDATE `sys_menu`
SET `menu_name` = '查看接口服务',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'interface_service_list_001';

UPDATE `sys_menu`
SET `menu_name` = '维护接口服务',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'interface_service_update_001';

UPDATE `sys_menu`
SET `menu_name` = '测试接口服务',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'interface_service_test_001';

UPDATE `sys_menu`
SET `menu_name` = '数据版本',
    `remark` = '实体数据版本策略、发布与比较',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'entity_version_management_001';

UPDATE `sys_menu`
SET `menu_name` = '查看数据版本配置',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'entity_version_config_list_001';

UPDATE `sys_menu`
SET `menu_name` = '维护数据版本配置',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'entity_version_config_update_001';

UPDATE `sys_menu`
SET `menu_name` = '发布数据版本配置',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'entity_version_config_publish_001';

INSERT IGNORE INTO `sys_role_menu` (
  `id`, `role_id`, `menu_id`, `create_time`
)
SELECT
  MD5(CONCAT('1:', `id`)), '1', `id`, CURRENT_TIMESTAMP
FROM `sys_menu`
WHERE `id` IN (
  'interface_service_menu_001',
  'interface_service_list_001',
  'interface_service_update_001',
  'interface_service_test_001',
  'entity_version_management_001',
  'entity_version_config_list_001',
  'entity_version_config_update_001',
  'entity_version_config_publish_001'
);
