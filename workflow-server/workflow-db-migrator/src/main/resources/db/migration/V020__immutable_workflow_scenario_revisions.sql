CREATE TABLE `integration_workflow_scenario_revision` (
  `id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `scenario_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `revision` bigint NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'DRAFT',
  `display_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `process_key` varchar(100) COLLATE utf8mb4_bin NOT NULL,
  `process_definition_version` int DEFAULT NULL,
  `input_schema_json` longtext COLLATE utf8mb4_bin NOT NULL,
  `outcome_mapping_json` longtext COLLATE utf8mb4_bin NOT NULL,
  `identity_mapping_json` longtext COLLATE utf8mb4_bin NOT NULL,
  `event_types_json` longtext COLLATE utf8mb4_bin NOT NULL,
  `config_hash` char(64) COLLATE utf8mb4_bin NOT NULL,
  `created_by` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `published_by` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  `published_time` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_scenario_revision`
    (`scenario_id`,`revision`),
  KEY `idx_integration_scenario_revision_status`
    (`scenario_id`,`status`,`revision`),
  CONSTRAINT `fk_integration_scenario_revision_scenario`
    FOREIGN KEY (`scenario_id`)
    REFERENCES `integration_workflow_scenario` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_integration_scenario_revision_status`
    CHECK (`status` IN ('DRAFT','PUBLISHED','RETIRED')),
  CONSTRAINT `chk_integration_scenario_revision_number`
    CHECK (`revision` > 0),
  CONSTRAINT `chk_integration_scenario_revision_version`
    CHECK (`process_definition_version` IS NULL
      OR `process_definition_version` > 0),
  CONSTRAINT `chk_integration_scenario_revision_json`
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
  CONSTRAINT `chk_integration_scenario_revision_hash`
    CHECK (`config_hash` REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Immutable external workflow scenario revisions';

ALTER TABLE `integration_workflow_scenario`
  DROP CHECK `chk_integration_scenario_status`,
  ADD COLUMN `published_revision` bigint DEFAULT NULL
    AFTER `revision`,
  ADD COLUMN `draft_revision` bigint DEFAULT NULL
    AFTER `published_revision`,
  ADD CONSTRAINT `chk_integration_scenario_status_v020`
    CHECK (`status` IN ('DRAFT','ACTIVE','DISABLED')),
  ADD CONSTRAINT `chk_integration_scenario_published_revision`
    CHECK (`published_revision` IS NULL OR `published_revision` > 0),
  ADD CONSTRAINT `chk_integration_scenario_draft_revision`
    CHECK (`draft_revision` IS NULL OR `draft_revision` > 0);

INSERT INTO `integration_workflow_scenario_revision` (
  `id`, `scenario_id`, `revision`, `status`, `display_name`, `process_key`,
  `process_definition_version`, `input_schema_json`, `outcome_mapping_json`,
  `identity_mapping_json`, `event_types_json`, `config_hash`, `created_by`,
  `published_by`, `create_time`, `published_time`
)
SELECT CONCAT(id, ':', revision), id, revision, 'PUBLISHED', display_name,
       process_key, process_definition_version, input_schema_json,
       outcome_mapping_json, identity_mapping_json, event_types_json,
       config_hash, created_by, updated_by, create_time, update_time
  FROM `integration_workflow_scenario`
 WHERE status = 'ACTIVE';

UPDATE `integration_workflow_scenario`
   SET published_revision = revision,
       draft_revision = NULL
 WHERE status = 'ACTIVE';
