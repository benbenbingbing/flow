ALTER TABLE `integration_process_binding`
  ADD COLUMN `business_version` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL
    AFTER `business_id`,
  ADD COLUMN `identity_namespace` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL
    AFTER `external_initiator_id`,
  ADD CONSTRAINT `chk_integration_binding_business_version`
    CHECK (`business_version` IS NULL OR CHAR_LENGTH(`business_version`) > 0),
  ADD CONSTRAINT `chk_integration_binding_identity_namespace`
    CHECK (`identity_namespace` IS NULL OR CHAR_LENGTH(`identity_namespace`) > 0);
