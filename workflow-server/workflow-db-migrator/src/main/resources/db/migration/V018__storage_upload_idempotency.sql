ALTER TABLE `storage_file_object`
  ADD COLUMN `idempotency_key` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL
    AFTER `owner_user_id`,
  ADD COLUMN `request_hash` char(64) COLLATE utf8mb4_bin DEFAULT NULL
    AFTER `idempotency_key`,
  ADD UNIQUE KEY `uk_storage_file_owner_idempotency`
    (`owner_user_id`, `idempotency_key`),
  ADD CONSTRAINT `chk_storage_file_idempotency`
    CHECK (
      (`idempotency_key` IS NULL AND `request_hash` IS NULL)
      OR
      (`idempotency_key` IS NOT NULL
        AND `request_hash` REGEXP '^[0-9a-f]{64}$')
    );
