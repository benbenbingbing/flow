-- 按使用场景重组平台导航。页面路由和权限标识保持不变，只调整菜单归属与名称。
-- 历史全量基线缺少配置管理和流程用户组导航，这里只在固定资源不存在时补齐。

INSERT INTO `sys_menu` (
  `id`,`parent_id`,`menu_name`,`menu_type`,`icon`,`sort`,`path`,`component`,
  `perm`,`status`,`visible`,`is_frame`,`is_cache`,`query`,`keep_alive`,
  `breadcrumb`,`remark`,`deleted`,`create_by`,`create_time`,`update_by`,
  `update_time`,`entity_code`,`resource_type`,`list_key`
)
SELECT
  '300','0','配置管理','M','Box',30,'/entity',NULL,
  NULL,'0','0','0','0',NULL,'0','1','业务配置统一入口',0,
  'migration-v075',CURRENT_TIMESTAMP,'migration-v075',CURRENT_TIMESTAMP,
  NULL,NULL,NULL
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menu` existing_menu WHERE existing_menu.`id` = '300'
);

INSERT INTO `sys_menu` (
  `id`,`parent_id`,`menu_name`,`menu_type`,`icon`,`sort`,`path`,`component`,
  `perm`,`status`,`visible`,`is_frame`,`is_cache`,`query`,`keep_alive`,
  `breadcrumb`,`remark`,`deleted`,`create_by`,`create_time`,`update_by`,
  `update_time`,`entity_code`,`resource_type`,`list_key`
)
SELECT
  '403','300','流程用户组','C','FolderOpened',3,'/system/group','system/Group',
  NULL,'0','0','0','0',NULL,'0','1','流程候选人与办理人用户组',0,
  'migration-v075',CURRENT_TIMESTAMP,'migration-v075',CURRENT_TIMESTAMP,
  NULL,NULL,NULL
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menu` existing_menu WHERE existing_menu.`id` = '403'
);

-- 旧版开发指南来自已归档的菜单种子；补齐全新数据库中缺失的三个稳定入口。
INSERT INTO `sys_menu` (
  `id`,`parent_id`,`menu_name`,`menu_type`,`icon`,`sort`,`path`,`component`,
  `perm`,`status`,`visible`,`is_frame`,`is_cache`,`query`,`keep_alive`,
  `breadcrumb`,`remark`,`deleted`,`create_by`,`create_time`,`update_by`,
  `update_time`,`entity_code`,`resource_type`,`list_key`
)
SELECT
  guide_seed.`id`,
  'dev_guide_dir',
  guide_seed.`menu_name`,
  'C',
  'Document',
  guide_seed.`sort_order`,
  guide_seed.`path`,
  guide_seed.`component`,
  'system:dev:list',
  '0','0','0','0',NULL,'0','1',guide_seed.`remark`,0,
  'migration-v075',CURRENT_TIMESTAMP,'migration-v075',CURRENT_TIMESTAMP,
  NULL,NULL,NULL
FROM (
  SELECT
    'dev_guide_list' AS `id`,
    '列表字段扩展' AS `menu_name`,
    1 AS `sort_order`,
    '/system/dev-guide' AS `path`,
    'system/DevGuide' AS `component`,
    '表单与列表配置扩展开发手册' AS `remark`
  UNION ALL
  SELECT
    'custom_list_guide',
    '自定义列表组件',
    3,
    '/system/custom-list-guide',
    'system/CustomListGuide',
    '自定义列表组件开发手册'
  UNION ALL
  SELECT
    'custom_form_guide',
    '自定义表单组件',
    4,
    '/system/custom-form-guide',
    'system/CustomFormGuide',
    '自定义表单组件开发手册'
) guide_seed
WHERE NOT EXISTS (
  SELECT 1
  FROM `sys_menu` existing_menu
  WHERE existing_menu.`id` = guide_seed.`id`
);

-- 配置类资源统一进入配置管理；保留稳定页面地址，避免已有书签失效。
UPDATE `sys_menu`
SET `parent_id` = '300',
    `menu_name` = '流程用户组',
    `sort` = 3,
    `update_by` = 'migration-v075',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = '403'
  AND `path` = '/system/group'
  AND `menu_type` = 'C'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `parent_id` = '300',
    `sort` = 4,
    `update_by` = 'migration-v075',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'list_column_template_menu_001'
  AND `deleted` = 0;

-- 原“流程配置”本身就是指南容器，复用固定 ID 改名可完整保留角色授权。
UPDATE `sys_menu`
SET `menu_name` = '开发手册',
    `icon` = 'Notebook',
    `sort` = 1,
    `path` = '',
    `component` = NULL,
    `perm` = NULL,
    `remark` = '定制开发相关配置与扩展手册',
    `update_by` = 'migration-v075',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'flow_setting_menu_001'
  AND `parent_id` = 'dev_guide_dir'
  AND `menu_type` = 'M'
  AND `deleted` = 0;

-- 只收拢已知指南，避免误移动运行库中用户自行挂到定制开发下的功能菜单。
UPDATE `sys_menu`
SET `parent_id` = 'flow_setting_menu_001',
    `update_by` = 'migration-v075',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` IN (
    'dev_guide_list',
    'list_field_guide_v2_001',
    'custom_list_guide',
    'custom_form_guide',
    'flow_action_guide_menu_001'
  )
  AND `menu_type` IN ('M', 'C')
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `sort` = CASE `id`
      WHEN 'dev_guide_list' THEN 1
      WHEN 'list_field_guide_v2_001' THEN 2
      WHEN 'custom_list_guide' THEN 3
      WHEN 'custom_form_guide' THEN 4
      WHEN 'flow_action_guide_menu_001' THEN 5
      ELSE `sort`
    END,
    `update_by` = 'migration-v075',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` IN (
    'dev_guide_list',
    'list_field_guide_v2_001',
    'custom_list_guide',
    'custom_form_guide',
    'flow_action_guide_menu_001'
  )
  AND `parent_id` = 'flow_setting_menu_001'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `parent_id` = 'dev_guide_dir',
    `sort` = 2,
    `update_by` = 'migration-v075',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'extension_management_menu_001'
  AND `deleted` = 0;

-- 工作日历属于平台级基础设置；SLA 策略和运行监控收拢到独立目录。
UPDATE `sys_menu`
SET `parent_id` = '400',
    `sort` = 7,
    `update_by` = 'migration-v075',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'work_calendar_menu_001'
  AND `deleted` = 0;

INSERT INTO `sys_menu` (
  `id`,`parent_id`,`menu_name`,`menu_type`,`icon`,`sort`,`path`,`component`,
  `perm`,`status`,`visible`,`is_frame`,`is_cache`,`query`,`keep_alive`,
  `breadcrumb`,`remark`,`deleted`,`create_by`,`create_time`,`update_by`,
  `update_time`,`entity_code`,`resource_type`,`list_key`
) VALUES (
  'sla_management_dir_001','400','SLA管理','M','Timer',8,'',NULL,
  NULL,'0','0','0','0',NULL,'0','1','SLA策略与运行监控',0,
  'migration-v075',CURRENT_TIMESTAMP,'migration-v075',CURRENT_TIMESTAMP,
  NULL,NULL,NULL
);

UPDATE `sys_menu`
SET `parent_id` = 'sla_management_dir_001',
    `sort` = CASE `id`
      WHEN 'task_sla_policy_menu_001' THEN 1
      WHEN 'task_sla_monitor_menu_001' THEN 2
      ELSE `sort`
    END,
    `update_by` = 'migration-v075',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` IN ('task_sla_policy_menu_001', 'task_sla_monitor_menu_001')
  AND `deleted` = 0;

-- 为角色补齐新增或变化后的祖先目录授权；页面及按钮权限仍沿用原菜单 ID。
INSERT INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`)
SELECT DISTINCT
  MD5(CONCAT(source_grant.`role_id`, ':300')),
  source_grant.`role_id`,
  '300',
  CURRENT_TIMESTAMP
FROM `sys_role_menu` source_grant
WHERE source_grant.`menu_id` IN ('403', 'list_column_template_menu_001')
  AND NOT EXISTS (
    SELECT 1
    FROM `sys_role_menu` existing_grant
    WHERE existing_grant.`role_id` = source_grant.`role_id`
      AND existing_grant.`menu_id` = '300'
  );

INSERT INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`)
SELECT DISTINCT
  MD5(CONCAT(source_grant.`role_id`, ':flow_setting_menu_001')),
  source_grant.`role_id`,
  'flow_setting_menu_001',
  CURRENT_TIMESTAMP
FROM `sys_role_menu` source_grant
JOIN `sys_menu` guide_menu
  ON guide_menu.`id` = source_grant.`menu_id`
 AND guide_menu.`parent_id` = 'flow_setting_menu_001'
 AND guide_menu.`menu_type` IN ('M', 'C')
 AND guide_menu.`deleted` = 0
WHERE NOT EXISTS (
  SELECT 1
  FROM `sys_role_menu` existing_grant
  WHERE existing_grant.`role_id` = source_grant.`role_id`
    AND existing_grant.`menu_id` = 'flow_setting_menu_001'
);

INSERT INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`)
SELECT DISTINCT
  MD5(CONCAT(source_grant.`role_id`, ':dev_guide_dir')),
  source_grant.`role_id`,
  'dev_guide_dir',
  CURRENT_TIMESTAMP
FROM `sys_role_menu` source_grant
WHERE source_grant.`menu_id` IN (
    'flow_setting_menu_001', 'extension_management_menu_001'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM `sys_role_menu` existing_grant
    WHERE existing_grant.`role_id` = source_grant.`role_id`
      AND existing_grant.`menu_id` = 'dev_guide_dir'
  );

INSERT INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`)
SELECT DISTINCT
  MD5(CONCAT(source_grant.`role_id`, ':sla_management_dir_001')),
  source_grant.`role_id`,
  'sla_management_dir_001',
  CURRENT_TIMESTAMP
FROM `sys_role_menu` source_grant
WHERE source_grant.`menu_id` IN (
    'task_sla_policy_menu_001', 'task_sla_monitor_menu_001'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM `sys_role_menu` existing_grant
    WHERE existing_grant.`role_id` = source_grant.`role_id`
      AND existing_grant.`menu_id` = 'sla_management_dir_001'
  );

INSERT INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`)
SELECT DISTINCT
  MD5(CONCAT(source_grant.`role_id`, ':400')),
  source_grant.`role_id`,
  '400',
  CURRENT_TIMESTAMP
FROM `sys_role_menu` source_grant
WHERE source_grant.`menu_id` IN (
    'work_calendar_menu_001', 'sla_management_dir_001'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM `sys_role_menu` existing_grant
    WHERE existing_grant.`role_id` = source_grant.`role_id`
      AND existing_grant.`menu_id` = '400'
  );

-- 超级管理员需要拥有本次补建目录和导航，兼容历史角色主键不是固定 1 的环境。
INSERT INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`)
SELECT
  MD5(CONCAT(role_record.`id`, ':', menu_record.`id`)),
  role_record.`id`,
  menu_record.`id`,
  CURRENT_TIMESTAMP
FROM `sys_role` role_record
JOIN `sys_menu` menu_record
  ON menu_record.`id` IN (
    '300',
    '403',
    'list_column_template_menu_001',
    'dev_guide_dir',
    'flow_setting_menu_001',
    'dev_guide_list',
    'list_field_guide_v2_001',
    'custom_list_guide',
    'custom_form_guide',
    'flow_action_guide_menu_001',
    'extension_management_menu_001',
    '400',
    'work_calendar_menu_001',
    'sla_management_dir_001',
    'task_sla_policy_menu_001',
    'task_sla_monitor_menu_001'
  )
WHERE role_record.`role_code` = 'super_admin'
  AND role_record.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1
    FROM `sys_role_menu` existing_grant
    WHERE existing_grant.`role_id` = role_record.`id`
      AND existing_grant.`menu_id` = menu_record.`id`
  );
