ALTER TABLE `workflow_outbox_event`
  ADD COLUMN `owner_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    AFTER `status`,
  ADD COLUMN `lease_token` bigint NOT NULL DEFAULT '0' AFTER `owner_id`,
  ADD COLUMN `lease_until` datetime(6) DEFAULT NULL AFTER `lease_token`,
  ADD KEY `idx_workflow_outbox_lease` (`status`,`lease_until`);

UPDATE `workflow_outbox_event`
SET `status` = 'FAILED',
    `next_retry_time` = UTC_TIMESTAMP(6),
    `error_message` = 'LEASE_MIGRATION_RECOVERY',
    `owner_id` = NULL,
    `lease_until` = NULL,
    `update_time` = UTC_TIMESTAMP(6)
WHERE `status` = 'PROCESSING';
