UPDATE sys_menu
SET parent_id = '400',
    sort = 13,
    update_time = CURRENT_TIMESTAMP
WHERE id = 'list_column_template_menu_001'
  AND deleted = 0;
