CREATE TABLE `process_task_add_sign` (
  `id` varchar(64) NOT NULL,
  `process_instance_id` varchar(64) NOT NULL,
  `source_task_id` varchar(64) NOT NULL,
  `node_id` varchar(100) DEFAULT NULL,
  `operation_type` varchar(20) NOT NULL DEFAULT 'PARALLEL',
  `operator_id` varchar(64) NOT NULL,
  `comment` varchar(1000) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `engine_execution_id` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `complete_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_add_sign_source_task` (`source_task_id`,`status`),
  KEY `idx_add_sign_process` (`process_instance_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行时加签记录';

CREATE TABLE `process_task_add_sign_user` (
  `id` varchar(64) NOT NULL,
  `add_sign_id` varchar(64) NOT NULL,
  `user_id` varchar(64) NOT NULL,
  `user_name_snapshot` varchar(100) DEFAULT NULL,
  `generated_task_id` varchar(64) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'TODO',
  `sort_order` int NOT NULL DEFAULT 0,
  `complete_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_add_sign_user` (`add_sign_id`,`user_id`),
  UNIQUE KEY `uk_add_sign_generated_task` (`generated_task_id`),
  KEY `idx_add_sign_user_status` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行时加签人员';

ALTER TABLE `process_cc_record`
  ADD COLUMN `operator_id` varchar(64) DEFAULT NULL COMMENT '操作人ID' AFTER `cc_timing`,
  ADD COLUMN `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人名称' AFTER `operator_id`,
  ADD COLUMN `comment` varchar(1000) DEFAULT NULL COMMENT '知会备注' AFTER `operator_name`,
  ADD COLUMN `source_task_id` varchar(64) DEFAULT NULL COMMENT '来源任务ID' AFTER `comment`,
  ADD COLUMN `source_type` varchar(20) DEFAULT NULL COMMENT '来源类型' AFTER `source_task_id`,
  ADD COLUMN `recipient_rule_snapshot` text DEFAULT NULL COMMENT '收件人规则快照' AFTER `source_type`,
  ADD COLUMN `unique_key` varchar(255) DEFAULT NULL COMMENT '幂等键' AFTER `recipient_rule_snapshot`,
  ADD UNIQUE KEY `uk_process_cc_unique_key` (`unique_key`),
  ADD KEY `idx_process_cc_source_task` (`source_task_id`);
