CREATE TABLE `integration_secret` (
  `id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `secret_name` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `secret_version` bigint NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE',
  `key_version` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `encrypted_data_key` varchar(1024) COLLATE utf8mb4_bin DEFAULT NULL,
  `data_key_nonce` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `secret_ciphertext` longtext COLLATE utf8mb4_bin,
  `secret_nonce` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `secret_hint` varchar(12) COLLATE utf8mb4_bin NOT NULL,
  `created_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `revoked_by` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `destroyed_by` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `destroyed_at` datetime(6) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  `active_application_id` varchar(64) COLLATE utf8mb4_bin
    GENERATED ALWAYS AS (
      CASE WHEN `status` = 'ACTIVE' THEN `application_id` ELSE NULL END
    ) STORED,
  `active_secret_name` varchar(64) COLLATE utf8mb4_bin
    GENERATED ALWAYS AS (
      CASE WHEN `status` = 'ACTIVE' THEN `secret_name` ELSE NULL END
    ) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_secret_version`
    (`application_id`,`secret_name`,`secret_version`),
  UNIQUE KEY `uk_integration_secret_active`
    (`active_application_id`,`active_secret_name`),
  UNIQUE KEY `uk_integration_secret_id_application`
    (`id`,`application_id`),
  KEY `idx_integration_secret_application`
    (`application_id`,`status`,`create_time`),
  CONSTRAINT `fk_integration_secret_application`
    FOREIGN KEY (`application_id`)
    REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_integration_secret_name`
    CHECK (
      CHAR_LENGTH(`secret_name`) BETWEEN 1 AND 64
      AND `secret_name` REGEXP '^[A-Za-z][A-Za-z0-9._-]*$'
    ),
  CONSTRAINT `chk_integration_secret_version`
    CHECK (`secret_version` > 0),
  CONSTRAINT `chk_integration_secret_status`
    CHECK (`status` IN ('ACTIVE','REVOKED','DESTROYED')),
  CONSTRAINT `chk_integration_secret_material`
    CHECK (
      (`status` IN ('ACTIVE','REVOKED')
        AND `key_version` IS NOT NULL
        AND `encrypted_data_key` IS NOT NULL
        AND `data_key_nonce` IS NOT NULL
        AND `secret_ciphertext` IS NOT NULL
        AND `secret_nonce` IS NOT NULL
        AND CHAR_LENGTH(`secret_ciphertext`) <= 131072)
      OR
      (`status` = 'DESTROYED'
        AND `key_version` IS NULL
        AND `encrypted_data_key` IS NULL
        AND `data_key_nonce` IS NULL
        AND `secret_ciphertext` IS NULL
        AND `secret_nonce` IS NULL)
    ),
  CONSTRAINT `chk_integration_secret_lifecycle`
    CHECK (
      (`status` = 'ACTIVE'
        AND `revoked_by` IS NULL AND `revoked_at` IS NULL
        AND `destroyed_by` IS NULL AND `destroyed_at` IS NULL)
      OR
      (`status` = 'REVOKED'
        AND `revoked_by` IS NOT NULL AND `revoked_at` IS NOT NULL
        AND `destroyed_by` IS NULL AND `destroyed_at` IS NULL)
      OR
      (`status` = 'DESTROYED'
        AND `revoked_by` IS NOT NULL AND `revoked_at` IS NOT NULL
        AND `destroyed_by` IS NOT NULL AND `destroyed_at` IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Envelope-encrypted integration secrets';

CREATE TABLE `integration_connector_config` (
  `id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `config_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `connector_code` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE',
  `configuration_document` longtext COLLATE utf8mb4_bin NOT NULL,
  `allowed_hosts_document` longtext COLLATE utf8mb4_bin NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `updated_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_connector_name`
    (`application_id`,`config_name`),
  UNIQUE KEY `uk_integration_connector_id_application`
    (`id`,`application_id`),
  KEY `idx_integration_connector_application`
    (`application_id`,`status`,`connector_code`),
  CONSTRAINT `fk_integration_connector_application`
    FOREIGN KEY (`application_id`)
    REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_integration_connector_code`
    CHECK (`connector_code` = 'http-json'),
  CONSTRAINT `chk_integration_connector_status`
    CHECK (`status` IN ('ACTIVE','DISABLED')),
  CONSTRAINT `chk_integration_connector_configuration`
    CHECK (
      JSON_VALID(`configuration_document`)
      AND JSON_TYPE(`configuration_document`) = 'OBJECT'
      AND CHAR_LENGTH(`configuration_document`) <= 262144
    ),
  CONSTRAINT `chk_integration_connector_hosts`
    CHECK (
      JSON_VALID(`allowed_hosts_document`)
      AND JSON_TYPE(`allowed_hosts_document`) = 'ARRAY'
      AND JSON_LENGTH(`allowed_hosts_document`) BETWEEN 1 AND 100
      AND CHAR_LENGTH(`allowed_hosts_document`) <= 16384
    ),
  CONSTRAINT `chk_integration_connector_version`
    CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Application-owned connector configurations';
