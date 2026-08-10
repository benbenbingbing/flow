UPDATE sys_menu
SET remark = '可视化维护用于一次性初始化的列表列模板',
    update_time = CURRENT_TIMESTAMP
WHERE id = 'list_column_template_menu_001'
  AND deleted = 0;
