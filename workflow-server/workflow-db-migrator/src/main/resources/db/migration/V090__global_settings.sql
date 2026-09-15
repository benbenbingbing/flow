-- 全局设置与个人偏好：值按普通文本保存，格式与业务类型由应用校验。
CREATE TABLE `sys_global_setting` (
  `id` varchar(64) NOT NULL COMMENT '主键ID，由应用分配',
  `scope_type` varchar(16) NOT NULL COMMENT '作用域：SYSTEM-系统 USER-用户',
  `owner_id` varchar(64) NOT NULL COMMENT '归属标识：SYSTEM固定为0，USER为用户ID',
  `setting_key` varchar(160) NOT NULL COMMENT '稳定设置键，采用小写英文点分命名',
  `name` varchar(100) NOT NULL COMMENT '设置名称，简要说明用途',
  `setting_value_type` varchar(16) NOT NULL COMMENT '值格式：BOOLEAN-布尔 NUMBER-数字 STRING-字符串 JSON-对象或数组',
  `setting_value` text NOT NULL COMMENT '设置值文本，JSON格式由应用序列化、解析及校验',
  `remark` varchar(500) DEFAULT NULL COMMENT '详细逻辑说明，包括取值含义、默认行为和生效规则',
  `version` bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，每次更新递增',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人用户ID',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '最近修改人用户ID',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    COMMENT '创建时间',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_global_setting_owner_key`
    (`scope_type`, `owner_id`, `setting_key`),
  CONSTRAINT `chk_sys_global_setting_scope_owner` CHECK (
    (`scope_type` = 'SYSTEM' AND `owner_id` = '0')
    OR (`scope_type` = 'USER' AND CHAR_LENGTH(TRIM(`owner_id`)) > 0
        AND `owner_id` <> '0')
  ),
  CONSTRAINT `chk_sys_global_setting_key` CHECK (
    CHAR_LENGTH(TRIM(`setting_key`)) > 0
  ),
  CONSTRAINT `chk_sys_global_setting_name` CHECK (
    CHAR_LENGTH(TRIM(`name`)) > 0
  ),
  CONSTRAINT `chk_sys_global_setting_value_type` CHECK (
    `setting_value_type` IN ('BOOLEAN', 'NUMBER', 'STRING', 'JSON')
  ),
  CONSTRAINT `chk_sys_global_setting_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='全局设置及用户个人偏好';

-- 首个系统默认值为展开；用户首次切换才创建个人覆盖。
INSERT INTO sys_global_setting (id, scope_type, owner_id, setting_key, name, setting_value_type, setting_value, remark)
VALUES ('setting_entity_field_types', 'SYSTEM', '0', 'ui.entity_design.field_types_collapsed',
        '实体设计字段类型面板收起状态', 'BOOLEAN', 'false',
        'true 表示收起，false 表示展开，默认展开。同一账号在所有实体设计页共用；用户设置优先于系统设置，删除个人记录后恢复继承。切换状态自动保存，不影响实体未保存状态和发布。');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, icon, sort, path, component,
                      perm, status, visible, deleted, create_by, update_by, remark)
VALUES
 ('global_settings_menu', '400', '全局设置', 'C', 'Setting', 15,
  '/system/settings', 'system/GlobalSettings', NULL, '0', '0', 0, 'migration-v090', 'migration-v090',
  '设置系统默认值，用户个人偏好可单独覆盖'),
 ('global_settings_view', 'global_settings_menu', '查看全局设置', 'F', NULL, 1,
  '', '', 'system:setting:view', '0', '0', 0, 'migration-v090', 'migration-v090', '查看系统设置及逻辑说明'),
 ('global_settings_manage', 'global_settings_menu', '维护全局设置', 'F', NULL, 2,
  '', '', 'system:setting:manage', '0', '0', 0, 'migration-v090', 'migration-v090', '修改或恢复系统默认值');

-- 默认仅授权超级管理员；个人偏好接口通过当前登录用户作对象级授权。
INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT MD5(CONCAT(r.id, ':', m.id)), r.id, m.id, CURRENT_TIMESTAMP
FROM sys_role r
JOIN sys_menu m ON m.id IN ('400', 'global_settings_menu', 'global_settings_view', 'global_settings_manage')
WHERE r.role_code = 'super_admin' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu existing WHERE existing.role_id = r.id AND existing.menu_id = m.id);
