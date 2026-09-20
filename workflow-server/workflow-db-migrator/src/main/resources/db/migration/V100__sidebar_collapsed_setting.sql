-- 主菜单折叠复用全局设置表；不为用户预建覆盖，首次手动切换后才记录个人偏好。
INSERT INTO sys_global_setting
    (id, scope_type, owner_id, setting_key, name, setting_value_type, setting_value, remark)
SELECT 'setting_layout_sidebar_collapsed', 'SYSTEM', '0', 'ui.layout.sidebar_collapsed',
       '左侧主菜单收起状态', 'BOOLEAN', 'false',
       'true 表示收起，false 表示展开，默认展开。未保存个人偏好时使用系统设置；用户手动切换后自动保存个人偏好，同一账号跨页面和浏览器共用，用户配置优先于系统配置。仅控制桌面主菜单，移动端导航抽屉不受影响。'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_global_setting
    WHERE scope_type = 'SYSTEM' AND owner_id = '0' AND setting_key = 'ui.layout.sidebar_collapsed'
);
