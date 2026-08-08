ALTER TABLE `integration_process_binding`
  ADD COLUMN `identity_mapping_snapshot_json` longtext COLLATE utf8mb4_bin DEFAULT NULL
    AFTER `identity_namespace`,
  ADD CONSTRAINT `chk_integration_binding_identity_snapshot`
    CHECK (`identity_mapping_snapshot_json` IS NULL
      OR (JSON_VALID(`identity_mapping_snapshot_json`)
          AND CHAR_LENGTH(`identity_mapping_snapshot_json`) <= 16384));
