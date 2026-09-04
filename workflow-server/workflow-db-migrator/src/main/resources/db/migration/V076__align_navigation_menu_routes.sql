-- 菜单层级由 V075 完成；本迁移仅统一页面路由的模块前缀。
-- 前端保留旧地址重定向，因此历史书签仍可继续使用。

UPDATE `sys_menu`
SET `path` = '/config',
    `update_by` = 'migration-v076',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = '300'
  AND `menu_type` = 'M'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `path` = '/config/process-user-groups',
    `update_by` = 'migration-v076',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = '403'
  AND `menu_type` = 'C'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `path` = '/config/list-column-templates',
    `update_by` = 'migration-v076',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'list_column_template_menu_001'
  AND `menu_type` = 'C'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `path` = '/dev/manual',
    `update_by` = 'migration-v076',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'flow_setting_menu_001'
  AND `menu_type` = 'M'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `path` = CASE `id`
      WHEN 'dev_guide_list' THEN '/dev/manual/list-field-extension'
      WHEN 'list_field_guide_v2_001' THEN '/dev/manual/list-field-extension-v2'
      WHEN 'custom_list_guide' THEN '/dev/manual/custom-list'
      WHEN 'custom_form_guide' THEN '/dev/manual/custom-form'
      WHEN 'flow_action_guide_menu_001' THEN '/dev/manual/flow-actions'
      ELSE `path`
    END,
    `update_by` = 'migration-v076',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` IN (
    'dev_guide_list',
    'list_field_guide_v2_001',
    'custom_list_guide',
    'custom_form_guide',
    'flow_action_guide_menu_001'
  )
  AND `menu_type` = 'C'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `path` = '/dev/extensions',
    `update_by` = 'migration-v076',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'extension_management_menu_001'
  AND `menu_type` = 'C'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `path` = '/system/sla',
    `update_by` = 'migration-v076',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 'sla_management_dir_001'
  AND `menu_type` = 'M'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `path` = CASE `id`
      WHEN 'task_sla_policy_menu_001' THEN '/system/sla/policies'
      WHEN 'task_sla_monitor_menu_001' THEN '/system/sla/monitor'
      ELSE `path`
    END,
    `update_by` = 'migration-v076',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` IN ('task_sla_policy_menu_001', 'task_sla_monitor_menu_001')
  AND `menu_type` = 'C'
  AND `deleted` = 0;
