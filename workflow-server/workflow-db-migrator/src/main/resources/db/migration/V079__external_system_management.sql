-- 外部系统基础资料及其可扩展参数。这里只管理对接元数据，具体接口仍由业务扩展实现。

CREATE TABLE `sys_external_system` (
  `id` varchar(64) NOT NULL COMMENT '外部系统ID',
  `system_name` varchar(100) NOT NULL COMMENT '外部系统名称',
  `system_code` varchar(100) NOT NULL COMMENT '外部系统稳定编码，删除后也不得复用',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态：0-启用 1-禁用',
  `address` varchar(500) NOT NULL COMMENT '外部系统地址',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `version` bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    COMMENT '创建时间',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_external_system_code` (`system_code`),
  KEY `idx_sys_external_system_status_name`
    (`deleted`,`status`,`system_name`),
  CONSTRAINT `chk_sys_external_system_status`
    CHECK (`status` IN ('0','1')),
  CONSTRAINT `chk_sys_external_system_deleted`
    CHECK (`deleted` IN (0,1)),
  CONSTRAINT `chk_sys_external_system_version`
    CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='外部系统基础信息';

CREATE TABLE `sys_external_system_parameter` (
  `id` varchar(64) NOT NULL COMMENT '参数ID',
  `external_system_id` varchar(64) NOT NULL COMMENT '外部系统ID',
  `parameter_name_zh` varchar(100) NOT NULL COMMENT '参数中文名',
  `parameter_name_en` varchar(100) NOT NULL COMMENT '参数英文名',
  `parameter_value` longtext NOT NULL
    COMMENT '普通配置参数值，敏感凭据应使用受控密钥存储',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    COMMENT '创建时间',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-删除',
  `active_parameter_name_en` varchar(100)
    GENERATED ALWAYS AS (
      CASE WHEN `deleted` = 0 THEN `parameter_name_en` ELSE NULL END
    ) STORED COMMENT '仅活动参数参与英文名唯一约束',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_external_system_parameter_active_name`
    (`external_system_id`,`active_parameter_name_en`),
  KEY `idx_sys_external_system_parameter_order`
    (`external_system_id`,`deleted`,`sort_order`,`id`),
  CONSTRAINT `fk_sys_external_system_parameter_system`
    FOREIGN KEY (`external_system_id`) REFERENCES `sys_external_system` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_sys_external_system_parameter_deleted`
    CHECK (`deleted` IN (0,1)),
  CONSTRAINT `chk_sys_external_system_parameter_sort`
    CHECK (`sort_order` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='外部系统扩展参数';

INSERT INTO `sys_menu` (
  `id`,`parent_id`,`menu_name`,`menu_type`,`icon`,`sort`,`path`,`component`,
  `perm`,`status`,`visible`,`is_frame`,`is_cache`,`query`,`keep_alive`,
  `breadcrumb`,`remark`,`deleted`,`create_by`,`create_time`,`update_by`,
  `update_time`,`entity_code`,`resource_type`,`list_key`
) VALUES
  (
    'external_system_menu_001','400','外部系统','C','Connection',14,
    '/system/external-systems','system/ExternalSystem',
    NULL,'0','0','0','0',NULL,'0','1',
    '维护外部系统基础资料和对接参数；具体接口由业务扩展实现',0,
    'migration-v079',CURRENT_TIMESTAMP,'migration-v079',CURRENT_TIMESTAMP,
    NULL,NULL,NULL
  ),
  (
    'external_system_view_permission_001','external_system_menu_001',
    '查看外部系统','F',NULL,1,'','',
    'system:external-system:view','0','0','0','0',NULL,'0','1',
    '查看外部系统基本信息列表',0,
    'migration-v079',CURRENT_TIMESTAMP,'migration-v079',CURRENT_TIMESTAMP,
    NULL,NULL,NULL
  ),
  (
    'external_system_manage_permission_001','external_system_menu_001',
    '维护外部系统','F',NULL,2,'','',
    'system:external-system:manage','0','0','0','0',NULL,'0','1',
    '新增、编辑、启停和删除外部系统及其参数',0,
    'migration-v079',CURRENT_TIMESTAMP,'migration-v079',CURRENT_TIMESTAMP,
    NULL,NULL,NULL
  );

-- 新管理能力默认只授予超级管理员，并补齐系统管理父目录授权。
INSERT INTO `sys_role_menu` (`id`,`role_id`,`menu_id`,`create_time`)
SELECT
  MD5(CONCAT(role_record.`id`, ':', menu_record.`id`)),
  role_record.`id`,
  menu_record.`id`,
  CURRENT_TIMESTAMP
FROM `sys_role` role_record
JOIN `sys_menu` menu_record
  ON menu_record.`id` IN (
    '400',
    'external_system_menu_001',
    'external_system_view_permission_001',
    'external_system_manage_permission_001'
  )
WHERE role_record.`role_code` = 'super_admin'
  AND role_record.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1
    FROM `sys_role_menu` existing_grant
    WHERE existing_grant.`role_id` = role_record.`id`
      AND existing_grant.`menu_id` = menu_record.`id`
  );
