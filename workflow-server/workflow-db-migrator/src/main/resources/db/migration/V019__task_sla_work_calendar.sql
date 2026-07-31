CREATE TABLE `work_calendar` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `calendar_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `calendar_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `timezone_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version` int NOT NULL DEFAULT '1',
  `default_flag` tinyint NOT NULL DEFAULT '0',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `effective_from` date DEFAULT NULL,
  `effective_to` date DEFAULT NULL,
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_calendar_code_version`
    (`calendar_code`,`version`,`deleted`),
  KEY `idx_work_calendar_status` (`status`,`default_flag`,`deleted`),
  CONSTRAINT `chk_work_calendar_version` CHECK (`version` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='工作日历';

CREATE TABLE `work_calendar_period` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `calendar_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `day_of_week` tinyint NOT NULL,
  `start_minute` smallint NOT NULL,
  `end_minute` smallint NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_calendar_period`
    (`calendar_id`,`day_of_week`,`start_minute`,`end_minute`),
  KEY `idx_work_calendar_period_day` (`calendar_id`,`day_of_week`,`sort_order`),
  CONSTRAINT `chk_work_calendar_period_day`
    CHECK (`day_of_week` BETWEEN 1 AND 7),
  CONSTRAINT `chk_work_calendar_period_minutes`
    CHECK (`start_minute` >= 0 AND `end_minute` <= 1440
      AND `start_minute` < `end_minute`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='工作日历每周时段';

CREATE TABLE `work_calendar_exception` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `calendar_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `exception_date` date NOT NULL,
  `exception_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `exception_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_calendar_exception`
    (`calendar_id`,`exception_date`),
  KEY `idx_work_calendar_exception_date`
    (`calendar_id`,`exception_date`,`exception_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='工作日历特殊日期';

CREATE TABLE `work_calendar_exception_period` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `exception_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `start_minute` smallint NOT NULL,
  `end_minute` smallint NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_calendar_exception_period`
    (`exception_id`,`start_minute`,`end_minute`),
  KEY `idx_work_calendar_exception_period`
    (`exception_id`,`sort_order`),
  CONSTRAINT `chk_work_calendar_exception_period_minutes`
    CHECK (`start_minute` >= 0 AND `end_minute` <= 1440
      AND `start_minute` < `end_minute`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='特殊工作日时段';

CREATE TABLE `work_calendar_binding` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `calendar_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `priority` int NOT NULL DEFAULT '0',
  `effective_from` date DEFAULT NULL,
  `effective_to` date DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_work_calendar_binding_scope`
    (`scope_type`,`scope_key`,`status`,`deleted`,`priority`),
  KEY `idx_work_calendar_binding_calendar` (`calendar_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='工作日历作用域绑定';

CREATE TABLE `task_sla_policy` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `policy_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `policy_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version` int NOT NULL DEFAULT '1',
  `response_target_minutes` int DEFAULT NULL,
  `completion_target_minutes` int NOT NULL,
  `response_time_basis` varchar(20) COLLATE utf8mb4_unicode_ci
    NOT NULL DEFAULT 'WORKING_TIME',
  `completion_time_basis` varchar(20) COLLATE utf8mb4_unicode_ci
    NOT NULL DEFAULT 'WORKING_TIME',
  `allow_manual_pause` tinyint NOT NULL DEFAULT '0',
  `pause_on_process_suspend` tinyint NOT NULL DEFAULT '1',
  `max_pause_minutes` int DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_sla_policy_version`
    (`policy_code`,`version`,`deleted`),
  KEY `idx_task_sla_policy_status`
    (`policy_code`,`status`,`deleted`,`version`),
  CONSTRAINT `chk_task_sla_policy_response`
    CHECK (`response_target_minutes` IS NULL
      OR `response_target_minutes` > 0),
  CONSTRAINT `chk_task_sla_policy_completion`
    CHECK (`completion_target_minutes` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户任务SLA策略版本';

CREATE TABLE `task_sla_escalation_step` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `policy_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `step_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `metric_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `trigger_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `offset_minutes` int NOT NULL DEFAULT '0',
  `repeat_interval_minutes` int DEFAULT NULL,
  `max_executions` int NOT NULL DEFAULT '1',
  `action_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `template_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recipient_config_json` longtext COLLATE utf8mb4_unicode_ci,
  `target_config_json` longtext COLLATE utf8mb4_unicode_ci,
  `sort_order` int NOT NULL DEFAULT '0',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_task_sla_step_policy` (`policy_id`,`enabled`,`sort_order`),
  CONSTRAINT `chk_task_sla_step_executions`
    CHECK (`max_executions` > 0),
  CONSTRAINT `chk_task_sla_step_repeat`
    CHECK (`repeat_interval_minutes` IS NULL
      OR `repeat_interval_minutes` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='SLA提醒与升级步骤';

CREATE TABLE `process_task_sla` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `process_definition_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `process_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `node_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `node_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `business_key` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entity_data_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `policy_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `policy_version` int NOT NULL,
  `policy_snapshot_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `calendar_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `calendar_version` int DEFAULT NULL,
  `calendar_snapshot_json` longtext COLLATE utf8mb4_unicode_ci,
  `timezone_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `current_assignee_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` datetime(6) NOT NULL,
  `responded_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `response_due_at` datetime(6) DEFAULT NULL,
  `completion_due_at` datetime(6) NOT NULL,
  `response_remaining_minutes` int DEFAULT NULL,
  `completion_remaining_minutes` int DEFAULT NULL,
  `response_status` varchar(20) COLLATE utf8mb4_unicode_ci
    NOT NULL DEFAULT 'PENDING',
  `completion_status` varchar(20) COLLATE utf8mb4_unicode_ci
    NOT NULL DEFAULT 'PENDING',
  `overall_status` varchar(20) COLLATE utf8mb4_unicode_ci
    NOT NULL DEFAULT 'RUNNING',
  `pause_started_at` datetime(6) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '1',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_task_sla_task` (`task_id`),
  KEY `idx_process_task_sla_process` (`process_instance_id`,`node_id`),
  KEY `idx_process_task_sla_response`
    (`response_status`,`response_due_at`),
  KEY `idx_process_task_sla_completion`
    (`completion_status`,`completion_due_at`),
  KEY `idx_process_task_sla_assignee`
    (`current_assignee_id`,`overall_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户任务SLA运行台账';

CREATE TABLE `process_task_sla_pause` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sla_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `pause_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operator_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` datetime(6) NOT NULL,
  `resumed_at` datetime(6) DEFAULT NULL,
  `duration_seconds` bigint DEFAULT NULL,
  `response_remaining_minutes` int DEFAULT NULL,
  `completion_remaining_minutes` int DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_process_task_sla_pause`
    (`sla_id`,`started_at`,`resumed_at`),
  KEY `idx_process_task_sla_pause_task` (`task_id`,`resumed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户任务SLA暂停历史';

CREATE TABLE `process_task_sla_event` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sla_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `step_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `event_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `metric_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `trigger_at` datetime(6) NOT NULL,
  `action_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `action_config_snapshot` longtext COLLATE utf8mb4_unicode_ci,
  `execution_no` int NOT NULL DEFAULT '1',
  `max_executions` int NOT NULL DEFAULT '1',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0',
  `max_retries` int NOT NULL DEFAULT '5',
  `next_retry_time` datetime(6) DEFAULT NULL,
  `owner_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_token` bigint NOT NULL DEFAULT '0',
  `lease_until` datetime(6) DEFAULT NULL,
  `idempotency_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_json` longtext COLLATE utf8mb4_unicode_ci,
  `error_message` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_task_sla_event_key` (`idempotency_key`),
  KEY `idx_process_task_sla_event_ready`
    (`status`,`trigger_at`,`next_retry_time`),
  KEY `idx_process_task_sla_event_lease`
    (`status`,`lease_until`),
  KEY `idx_process_task_sla_event_sla`
    (`sla_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='SLA到期事件与升级动作执行队列';

ALTER TABLE `process_task`
  ADD COLUMN `sla_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    AFTER `due_time`,
  ADD COLUMN `response_due_time` datetime(6) DEFAULT NULL
    AFTER `sla_status`,
  ADD KEY `idx_process_task_sla_status`
    (`sla_status`,`due_time`);

INSERT INTO `sys_menu` (
  `id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`,
  `path`, `component`, `perm`, `status`, `visible`, `is_frame`,
  `is_cache`, `keep_alive`, `breadcrumb`, `deleted`,
  `create_time`, `update_time`
) VALUES
  (
    'work_calendar_menu_001', '0', '工作日历', 'C', 'Calendar', 76,
    '/system/work-calendars', 'system/WorkCalendarManagement',
    'system:work-calendar:view', '0', '0', '0', '0', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  ),
  (
    'task_sla_policy_menu_001', '0', 'SLA策略', 'C', 'Timer', 77,
    '/process/sla-policies', 'process/TaskSlaPolicyManagement',
    'process:sla-policy:view', '0', '0', '0', '0', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  ),
  (
    'task_sla_monitor_menu_001', '0', 'SLA监控', 'C', 'DataAnalysis', 78,
    '/process/sla-monitor', 'process/TaskSlaMonitor',
    'process:sla:monitor', '0', '0', '0', '0', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  ),
  (
    'work_calendar_manage_perm_001', 'work_calendar_menu_001',
    '维护工作日历', 'F', NULL, 1, '', '',
    'system:work-calendar:manage', '0', '0', '0', '0', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  ),
  (
    'work_calendar_publish_perm_001', 'work_calendar_menu_001',
    '发布工作日历', 'F', NULL, 2, '', '',
    'system:work-calendar:publish', '0', '0', '0', '0', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  ),
  (
    'task_sla_policy_manage_perm_001', 'task_sla_policy_menu_001',
    '维护SLA策略', 'F', NULL, 1, '', '',
    'process:sla-policy:manage', '0', '0', '0', '0', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  ),
  (
    'task_sla_policy_publish_perm_001', 'task_sla_policy_menu_001',
    '发布SLA策略', 'F', NULL, 2, '', '',
    'process:sla-policy:publish', '0', '0', '0', '0', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  );

INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`)
SELECT MD5(CONCAT('1:', `id`)), '1', `id`, CURRENT_TIMESTAMP
FROM `sys_menu`
WHERE `id` IN (
  'work_calendar_menu_001',
  'task_sla_policy_menu_001',
  'task_sla_monitor_menu_001',
  'work_calendar_manage_perm_001',
  'work_calendar_publish_perm_001',
  'task_sla_policy_manage_perm_001',
  'task_sla_policy_publish_perm_001'
);
