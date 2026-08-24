CREATE TABLE `process_assignee_incident` (
  `id` varchar(64) NOT NULL,
  `process_config_id` varchar(64) DEFAULT NULL,
  `process_definition_id` varchar(128) DEFAULT NULL,
  `process_instance_id` varchar(128) DEFAULT NULL,
  `task_id` varchar(128) DEFAULT NULL,
  `node_id` varchar(200) NOT NULL,
  `node_name` varchar(300) DEFAULT NULL,
  `policy` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL COMMENT 'OPEN/RETRY_SCHEDULED/RESOLVED/TERMINATED/MANUAL_REQUIRED',
  `empty_reason_code` varchar(100) NOT NULL,
  `empty_reason_message` varchar(1000) DEFAULT NULL,
  `resolver_code` varchar(200) DEFAULT NULL,
  `resolver_extra_params_json` longtext DEFAULT NULL,
  `fallback_user` varchar(100) DEFAULT NULL,
  `fallback_group` varchar(100) DEFAULT NULL,
  `responsibility_owner` varchar(100) NOT NULL,
  `retry_count` int NOT NULL DEFAULT 0,
  `max_retries` int NOT NULL DEFAULT 0,
  `initial_delay_seconds` int DEFAULT NULL,
  `backoff_multiplier` decimal(8,3) DEFAULT NULL,
  `next_retry_at` datetime DEFAULT NULL,
  `resolution_action` varchar(64) DEFAULT NULL,
  `resolved_by` varchar(100) DEFAULT NULL,
  `resolved_at` datetime DEFAULT NULL,
  `detail_json` longtext DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `open_slot` varchar(128) GENERATED ALWAYS AS (
    CASE WHEN status IN ('OPEN', 'RETRY_SCHEDULED', 'MANUAL_REQUIRED')
      THEN CONCAT(COALESCE(task_id, process_instance_id, 'NO_INSTANCE'), ':', node_id)
      ELSE NULL END
  ) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_assignee_incident_open` (`open_slot`),
  KEY `idx_process_assignee_incident_status` (`status`, `next_retry_at`),
  KEY `idx_process_assignee_incident_instance` (`process_instance_id`, `create_time`),
  KEY `idx_process_assignee_incident_owner` (`responsibility_owner`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空办理人阻断事件';

CREATE TABLE `process_assignee_incident_action` (
  `id` varchar(64) NOT NULL,
  `incident_id` varchar(64) NOT NULL,
  `request_id` varchar(128) NOT NULL COMMENT '幂等请求ID',
  `action_type` varchar(64) NOT NULL,
  `status` varchar(24) NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED',
  `operator` varchar(100) NOT NULL,
  `request_json` longtext DEFAULT NULL,
  `result_json` longtext DEFAULT NULL,
  `error_message` varchar(1500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assignee_incident_action_request` (`incident_id`, `request_id`),
  KEY `idx_assignee_incident_action` (`incident_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空办理人事件处置审计';

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, query, keep_alive, breadcrumb,
  remark, deleted, create_by, create_time, update_by, update_time,
  entity_code, resource_type, list_key
) VALUES (
  'assignee_incident_menu_001', '0', '空办理人事件', 'C', 'Warning', 78,
  '/system/assignee-incidents', 'system/AssigneeIncidentManagement',
  'process:assignee-incident:list', '0', '0', '0', '0', NULL, '0', '1',
  '查看空办理人告警、重试和人工恢复记录', 0, NULL,
  CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, NULL, NULL, NULL
);

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, query, keep_alive, breadcrumb,
  remark, deleted, create_by, create_time, update_by, update_time,
  entity_code, resource_type, list_key
) VALUES (
  'assignee_incident_handle_001', 'assignee_incident_menu_001',
  '处置空办理人事件', 'F', NULL, 1, '', '',
  'process:assignee-incident:handle', '0', '0', '0', '0', NULL, '0', '1',
  '补充办理人、重试解析器、转兜底组或终止实例', 0, NULL,
  CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, NULL, NULL, NULL
);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT DISTINCT MD5(CONCAT(grant_row.role_id, ':assignee_incident_menu_001')),
       grant_row.role_id, 'assignee_incident_menu_001', CURRENT_TIMESTAMP
FROM sys_role_menu grant_row
JOIN sys_menu source_menu ON source_menu.id = grant_row.menu_id
WHERE source_menu.perm IN ('process:definition:list', 'process:definition:manage')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = grant_row.role_id
      AND existing.menu_id = 'assignee_incident_menu_001'
  );

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT DISTINCT MD5(CONCAT(grant_row.role_id, ':assignee_incident_handle_001')),
       grant_row.role_id, 'assignee_incident_handle_001', CURRENT_TIMESTAMP
FROM sys_role_menu grant_row
JOIN sys_menu source_menu ON source_menu.id = grant_row.menu_id
WHERE source_menu.perm IN ('process:definition:publish', 'process:definition:manage')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = grant_row.role_id
      AND existing.menu_id = 'assignee_incident_handle_001'
  );
