ALTER TABLE `entity_process_link`
  ADD COLUMN `ended_at` datetime(6) DEFAULT NULL AFTER `entity_status`,
  DROP INDEX `idx_entity_process_instance`,
  ADD UNIQUE KEY `uk_entity_process_instance` (`process_instance_id`);

CREATE TABLE `process_status_sync_event` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_sequence` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_code` varchar(63) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_record_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_status` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status_category` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `state` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `applied_at` datetime(6) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_status_sync_event`
    (`process_instance_id`,`event_type`,`event_sequence`),
  KEY `idx_process_status_sync_entity`
    (`entity_code`,`entity_record_id`,`create_time`),
  KEY `idx_process_status_sync_state` (`state`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='流程到实体状态同步幂等审计';
