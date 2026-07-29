ALTER TABLE `integration_application_credential`
  ADD CONSTRAINT `fk_integration_credential_application`
    FOREIGN KEY (`application_id`)
    REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT;

ALTER TABLE `integration_application_scope`
  ADD CONSTRAINT `fk_integration_scope_application`
    FOREIGN KEY (`application_id`)
    REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT;

ALTER TABLE `integration_process_grant`
  ADD COLUMN `input_schema_json` longtext COLLATE utf8mb4_bin NOT NULL
    DEFAULT ('{"type":"object","maxProperties":0,"additionalProperties":false}')
    AFTER `process_key`,
  ADD COLUMN `allowed_message_keys` longtext COLLATE utf8mb4_bin NOT NULL
    DEFAULT ('[]')
    AFTER `input_schema_json`,
  ADD CONSTRAINT `fk_integration_process_grant_application`
    FOREIGN KEY (`application_id`)
    REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT,
  ADD CONSTRAINT `chk_integration_process_grant_schema`
    CHECK (
      JSON_VALID(`input_schema_json`)
      AND CHAR_LENGTH(`input_schema_json`) <= 65535
    ),
  ADD CONSTRAINT `chk_integration_process_grant_messages`
    CHECK (
      JSON_VALID(`allowed_message_keys`)
      AND CHAR_LENGTH(`allowed_message_keys`) <= 8192
    );

CREATE TABLE `integration_idempotency_record` (
  `id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `operation` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `idempotency_key` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `request_hash` char(64) COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(24) COLLATE utf8mb4_bin NOT NULL,
  `resource_type` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `resource_id` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `response_status` smallint DEFAULT NULL,
  `response_body` longtext COLLATE utf8mb4_bin,
  `fencing_token` bigint NOT NULL DEFAULT '1',
  `processing_started_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_idempotency_operation`
    (`application_id`,`operation`,`idempotency_key`),
  KEY `idx_integration_idempotency_expiry`
    (`expires_at`,`status`),
  KEY `idx_integration_idempotency_resource`
    (`application_id`,`resource_type`,`resource_id`),
  CONSTRAINT `fk_integration_idempotency_application`
    FOREIGN KEY (`application_id`)
    REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_integration_idempotency_status`
    CHECK (`status` IN (
      'PROCESSING','SUCCEEDED','FAILED_RETRYABLE'
    )),
  CONSTRAINT `chk_integration_idempotency_hash`
    CHECK (`request_hash` REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT `chk_integration_idempotency_fencing`
    CHECK (`fencing_token` > 0),
  CONSTRAINT `chk_integration_idempotency_response`
    CHECK (
      (`status` = 'SUCCEEDED'
        AND `resource_type` IS NOT NULL
        AND `resource_id` IS NOT NULL
        AND `response_status` BETWEEN 200 AND 299
        AND `response_body` IS NOT NULL
        AND JSON_VALID(`response_body`))
      OR
      (`status` <> 'SUCCEEDED'
        AND `response_status` IS NULL
        AND `response_body` IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Application-scoped write idempotency';

CREATE TABLE `integration_process_binding` (
  `id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `external_system` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `business_type` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `business_id` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `process_instance_id` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `process_definition_key` varchar(100) COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_binding_business`
    (`application_id`,`external_system`,`business_type`,`business_id`),
  UNIQUE KEY `uk_integration_binding_instance`
    (`application_id`,`process_instance_id`),
  KEY `idx_integration_binding_instance`
    (`process_instance_id`),
  KEY `idx_integration_binding_process`
    (`application_id`,`process_definition_key`,`create_time`),
  CONSTRAINT `fk_integration_binding_application`
    FOREIGN KEY (`application_id`)
    REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='External business to process bindings';

CREATE TABLE `integration_api_request_lease` (
  `lease_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`lease_id`),
  KEY `idx_integration_api_lease_application`
    (`application_id`,`expires_at`),
  KEY `idx_integration_api_lease_expiry`
    (`expires_at`),
  CONSTRAINT `fk_integration_api_lease_application`
    FOREIGN KEY (`application_id`)
    REFERENCES `integration_application` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Cross-Pod open API concurrency leases';
