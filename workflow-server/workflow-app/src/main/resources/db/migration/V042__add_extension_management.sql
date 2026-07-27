-- 统一扩展管理：人员解析器目录与系统管理直属菜单。

CREATE TABLE IF NOT EXISTS process_person_resolver_definition (
  id varchar(64) NOT NULL COMMENT '人员解析器定义ID',
  resolver_code varchar(100) NOT NULL COMMENT '稳定解析器编码',
  display_name varchar(200) NOT NULL COMMENT '中文名称',
  description varchar(1000) DEFAULT NULL COMMENT '用途说明',
  bean_name varchar(200) NOT NULL COMMENT 'Spring Bean名称',
  implementation_version int NOT NULL DEFAULT 1 COMMENT '实现版本',
  contract_version int NOT NULL DEFAULT 1 COMMENT '平台契约版本',
  supported_usages_document text DEFAULT NULL COMMENT 'ASSIGNEE/CANDIDATE/MULTI_INSTANCE/CC',
  extra_param_schema_document longtext DEFAULT NULL COMMENT 'extraParams Schema',
  dynamic_extra_params tinyint NOT NULL DEFAULT 0 COMMENT '是否允许动态extraParams',
  enabled tinyint NOT NULL DEFAULT 0 COMMENT '是否允许在流程配置中选择',
  revision int NOT NULL DEFAULT 1 COMMENT '目录修订号',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_person_resolver_code (resolver_code, deleted),
  KEY idx_person_resolver_enabled (enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='受控人员解析器目录';

SET @system_manage_id = (
    SELECT id
    FROM sys_menu
    WHERE menu_name = '系统管理'
      AND menu_type = 'M'
      AND deleted = 0
    ORDER BY create_time
    LIMIT 1
);

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, create_time, update_time, deleted
)
SELECT
    'extension_management_menu_001', COALESCE(@system_manage_id, '0'),
    '扩展管理', 'C', 'Setting', 70, '/system/extensions',
    'system/ExtensionManagement', NULL,
    '0', '0', '0', '0', NOW(), NOW(), 0
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu
    WHERE id = 'extension_management_menu_001'
       OR path = '/system/extensions'
);

UPDATE sys_menu
SET parent_id = COALESCE(@system_manage_id, '0'),
    menu_name = '扩展管理',
    menu_type = 'C',
    icon = 'Setting',
    sort = 70,
    path = '/system/extensions',
    component = 'system/ExtensionManagement',
    perm = NULL,
    status = '0',
    visible = '0',
    deleted = 0,
    update_time = NOW()
WHERE id = 'extension_management_menu_001'
   OR path = '/system/extensions';

SET @extension_menu_id = (
    SELECT id
    FROM sys_menu
    WHERE id = 'extension_management_menu_001'
       OR path = '/system/extensions'
    ORDER BY CASE WHEN id = 'extension_management_menu_001' THEN 0 ELSE 1 END
    LIMIT 1
);

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, create_time, update_time, deleted
)
SELECT permission.id, @extension_menu_id, permission.menu_name, 'F', NULL,
       permission.sort, '', '', permission.perm,
       '0', '0', '0', '0', NOW(), NOW(), 0
FROM (
    SELECT 'extension_list_permission_001' id, '查看扩展' menu_name,
           1 sort, 'system:extension:list' perm
    UNION ALL
    SELECT 'extension_update_permission_001', '维护扩展',
           2, 'system:extension:update'
    UNION ALL
    SELECT 'extension_test_permission_001', '测试扩展',
           3, 'system:extension:test'
) permission
WHERE @extension_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.perm = permission.perm
        AND existing.deleted = 0
  );

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT REPLACE(UUID(), '-', ''), role.id, menu.id, NOW()
FROM sys_role role
JOIN sys_menu menu
  ON menu.id COLLATE utf8mb4_unicode_ci
       = @extension_menu_id COLLATE utf8mb4_unicode_ci
  OR menu.perm IN (
      'system:extension:list',
      'system:extension:update',
      'system:extension:test'
  )
WHERE role.role_code IN ('super_admin', 'admin')
  AND role.deleted = 0
  AND menu.deleted = 0;
