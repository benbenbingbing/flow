ALTER TABLE `integration_process_binding`
  ADD UNIQUE KEY `uk_integration_binding_global_instance`
    (`process_instance_id`);

CREATE TABLE `webhook_endpoint` (
  `id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `endpoint_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `endpoint_url` varchar(2048) COLLATE utf8mb4_bin NOT NULL,
  `endpoint_hash` char(64) COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL,
  `secret_ciphertext` varchar(1024) COLLATE utf8mb4_bin NOT NULL,
  `secret_version` bigint NOT NULL DEFAULT '1',
  `secret_hint` char(8) COLLATE utf8mb4_bin NOT NULL,
  `previous_secret_ciphertext` varchar(1024) COLLATE utf8mb4_bin DEFAULT NULL,
  `previous_secret_version` bigint DEFAULT NULL,
  `previous_secret_valid_until` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `updated_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_webhook_endpoint_application_id`
    (`id`,`application_id`),
  UNIQUE KEY `uk_webhook_endpoint_url`
    (`application_id`,`endpoint_hash`),
  KEY `idx_webhook_endpoint_application`
    (`application_id`,`status`,`create_time`),
  CONSTRAINT `fk_webhook_endpoint_application`
    FOREIGN KEY (`application_id`)
    REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_webhook_endpoint_status`
    CHECK (`status` IN ('ACTIVE','DISABLED')),
  CONSTRAINT `chk_webhook_endpoint_hash`
    CHECK (`endpoint_hash` REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT `chk_webhook_endpoint_secret_version`
    CHECK (
      `secret_version` > 0
      AND (
        (`previous_secret_version` IS NULL
          AND `previous_secret_ciphertext` IS NULL
          AND `previous_secret_valid_until` IS NULL)
        OR
        (`previous_secret_version` > 0
          AND `previous_secret_version` < `secret_version`
          AND `previous_secret_ciphertext` IS NOT NULL
          AND `previous_secret_valid_until` IS NOT NULL)
      )
    ),
  CONSTRAINT `chk_webhook_endpoint_version`
    CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Managed webhook destinations';

CREATE TABLE `webhook_subscription` (
  `id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `endpoint_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `event_type` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL,
  `created_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `updated_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_webhook_subscription_application_id`
    (`id`,`application_id`),
  UNIQUE KEY `uk_webhook_subscription_event`
    (`endpoint_id`,`event_type`),
  KEY `idx_webhook_subscription_dispatch`
    (`application_id`,`event_type`,`status`),
  CONSTRAINT `fk_webhook_subscription_application`
    FOREIGN KEY (`application_id`)
    REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_webhook_subscription_endpoint`
    FOREIGN KEY (`endpoint_id`,`application_id`)
    REFERENCES `webhook_endpoint` (`id`,`application_id`)
    ON DELETE CASCADE,
  CONSTRAINT `chk_webhook_subscription_status`
    CHECK (`status` IN ('ACTIVE','DISABLED')),
  CONSTRAINT `chk_webhook_subscription_event_type`
    CHECK (`event_type` IN (
      'com.flow.process.started.v1',
      'com.flow.task.created.v1',
      'com.flow.task.completed.v1',
      'com.flow.process.completed.v1',
      'com.flow.process.terminated.v1',
      'com.flow.process.failed.v1'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Application webhook event subscriptions';

CREATE TABLE `webhook_event` (
  `event_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `source_event_key` varchar(191) COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `event_type` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `subject` varchar(191) COLLATE utf8mb4_bin NOT NULL,
  `process_instance_id` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `trace_id` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `payload_document` longtext COLLATE utf8mb4_bin NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_webhook_event_application_id`
    (`event_id`,`application_id`),
  UNIQUE KEY `uk_webhook_event_source` (`source_event_key`),
  KEY `idx_webhook_event_application`
    (`application_id`,`occurred_at`,`event_id`),
  KEY `idx_webhook_event_expiry` (`expires_at`),
  CONSTRAINT `fk_webhook_event_application`
    FOREIGN KEY (`application_id`)
    REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_webhook_event_payload`
    CHECK (
      JSON_VALID(`payload_document`)
      AND CHAR_LENGTH(`payload_document`) <= 262144
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Materialized stable CloudEvents';

CREATE TABLE `webhook_delivery` (
  `id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `subscription_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `event_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `replay_sequence` int NOT NULL DEFAULT '0',
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `max_attempts` int NOT NULL DEFAULT '8',
  `next_attempt_at` datetime(6) NOT NULL,
  `owner_id` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `lease_token` bigint NOT NULL DEFAULT '0',
  `lease_until` datetime(6) DEFAULT NULL,
  `signing_secret_ciphertext` varchar(1024) COLLATE utf8mb4_bin NOT NULL,
  `signing_secret_version` bigint NOT NULL,
  `response_status` smallint DEFAULT NULL,
  `response_body_excerpt` text
    COLLATE utf8mb4_unicode_ci,
  `error_code` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_attempt_at` datetime(6) DEFAULT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_webhook_delivery_replay`
    (`subscription_id`,`event_id`,`replay_sequence`),
  KEY `idx_webhook_delivery_ready`
    (`status`,`next_attempt_at`,`lease_until`),
  KEY `idx_webhook_delivery_event`
    (`event_id`,`create_time`),
  KEY `idx_webhook_delivery_application`
    (`application_id`,`status`,`create_time`),
  CONSTRAINT `fk_webhook_delivery_application`
    FOREIGN KEY (`application_id`)
    REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_webhook_delivery_subscription`
    FOREIGN KEY (`subscription_id`,`application_id`)
    REFERENCES `webhook_subscription` (`id`,`application_id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_webhook_delivery_event`
    FOREIGN KEY (`event_id`,`application_id`)
    REFERENCES `webhook_event` (`event_id`,`application_id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_webhook_delivery_status`
    CHECK (`status` IN (
      'PENDING','PROCESSING','RETRY','SUCCEEDED','DEAD'
    )),
  CONSTRAINT `chk_webhook_delivery_attempts`
    CHECK (
      `attempt_count` >= 0
      AND `max_attempts` BETWEEN 1 AND 20
      AND `attempt_count` <= `max_attempts`
    ),
  CONSTRAINT `chk_webhook_delivery_replay_sequence`
    CHECK (`replay_sequence` >= 0),
  CONSTRAINT `chk_webhook_delivery_lease`
    CHECK (
      (`status` = 'PROCESSING'
        AND `owner_id` IS NOT NULL
        AND `lease_until` IS NOT NULL
        AND `lease_token` > 0)
      OR
      (`status` <> 'PROCESSING'
        AND `owner_id` IS NULL
        AND `lease_until` IS NULL)
    ),
  CONSTRAINT `chk_webhook_delivery_result`
    CHECK (
      (`status` = 'SUCCEEDED'
        AND `delivered_at` IS NOT NULL
        AND `response_status` BETWEEN 200 AND 299)
      OR
      (`status` <> 'SUCCEEDED' AND `delivered_at` IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='At-least-once webhook deliveries';
