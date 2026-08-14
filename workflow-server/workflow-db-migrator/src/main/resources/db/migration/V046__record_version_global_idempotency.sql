-- 同一实体记录的一次业务固化请求只能生成一个数据版本，不能因触发器编码不同重复生成。
-- BusinessMigrationPreflight 在 Flyway 写迁移历史前检查历史重复键；
-- 冲突版本必须人工核对，禁止静默删除审计数据。

ALTER TABLE `entity_record_version`
  DROP INDEX `uk_entity_record_version_idempotent`,
  ADD UNIQUE KEY `uk_entity_record_version_idempotent`
    (`entity_code`, `record_id`, `idempotency_key`);
