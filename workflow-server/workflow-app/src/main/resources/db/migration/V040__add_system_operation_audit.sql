CREATE TABLE IF NOT EXISTS `system_operation_log` (
  `id` varchar(64) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `trace_id` varchar(64) DEFAULT NULL,
  `module_code` varchar(32) NOT NULL,
  `operation_code` varchar(64) NOT NULL,
  `operation_name` varchar(128) NOT NULL,
  `risk_level` varchar(16) NOT NULL,
  `result` varchar(16) NOT NULL,
  `operator_id` varchar(64) DEFAULT NULL,
  `operator_name` varchar(100) DEFAULT NULL,
  `operator_ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `request_method` varchar(16) DEFAULT NULL,
  `request_path` varchar(512) DEFAULT NULL,
  `target_type` varchar(64) DEFAULT NULL,
  `target_id` varchar(128) DEFAULT NULL,
  `target_name` varchar(255) DEFAULT NULL,
  `summary` varchar(1000) DEFAULT NULL,
  `before_json` longtext,
  `after_json` longtext,
  `changed_fields_json` longtext,
  `payload_truncated` tinyint NOT NULL DEFAULT 0,
  `error_code` varchar(100) DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_operation_event` (`event_id`),
  KEY `idx_system_operation_created` (`create_time`),
  KEY `idx_system_operation_operator` (`operator_id`, `create_time`),
  KEY `idx_system_operation_module` (`module_code`, `operation_code`, `create_time`),
  KEY `idx_system_operation_target` (`target_type`, `target_id`),
  KEY `idx_system_operation_result` (`result`, `create_time`),
  KEY `idx_system_operation_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统关键操作审计日志';

CREATE TABLE IF NOT EXISTS `system_audit_outbox` (
  `id` varchar(64) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `payload_json` longtext NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT 0,
  `next_retry_time` datetime DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `processed_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_audit_outbox_event` (`event_id`),
  KEY `idx_system_audit_outbox_ready` (`status`, `next_retry_time`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统审计可靠投递Outbox';

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
    permission_id, COALESCE(@system_manage_id, '0'), permission_name, 'F', '', 990,
    '', '', permission_code, '0', '1', '0', '0', NOW(), NOW(), 0
FROM (
    SELECT 'system_audit_list_perm_001' permission_id,
           '系统日志查询' permission_name,
           'system:audit:list' permission_code
    UNION ALL
    SELECT 'system_audit_detail_perm_001', '系统日志详情', 'system:audit:detail'
    UNION ALL
    SELECT 'system_audit_export_perm_001', '系统日志导出', 'system:audit:export'
) permissions
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu existing
    WHERE existing.id = permissions.permission_id
       OR existing.perm = permissions.permission_code
);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT REPLACE(UUID(), '-', ''), role.id, menu.id, NOW()
FROM sys_role role
JOIN sys_menu menu
  ON menu.perm IN ('system:audit:list', 'system:audit:detail', 'system:audit:export')
WHERE role.role_code IN ('super_admin', 'admin')
  AND role.deleted = 0
  AND menu.deleted = 0;
