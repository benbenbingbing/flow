CREATE TABLE `integration_application` (
  `id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `client_id` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `application_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `owner_organization_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE',
  `rate_limit_per_minute` int NOT NULL DEFAULT '60',
  `max_concurrency` int NOT NULL DEFAULT '10',
  `allowed_source_cidrs` longtext COLLATE utf8mb4_unicode_ci,
  `expires_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `updated_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_application_client_id` (`client_id`),
  KEY `idx_integration_application_owner`
    (`owner_organization_id`,`status`),
  KEY `idx_integration_application_status_expiry`
    (`status`,`expires_at`),
  CONSTRAINT `chk_integration_application_status`
    CHECK (`status` IN ('ACTIVE','DISABLED','REVOKED')),
  CONSTRAINT `chk_integration_application_rate_limit`
    CHECK (`rate_limit_per_minute` BETWEEN 1 AND 10000),
  CONSTRAINT `chk_integration_application_concurrency`
    CHECK (`max_concurrency` BETWEEN 1 AND 1000),
  CONSTRAINT `chk_integration_application_cidrs`
    CHECK (`allowed_source_cidrs` IS NULL
      OR JSON_VALID(`allowed_source_cidrs`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Open integration applications';

CREATE TABLE `integration_application_credential` (
  `id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `secret_hash` varchar(255) COLLATE utf8mb4_bin NOT NULL,
  `credential_hint` varchar(12) COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE',
  `credential_version` bigint NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `last_used_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `revoked_by` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  `active_application_id` varchar(64) COLLATE utf8mb4_bin
    GENERATED ALWAYS AS (
      CASE WHEN `status` = 'ACTIVE' THEN `application_id` ELSE NULL END
    ) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_credential_active`
    (`active_application_id`),
  UNIQUE KEY `uk_integration_credential_version`
    (`application_id`,`credential_version`),
  KEY `idx_integration_credential_application`
    (`application_id`,`status`,`create_time`),
  CONSTRAINT `chk_integration_credential_status`
    CHECK (`status` IN ('ACTIVE','REVOKED')),
  CONSTRAINT `chk_integration_credential_revocation`
    CHECK (
      (`status` = 'ACTIVE' AND `revoked_at` IS NULL
        AND `revoked_by` IS NULL)
      OR
      (`status` = 'REVOKED' AND `revoked_at` IS NOT NULL
        AND `revoked_by` IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Argon2id credential hashes';

CREATE TABLE `integration_application_scope` (
  `application_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `scope` varchar(100) COLLATE utf8mb4_bin NOT NULL,
  `granted_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`application_id`,`scope`),
  KEY `idx_integration_scope_scope` (`scope`,`application_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Application OAuth scopes';

CREATE TABLE `integration_process_grant` (
  `application_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `process_key` varchar(100) COLLATE utf8mb4_bin NOT NULL,
  `granted_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`application_id`,`process_key`),
  KEY `idx_integration_process_grant_process`
    (`process_key`,`application_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Application process grants';

CREATE TABLE `integration_rate_limit_bucket` (
  `bucket_key` char(64) COLLATE utf8mb4_bin NOT NULL,
  `window_epoch` bigint NOT NULL,
  `request_count` int NOT NULL DEFAULT '0',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`bucket_key`,`window_epoch`),
  KEY `idx_integration_rate_bucket_updated` (`update_time`),
  CONSTRAINT `chk_integration_rate_bucket_count`
    CHECK (`request_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Cross-Pod integration rate limits';

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, sort, perm,
  status, visible, deleted, create_time, update_time
) VALUES
  ('integration_perm_view', '0', 'View integration applications', 'F', 0,
    'system:integration:view', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('integration_perm_manage', '0', 'Manage integration applications', 'F', 0,
    'system:integration:manage', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('integration_perm_secret_rotate', '0', 'Rotate integration credentials',
    'F', 0, 'system:integration:secret-rotate', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('integration_perm_delivery_replay', '0', 'Replay integration deliveries',
    'F', 0, 'system:integration:delivery-replay', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT MD5(CONCAT('1:', id)), '1', id, CURRENT_TIMESTAMP
FROM sys_menu
WHERE id LIKE 'integration_perm_%';
