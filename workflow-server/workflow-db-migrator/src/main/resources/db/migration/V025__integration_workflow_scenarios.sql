CREATE TABLE `integration_workflow_scenario` (
  `id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `scenario_key` varchar(100) COLLATE utf8mb4_bin NOT NULL,
  `display_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `process_key` varchar(100) COLLATE utf8mb4_bin NOT NULL,
  `process_definition_version` int DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE',
  `input_schema_json` longtext COLLATE utf8mb4_bin NOT NULL,
  `outcome_mapping_json` longtext COLLATE utf8mb4_bin NOT NULL,
  `identity_mapping_json` longtext COLLATE utf8mb4_bin NOT NULL,
  `event_types_json` longtext COLLATE utf8mb4_bin NOT NULL,
  `revision` bigint NOT NULL DEFAULT '1',
  `config_hash` char(64) COLLATE utf8mb4_bin NOT NULL,
  `created_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `updated_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_scenario_application_key`
    (`application_id`,`scenario_key`),
  KEY `idx_integration_scenario_process`
    (`application_id`,`process_key`,`status`),
  CONSTRAINT `fk_integration_scenario_application`
    FOREIGN KEY (`application_id`)
    REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_integration_scenario_status`
    CHECK (`status` IN ('ACTIVE','DISABLED')),
  CONSTRAINT `chk_integration_scenario_version`
    CHECK (`process_definition_version` IS NULL
      OR `process_definition_version` > 0),
  CONSTRAINT `chk_integration_scenario_revision`
    CHECK (`revision` > 0),
  CONSTRAINT `chk_integration_scenario_json`
    CHECK (
      JSON_VALID(`input_schema_json`)
      AND JSON_VALID(`outcome_mapping_json`)
      AND JSON_VALID(`identity_mapping_json`)
      AND JSON_VALID(`event_types_json`)
      AND CHAR_LENGTH(`input_schema_json`) <= 65535
      AND CHAR_LENGTH(`outcome_mapping_json`) <= 16384
      AND CHAR_LENGTH(`identity_mapping_json`) <= 16384
      AND CHAR_LENGTH(`event_types_json`) <= 8192
    ),
  CONSTRAINT `chk_integration_scenario_hash`
    CHECK (`config_hash` REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Configurable external workflow scenarios';

ALTER TABLE `integration_process_binding`
  ADD COLUMN `scenario_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL
    AFTER `application_id`,
  ADD COLUMN `scenario_key` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL
    AFTER `scenario_id`,
  ADD COLUMN `scenario_revision` bigint DEFAULT NULL
    AFTER `scenario_key`,
  ADD COLUMN `scenario_config_hash` char(64) COLLATE utf8mb4_bin DEFAULT NULL
    AFTER `scenario_revision`,
  ADD COLUMN `input_snapshot_json` longtext COLLATE utf8mb4_bin DEFAULT NULL
    AFTER `process_definition_key`,
  ADD COLUMN `input_hash` char(64) COLLATE utf8mb4_bin DEFAULT NULL
    AFTER `input_snapshot_json`,
  ADD COLUMN `outcome_mapping_snapshot_json` longtext COLLATE utf8mb4_bin DEFAULT NULL
    AFTER `input_hash`,
  ADD COLUMN `event_types_snapshot_json` longtext COLLATE utf8mb4_bin DEFAULT NULL
    AFTER `outcome_mapping_snapshot_json`,
  ADD COLUMN `external_initiator_id` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL
    AFTER `event_types_snapshot_json`,
  ADD KEY `idx_integration_binding_scenario`
    (`application_id`,`scenario_key`,`create_time`),
  ADD CONSTRAINT `fk_integration_binding_scenario`
    FOREIGN KEY (`scenario_id`)
    REFERENCES `integration_workflow_scenario` (`id`)
    ON DELETE RESTRICT,
  ADD CONSTRAINT `chk_integration_binding_snapshot`
    CHECK (
      (`input_snapshot_json` IS NULL AND `input_hash` IS NULL)
      OR
      (`input_snapshot_json` IS NOT NULL
       AND JSON_VALID(`input_snapshot_json`)
       AND `input_hash` REGEXP '^[0-9a-f]{64}$'
       AND CHAR_LENGTH(`input_snapshot_json`) <= 262144)
    ),
  ADD CONSTRAINT `chk_integration_binding_scenario_snapshot`
    CHECK (
      (`scenario_id` IS NULL
       AND `outcome_mapping_snapshot_json` IS NULL
       AND `event_types_snapshot_json` IS NULL)
      OR
      (`scenario_id` IS NOT NULL
       AND JSON_VALID(`outcome_mapping_snapshot_json`)
       AND JSON_VALID(`event_types_snapshot_json`)
       AND CHAR_LENGTH(`outcome_mapping_snapshot_json`) <= 16384
       AND CHAR_LENGTH(`event_types_snapshot_json`) <= 8192)
    );
