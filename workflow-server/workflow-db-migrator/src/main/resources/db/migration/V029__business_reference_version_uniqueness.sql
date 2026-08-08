ALTER TABLE `integration_process_binding`
  DROP INDEX `uk_integration_binding_business`,
  ADD COLUMN `business_version_key` varchar(128) COLLATE utf8mb4_bin
    GENERATED ALWAYS AS (COALESCE(`business_version`, '')) STORED
    AFTER `business_version`,
  ADD UNIQUE KEY `uk_integration_binding_business`
    (`application_id`, `external_system`, `business_type`, `business_id`,
     `business_version_key`);
