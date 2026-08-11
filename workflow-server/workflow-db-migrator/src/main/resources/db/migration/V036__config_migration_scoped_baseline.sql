-- Allow full-asset and fine-grained migration baselines to coexist.
ALTER TABLE `config_asset_baseline`
  ADD COLUMN `scope_key` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FULL'
    AFTER `business_key`,
  DROP INDEX `uk_asset_baseline`,
  ADD UNIQUE KEY `uk_asset_baseline_scope` (`asset_type`, `business_key`, `scope_key`);
