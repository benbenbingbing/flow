ALTER TABLE `process_action_execution`
  ADD COLUMN `owner_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    AFTER `status`,
  ADD COLUMN `lease_token` bigint NOT NULL DEFAULT '0' AFTER `owner_id`,
  ADD COLUMN `lease_until` datetime(6) DEFAULT NULL AFTER `lease_token`,
  ADD KEY `idx_process_action_execution_lease` (`status`,`lease_until`);

UPDATE `process_action_execution`
SET `status` = 'FAILED',
    `next_retry_time` = UTC_TIMESTAMP(6),
    `error_message` = 'LEASE_MIGRATION_RECOVERY',
    `owner_id` = NULL,
    `lease_until` = NULL,
    `update_time` = UTC_TIMESTAMP(6)
WHERE `status` = 'RUNNING';
