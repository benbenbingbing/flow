-- 职务管理导航、最小权限和相对组织职务解析器目录。
-- 新权限仅授予超级管理员；解析器先登记为关闭，待数据核对后显式启用。

-- 历史运行库通常已有 400；全量迁移基线没有。这里只补缺失父目录，
-- 已存在记录由 BusinessMigrationPreflight 校验语义，禁止迁移静默覆盖。
INSERT INTO `sys_menu` (
  `id`,`parent_id`,`menu_name`,`menu_type`,`icon`,`sort`,`path`,`component`,
  `perm`,`status`,`visible`,`is_frame`,`is_cache`,`query`,`keep_alive`,
  `breadcrumb`,`remark`,`deleted`,`create_by`,`create_time`,`update_by`,
  `update_time`,`entity_code`,`resource_type`,`list_key`
)
SELECT
  '400','0','系统管理','M','Setting',40,'/system',NULL,
  NULL,'0','0','0','0',NULL,'0','1','系统管理稳定父目录',0,
  'migration-v070',CURRENT_TIMESTAMP,'migration-v070',CURRENT_TIMESTAMP,
  NULL,NULL,NULL
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menu` existing_parent WHERE existing_parent.`id` = '400'
);

INSERT INTO `sys_menu` (
  `id`,`parent_id`,`menu_name`,`menu_type`,`icon`,`sort`,`path`,`component`,
  `perm`,`status`,`visible`,`is_frame`,`is_cache`,`query`,`keep_alive`,
  `breadcrumb`,`remark`,`deleted`,`create_by`,`create_time`,`update_by`,
  `update_time`,`entity_code`,`resource_type`,`list_key`
) VALUES (
  'position_management_menu_001','400','职务管理','C','Briefcase',35,
  '/system/position','system/Position','system:position:view',
  '0','0','0','0',NULL,'0','1','全局职务定义与组织任职管理',0,
  'migration-v070',CURRENT_TIMESTAMP,'migration-v070',CURRENT_TIMESTAMP,
  NULL,NULL,NULL
);

INSERT INTO `sys_menu` (
  `id`,`parent_id`,`menu_name`,`menu_type`,`icon`,`sort`,`path`,`component`,
  `perm`,`status`,`visible`,`is_frame`,`is_cache`,`query`,`keep_alive`,
  `breadcrumb`,`remark`,`deleted`,`create_by`,`create_time`,`update_by`,
  `update_time`,`entity_code`,`resource_type`,`list_key`
) VALUES
  ('position_manage_permission_001','position_management_menu_001',
   '维护职务定义','F',NULL,1,'','', 'system:position:manage',
   '0','0','0','0',NULL,'0','1','新增、编辑、启停职务定义',0,
   'migration-v070',CURRENT_TIMESTAMP,'migration-v070',CURRENT_TIMESTAMP,
   NULL,NULL,NULL),
  ('position_assign_permission_001','position_management_menu_001',
   '维护组织任职','F',NULL,2,'','', 'system:position:assign',
   '0','0','0','0',NULL,'0','1','任命、转任、撤销和调整任职有效期',0,
   'migration-v070',CURRENT_TIMESTAMP,'migration-v070',CURRENT_TIMESTAMP,
   NULL,NULL,NULL);

INSERT INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`)
SELECT MD5(CONCAT(role_record.`id`, ':', menu_record.`id`)),
       role_record.`id`, menu_record.`id`, CURRENT_TIMESTAMP
FROM `sys_role` role_record
JOIN `sys_menu` menu_record
  ON menu_record.`id` IN (
    '400',
    'position_management_menu_001',
    'position_manage_permission_001',
    'position_assign_permission_001'
  )
WHERE role_record.`role_code` = 'super_admin'
  AND role_record.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1
    FROM `sys_role_menu` existing_grant
    WHERE existing_grant.`role_id` = role_record.`id`
      AND existing_grant.`menu_id` = menu_record.`id`
  );

INSERT INTO `process_person_resolver_definition` (
  `id`,`resolver_code`,`display_name`,`description`,`bean_name`,
  `implementation_version`,`contract_version`,`supported_usages_document`,
  `extra_param_schema_document`,`dynamic_extra_params`,`enabled`,`revision`,
  `create_time`,`update_time`,`deleted`
) VALUES (
  'person_resolver_relative_position_001',
  'relativeOrgPosition',
  '相对组织职务',
  '按流程发起人冻结组织链查询节点激活时的有效职务任职人',
  'relativeOrgPositionPersonResolver',
  1,
  1,
  '["ASSIGNEE","CANDIDATE","MULTI_INSTANCE"]',
  '{"type":"object","additionalProperties":false,"required":["schemaVersion","subject","anchor","positionCode","hierarchy","multipleMatchPolicy"],"properties":{"schemaVersion":{"const":1},"subject":{"const":"PROCESS_INITIATOR"},"anchor":{"enum":["DEPARTMENT","ORGANIZATION"]},"positionCode":{"type":"string","minLength":1,"maxLength":100},"hierarchy":{"type":"object"},"multipleMatchPolicy":{"enum":["ERROR","PRIMARY_OR_ERROR","ALL"]}}}',
  0,
  0,
  1,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  0
);
