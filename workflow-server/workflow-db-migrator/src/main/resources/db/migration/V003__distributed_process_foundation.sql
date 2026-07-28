CREATE TABLE `entity_process_link` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_code` varchar(63) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_record_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `generation` int NOT NULL DEFAULT '1',
  `process_definition_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `state` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_status` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_process_generation`
    (`entity_code`,`entity_record_id`,`generation`),
  UNIQUE KEY `uk_entity_process_request` (`request_id`),
  KEY `idx_entity_process_instance` (`process_instance_id`),
  KEY `idx_entity_process_state` (`state`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='实体与流程实例原子关联';
