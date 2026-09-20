-- 标签模式复用设置表，仅初始化系统默认值，不预建用户覆盖或持久化业务页面内容。
INSERT INTO sys_global_setting
    (id, scope_type, owner_id, setting_key, name, setting_value_type, setting_value, remark)
SELECT 'setting_layout_tabs_enabled', 'SYSTEM', '0', 'ui.layout.tabs_enabled',
       '启用顶部多标签页', 'BOOLEAN', 'false',
       'true 表示在顶部面包屑位置显示页面选项卡，false 表示单页模式并显示面包屑，默认关闭。用户可在右上角用户菜单切换，个人配置优先于系统配置。切换标签保留页面状态，关闭未保存页面时确认；已打开标签和业务输入仅保留在当前会话内，刷新页面后不恢复。偏好保存失败不影响本次模式切换。'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_global_setting
    WHERE scope_type = 'SYSTEM' AND owner_id = '0' AND setting_key = 'ui.layout.tabs_enabled'
);
