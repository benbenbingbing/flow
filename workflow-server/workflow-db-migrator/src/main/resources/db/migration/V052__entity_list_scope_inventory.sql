CREATE TABLE `entity_list_scope_inventory` (
  `id` varchar(64) NOT NULL COMMENT '盘点记录ID',
  `list_id` varchar(64) NOT NULL COMMENT '列表配置ID',
  `entity_id` varchar(64) NOT NULL COMMENT '实体定义ID',
  `entity_code` varchar(100) NOT NULL COMMENT '实体编码',
  `list_key` varchar(100) NOT NULL COMMENT '列表标识',
  `list_name` varchar(200) DEFAULT NULL COMMENT '列表名称',
  `detected_policy` varchar(32) NOT NULL COMMENT '盘点时检测到的未绑定策略',
  `detected_enforcement` varchar(16) NOT NULL COMMENT '盘点时检测到的执行阶段',
  `owner_id` varchar(64) DEFAULT NULL COMMENT '治理责任人ID',
  `owner_name` varchar(100) DEFAULT NULL COMMENT '治理责任人名称',
  `processing_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'UNASSIGNED/PENDING/CONFIRMED/EXCEPTION',
  `selected_policy` varchar(32) DEFAULT NULL COMMENT '管理员确认后的策略',
  `confirmation_reason` varchar(500) DEFAULT NULL COMMENT '确认原因',
  `confirmed_by` varchar(64) DEFAULT NULL COMMENT '确认人ID',
  `confirmed_at` datetime DEFAULT NULL COMMENT '确认时间',
  `exception_note` varchar(500) DEFAULT NULL COMMENT '异常说明',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_list_scope_inventory_list` (`list_id`),
  KEY `idx_entity_list_scope_inventory_status` (`processing_status`, `update_time`),
  KEY `idx_entity_list_scope_inventory_entity` (`entity_code`, `list_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存量列表数据范围安全策略盘点';

INSERT INTO `entity_list_scope_inventory` (
  id, list_id, entity_id, entity_code, list_key, list_name,
  detected_policy, detected_enforcement, processing_status,
  create_time, update_time
)
SELECT
  config.id, config.id, config.entity_id, config.entity_code,
  config.list_key, config.list_name,
  COALESCE(config.unbound_scope_policy, 'EXPLICIT_ALL'),
  COALESCE(config.scope_enforcement_mode, 'OBSERVE'),
  'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM entity_list_config config
WHERE config.deleted = 0
  AND COALESCE(config.scope_default_confirmed, 0) = 0
  AND COALESCE(config.scope_enforcement_mode, 'OBSERVE') = 'OBSERVE';

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, query, keep_alive, breadcrumb,
  remark, deleted, create_by, create_time, update_by, update_time,
  entity_code, resource_type, list_key
) VALUES (
  'entity_scope_inventory_menu_001', '0', '数据范围盘点', 'C', 'Lock', 76,
  '/system/entity-scope-inventory', 'system/EntityListScopeInventory',
  'entity:list-scope:inventory', '0', '0', '0', '0', NULL, '0', '1',
  '集中确认存量列表的安全默认策略、责任人与处理状态', 0, NULL,
  CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, NULL, NULL, NULL
);

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, query, keep_alive, breadcrumb,
  remark, deleted, create_by, create_time, update_by, update_time,
  entity_code, resource_type, list_key
) VALUES (
  'entity_scope_explicit_all_permission_001', 'entity_scope_inventory_menu_001',
  '允许全量数据放行', 'F', NULL, 1, '', '',
  'entity:list-scope:explicit-all', '0', '0', '0', '0', NULL, '0', '1',
  '允许把未绑定数据范围规则显式设置为全量可见的高风险权限', 0, NULL,
  CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, NULL, NULL, NULL
);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT DISTINCT
  MD5(CONCAT(source_grant.role_id, ':entity_scope_inventory_menu_001')),
  source_grant.role_id, 'entity_scope_inventory_menu_001', CURRENT_TIMESTAMP
FROM sys_role_menu source_grant
JOIN sys_menu source_menu ON source_menu.id = source_grant.menu_id
WHERE source_menu.perm = 'entity:definition:manage'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing_grant
    WHERE existing_grant.role_id = source_grant.role_id
      AND existing_grant.menu_id = 'entity_scope_inventory_menu_001'
  );

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT
  MD5(CONCAT(role.id, ':entity_scope_explicit_all_permission_001')),
  role.id, 'entity_scope_explicit_all_permission_001', CURRENT_TIMESTAMP
FROM sys_role role
WHERE role.role_code = 'super_admin'
  AND role.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing_grant
    WHERE existing_grant.role_id = role.id
      AND existing_grant.menu_id = 'entity_scope_explicit_all_permission_001'
  );
